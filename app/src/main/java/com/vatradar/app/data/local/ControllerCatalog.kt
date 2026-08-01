package com.vatradar.app.data.local

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 알림에 등록할 관제소 목록.
 *
 * CTR/FSS는 수가 많지 않고(전 세계 수백 곳) 이름을 외우기 어려워 목록에서 고르게 합니다.
 * 나머지 관제석(APP/TWR/GND/DEL)은 공항마다 붙으므로 공항을 고르면 한 번에 담습니다.
 *
 * VATSpy의 firs.txt에는 섹터까지 다 들어 있어(EGTT-C, EGTT-D …) 그대로 보여주면
 * 목록이 수천 줄이 됩니다. 여기서는 **루트 FIR만** 남깁니다 — 섹터로 나뉘어 접속해도
 * CallsignMatcher가 루트 이름으로 잡아 주기 때문입니다.
 */
object ControllerCatalog {

    data class CenterEntry(
        /** 등록할 콜사인. 예: RKRR_CTR, LON_CTR */
        val callsign: String,
        val name: String,
        /** ISO2 국가 코드. 공항 DB의 ICAO 접두사로 추정합니다. */
        val country: String?
    )

    @Volatile
    private var cache: List<CenterEntry>? = null

    /**
     * @param icaoPrefixToCountry 공항 DB에서 만든 ICAO 2글자 → 국가 코드 표
     */
    suspend fun centers(
        context: Context,
        icaoPrefixToCountry: Map<String, String>
    ): List<CenterEntry> = cache ?: withContext(Dispatchers.IO) {
        val loaded = runCatching { load(context, icaoPrefixToCountry) }
            .onFailure { Log.w("VATRadar", "관제소 목록 적재 실패", it) }
            .getOrDefault(emptyList())
        cache = loaded
        loaded
    }

    private fun load(
        context: Context,
        icaoPrefixToCountry: Map<String, String>
    ): List<CenterEntry> {
        val entries = LinkedHashMap<String, CenterEntry>()

        context.assets.open("firs.txt").bufferedReader().forEachLine { line ->
            val parts = line.split('|')
            if (parts.size < 4) return@forEachLine
            val id = parts[0]
            // 섹터(EGTT-C 등)는 건너뜁니다. 루트만 목록에 올립니다.
            if ('-' in id) return@forEachLine

            val prefix = parts[1].trim().uppercase()
            val icao = parts[2].trim().uppercase()
            val base = prefix.ifEmpty { icao }
            if (base.isEmpty()) return@forEachLine

            val callsign = base + "_CTR"
            if (callsign in entries) return@forEachLine

            // 이름은 "Incheon ACC - Incheon"처럼 뒤에 FIR 이름이 붙어 있어 앞부분만 씁니다.
            val name = parts[3].substringBefore(" - ").trim().ifEmpty { icao }

            entries[callsign] = CenterEntry(
                callsign = callsign,
                name = name,
                country = icaoPrefixToCountry[icao.take(2)]
                    ?: icaoPrefixToCountry[icao.take(1)]
            )
        }

        return entries.values.sortedBy { it.name }
    }
}
