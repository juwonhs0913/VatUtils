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
import com.vatradar.app.domain.model.HaulRange
import com.vatradar.app.domain.model.OfpSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RouteUiState(
    val haul: HaulRange = HaulRange.MEDIUM,
    val airportPoolSize: Int = 0,
    val route: RandomRoute? = null,
    val rolling: Boolean = false,
    val error: String? = null,

    // 챌린지 / 등급
    val remainingRolls: Int = ChallengeRepository.DAILY_ROLL_LIMIT,
    val millisUntilReset: Long = 0,
    val totalPoints: Int = 0,
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
                vatsimCid = s.vatsimCid,
                airportPoolSize = airportRepo.poolSize(_uiState.value.haul)
            )
            refreshChallengeState()
            syncFlightProgress()
        }
        // 포인트·완주 수는 DB가 바뀌면 자동으로 따라오게 합니다.
        viewModelScope.launch {
            challengeRepo.totalPoints.collect {
                _uiState.value = _uiState.value.copy(totalPoints = it)
            }
        }
        viewModelScope.launch {
            challengeRepo.completedCount.collect {
                _uiState.value = _uiState.value.copy(completedCount = it)
            }
        }
    }

    private suspend fun refreshChallengeState() {
        _uiState.value = _uiState.value.copy(
            remainingRolls = challengeRepo.remainingRollsToday(),
            millisUntilReset = challengeRepo.millisUntilReset(),
            activeChallenges = challengeRepo.activeChallenges()
        )
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

    fun setHaul(haul: HaulRange) {
        _uiState.value = _uiState.value.copy(haul = haul)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(airportPoolSize = airportRepo.poolSize(haul))
        }
    }

    fun roll() {
        viewModelScope.launch {
            if (challengeRepo.remainingRollsToday() <= 0) {
                _uiState.value = _uiState.value.copy(error = NO_ROLLS_LEFT)
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                rolling = true, error = null, ofp = null, ofpError = null
            )

            val result = airportRepo.randomRoute(_uiState.value.haul)
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
        const val NO_ROLLS_LEFT = "NO_ROLLS_LEFT"
    }
}
