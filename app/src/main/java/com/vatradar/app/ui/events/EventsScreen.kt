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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
    val selectedTab: Int = 0
)

class EventsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ServiceLocator.eventsRepository()
    private val _uiState = MutableStateFlow(EventsUiState())
    val uiState = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            _uiState.value = when (val r = repo.fetchEvents()) {
                is Outcome.Success -> _uiState.value.copy(loading = false, events = r.data)
                is Outcome.Failure -> _uiState.value.copy(loading = false, error = r.message)
            }
        }
    }

    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = index)
    }
}

@Composable
fun EventsScreen(viewModel: EventsViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()

    // PRD 요구: 진행 중 / 예정 탭 분리
    val ongoing = state.events.filter { it.startEpochMillis <= now && it.endEpochMillis >= now }
    val upcoming = state.events.filter { it.startEpochMillis > now }
    val shown = if (state.selectedTab == 0) ongoing else upcoming

    Column(modifier = Modifier.fillMaxSize()) {
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
        }

        when {
            state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

            state.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
            }

            shown.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    stringResource(
                        if (state.selectedTab == 0) R.string.no_ongoing_events
                        else R.string.no_upcoming_events
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(shown, key = { it.id }) { EventCard(it) }
            }
        }
    }
}

@Composable
private fun EventCard(event: VatsimEvent) {
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

                if (event.link.isNotBlank()) {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            CustomTabsIntent.Builder().build().launchUrl(context, event.link.toUri())
                        }
                    ) { Text(stringResource(R.string.view_on_vatsim)) }
                }
            }
        }
    }
}
