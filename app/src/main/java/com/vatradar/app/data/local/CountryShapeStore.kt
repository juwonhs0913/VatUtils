package com.vatradar.app.data.local

import android.content.Context
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 나라 경계 도형 (Natural Earth 110m 단순화본).
 *
 * "비행한 나라를 색칠한다"는 목적이라 정확한 국경선일 필요가 없습니다.
 * Douglas-Peucker로 0.35도까지 줄이고 구멍과 작은 섬을 버려 52KB로 만들었습니다.
 *
 * 형식: `ISO2;lon,lat lon,lat ...|lon,lat ...`  (| 는 여러 조각을 가진 나라)
 */
object CountryShapeStore {

    @Volatile
    private var cache: Map<String, List<List<LatLng>>>? = null

    suspend fun shapes(context: Context): Map<String, List<List<LatLng>>> =
        cache ?: withContext(Dispatchers.IO) {
            val loaded = runCatching { load(context) }
                .onFailure { Log.w("VATRadar", "나라 경계 적재 실패", it) }
                .getOrDefault(emptyMap())
            cache = loaded
            loaded
        }

    private fun load(context: Context): Map<String, List<List<LatLng>>> {
        val result = HashMap<String, List<List<LatLng>>>(200)
        context.assets.open(ASSET).bufferedReader().forEachLine { line ->
            val separator = line.indexOf(';')
            if (separator <= 0) return@forEachLine
            val iso = line.substring(0, separator)

            val rings = line.substring(separator + 1).split('|').mapNotNull { ring ->
                val points = ring.split(' ').mapNotNull { pair ->
                    val comma = pair.indexOf(',')
                    if (comma <= 0) return@mapNotNull null
                    val lon = pair.substring(0, comma).toDoubleOrNull() ?: return@mapNotNull null
                    val lat = pair.substring(comma + 1).toDoubleOrNull() ?: return@mapNotNull null
                    LatLng(lat, lon)
                }
                points.takeIf { it.size >= 4 }
            }
            if (rings.isNotEmpty()) result[iso] = rings
        }
        return result
    }

    private const val ASSET = "country_shapes.txt"
}
