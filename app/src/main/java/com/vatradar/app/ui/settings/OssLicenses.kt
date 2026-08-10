package com.vatradar.app.ui.settings

import android.content.Context
import android.util.Log
import com.vatradar.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * 빌드할 때 만들어진 오픈소스 라이선스 원문을 읽습니다.
 *
 * oss-licenses-plugin이 의존성 POM을 훑어 두 개의 raw 리소스를 넣어 줍니다.
 *
 *   third_party_license_metadata : `시작위치:길이 라이브러리이름` 한 줄에 하나
 *   third_party_licenses         : 원문을 이어 붙인 한 덩어리 (릴리스 기준 약 310KB)
 *
 * 같이 딸려 오는 OssLicensesMenuActivity를 쓰지 않는 이유가 있습니다. 그 화면은 목록을
 * Play services의 OssLicensesService에서 받아오는데, 그 서비스가 없는 기기에서는
 * `API_UNAVAILABLE`로 실패해 **빈 화면**이 나옵니다(에뮬레이터에서 실제로 그랬습니다).
 * 게다가 앱 테마를 따르지 않아 혼자 밝은 화면으로 뜹니다.
 * 원문은 이미 APK 안에 들어 있으므로 직접 읽으면 기기와 상관없이 항상 보입니다.
 */
object OssLicenses {

    data class Entry(val name: String, val offset: Int, val length: Int)

    suspend fun entries(context: Context): List<Entry> = withContext(Dispatchers.IO) {
        runCatching {
            context.resources.openRawResource(R.raw.third_party_license_metadata)
                .bufferedReader()
                .useLines { lines -> lines.mapNotNull(::parse).toList() }
                .distinctBy { it.name }
                .sortedBy { it.name.lowercase() }
        }.onFailure { Log.w("VATRadar", "라이선스 목록 읽기 실패", it) }
            .getOrDefault(emptyList())
    }

    suspend fun text(context: Context, entry: Entry): String = withContext(Dispatchers.IO) {
        runCatching {
            context.resources.openRawResource(R.raw.third_party_licenses).use { stream ->
                stream.skipFully(entry.offset.toLong())
                String(stream.readExactly(entry.length), Charsets.UTF_8).trim()
            }
        }.onFailure { Log.w("VATRadar", "라이선스 본문 읽기 실패", it) }
            .getOrDefault("")
    }

    /** `0:47 play-services-maps` */
    private fun parse(line: String): Entry? {
        val space = line.indexOf(' ')
        if (space <= 0) return null
        val colon = line.lastIndexOf(':', space)
        if (colon <= 0) return null
        val offset = line.substring(0, colon).toIntOrNull() ?: return null
        val length = line.substring(colon + 1, space).toIntOrNull() ?: return null
        val name = line.substring(space + 1).trim()
        return if (name.isEmpty()) null else Entry(name, offset, length)
    }

    /** 압축된 리소스 스트림은 skip이 한 번에 다 건너뛴다는 보장이 없습니다. */
    private fun InputStream.skipFully(count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped <= 0) {
                if (read() < 0) return
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    private fun InputStream.readExactly(count: Int): ByteArray {
        val buffer = ByteArray(count)
        var filled = 0
        while (filled < count) {
            val read = read(buffer, filled, count - filled)
            if (read < 0) break
            filled += read
        }
        return if (filled == count) buffer else buffer.copyOf(filled)
    }
}
