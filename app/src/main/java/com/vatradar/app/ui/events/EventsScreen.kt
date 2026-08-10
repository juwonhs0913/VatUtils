package com.vatradar.app.ui.events

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.ui.graphics.Color
import com.vatradar.app.notification.EventReminderWorker
import com.vatradar.app.R
import androidx.core.net.toUri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.vatradar.app.data.repository.Outcome
import com.vatradar.app.di.ServiceLocator
import com.vatradar.app.domain.model.VatsimEvent
import com.vatradar.app.util.formatEventPeriod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EventsUiState(
    val loading: Boolean = true,
    val events: List<VatsimEvent> = emptyList(),
    val error: String? = null,
    val selectedTab: Int = 0,
    val query: String = "",
    val starred: Set<String> = emptySet()
)

class EventsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ServiceLocator.eventsRepository()
    private val settings = ServiceLocator.settingsRepository(app)
    private val _uiState = MutableStateFlow(EventsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            settings.settings.collect { stored ->
                _uiState.value = _uiState.value.copy(starred = stored.starredEvents)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            _uiState.value = when (val r = repo.fetchEvents()) {
                is Outcome.Success -> _uiState.value.copy(
                    loading = false,
                    events = r.data,
                    selectedTab = defaultTabFor(r.data)
                )
                is Outcome.Failure -> _uiState.value.copy(loading = false, error = r.message)
            }
        }
    }

    /**
     * 진행 중인 이벤트는 대부분 0건입니다(VATSIM 이벤트는 하루 몇 시간만 열립니다).
     * 빈 탭으로 화면을 열면 아무것도 없는 것처럼 보이므로, 진행 중이 없으면 예정 탭으로 엽니다.
     */
    private fun defaultTabFor(events: List<VatsimEvent>): Int {
        val now = System.currentTimeMillis()
        val hasOngoing = events.any { it.startEpochMillis <= now && it.endEpochMillis >= now }
        return if (hasOngoing) 0 else 1
    }

    /**
     * 관심 표시를 켜고 끕니다.
     *
     * 예약은 표시할 때 겁니다. 앱을 다시 열지 않아도 알림이 오도록
     * WorkManager에 맡기고, 취소하면 예약도 함께 지웁니다.
     */
    fun toggleStar(event: VatsimEvent) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            if (event.id.toString() in _uiState.value.starred) {
                settings.unstarEvent(event.id)
                EventReminderWorker.cancel(context, event.id)
            } else {
                settings.starEvent(event.id)
                EventReminderWorker.schedule(
                    context, event.id, event.name, event.startEpochMillis
                )
            }
        }
    }

    fun setQuery(value: String) {
        _uiState.value = _uiState.value.copy(query = value)
    }

    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = index)
    }
}

@Composable
fun EventsScreen(viewModel: EventsViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()

    // 이름·공항·주최자 어디에 걸려도 찾히게 합니다.
    // 사용자는 "RKSI"로도, "Cross the Pond"로도 찾으려 하기 때문입니다.
    val matching = remember(state.events, state.query) {
        val q = state.query.trim()
        if (q.isEmpty()) state.events
        else state.events.filter { event ->
            event.name.contains(q, ignoreCase = true) ||
                event.shortDescription.contains(q, ignoreCase = true) ||
                event.airports.any { it.contains(q, ignoreCase = true) } ||
                event.organisers.any { it.contains(q, ignoreCase = true) }
        }
    }

    // PRD 요구: 진행 중 / 예정 탭 분리. 여기에 관심 표시한 것만 모아 보는 탭을 더합니다.
    val ongoing = matching.filter { it.startEpochMillis <= now && it.endEpochMillis >= now }
    val upcoming = matching.filter { it.startEpochMillis > now }
    // 관심 목록은 진행 중과 예정을 섞어 시간순으로 보여 줍니다 — 별을 누른 사람에게
    // 지금 열려 있는지 아닌지는 탭을 나눌 만큼 중요한 구분이 아닙니다.
    val starred = matching.filter { it.id.toString() in state.starred }
        .sortedBy { it.startEpochMillis }
    val shown = when (state.selectedTab) {
        0 -> ongoing
        1 -> upcoming
        else -> starred
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.search_events)) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setQuery("") }) {
                        Icon(Icons.Default.Close, stringResource(R.string.clear))
                    }
                }
            },
            singleLine = true
        )

        TabRow(selectedTabIndex = state.selectedTab) {
            Tab(
                selected = state.selectedTab == 0,
                onClick = { viewModel.selectTab(0) },
                text = { Text(stringResource(R.string.events_ongoing, ongoing.size)) }
            )
            Tab(
                selected = state.selectedTab == 1,
                onClick = { viewModel.selectTab(1) },
                text = { Text(stringResource(R.string.events_upcoming, upcoming.size)) }
            )
            Tab(
                selected = state.selectedTab == 2,
                onClick = { viewModel.selectTab(2) },
                text = { Text(stringResource(R.string.events_starred, starred.size)) }
            )
        }

        when {
            state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

            state.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
            }

            shown.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    stringResource(
                        when {
                            state.query.isNotBlank() -> R.string.no_matching_events
                            state.selectedTab == 0 -> R.string.no_ongoing_events
                            state.selectedTab == 1 -> R.string.no_upcoming_events
                            else -> R.string.no_starred_events
                        }
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp),
                    textAlign = TextAlign.Center
                )
            }

            else -> LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(shown, key = { it.id }) { event ->
                    EventCard(
                        event = event,
                        starred = event.id.toString() in state.starred,
                        onToggleStar = { viewModel.toggleStar(event) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EventCard(
    event: VatsimEvent,
    starred: Boolean,
    onToggleStar: () -> Unit
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            if (event.bannerUrl.isNotBlank()) {
                AsyncImage(
                    model = event.bannerUrl,
                    contentDescription = event.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 6f)
                )
            }
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(event.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    formatEventPeriod(event.startEpochMillis, event.endEpochMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                if (event.airports.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        event.airports.take(6).forEach { icao ->
                            SuggestionChip(onClick = {}, label = { Text(icao) })
                        }
                    }
                }

                if (event.organisers.isNotEmpty()) {
                    Text(
                        stringResource(R.string.organised_by, event.organisers.joinToString(", ")),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (event.shortDescription.isNotBlank()) {
                    Text(
                        event.shortDescription,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 4
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (event.link.isNotBlank()) {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                CustomTabsIntent.Builder().build()
                                    .launchUrl(context, event.link.toUri())
                            }
                        ) { Text(stringResource(R.string.view_on_vatsim)) }
                    } else {
                        Spacer(Modifier)
                    }

                    IconButton(onClick = onToggleStar) {
                        Icon(
                            if (starred) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = stringResource(
                                if (starred) R.string.unstar_event else R.string.star_event
                            ),
                            // 켜면 노랑, 끄면 기본색.
                            tint = if (starred) Color(0xFFF5B301)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
