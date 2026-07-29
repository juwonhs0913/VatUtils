package com.vatradar.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "vatradar_settings")

data class UserSettings(
    val simBriefId: String = "",
    val aircraftType: String = "B77W",
    val airline: String = "",
    val watchedCallsigns: Set<String> = emptySet(),
    val minRunwayFt: Int = 8000,
    val hardSurfaceOnly: Boolean = true,
    val notifyEnabled: Boolean = false
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SIMBRIEF_ID = stringPreferencesKey("simbrief_id")
        val AIRCRAFT_TYPE = stringPreferencesKey("aircraft_type")
        val AIRLINE = stringPreferencesKey("airline")
        val WATCHED = stringSetPreferencesKey("watched_callsigns")
        val MIN_RUNWAY = intPreferencesKey("min_runway_ft")
        val HARD_ONLY = booleanPreferencesKey("hard_surface_only")
        val NOTIFY = booleanPreferencesKey("notify_enabled")
        /** 이미 알린 콜사인을 기억해 접속이 유지되는 동안 중복 알림을 막습니다. */
        val ALREADY_NOTIFIED = stringSetPreferencesKey("already_notified")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { p ->
        UserSettings(
            simBriefId = p[Keys.SIMBRIEF_ID] ?: "",
            aircraftType = p[Keys.AIRCRAFT_TYPE] ?: "B77W",
            airline = p[Keys.AIRLINE] ?: "",
            watchedCallsigns = p[Keys.WATCHED] ?: emptySet(),
            minRunwayFt = p[Keys.MIN_RUNWAY] ?: 8000,
            hardSurfaceOnly = p[Keys.HARD_ONLY] ?: true,
            notifyEnabled = p[Keys.NOTIFY] ?: false
        )
    }

    suspend fun current(): UserSettings = settings.first()

    suspend fun setSimBriefId(value: String) = edit { it[Keys.SIMBRIEF_ID] = value.trim() }
    suspend fun setAircraftType(value: String) = edit { it[Keys.AIRCRAFT_TYPE] = value.trim().uppercase() }
    suspend fun setAirline(value: String) = edit { it[Keys.AIRLINE] = value.trim().uppercase() }
    suspend fun setMinRunwayFt(value: Int) = edit { it[Keys.MIN_RUNWAY] = value }
    suspend fun setHardSurfaceOnly(value: Boolean) = edit { it[Keys.HARD_ONLY] = value }
    suspend fun setNotifyEnabled(value: Boolean) = edit { it[Keys.NOTIFY] = value }

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
