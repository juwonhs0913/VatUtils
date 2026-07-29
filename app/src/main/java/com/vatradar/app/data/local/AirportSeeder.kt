package com.vatradar.app.data.local

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 첫 실행 시 assets/airports.csv를 Room에 적재합니다.
 *
 * 프리팩된 .db를 넣지 않고 CSV를 시딩하는 이유:
 * 프리팩 DB는 Room 스키마 해시가 바뀔 때마다 다시 만들어야 하지만,
 * CSV는 스키마와 무관하고 압축이 잘 되어 APK 증가분이 작습니다(약 1MB).
 */
object AirportSeeder {

    private const val TAG = "VATRadar"
    private const val ASSET = "airports.csv"
    private const val BATCH = 1000

    suspend fun seedIfNeeded(context: Context, database: AppDatabase) = withContext(Dispatchers.IO) {
        val dao = database.airportDao()
        if (dao.count() > 0) return@withContext

        val started = System.currentTimeMillis()

        // 파싱을 먼저 끝내고 삽입은 한 트랜잭션으로 묶습니다.
        // 배치별로 트랜잭션을 나누면 매번 디스크 동기화가 일어나 수십 배 느려집니다.
        val airports = ArrayList<AirportEntity>(14_000)
        context.assets.open(ASSET).bufferedReader().useLines { lines ->
            lines.drop(1).forEach { line -> parse(line)?.let { airports += it } }
        }

        database.withTransaction {
            airports.chunked(BATCH).forEach { dao.insertAll(it) }
        }

        Log.d(TAG, "공항 시딩 완료: ${airports.size}개 (${System.currentTimeMillis() - started}ms)")
    }

    /**
     * icao,name,iata,country,country_name,continent,lat,lon,elev_ft,max_rwy_ft,hard
     * 공항명에 쉼표가 들어갈 수 있어 따옴표를 인식하는 최소 CSV 파서를 씁니다.
     */
    private fun parse(line: String): AirportEntity? {
        if (line.isBlank()) return null
        val f = splitCsv(line)
        if (f.size < 11) return null
        return try {
            AirportEntity(
                icao = f[0],
                name = f[1],
                iata = f[2],
                country = f[3],
                countryName = f[4],
                continent = f[5],
                latitude = f[6].toDouble(),
                longitude = f[7].toDouble(),
                elevationFt = f[8].toIntOrNull() ?: 0,
                maxRunwayFt = f[9].toIntOrNull() ?: 0,
                hardSurface = f[10] == "1"
            )
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun splitCsv(line: String): List<String> {
        val out = ArrayList<String>(11)
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    out += sb.toString(); sb.setLength(0)
                }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }
}
