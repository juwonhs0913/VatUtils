package com.vatradar.app.ui.myflights

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.vatradar.app.data.local.CountryShapeStore
import com.vatradar.app.data.remote.LoggedFlight
import com.vatradar.app.di.ServiceLocator
import com.vatradar.app.domain.model.distanceNmTo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class MyFlightsUiState(
    val cid: String = "",
    val loading: Boolean = false,
    val since: Long? = null,
    val flights: List<LoggedFlight> = emptyList(),
    val totalHours: Double = 0.0,
    val totalDistanceNm: Int = 0,
    val flightCount: Int = 0,
    /** 중복을 뺀 방문 공항 수 (출발지와 도착지 모두 셈). */
    val airportCount: Int = 0,
    val countryCount: Int = 0,
    val totalCountries: Int = 0,
    /** 비행한 나라의 경계. 지도에 색으로 채웁니다. */
    val visitedShapes: List<List<LatLng>> = emptyList()
)

class MyFlightsViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo = ServiceLocator.settingsRepository(app)
    private val airportRepo = ServiceLocator.airportRepository(app)
    private val logbookApi = ServiceLocator.logbookApiService()

    private val _uiState = MutableStateFlow(MyFlightsUiState())
    val uiState = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val cid = settingsRepo.current().vatsimCid
            _uiState.value = _uiState.value.copy(cid = cid)
            if (cid.isBlank()) return@launch

            _uiState.value = _uiState.value.copy(loading = true)

            val response = runCatching { logbookApi.fetch(cid) }
                .onFailure { Log.w("VATFlight", "비행 기록 조회 실패", it) }
                .getOrNull()
                ?.takeIf { it.isSuccessful }
                ?.body()

            if (response == null || !response.ok) {
                _uiState.value = _uiState.value.copy(loading = false)
                return@launch
            }

            // 완료된 비행만 통계에 넣습니다. 진행 중인 건은 거리·시간이 계속 변합니다.
            val finished = response.flights.filter { it.isFinished }
            val icaos = finished.flatMap { listOf(it.departure, it.arrival) }.distinct()
            val airports = airportRepo.findAll(icaos).associateBy { it.icao }

            var distance = 0
            finished.forEach { flight ->
                val from = airports[flight.departure]
                val to = airports[flight.arrival]
                if (from != null && to != null) distance += from.distanceNmTo(to).roundToInt()
            }

            val visitedIcaos = icaos.filter { airports.containsKey(it) }.toSet()
            val visitedCountries = visitedIcaos.mapNotNull { airports[it]?.country }.toSet()

            val shapes = CountryShapeStore.shapes(getApplication())
            _uiState.value = _uiState.value.copy(
                loading = false,
                since = response.since,
                flights = response.flights,
                totalHours = finished.sumOf { it.hours },
                totalDistanceNm = distance,
                flightCount = finished.size,
                airportCount = visitedIcaos.size,
                countryCount = visitedCountries.size,
                totalCountries = shapes.size,
                visitedShapes = visitedCountries.flatMap { shapes[it].orEmpty() }
            )
        }
    }
}
