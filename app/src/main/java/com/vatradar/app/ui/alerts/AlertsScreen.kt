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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.runtime.key
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
import com.vatradar.app.domain.ApproachDirectory
import com.vatradar.app.domain.WatchedStations
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
    /** 이 나라에서 실제로 접속한 적이 있는 접근관제석. 지어내지 않습니다. */
    val approaches: List<ApproachDirectory.Candidate> = emptyList(),
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
    private val positionRegistry = ServiceLocator.positionRegistry(app)

    /** ICAO 접두사 → 국가. 센터 목록과 접근관제석 판정에 함께 씁니다. */
    private var icaoPrefixes: Map<String, String> = emptyMap()

    private val _settings = MutableStateFlow(UserSettings())
    val settings = _settings.asStateFlow()

    private val _catalog = MutableStateFlow(CatalogState())
    val catalog = _catalog.asStateFlow()

    init {
        viewModelScope.launch { repo.settings.collect { _settings.value = it } }
        viewModelScope.launch {
            icaoPrefixes = airportRepo.icaoPrefixToCountry()
            _catalog.value = _catalog.value.copy(
                allCenters = ControllerCatalog.centers(getApplication(), icaoPrefixes),
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

    /** 공항 하나를 통째로 뺍니다. 개별 자리로 쪼개 저장돼 있어도 한 번에 지웁니다. */
    fun removeAll(entries: Set<String>) = viewModelScope.launch {
        apply(WatchedStations.Change(remove = entries))
    }

    /** 공항 [icao]의 자리 하나를 켜거나 끕니다. */
    fun togglePosition(icao: String, position: String, on: Boolean) = viewModelScope.launch {
        apply(
            WatchedStations.togglePosition(
                _settings.value.watchedCallsigns, icao, position, on
            )
        )
    }

    private suspend fun apply(change: WatchedStations.Change) {
        if (change.add.isEmpty() && change.remove.isEmpty()) return
        // 저장은 한 번에. 뺐다 넣는 사이에 목록이 잠깐 비면 화면이 깜빡입니다.
        repo.updateWatched(add = change.add, remove = change.remove)
        change.remove.forEach { FcmTopics.unsubscribe(it) }
        change.add.forEach { FcmTopics.subscribe(it) }
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

        val airports =
            if (state.ready) airportRepo.airportsIn(state.continent, state.country)
            else emptyList()

        _catalog.value = _catalog.value.copy(
            countries = airportRepo.countries(state.continent),
            centers = if (state.ready) centers else emptyList(),
            airports = airports,
            approaches = if (state.country != null) approachesFor(state.country) else emptyList()
        )
    }

    /**
     * 이 나라의 접근관제석.
     *
     * 후보를 만들 때 쓰는 공항 목록이 등록용 공항 목록([CatalogState.airports])과 다릅니다.
     * 등록용은 국제공항급만 올리지만, 여기서는 그 나라 공항을 **전부** 봅니다 —
     * 접근관제석은 지방 공항이나 군 비행장에도 붙어서 국제공항만 보면 후보가 빠집니다.
     * 판정 규칙은 [ApproachDirectory]에 있습니다.
     */
    private suspend fun approachesFor(country: String): List<ApproachDirectory.Candidate> =
        ApproachDirectory.candidatesFor(
            observed = positionRegistry.callsigns(),
            country = country,
            airports = airportRepo.airportsInCountry(country),
            icaoPrefixes = icaoPrefixes
        )

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
            onRemoveAll = viewModel::removeAll,
            onAdd = viewModel::addWatched,
            onTogglePosition = viewModel::togglePosition
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

/**
 * 지금 등록해 둔 관제소. 자세히 보기에서는 직접 입력도 여기서 합니다.
 *
 * 센터와 공항을 갈라 놓습니다. 센터는 하나가 나라 절반을 덮는 넓은 자리고 공항은
 * 자리가 여럿으로 쪼개지는 좁은 자리라, 한 줄에 섞어 늘어놓으면 무엇을 등록해 뒀는지
 * 읽히지 않습니다. 공항은 눌러서 자리별로 켜고 끌 수 있습니다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WatchedCard(
    watched: Set<String>,
    detailed: Boolean,
    onRemove: (String) -> Unit,
    onRemoveAll: (Set<String>) -> Unit,
    onAdd: (String) -> Unit,
    onTogglePosition: (String, String, Boolean) -> Unit
) {
    var typed by remember { mutableStateOf("") }
    val groups = remember(watched) { WatchedStations.group(watched) }

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
            }

            if (groups.centers.isNotEmpty()) {
                GroupHeading(stringResource(R.string.watched_centers, groups.centers.size))
                CallsignChips(groups.centers, onRemove)
            }

            if (groups.airports.isNotEmpty()) {
                GroupHeading(stringResource(R.string.watched_airports, groups.airports.size))
                groups.airports.forEach { airport ->
                    // 펼침 상태가 목록 순서가 아니라 공항을 따라가게 합니다. 키가 없으면
                    // 앞의 공항이 빠졌을 때 뒤의 공항이 그 자리를 물려받아 대신 펼쳐집니다.
                    key(airport.icao) {
                        WatchedAirportRow(
                            airport = airport,
                            onRemove = { onRemoveAll(airport.entries) },
                            onTogglePosition = { position, on ->
                                onTogglePosition(airport.icao, position, on)
                            }
                        )
                    }
                }
            }

            if (groups.others.isNotEmpty()) {
                GroupHeading(stringResource(R.string.watched_other, groups.others.size))
                CallsignChips(groups.others, onRemove)
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

@Composable
private fun GroupHeading(text: String) {
    HorizontalDivider()
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CallsignChips(callsigns: List<String>, onRemove: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        callsigns.forEach { callsign ->
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

/**
 * 등록해 둔 공항 한 곳.
 *
 * 눌러서 펼치면 자리별 상자가 나옵니다. 공항을 목록에서 고르면 그 공항의 모든 자리가
 * 한꺼번에 걸리는데, 관제탑만 받고 싶은 경우가 흔합니다 — 딜리버리와 그라운드는
 * 게이트에 있을 때만 쓰이니까요.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WatchedAirportRow(
    airport: WatchedStations.Airport,
    onRemove: () -> Unit,
    onTogglePosition: (String, Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val on = airport.boxes.filter { airport.isOn(it) }

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(airport.icao, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (airport.all) stringResource(R.string.all_positions)
                    else on.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.remove),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    airport.boxes.forEach { position ->
                        val checked = airport.isOn(position)
                        FilterChip(
                            selected = checked,
                            onClick = { onTogglePosition(position, !checked) },
                            label = { Text(position) },
                            leadingIcon = if (checked) {
                                {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null
                        )
                    }
                }
                if (airport.all) {
                    Text(
                        stringResource(R.string.positions_split_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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

            val approaches = catalog.approaches.filter {
                q.isEmpty() || it.callsign.contains(q, true) || it.servedBy.contains(q, true)
            }
            CandidateSection(
                title = stringResource(R.string.section_approaches, approaches.size),
                hint = stringResource(R.string.section_approaches_hint),
                rows = approaches.map { Candidate(it.callsign, it.callsign, it.servedBy) },
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
