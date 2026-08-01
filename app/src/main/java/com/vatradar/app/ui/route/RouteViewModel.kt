package com.vatradar.app.ui.route

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vatradar.app.data.local.ChallengeEntity
import com.vatradar.app.data.repository.ChallengeRepository
import com.vatradar.app.data.repository.Outcome
import com.vatradar.app.data.repository.RandomRoute
import com.vatradar.app.data.repository.WeatherReport
import com.vatradar.app.di.ServiceLocator
import com.vatradar.app.domain.model.RouteFilter
import com.vatradar.app.domain.model.OfpSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RouteUiState(
    val filter: RouteFilter = RouteFilter(),
    val routePoolSize: Int = 0,
    /** 현재 대륙에서 고를 수 있는 나라 (코드 to 표시 이름). */
    val countries: List<Pair<String, String>> = emptyList(),
    val route: RandomRoute? = null,
    val rolling: Boolean = false,
    val error: String? = null,

    // 챌린지
    val completedCount: Int = 0,
    val activeChallenges: List<ChallengeEntity> = emptyList(),
    val vatsimCid: String = "",
    val justCompleted: List<ChallengeEntity> = emptyList(),

    // F6 — 뽑힌 공항의 기상
    val originWeather: WeatherReport? = null,
    val destinationWeather: WeatherReport? = null,
    val weatherLoading: Boolean = false,

    // F5 — SimBrief
    val simBriefId: String = "",
    val ofp: OfpSummary? = null,
    val ofpLoading: Boolean = false,
    val ofpError: String? = null,
    val dispatchUrl: String? = null
)

class RouteViewModel(app: Application) : AndroidViewModel(app) {

    private val airportRepo = ServiceLocator.airportRepository(app)
    private val weatherRepo = ServiceLocator.weatherRepository()
    private val simBriefRepo = ServiceLocator.simBriefRepository()
    private val settingsRepo = ServiceLocator.settingsRepository(app)
    private val challengeRepo = ServiceLocator.challengeRepository(app)
    private val flightProgressRepo = ServiceLocator.flightProgressRepository(app)

    private val _uiState = MutableStateFlow(RouteUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val s = settingsRepo.current()
            _uiState.value = _uiState.value.copy(
                simBriefId = s.simBriefId,
                vatsimCid = s.vatsimCid
            )
            refreshFilterInfo()
            refreshChallengeState()
            syncFlightProgress()
        }
        viewModelScope.launch {
            challengeRepo.completedCount.collect {
                _uiState.value = _uiState.value.copy(completedCount = it)
            }
        }
    }

    private suspend fun refreshChallengeState() {
        _uiState.value = _uiState.value.copy(activeChallenges = challengeRepo.activeChallenges())
    }

    /** 선택한 범위의 후보 노선 수와 나라 목록을 갱신합니다. */
    private suspend fun refreshFilterInfo() {
        val filter = _uiState.value.filter
        _uiState.value = _uiState.value.copy(
            routePoolSize = airportRepo.routeCount(filter),
            countries = airportRepo.countries(filter.continent)
        )
    }

    fun setContinent(code: String?) {
        // 대륙이 바뀌면 이전에 고른 나라는 그 대륙에 없을 수 있으므로 함께 비웁니다.
        _uiState.value = _uiState.value.copy(filter = RouteFilter(continent = code))
        viewModelScope.launch { refreshFilterInfo() }
    }

    fun setCountry(code: String?) {
        _uiState.value = _uiState.value.copy(
            filter = _uiState.value.filter.copy(country = code)
        )
        viewModelScope.launch { refreshFilterInfo() }
    }

    /** 진행 중인 챌린지를 X로 지웁니다. */
    fun deleteChallenge(challengeId: Long) {
        viewModelScope.launch {
            challengeRepo.delete(settingsRepo.current().vatsimCid, challengeId)
            refreshChallengeState()
        }
    }

    /** 실시간 피드와 대조해 완주 여부를 갱신합니다. 화면에 들어올 때마다 확인합니다. */
    fun syncFlightProgress() {
        viewModelScope.launch {
            val cid = settingsRepo.current().vatsimCid
            if (cid.isBlank()) return@launch

            val completed = flightProgressRepo.sync(cid)
            refreshChallengeState()
            if (completed.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(justCompleted = completed)
            }
        }
    }

    fun consumeCompletionNotice() {
        _uiState.value = _uiState.value.copy(justCompleted = emptyList())
    }

    fun roll() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                rolling = true, error = null, ofp = null, ofpError = null
            )

            val result = airportRepo.randomRoute(_uiState.value.filter)
            if (result == null) {
                _uiState.value = _uiState.value.copy(rolling = false, error = ROLL_FAILED)
                return@launch
            }

            // 뽑은 시점의 누적 비행시간을 기준으로 잡아둡니다.
            // 나중에 접속이 끊긴 뒤 완주를 판정할 때 이 값과 비교합니다.
            val cid = settingsRepo.current().vatsimCid
            val baselineHours = if (cid.isBlank()) null else flightProgressRepo.fetchPilotHours(cid)

            val challengeId = challengeRepo.create(
                origin = result.origin,
                destination = result.destination,
                distanceNm = result.distanceNm,
                baselinePilotHours = baselineHours
            )

            // 서버에 감시를 맡깁니다. 비행 중 앱을 한 번도 켜지 않아도
            // Worker가 1분마다 확인해 완주를 잡아냅니다.
            challengeRepo.registerWatch(
                cid = cid,
                challengeId = challengeId,
                origin = result.origin,
                destination = result.destination,
                baselineHours = baselineHours
            )

            _uiState.value = _uiState.value.copy(
                rolling = false,
                route = result,
                originWeather = null,
                destinationWeather = null
            )
            refreshChallengeState()
            loadWeather()
        }
    }

    /** F6: 뽑힌 두 공항의 기상을 함께 조회합니다. */
    private fun loadWeather() {
        val route = _uiState.value.route ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(weatherLoading = true)
            val origin = weatherRepo.fetch(route.origin.icao)
            val destination = weatherRepo.fetch(route.destination.icao)
            _uiState.value = _uiState.value.copy(
                weatherLoading = false,
                originWeather = origin,
                destinationWeather = destination
            )
        }
    }

    // ---------------- F5 SimBrief ----------------

    fun prepareDispatch() {
        val s = _uiState.value
        val route = s.route ?: return
        _uiState.value = s.copy(
            dispatchUrl = simBriefRepo.buildDispatchUrl(
                origin = route.origin.icao,
                destination = route.destination.icao
            )
        )
    }

    fun consumeDispatchUrl() {
        _uiState.value = _uiState.value.copy(dispatchUrl = null)
    }

    fun fetchOfp(missingIdMessage: String) {
        val id = _uiState.value.simBriefId
        if (id.isBlank()) {
            _uiState.value = _uiState.value.copy(ofpError = missingIdMessage)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(ofpLoading = true, ofpError = null)
            when (val result = simBriefRepo.fetchLatestOfp(id)) {
                is Outcome.Success -> _uiState.value =
                    _uiState.value.copy(ofpLoading = false, ofp = result.data, ofpError = null)
                is Outcome.Failure -> _uiState.value =
                    _uiState.value.copy(ofpLoading = false, ofpError = result.message)
            }
        }
    }

    /** 설정 화면에서 값이 바뀔 수 있으므로 탭 복귀 시 다시 읽습니다. */
    fun reloadSettings() {
        viewModelScope.launch {
            val s = settingsRepo.current()
            _uiState.value = _uiState.value.copy(simBriefId = s.simBriefId, vatsimCid = s.vatsimCid)
        }
    }

    companion object {
        /** 화면에서 문자열 리소스로 치환합니다. */
        const val ROLL_FAILED = "ROLL_FAILED"
    }
}
