package com.vatradar.app.ui.alerts

import android.app.Application
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import com.vatradar.app.data.local.ControllerCatalog
import com.vatradar.app.domain.model.Airport
import com.vatradar.app.domain.model.Continent
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.TextButton
import androidx.compose.material3.AssistChip
import com.vatradar.app.R
import com.vatradar.app.data.prefs.UserSettings
import com.vatradar.app.di.ServiceLocator
import com.vatradar.app.notification.ControllerWatchWorker
import com.vatradar.app.notification.FcmTopics
import com.vatradar.app.notification.Notifications
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CatalogState(
    val continent: String? = null,
    val country: String? = null,
    val countries: List<Pair<String, String>> = emptyList(),
    val countryNames: Map<String, String> = emptyMap(),
    val allCenters: List<ControllerCatalog.CenterEntry> = emptyList(),
    val centers: List<ControllerCatalog.CenterEntry> = emptyList(),
    val airports: List<Airport> = emptyList(),
    /** 등록한 공항 근처의 어프로치 — 같은 접근관제일 수 있어 제안만 합니다. */
    val nearbyApproaches: List<String> = emptyList()
)

class AlertsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ServiceLocator.settingsRepository(app)

    private val _settings = MutableStateFlow(UserSettings())
    val settings = _settings.asStateFlow()

    init {
        viewModelScope.launch { repo.settings.collect { _settings.value = it } }
    }

    fun addWatched(v: String) = viewModelScope.launch {
        repo.addWatched(v)
        FcmTopics.subscribe(v)
        refreshSuggestions()
    }

    fun removeWatched(v: String) = viewModelScope.launch {
        repo.removeWatched(v)
        FcmTopics.unsubscribe(v)
        refreshSuggestions()
    }

    /**
     * 등록한 공항 근처의 어프로치 제안.
     *
     * 인천과 김포는 서울 어프로치 하나가 봅니다. 하네다와 나리타도 마찬가지입니다.
     * 그런데 관제사는 둘 중 한쪽 콜사인으로만 접속하므로 인천만 등록해 두면 놓칩니다.
     *
     * 자동으로 묶지 않는 이유: "가까우면 같은 접근관제"는 추측이라 틀립니다.
     * 히드로와 개트윅은 22해리인데 담당 디렉터가 서로 다릅니다. 어느 공항이 묶이는지는
     * 그 지역을 아는 사람이 제일 잘 알므로, 후보만 보여 주고 고르게 합니다.
     * 고르면 평범한 등록값이 되어 목록에 그대로 보이고 지울 수도 있습니다.
     */
    private suspend fun refreshSuggestions() {
        val watched = repo.current().watchedCallsigns
        val suggestions = airportRepo.approachNeighbours(watched)
            .filterNot { it in watched }
        _catalog.value = _catalog.value.copy(nearbyApproaches = suggestions)
    }

    // ---------------- 관제소 고르기 ----------------

    private val airportRepo = ServiceLocator.airportRepository(app)

    private val _catalog = MutableStateFlow(CatalogState())
    val catalog = _catalog.asStateFlow()

    init {
        viewModelScope.launch {
            val prefixes = airportRepo.icaoPrefixToCountry()
            val centers = ControllerCatalog.centers(getApplication(), prefixes)
            _catalog.value = _catalog.value.copy(
                allCenters = centers,
                countryNames = airportRepo.countryNames()
            )
            applyFilter()
            refreshSuggestions()
        }
    }

    fun setContinent(code: String?) {
        _catalog.value = _catalog.value.copy(continent = code, country = null)
        viewModelScope.launch { applyFilter() }
    }

    fun setCountry(code: String?) {
        _catalog.value = _catalog.value.copy(country = code)
        viewModelScope.launch { applyFilter() }
    }

    private suspend fun applyFilter() {
        val state = _catalog.value
        val countriesInScope = airportRepo.countries(state.continent).toMap()

        // FIR은 나라 정보가 원본에 없어 공항 접두사로 추정한 값을 씁니다.
        // 추정에 실패한 항목은 대륙/국가를 좁혔을 때 조용히 빠집니다.
        val centers = state.allCenters.filter { entry ->
            when {
                state.country != null -> entry.country == state.country
                state.continent != null -> entry.country != null && entry.country in countriesInScope
                else -> true
            }
        }

        _catalog.value = _catalog.value.copy(
            countries = airportRepo.countries(state.continent),
            centers = centers,
            airports = if (state.continent == null && state.country == null) emptyList()
                       else airportRepo.airportsIn(state.continent, state.country)
        )
    }

    fun setNotifyEnabled(enabled: Boolean) = viewModelScope.launch {
        repo.setNotifyEnabled(enabled)
        val context = getApplication<Application>()
        if (enabled) ControllerWatchWorker.enable(context) else ControllerWatchWorker.disable(context)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlertsScreen(viewModel: AlertsViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var newWatch by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.setNotifyEnabled(granted) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val catalog by viewModel.catalog.collectAsStateWithLifecycle()

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.enable_alerts), style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = settings.notifyEnabled,
                        onCheckedChange = { want ->
                            if (want && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !Notifications.canPost(context)
                            ) {
                                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.setNotifyEnabled(want)
                            }
                        }
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newWatch,
                        onValueChange = { newWatch = it },
                        label = { Text(stringResource(R.string.callsign_or_prefix)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        if (newWatch.isNotBlank()) {
                            viewModel.addWatched(newWatch)
                            newWatch = ""
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
                    }
                }

                if (settings.watchedCallsigns.isEmpty()) {
                    Text(
                        stringResource(R.string.no_watched_controllers),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        settings.watchedCallsigns.sorted().forEach { cs ->
                            InputChip(
                                selected = false,
                                onClick = { viewModel.removeWatched(cs) },
                                label = { Text(cs) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.remove),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                Text(
                    stringResource(R.string.alerts_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (catalog.nearbyApproaches.isNotEmpty()) {
            Card {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.nearby_approach_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringResource(R.string.nearby_approach_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        catalog.nearbyApproaches.forEach { callsign ->
                            AssistChip(
                                onClick = { viewModel.addWatched(callsign) },
                                label = { Text(callsign) },
                                leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) }
                            )
                        }
                    }
                }
            }
        }

        ControllerPicker(
            catalog = catalog,
            watched = settings.watchedCallsigns,
            onContinent = viewModel::setContinent,
            onCountry = viewModel::setCountry,
            onToggle = { callsign ->
                if (callsign in settings.watchedCallsigns) viewModel.removeWatched(callsign)
                else viewModel.addWatched(callsign)
            }
        )
    }
}

/**
 * 대륙 → 국가로 좁혀 가며 관제소를 고릅니다.
 *
 * CTR/FSS는 목록에서 바로 고르고, 나머지 관제석은 공항을 고르면 그 공항의
 * APP/TWR/GND/DEL이 한 번에 등록됩니다 (등록값이 ICAO 하나면 하위 관제석을 모두 잡습니다).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControllerPicker(
    catalog: CatalogState,
    watched: Set<String>,
    onContinent: (String?) -> Unit,
    onCountry: (String?) -> Unit,
    onToggle: (String) -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.pick_from_list),
                style = MaterialTheme.typography.titleMedium
            )



            // 대륙과 나라를 고르면 칩이 여러 줄로 늘어나 목록을 밀어냅니다.
            // 다 고르고 나면 접어 두고, 지금 무엇을 골랐는지만 한 줄로 보여 줍니다.
            var chipsOpen by remember { mutableStateOf(true) }

            if (chipsOpen) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = catalog.continent == null,
                        onClick = { onContinent(null) },
                        label = { Text(stringResource(R.string.worldwide)) }
                    )
                    Continent.entries.forEach { continent ->
                        FilterChip(
                            selected = catalog.continent == continent.code,
                            onClick = { onContinent(continent.code) },
                            label = { Text(continent.displayName) }
                        )
                    }
                }

                if (catalog.continent != null && catalog.countries.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = catalog.country == null,
                            onClick = { onCountry(null) },
                            label = { Text(stringResource(R.string.all_countries)) }
                        )
                        catalog.countries.forEach { (code, name) ->
                            FilterChip(
                                selected = catalog.country == code,
                                onClick = { onCountry(code) },
                                label = { Text(name) }
                            )
                        }
                    }
                }
            }

            TextButton(
                onClick = { chipsOpen = !chipsOpen },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    if (chipsOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    Modifier.size(20.dp)
                )
                Text(
                    "  " + if (chipsOpen) stringResource(R.string.collapse_list)
                    else stringResource(R.string.expand_list) + " · " + currentScope(catalog)
                )
            }

            HorizontalDivider()

            Text(
                stringResource(R.string.centers_heading, catalog.centers.size),
                style = MaterialTheme.typography.labelLarge
            )
            catalog.centers.take(80).forEach { entry ->
                PickerRow(
                    title = entry.callsign,
                    subtitle = entry.name,
                    selected = entry.callsign in watched,
                    onClick = { onToggle(entry.callsign) }
                )
            }

            if (catalog.airports.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    stringResource(R.string.airports_heading),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    stringResource(R.string.airports_heading_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                catalog.airports.take(80).forEach { airport ->
                    PickerRow(
                        title = airport.icao,
                        subtitle = airport.name,
                        selected = airport.icao in watched,
                        onClick = { onToggle(airport.icao) }
                    )
                }
            } else if (catalog.continent == null) {
                Text(
                    stringResource(R.string.pick_region_for_airports),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

        }
    }
}

@Composable
private fun PickerRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            if (selected) Icons.Default.Check else Icons.Default.Add,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 지금 좁혀 둔 범위를 한 줄로. 접었을 때 무엇을 골랐는지 알 수 있어야 합니다. */
@Composable
private fun currentScope(catalog: CatalogState): String {
    val country = catalog.countries.firstOrNull { it.first == catalog.country }?.second
    val continent = Continent.fromCode(catalog.continent)?.displayName
    return country ?: continent ?: stringResource(R.string.worldwide)
}
