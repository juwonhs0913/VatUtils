package com.vatradar.app.ui.route

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    private val _uiState = MutableStateFlow(RouteUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val s = settingsRepo.current()
            _uiState.value = _uiState.value.copy(
                simBriefId = s.simBriefId,
                airportPoolSize = airportRepo.poolSize(_uiState.value.haul)
            )
        }
    }

    fun setHaul(haul: HaulRange) {
        _uiState.value = _uiState.value.copy(haul = haul)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(airportPoolSize = airportRepo.poolSize(haul))
        }
    }

    fun roll() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                rolling = true, error = null, ofp = null, ofpError = null
            )

            val result = airportRepo.randomRoute(_uiState.value.haul)
            if (result == null) {
                _uiState.value = _uiState.value.copy(rolling = false, error = ROLL_FAILED)
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                rolling = false,
                route = result,
                originWeather = null,
                destinationWeather = null
            )
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
            _uiState.value = _uiState.value.copy(simBriefId = settingsRepo.current().simBriefId)
        }
    }

    private companion object {
        /** 화면에서 문자열 리소스로 치환합니다. */
        const val ROLL_FAILED = "ROLL_FAILED"
    }
}
