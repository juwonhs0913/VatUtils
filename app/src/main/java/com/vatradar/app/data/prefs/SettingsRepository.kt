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
    val watchedCallsigns: Set<String> = emptySet(),
    val notifyEnabled: Boolean = false,
    /** 빈 문자열이면 시스템 언어를 따릅니다. */
    val languageTag: String = "",
    /** "system" | "light" | "dark" */
    val themeMode: String = "system",
    /** "battery" (15분 폴링) | "realtime" (60초 포그라운드 감시) */
    val watchMode: String = "battery"
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SIMBRIEF_ID = stringPreferencesKey("simbrief_id")
        val WATCHED = stringSetPreferencesKey("watched_callsigns")
        val NOTIFY = booleanPreferencesKey("notify_enabled")
        val LANGUAGE = stringPreferencesKey("language_tag")
        val THEME = stringPreferencesKey("theme_mode")
        val WATCH_MODE = stringPreferencesKey("watch_mode")
        /** 이미 알린 콜사인을 기억해 접속이 유지되는 동안 중복 알림을 막습니다. */
        val ALREADY_NOTIFIED = stringSetPreferencesKey("already_notified")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { p ->
        UserSettings(
            simBriefId = p[Keys.SIMBRIEF_ID] ?: "",
            watchedCallsigns = p[Keys.WATCHED] ?: emptySet(),
            notifyEnabled = p[Keys.NOTIFY] ?: false,
            languageTag = p[Keys.LANGUAGE] ?: "",
            themeMode = p[Keys.THEME] ?: "system",
            watchMode = p[Keys.WATCH_MODE] ?: "battery"
        )
    }

    suspend fun current(): UserSettings = settings.first()

    suspend fun setSimBriefId(value: String) = edit { it[Keys.SIMBRIEF_ID] = value.trim() }
    suspend fun setNotifyEnabled(value: Boolean) = edit { it[Keys.NOTIFY] = value }
    suspend fun setLanguageTag(value: String) = edit { it[Keys.LANGUAGE] = value }
    suspend fun setThemeMode(value: String) = edit { it[Keys.THEME] = value }
    suspend fun setWatchMode(value: String) = edit { it[Keys.WATCH_MODE] = value }

    suspend fun addWatched(callsign: String) = edit { p ->
        val v = callsign.trim().uppercase()
        if (v.isNotEmpty()) p[Keys.WATCHED] = (p[Keys.WATCHED] ?: emptySet()) + v
    }

    suspend fun removeWatched(callsign: String) = edit { p ->
        p[Keys.WATCHED] = (p[Keys.WATCHED] ?: emptySet()) - callsign
    }

    suspend fun alreadyNotified(): Set<String> =
        context.dataStore.data.map { it[Keys.ALREADY_NOTIFIED] ?: emptySet() }.first()

    suspend fun setAlreadyNotified(value: Set<String>) = edit { it[Keys.ALREADY_NOTIFIED] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
