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
    /**
     * VATSIM Connect 로그인으로 받은 불투명 토큰.
     * 비어 있지 않으면 이 CID는 본인 것임이 서버에서 확인된 상태입니다.
     */
    val vatsimLinkToken: String = "",
    val watchedCallsigns: Set<String> = emptySet(),
    val notifyEnabled: Boolean = false,
    /** 빈 문자열이면 시스템 언어를 따릅니다. */
    val languageTag: String = "",
    /** "system" | "light" | "dark" */
    val themeMode: String = "system"
) {
    /** 직접 입력한 CID가 아니라 VATSIM 로그인으로 확인된 CID인지. */
    val vatsimVerified: Boolean get() = vatsimLinkToken.isNotBlank() && vatsimCid.isNotBlank()
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SIMBRIEF_ID = stringPreferencesKey("simbrief_id")
        val VATSIM_CID = stringPreferencesKey("vatsim_cid")
        val VATSIM_LINK_TOKEN = stringPreferencesKey("vatsim_link_token")
        val WATCHED = stringSetPreferencesKey("watched_callsigns")
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
            vatsimLinkToken = p[Keys.VATSIM_LINK_TOKEN] ?: "",
            watchedCallsigns = p[Keys.WATCHED] ?: emptySet(),
            notifyEnabled = p[Keys.NOTIFY] ?: false,
            languageTag = p[Keys.LANGUAGE] ?: "",
            themeMode = p[Keys.THEME] ?: "system"
        )
    }

    suspend fun current(): UserSettings = settings.first()

    suspend fun setSimBriefId(value: String) = edit { it[Keys.SIMBRIEF_ID] = value.trim() }
    suspend fun setVatsimCid(value: String) = edit { it[Keys.VATSIM_CID] = value.trim() }

    /** VATSIM Connect 로그인 성공. CID와 토큰은 항상 짝으로 움직입니다. */
    suspend fun setVatsimLink(cid: String, token: String) = edit {
        it[Keys.VATSIM_CID] = cid.trim()
        it[Keys.VATSIM_LINK_TOKEN] = token.trim()
    }

    /** 연결 해제. CID는 남겨 둡니다 — 지도에서 내 기체를 찾는 데는 여전히 쓰입니다. */
    suspend fun clearVatsimLink() = edit { it.remove(Keys.VATSIM_LINK_TOKEN) }
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

    suspend fun alreadyNotified(): Set<String> =
        context.dataStore.data.map { it[Keys.ALREADY_NOTIFIED] ?: emptySet() }.first()

    suspend fun setAlreadyNotified(value: Set<String>) = edit { it[Keys.ALREADY_NOTIFIED] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
