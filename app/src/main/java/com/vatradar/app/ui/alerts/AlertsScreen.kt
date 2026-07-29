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
import com.vatradar.app.R
import com.vatradar.app.data.prefs.UserSettings
import com.vatradar.app.di.ServiceLocator
import com.vatradar.app.notification.ControllerWatchWorker
import com.vatradar.app.notification.FcmTopics
import com.vatradar.app.notification.Notifications
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    }

    fun removeWatched(v: String) = viewModelScope.launch {
        repo.removeWatched(v)
        FcmTopics.unsubscribe(v)
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
    }
}
