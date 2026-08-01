package com.vatradar.app.data.local

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 실제로 운항되는 국제선 목록 (OpenFlights 가공본).
 *
 * 무작위로 두 공항을 이으면 아무도 날지 않는 조합이 자주 나옵니다.
 * 실재하는 노선에서 뽑으면 "이 구간을 실제로 어느 항공사가 뛴다"는 맥락이 붙습니다.
 *
 * 파일 형식은 자리를 아끼려고 고정폭입니다:
 *   1행: `# 항공사1|항공사2|...`  (색인 순서)
 *   그 뒤: `AAAABBBBn` — 출발 ICAO(4) + 도착 ICAO(4) + 항공사 색인
 *
 * 편명(예: KE907)은 들어 있지 않습니다. 무료로 쓸 수 있는 스케줄 데이터가 없어
 * 지어내는 대신 노선과 항공사까지만 제공합니다.
 */
object AirlineRouteStore {

    data class Route(val origin: String, val destination: String, val airline: String)

    @Volatile
    private var cache: List<Route>? = null

    suspend fun routes(context: Context): List<Route> =
        cache ?: withContext(Dispatchers.IO) {
            val loaded = runCatching { load(context) }
                .onFailure { Log.w("VATRadar", "노선 데이터 적재 실패", it) }
                .getOrDefault(emptyList())
            cache = loaded
            loaded
        }

    private fun load(context: Context): List<Route> {
        context.assets.open(ASSET).bufferedReader().use { reader ->
            val header = reader.readLine() ?: return emptyList()
            val airlines = header.removePrefix("# ").split('|')

            val result = ArrayList<Route>(32_000)
            reader.forEachLine { line ->
                if (line.length < 9) return@forEachLine
                val index = line.substring(8).toIntOrNull() ?: return@forEachLine
                result += Route(
                    origin = line.substring(0, 4),
                    destination = line.substring(4, 8),
                    airline = airlines.getOrElse(index) { "" }
                )
            }
            return result
        }
    }

    private const val ASSET = "airline_routes.txt"
}
