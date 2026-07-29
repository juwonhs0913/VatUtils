package com.vatradar.app.ui.settings

import android.app.Application
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vatradar.app.data.prefs.UserSettings
import com.vatradar.app.di.ServiceLocator
import com.vatradar.app.notification.ControllerWatchWorker
import com.vatradar.app.notification.FcmTopics
import com.vatradar.app.notification.Notifications
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ServiceLocator.settingsRepository(app)

    private val _settings = MutableStateFlow(UserSettings())
    val settings = _settings.asStateFlow()

    init {
        viewModelScope.launch {
            repo.settings.collect { _settings.value = it }
        }
    }

    fun setSimBriefId(v: String) = viewModelScope.launch { repo.setSimBriefId(v) }
    fun setAircraftType(v: String) = viewModelScope.launch { repo.setAircraftType(v) }
    fun setAirline(v: String) = viewModelScope.launch { repo.setAirline(v) }
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
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var simBriefId by remember(settings.simBriefId) { mutableStateOf(settings.simBriefId) }
    var aircraftType by remember(settings.aircraftType) { mutableStateOf(settings.aircraftType) }
    var airline by remember(settings.airline) { mutableStateOf(settings.airline) }
    var newWatch by remember { mutableStateOf("") }

    // Android 13+ 알림 권한
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
        // ---------------- F5 SimBrief ----------------
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("SimBrief", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = simBriefId,
                    onValueChange = { simBriefId = it; viewModel.setSimBriefId(it) },
                    label = { Text("SimBrief ID (Alias 또는 Pilot ID)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "SimBrief 계정 설정의 Alias 또는 숫자 Pilot ID를 입력하세요. OFP를 가져올 때 사용합니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = aircraftType,
                    onValueChange = { aircraftType = it; viewModel.setAircraftType(it) },
                    label = { Text("선호 기종 (ICAO, 예: B77W)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = airline,
                    onValueChange = { airline = it; viewModel.setAirline(it) },
                    label = { Text("항공사 코드 (선택, 예: KAL)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ---------------- F4 관심 관제소 ----------------
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("관심 관제소 알림", style = MaterialTheme.typography.titleMedium)

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("알림 켜기", style = MaterialTheme.typography.bodyMedium)
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
                        label = { Text("콜사인 또는 접두사 (예: RKSI, RKRR_CTR)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        if (newWatch.isNotBlank()) {
                            viewModel.addWatched(newWatch)
                            newWatch = ""
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "추가")
                    }
                }

                if (settings.watchedCallsigns.isEmpty()) {
                    Text(
                        "등록된 관제소가 없습니다.",
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
                                        contentDescription = "삭제",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                Text(
                    "접두사로 등록하면 하위 관제석까지 모두 감지합니다 (RKSI → RKSI_TWR, RKSI_APP 등).\n" +
                        "기기 단독 감시는 Android 제약으로 최대 15분 간격입니다. " +
                        "즉시 알림이 필요하면 README의 FCM 서버 배포 안내를 참고하세요.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
