package com.vatradar.app.ui.alerts

import android.app.Application
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.vatradar.app.R
import com.vatradar.app.data.local.ControllerCatalog
import com.vatradar.app.data.prefs.UserSettings
import com.vatradar.app.di.ServiceLocator
import com.vatradar.app.domain.model.Airport
import com.vatradar.app.domain.model.Continent
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
    /** 후보군 안에서 다시 좁히는 검색어. */
    val query: String = ""
) {
    /**
     * 후보를 보여줄 준비가 됐는가.
     *
     * 대륙만 고르면 후보가 수백 줄이 되어 고르는 게 아니라 훑는 일이 됩니다.
     * 나라까지 좁혀야 목록이 사람이 볼 만한 길이가 됩니다.
     */
    val ready: Boolean get() = continent != null && country != null
}

class AlertsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ServiceLocator.settingsRepository(app)
    private val airportRepo = ServiceLocator.airportRepository(app)

    private val _settings = MutableStateFlow(UserSettings())
    val settings = _settings.asStateFlow()

    private val _catalog = MutableStateFlow(CatalogState())
    val catalog = _catalog.asStateFlow()

    init {
        viewModelScope.launch { repo.settings.collect { _settings.value = it } }
        viewModelScope.launch {
            val prefixes = airportRepo.icaoPrefixToCountry()
            _catalog.value = _catalog.value.copy(
                allCenters = ControllerCatalog.centers(getApplication(), prefixes),
                countryNames = airportRepo.countryNames()
            )
            applyFilter()
        }
    }

    fun addWatched(v: String) = viewModelScope.launch {
        repo.addWatched(v)
        FcmTopics.subscribe(v)
    }

    fun removeWatched(v: String) = viewModelScope.launch {
        repo.removeWatched(v)
        FcmTopics.unsubscribe(v)
    }

    // ---------------- 관제소 고르기 ----------------

    fun setContinent(code: String?) {
        _catalog.value = _catalog.value.copy(continent = code, country = null, query = "")
        viewModelScope.launch { applyFilter() }
    }

    fun setCountry(code: String?) {
        _catalog.value = _catalog.value.copy(country = code, query = "")
        viewModelScope.launch { applyFilter() }
    }

    fun setQuery(value: String) {
        _catalog.value = _catalog.value.copy(query = value)
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
            centers = if (state.ready) centers else emptyList(),
            airports = if (state.ready) airportRepo.airportsIn(state.continent, state.country)
                       else emptyList()
        )
    }

    fun setNotifyEnabled(enabled: Boolean) = viewModelScope.launch {
        repo.setNotifyEnabled(enabled)
        val context = getApplication<Application>()
        if (enabled) ControllerWatchWorker.enable(context) else ControllerWatchWorker.disable(context)
    }
}

@Composable
fun AlertsScreen(viewModel: AlertsViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 간략히 보기: 스위치와 등록 목록만. 자세히 보기: 직접 입력과 고르기 목록까지.
    // 대부분의 방문은 "지금 뭘 등록해 뒀지"를 확인하러 오는 것이라 간략히가 기본입니다.
    var detailed by remember { mutableStateOf(false) }

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
        EnableCard(
            enabled = settings.notifyEnabled,
            onChange = { want ->
                if (want && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !Notifications.canPost(context)
                ) {
                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.setNotifyEnabled(want)
                }
            }
        )

        ViewModeSwitch(detailed = detailed, onChange = { detailed = it })

        WatchedCard(
            watched = settings.watchedCallsigns,
            detailed = detailed,
            onRemove = viewModel::removeWatched,
            onAdd = viewModel::addWatched
        )

        AnimatedVisibility(visible = detailed) {
            ControllerPicker(
                catalog = catalog,
                watched = settings.watchedCallsigns,
                onContinent = viewModel::setContinent,
                onCountry = viewModel::setCountry,
                onQuery = viewModel::setQuery,
                onToggle = { callsign ->
                    if (callsign in settings.watchedCallsigns) viewModel.removeWatched(callsign)
                    else viewModel.addWatched(callsign)
                }
            )
        }
    }
}

@Composable
private fun EnableCard(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.enable_alerts),
                    style = MaterialTheme.typography.titleMedium
                )
                Switch(checked = enabled, onCheckedChange = onChange)
            }
            if (!enabled) {
                Text(
                    stringResource(R.string.alerts_off_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewModeSwitch(detailed: Boolean, onChange: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = !detailed,
            onClick = { onChange(false) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
        ) { Text(stringResource(R.string.view_compact)) }
        SegmentedButton(
            selected = detailed,
            onClick = { onChange(true) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
        ) { Text(stringResource(R.string.view_detailed)) }
    }
}

/** 지금 등록해 둔 관제소. 자세히 보기에서는 직접 입력도 여기서 합니다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WatchedCard(
    watched: Set<String>,
    detailed: Boolean,
    onRemove: (String) -> Unit,
    onAdd: (String) -> Unit
) {
    var typed by remember { mutableStateOf("") }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.watched_heading, watched.size),
                style = MaterialTheme.typography.titleMedium
            )

            if (watched.isEmpty()) {
                Text(
                    stringResource(R.string.no_watched_controllers),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    watched.sorted().forEach { callsign ->
                        InputChip(
                            selected = false,
                            onClick = { onRemove(callsign) },
                            label = { Text(callsign) },
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

            if (detailed) {
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        label = { Text(stringResource(R.string.callsign_or_prefix)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        if (typed.isNotBlank()) {
                            onAdd(typed.trim().uppercase())
                            typed = ""
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
                    }
                }
                Text(
                    stringResource(R.string.alerts_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 대륙 → 국가로 좁힌 뒤 센터·어프로치·공항 후보를 고릅니다.
 *
 * 나라까지 골라야 후보가 나옵니다. 대륙만으로는 수백 줄이라 고르는 화면이 아니라
 * 스크롤하는 화면이 됩니다. 나라를 고르면 범위 칩은 접히고 한 줄 요약만 남습니다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControllerPicker(
    catalog: CatalogState,
    watched: Set<String>,
    onContinent: (String?) -> Unit,
    onCountry: (String?) -> Unit,
    onQuery: (String) -> Unit,
    onToggle: (String) -> Unit
) {
    var scopeOpen by remember { mutableStateOf(true) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.pick_from_list),
                style = MaterialTheme.typography.titleMedium
            )

            if (scopeOpen || !catalog.ready) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Continent.entries.forEach { continent ->
                        FilterChip(
                            selected = catalog.continent == continent.code,
                            onClick = {
                                onContinent(
                                    if (catalog.continent == continent.code) null else continent.code
                                )
                            },
                            label = { Text(continent.displayName) }
                        )
                    }
                }

                if (catalog.continent != null && catalog.countries.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        catalog.countries.forEach { (code, name) ->
                            FilterChip(
                                selected = catalog.country == code,
                                onClick = {
                                    val next = if (catalog.country == code) null else code
                                    onCountry(next)
                                    // 나라까지 골랐으면 칩을 접어 후보 목록에 자리를 내줍니다.
                                    scopeOpen = next == null
                                },
                                label = { Text(name) }
                            )
                        }
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(scopeSummary(catalog), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { scopeOpen = true }) {
                        Text(stringResource(R.string.change_region))
                    }
                }
            }

            if (!catalog.ready) {
                Text(
                    stringResource(R.string.pick_continent_and_country),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            HorizontalDivider()

            OutlinedTextField(
                value = catalog.query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.search_candidates)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (catalog.query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }) {
                            Icon(Icons.Default.Close, stringResource(R.string.clear))
                        }
                    }
                },
                singleLine = true
            )

            val q = catalog.query.trim()
            val centers = catalog.centers.filter {
                q.isEmpty() || it.callsign.contains(q, true) || it.name.contains(q, true)
            }
            val airports = catalog.airports.filter {
                q.isEmpty() || it.icao.contains(q, true) || it.name.contains(q, true)
            }

            CandidateSection(
                title = stringResource(R.string.section_centers, centers.size),
                rows = centers.map {
                    Candidate(it.callsign, it.callsign, it.name)
                },
                watched = watched,
                onToggle = onToggle
            )

            CandidateSection(
                title = stringResource(R.string.section_approaches, airports.size),
                hint = stringResource(R.string.section_approaches_hint),
                rows = airports.map {
                    Candidate("${it.icao}_APP", "${it.icao}_APP", it.name)
                },
                watched = watched,
                onToggle = onToggle
            )

            CandidateSection(
                title = stringResource(R.string.section_airports, airports.size),
                hint = stringResource(R.string.section_airports_hint),
                rows = airports.map {
                    Candidate(it.icao, it.icao, it.name)
                },
                watched = watched,
                onToggle = onToggle
            )
        }
    }
}

private data class Candidate(val callsign: String, val title: String, val subtitle: String)

/**
 * 후보 한 묶음.
 *
 * 나라 하나에도 공항이 수백 곳인 경우가 있어(미국) 처음 [LIMIT]개만 그리고,
 * 나머지는 검색으로 좁히게 합니다. 다 그리면 스크롤이 감당이 안 됩니다.
 */
@Composable
private fun CandidateSection(
    title: String,
    rows: List<Candidate>,
    watched: Set<String>,
    onToggle: (String) -> Unit,
    hint: String? = null
) {
    if (rows.isEmpty()) return
    val limit = 40

    HorizontalDivider()
    Text(title, style = MaterialTheme.typography.labelLarge)
    if (hint != null) {
        Text(
            hint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    rows.take(limit).forEach { row ->
        PickerRow(
            title = row.title,
            subtitle = row.subtitle,
            selected = row.callsign in watched,
            onClick = { onToggle(row.callsign) }
        )
    }
    if (rows.size > limit) {
        Text(
            stringResource(R.string.more_candidates, rows.size - limit),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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

/** 접었을 때 무엇을 골랐는지 한 줄로. */
@Composable
private fun scopeSummary(catalog: CatalogState): String {
    val continent = Continent.fromCode(catalog.continent)?.displayName
        ?: stringResource(R.string.worldwide)
    val country = catalog.countries.firstOrNull { it.first == catalog.country }?.second
    return if (country != null) "$continent · $country" else continent
}
