package com.vatradar.app.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vatradar.app.data.repository.VatsimResult
import com.vatradar.app.data.repository.VatsimSnapshot
import com.vatradar.app.data.repository.WeatherReport
import com.vatradar.app.di.ServiceLocator
import com.vatradar.app.domain.model.Aircraft
import com.vatradar.app.domain.model.Controller
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MapUiState(
    val snapshot: VatsimSnapshot? = null,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val selectedAircraft: Aircraft? = null,
    /** 한 공항에 여러 관제석이 동시에 열려 있을 수 있어 목록으로 다룹니다. */
    val selectedControllers: List<Controller> = emptyList(),
    val weather: WeatherReport? = null,
    val weatherLoading: Boolean = false,
    val showControllers: Boolean = true,
    val showAircraft: Boolean = true
)

class MapViewModel(app: Application) : AndroidViewModel(app) {

    private val vatsimRepo = ServiceLocator.vatsimRepository(app)
    private val weatherRepo = ServiceLocator.weatherRepository()
    private val airportRepo = ServiceLocator.airportRepository(app)

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState = _uiState.asStateFlow()

    private var pollJob: Job? = null

    init {
        startPolling()
    }

    /** PRD 요구: 15초 주기 갱신. */
    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            when (val result = vatsimRepo.fetchSnapshot()) {
                is VatsimResult.Success ->
                    _uiState.value = _uiState.value.copy(
                        snapshot = result.snapshot,
                        isRefreshing = false,
                        error = null
                    )
                is VatsimResult.Error ->
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        // 기존 스냅샷은 유지합니다. 일시적 실패로 지도가 비면 더 나쁩니다.
                        error = result.message
                    )
            }
        }
    }

    fun selectAircraft(aircraft: Aircraft?) {
        _uiState.value = _uiState.value.copy(
            selectedAircraft = aircraft,
            selectedControllers = emptyList()
        )
    }

    fun selectControllers(controllers: List<Controller>) {
        if (controllers.isEmpty()) return
        _uiState.value = _uiState.value.copy(
            selectedControllers = controllers,
            selectedAircraft = null
        )
        // 공항 단위 관제석이면 그 공항 기상을 함께 띄웁니다.
        loadWeather(controllers.first().prefix)
    }

    /** F6: 지도에서 공항/관제소를 탭하면 기상 정보를 띄웁니다. */
    fun loadWeather(icao: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(weatherLoading = true, weather = null)
            // 4자리 ICAO가 아니면(예: FIR 코드) 기상 조회 대상이 아닙니다.
            val airport = airportRepo.find(icao)
            if (airport == null) {
                _uiState.value = _uiState.value.copy(weatherLoading = false, weather = null)
                return@launch
            }
            val report = weatherRepo.fetch(icao)
            _uiState.value = _uiState.value.copy(weatherLoading = false, weather = report)
        }
    }

    fun dismissSheet() {
        _uiState.value = _uiState.value.copy(
            selectedAircraft = null,
            selectedControllers = emptyList(),
            weather = null,
            weatherLoading = false
        )
    }

    fun toggleControllers() {
        _uiState.value = _uiState.value.copy(showControllers = !_uiState.value.showControllers)
    }

    fun toggleAircraft() {
        _uiState.value = _uiState.value.copy(showAircraft = !_uiState.value.showAircraft)
    }

    companion object {
        const val REFRESH_INTERVAL_MS = 15_000L
    }
}
