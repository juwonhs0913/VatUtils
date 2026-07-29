package com.vatradar.app.ui.route

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vatradar.app.data.local.CountryRow
import com.vatradar.app.data.repository.Outcome
import com.vatradar.app.data.repository.WeatherReport
import com.vatradar.app.di.ServiceLocator
import com.vatradar.app.domain.model.Airport
import com.vatradar.app.domain.model.OfpSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RouteUiState(
    val continent: String? = null,
    val country: String? = null,
    val countries: List<CountryRow> = emptyList(),
    val minRunwayFt: Int = 8000,
    val hardSurfaceOnly: Boolean = true,
    val candidateCount: Int = 0,
    val origin: Airport? = null,
    val destination: Airport? = null,
    val rolling: Boolean = false,
    val error: String? = null,

    // F6 — 뽑힌 공항의 기상
    val originWeather: WeatherReport? = null,
    val destinationWeather: WeatherReport? = null,
    val weatherLoading: Boolean = false,

    // F5 — SimBrief
    val simBriefId: String = "",
    val aircraftType: String = "B77W",
    val airline: String = "",
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
                minRunwayFt = s.minRunwayFt,
                hardSurfaceOnly = s.hardSurfaceOnly,
                simBriefId = s.simBriefId,
                aircraftType = s.aircraftType,
                airline = s.airline
            )
            reloadCountries()
            recount()
        }
    }

    // ---------------- 필터 ----------------

    fun setContinent(code: String?) {
        _uiState.value = _uiState.value.copy(continent = code, country = null)
        viewModelScope.launch {
            reloadCountries()
            recount()
        }
    }

    fun setCountry(code: String?) {
        _uiState.value = _uiState.value.copy(country = code)
        viewModelScope.launch { recount() }
    }

    fun setMinRunway(ft: Int) {
        _uiState.value = _uiState.value.copy(minRunwayFt = ft)
        viewModelScope.launch {
            settingsRepo.setMinRunwayFt(ft)
            recount()
        }
    }

    fun setHardSurfaceOnly(value: Boolean) {
        _uiState.value = _uiState.value.copy(hardSurfaceOnly = value)
        viewModelScope.launch {
            settingsRepo.setHardSurfaceOnly(value)
            recount()
        }
    }

    private suspend fun reloadCountries() {
        _uiState.value = _uiState.value.copy(
            countries = airportRepo.countries(_uiState.value.continent)
        )
    }

    private suspend fun recount() {
        val s = _uiState.value
        _uiState.value = s.copy(
            candidateCount = airportRepo.countMatching(
                s.minRunwayFt, s.continent, s.country, s.hardSurfaceOnly
            )
        )
    }

    // ---------------- F3 추첨 ----------------

    fun roll() {
        viewModelScope.launch {
            val s = _uiState.value
            _uiState.value = s.copy(rolling = true, error = null, ofp = null, ofpError = null)

            val pair = airportRepo.random(s.minRunwayFt, s.continent, s.country, s.hardSurfaceOnly)
            if (pair == null) {
                _uiState.value = _uiState.value.copy(
                    rolling = false,
                    error = "조건을 만족하는 공항이 2곳 미만입니다. 필터를 완화해 주세요."
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                rolling = false,
                origin = pair.first,
                destination = pair.second,
                originWeather = null,
                destinationWeather = null
            )
            loadWeather()
        }
    }

    /** F6: 뽑힌 두 공항의 기상을 함께 조회합니다. */
    private fun loadWeather() {
        val s = _uiState.value
        val origin = s.origin ?: return
        val destination = s.destination ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(weatherLoading = true)
            val o = weatherRepo.fetch(origin.icao)
            val d = weatherRepo.fetch(destination.icao)
            _uiState.value = _uiState.value.copy(
                weatherLoading = false,
                originWeather = o,
                destinationWeather = d
            )
        }
    }

    // ---------------- F5 SimBrief ----------------

    fun prepareDispatch() {
        val s = _uiState.value
        val origin = s.origin ?: return
        val destination = s.destination ?: return
        _uiState.value = s.copy(
            dispatchUrl = simBriefRepo.buildDispatchUrl(
                origin = origin.icao,
                destination = destination.icao,
                aircraftType = s.aircraftType,
                airline = s.airline.takeIf { it.isNotBlank() },
                flightNumber = null
            )
        )
    }

    fun consumeDispatchUrl() {
        _uiState.value = _uiState.value.copy(dispatchUrl = null)
    }

    fun fetchOfp() {
        val id = _uiState.value.simBriefId
        if (id.isBlank()) {
            _uiState.value = _uiState.value.copy(
                ofpError = "설정에서 SimBrief ID(Alias 또는 Pilot ID)를 먼저 등록해 주세요."
            )
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
            _uiState.value = _uiState.value.copy(
                simBriefId = s.simBriefId,
                aircraftType = s.aircraftType,
                airline = s.airline
            )
        }
    }
}
