package com.vatradar.app.ui.settings

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Verified
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vatradar.app.R
import com.vatradar.app.auth.VatsimConnect
import com.vatradar.app.data.prefs.UserSettings
import com.vatradar.app.di.ServiceLocator
import com.vatradar.app.notification.FcmTopics
import com.vatradar.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ServiceLocator.settingsRepository(app)

    private val _settings = MutableStateFlow(UserSettings())
    val settings = _settings.asStateFlow()

    init {
        viewModelScope.launch { repo.settings.collect { _settings.value = it } }
    }

    fun setSimBriefId(v: String) = viewModelScope.launch { repo.setSimBriefId(v) }
    fun setVatsimCid(v: String) = viewModelScope.launch {
        // CID가 바뀌면 이전 토픽을 끊고 새 토픽을 구독합니다.
        val previous = repo.current().vatsimCid
        if (previous.isNotBlank() && previous != v.trim()) FcmTopics.unsubscribeCid(previous)
        repo.setVatsimCid(v)
        FcmTopics.subscribeCid(v)
    }

    /** VATSIM 연결 해제. 서버에 남은 토큰도 함께 지웁니다. */
    fun disconnectVatsim() = viewModelScope.launch {
        val token = repo.current().vatsimLinkToken
        repo.clearVatsimLink()
        ServiceLocator.challengeRepository(getApplication()).revokeLink(token)
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { repo.setThemeMode(mode.tag) }

    fun setLanguage(language: AppLanguage) = viewModelScope.launch {
        repo.setLanguageTag(language.tag)
        // 저장 후 적용합니다. AppCompat이 액티비티를 새 로케일로 다시 만듭니다.
        AppLanguage.apply(language)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var simBriefId by remember(settings.simBriefId) { mutableStateOf(settings.simBriefId) }
    var vatsimCid by remember(settings.vatsimCid) { mutableStateOf(settings.vatsimCid) }
    var languageExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ---------------- 화면 모드 ----------------
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.DarkMode, null, Modifier.size(20.dp))
                    Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleMedium)
                }

                val currentTheme = ThemeMode.fromTag(settings.themeMode)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = currentTheme == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(stringResource(mode.labelRes)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // ---------------- 언어 (버튼을 누르면 아래에 목록) ----------------
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val selected = AppLanguage.fromTag(settings.languageTag)

                TextButton(
                    onClick = { languageExpanded = !languageExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Language, null, Modifier.size(20.dp))
                    Text(
                        "  " + stringResource(R.string.language),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(selected.displayName, style = MaterialTheme.typography.bodyMedium)
                    Icon(
                        if (languageExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null,
                        Modifier.size(20.dp)
                    )
                }

                AnimatedVisibility(visible = languageExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppLanguage.entries.forEach { language ->
                                FilterChip(
                                    selected = selected == language,
                                    onClick = {
                                        viewModel.setLanguage(language)
                                        languageExpanded = false
                                    },
                                    label = { Text(language.displayName) }
                                )
                            }
                        }
                        Text(
                            stringResource(R.string.language_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ---------------- VATSIM ----------------
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("VATSIM", style = MaterialTheme.typography.titleMedium)

                if (settings.vatsimVerified) {
                    // 연결된 상태에서는 CID를 못 고칩니다. 고칠 수 있으면
                    // "확인된 CID"라는 말 자체가 성립하지 않습니다.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Verified,
                            null,
                            Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.vatsim_connected, settings.vatsimCid),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                stringResource(R.string.vatsim_connected_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { viewModel.disconnectVatsim() }) {
                            Text(stringResource(R.string.vatsim_disconnect))
                        }
                    }
                } else {
                    val context = LocalContext.current
                    Button(
                        onClick = { VatsimConnect.launch(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Login, null, Modifier.size(20.dp))
                        Text("  " + stringResource(R.string.vatsim_connect))
                    }
                    Text(
                        stringResource(R.string.vatsim_connect_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = vatsimCid,
                        onValueChange = { vatsimCid = it; viewModel.setVatsimCid(it) },
                        label = { Text(stringResource(R.string.vatsim_cid)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        stringResource(R.string.vatsim_cid_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ---------------- SimBrief ----------------
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.simbrief), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = simBriefId,
                    onValueChange = { simBriefId = it; viewModel.setSimBriefId(it) },
                    label = { Text(stringResource(R.string.simbrief_id)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.simbrief_id_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
