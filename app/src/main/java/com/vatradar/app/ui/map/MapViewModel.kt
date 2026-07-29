package com.vatradar.app.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.vatradar.app.data.repository.VatsimResult
import com.vatradar.app.data.repository.VatsimSnapshot
import com.vatradar.app.data.repository.WeatherReport
import com.vatradar.app.di.ServiceLocator
import com.vatradar.app.domain.model.Aircraft
import com.vatradar.app.domain.model.Controller
import com.vatradar.app.domain.model.greatCircleNm
import com.vatradar.app.util.plannedArrivalZulu
import com.vatradar.app.util.zuluAfterMinutes
import kotlin.math.roundToInt
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
    val showAircraft: Boolean = true,

    /** 선택한 항공기의 항로. 지나온 구간과 남은 구간을 나눠 그립니다. */
    val routeFlown: List<LatLng> = emptyList(),
    val routeRemaining: List<LatLng> = emptyList(),
    val routeEndpoints: List<LatLng> = emptyList(),

    /** 도착 예상시각(Zulu). 순항 중이면 잔여 거리로 계산하고, 아니면 비행계획값을 씁니다. */
    val estimatedArrival: String? = null,
    /** 위 값이 실시간 계산인지(true) 비행계획상 예정인지(false). */
    val etaIsLive: Boolean = false
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
            selectedControllers = emptyList(),
            routeFlown = emptyList(),
            routeRemaining = emptyList(),
            routeEndpoints = emptyList(),
            estimatedArrival = null,
            etaIsLive = false
        )
        if (aircraft != null) loadRoutePath(aircraft)
    }

    /**
     * 비행계획의 출발지·도착지를 공항 DB에서 찾아 항로 선을 만듭니다.
     * 비행계획이 없거나 공항을 못 찾으면 그릴 수 있는 구간만 그립니다.
     */
    private fun loadRoutePath(aircraft: Aircraft) {
        viewModelScope.launch {
            val departure = aircraft.departure?.let { airportRepo.find(it) }
            val arrival = aircraft.arrival?.let { airportRepo.find(it) }

            // 선택이 바뀐 뒤에 응답이 오면 버립니다.
            if (_uiState.value.selectedAircraft?.callsign != aircraft.callsign) return@launch

            val current = LatLng(aircraft.latitude, aircraft.longitude)
            val from = departure?.let { LatLng(it.latitude, it.longitude) }
            val to = arrival?.let { LatLng(it.latitude, it.longitude) }

            // 순항 중이면 잔여 거리 ÷ 대지속도가 비행계획값보다 훨씬 정확합니다.
            // 지상에 있거나 도착지를 모르면 비행계획상 예정 도착시각으로 물러섭니다.
            val liveEta = if (arrival != null && aircraft.groundSpeed >= MIN_SPEED_FOR_ETA_KT) {
                val remainingNm = greatCircleNm(
                    aircraft.latitude, aircraft.longitude,
                    arrival.latitude, arrival.longitude
                )
                zuluAfterMinutes((remainingNm / aircraft.groundSpeed * 60).roundToInt())
            } else {
                null
            }

            val plannedEta = plannedArrivalZulu(
                aircraft.plannedDepartureHhmm,
                aircraft.enrouteTimeHhmm
            )

            _uiState.value = _uiState.value.copy(
                routeFlown = if (from != null) listOf(from, current) else emptyList(),
                routeRemaining = if (to != null) listOf(current, to) else emptyList(),
                routeEndpoints = listOfNotNull(from, to),
                estimatedArrival = liveEta ?: plannedEta,
                etaIsLive = liveEta != null
            )
        }
    }

    fun selectControllers(controllers: List<Controller>) {
        if (controllers.isEmpty()) return
        _uiState.value = _uiState.value.copy(
            selectedControllers = controllers,
            selectedAircraft = null,
            routeFlown = emptyList(),
            routeRemaining = emptyList(),
            routeEndpoints = emptyList(),
            estimatedArrival = null,
            etaIsLive = false
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
            weatherLoading = false,
            routeFlown = emptyList(),
            routeRemaining = emptyList(),
            routeEndpoints = emptyList(),
            estimatedArrival = null,
            etaIsLive = false
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

        /** 이보다 느리면 지상 이동 중으로 보고 실시간 ETA를 내지 않습니다. */
        private const val MIN_SPEED_FOR_ETA_KT = 60
    }
}
