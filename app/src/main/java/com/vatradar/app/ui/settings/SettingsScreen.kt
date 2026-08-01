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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import android.util.Log
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import com.vatradar.app.R
import com.vatradar.app.data.prefs.UserSettings
import com.vatradar.app.data.remote.LogbookRegisterRequest
import com.vatradar.app.di.ServiceLocator
import com.vatradar.app.notification.FcmTopics
import com.vatradar.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ServiceLocator.settingsRepository(app)

    /** 저장된 값. */
    private val _settings = MutableStateFlow(UserSettings())
    val settings = _settings.asStateFlow()

    /**
     * 화면에서 편집 중인 값.
     *
     * 예전에는 손대는 즉시 저장됐습니다. CID를 고치는 중간 상태(예: "18")로도
     * 서버에 등록이 나가서, 저장 버튼을 두고 확정된 값만 반영하도록 바꿨습니다.
     */
    private val _draft = MutableStateFlow(UserSettings())
    val draft = _draft.asStateFlow()

    val dirty: StateFlow<Boolean> = combine(_settings, _draft) { saved, draft -> saved != draft }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _savedNotice = MutableStateFlow(false)
    val savedNotice = _savedNotice.asStateFlow()

    init {
        viewModelScope.launch {
            repo.settings.collect { stored ->
                val hadEdits = _settings.value != _draft.value
                _settings.value = stored
                // 사용자가 편집 중이면 덮어쓰지 않습니다.
                if (!hadEdits) _draft.value = stored
            }
        }
    }

    fun editSimBriefId(v: String) { _draft.value = _draft.value.copy(simBriefId = v) }
    fun editVatsimCid(v: String) { _draft.value = _draft.value.copy(vatsimCid = v) }
    fun editThemeMode(mode: ThemeMode) { _draft.value = _draft.value.copy(themeMode = mode.tag) }
    fun editLanguage(language: AppLanguage) { _draft.value = _draft.value.copy(languageTag = language.tag) }

    fun discard() { _draft.value = _settings.value }

    fun save() = viewModelScope.launch {
        val saved = _settings.value
        val draft = _draft.value

        if (draft.simBriefId != saved.simBriefId) repo.setSimBriefId(draft.simBriefId)

        if (draft.vatsimCid.trim() != saved.vatsimCid) {
            if (saved.vatsimCid.isNotBlank()) FcmTopics.unsubscribeCid(saved.vatsimCid)
            repo.setVatsimCid(draft.vatsimCid)
            FcmTopics.subscribeCid(draft.vatsimCid)
            // 기록은 등록한 시점부터만 쌓입니다 (VATSIM이 과거 비행의 공항을 공개하지 않음).
            registerLogbook(draft.vatsimCid)
        }

        if (draft.themeMode != saved.themeMode) repo.setThemeMode(draft.themeMode)

        if (draft.languageTag != saved.languageTag) {
            repo.setLanguageTag(draft.languageTag)
            // 로케일 적용은 마지막에 합니다 — 액티비티가 다시 만들어지기 때문입니다.
            AppLanguage.apply(AppLanguage.fromTag(draft.languageTag))
        }

        _savedNotice.value = true
    }

    fun consumeSavedNotice() { _savedNotice.value = false }

    private suspend fun registerLogbook(cid: String) {
        val trimmed = cid.trim()
        if (trimmed.length < 6) return
        runCatching { ServiceLocator.logbookApiService().register(LogbookRegisterRequest(trimmed)) }
            .onFailure { Log.w("VATRadar", "비행 기록 등록 실패", it) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val settings by viewModel.draft.collectAsStateWithLifecycle()
    val dirty by viewModel.dirty.collectAsStateWithLifecycle()
    val savedNotice by viewModel.savedNotice.collectAsStateWithLifecycle()
    var languageExpanded by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }
    val savedText = stringResource(R.string.saved)

    LaunchedEffect(savedNotice) {
        if (savedNotice) {
            snackbarHost.showSnackbar(savedText)
            viewModel.consumeSavedNotice()
        }
    }

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
                            onClick = { viewModel.editThemeMode(mode) },
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
                                        viewModel.editLanguage(language)
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
                OutlinedTextField(
                    value = settings.vatsimCid,
                    onValueChange = viewModel::editVatsimCid,
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

        // ---------------- SimBrief ----------------
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.simbrief), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = settings.simBriefId,
                    onValueChange = viewModel::editSimBriefId,
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
        // ---------------- 저장 ----------------
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (dirty) {
                TextButton(onClick = viewModel::discard) {
                    Text(stringResource(R.string.discard))
                }
            }
            Button(
                onClick = { viewModel.save() },
                enabled = dirty,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                Text("  " + stringResource(R.string.save))
            }
        }

        if (dirty) {
            Text(
                stringResource(R.string.unsaved_changes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        SnackbarHost(snackbarHost)
    }
}
