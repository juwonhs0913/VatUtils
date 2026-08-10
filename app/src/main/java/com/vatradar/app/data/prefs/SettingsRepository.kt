package com.vatradar.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "vatradar_settings")

data class UserSettings(
    val simBriefId: String = "",
    /** VATSIM CID. 챌린지 완주 확인에 씁니다. */
    val vatsimCid: String = "",
    val watchedCallsigns: Set<String> = emptySet(),
    /** 관심 표시한 이벤트 ID. 시작 1시간 전에 알립니다. */
    val starredEvents: Set<String> = emptySet(),
    val notifyEnabled: Boolean = false,
    /** 빈 문자열이면 시스템 언어를 따릅니다. */
    val languageTag: String = "",
    /** "system" | "light" | "dark" */
    val themeMode: String = "system"
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SIMBRIEF_ID = stringPreferencesKey("simbrief_id")
        val VATSIM_CID = stringPreferencesKey("vatsim_cid")
        val WATCHED = stringSetPreferencesKey("watched_callsigns")
        val STARRED_EVENTS = stringSetPreferencesKey("starred_events")
        val NOTIFY = booleanPreferencesKey("notify_enabled")
        val LANGUAGE = stringPreferencesKey("language_tag")
        val THEME = stringPreferencesKey("theme_mode")
        /** 이미 알린 콜사인을 기억해 접속이 유지되는 동안 중복 알림을 막습니다. */
        val ALREADY_NOTIFIED = stringSetPreferencesKey("already_notified")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { p ->
        UserSettings(
            simBriefId = p[Keys.SIMBRIEF_ID] ?: "",
            vatsimCid = p[Keys.VATSIM_CID] ?: "",
            watchedCallsigns = p[Keys.WATCHED] ?: emptySet(),
            starredEvents = p[Keys.STARRED_EVENTS] ?: emptySet(),
            notifyEnabled = p[Keys.NOTIFY] ?: false,
            languageTag = p[Keys.LANGUAGE] ?: "",
            themeMode = p[Keys.THEME] ?: "system"
        )
    }

    suspend fun current(): UserSettings = settings.first()

    suspend fun setSimBriefId(value: String) = edit { it[Keys.SIMBRIEF_ID] = value.trim() }
    suspend fun setVatsimCid(value: String) = edit { it[Keys.VATSIM_CID] = value.trim() }
    suspend fun setNotifyEnabled(value: Boolean) = edit { it[Keys.NOTIFY] = value }
    suspend fun setLanguageTag(value: String) = edit { it[Keys.LANGUAGE] = value }
    suspend fun setThemeMode(value: String) = edit { it[Keys.THEME] = value }

    suspend fun addWatched(callsign: String) = edit { p ->
        val v = callsign.trim().uppercase()
        if (v.isNotEmpty()) p[Keys.WATCHED] = (p[Keys.WATCHED] ?: emptySet()) + v
    }

    suspend fun removeWatched(callsign: String) = edit { p ->
        p[Keys.WATCHED] = (p[Keys.WATCHED] ?: emptySet()) - callsign
    }

    /**
     * 여러 항목을 한 번에 갈아 끼웁니다.
     *
     * 뺄 것과 넣을 것을 따로 저장하면 그 사이에 목록이 잠깐 비어 화면이 깜빡입니다.
     * 공항의 자리 하나를 끄면 `RKSI` 한 줄이 `RKSI_DEL`·`RKSI_GND` 두 줄로 바뀌는데,
     * 그 찰나에 공항이 목록에서 통째로 사라져 보였습니다.
     */
    suspend fun updateWatched(add: Set<String>, remove: Set<String>) = edit { p ->
        val current = p[Keys.WATCHED] ?: emptySet()
        p[Keys.WATCHED] = current - remove + add.map { it.trim().uppercase() }.filter { it.isNotEmpty() }
    }

    suspend fun starEvent(id: Int) = edit { p ->
        p[Keys.STARRED_EVENTS] = (p[Keys.STARRED_EVENTS] ?: emptySet()) + id.toString()
    }

    suspend fun unstarEvent(id: Int) = edit { p ->
        p[Keys.STARRED_EVENTS] = (p[Keys.STARRED_EVENTS] ?: emptySet()) - id.toString()
    }

    suspend fun alreadyNotified(): Set<String> =
        context.dataStore.data.map { it[Keys.ALREADY_NOTIFIED] ?: emptySet() }.first()

    suspend fun setAlreadyNotified(value: Set<String>) = edit { it[Keys.ALREADY_NOTIFIED] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
