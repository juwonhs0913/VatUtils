package com.vatradar.app.data.repository

import android.content.Context
import android.util.Log
import com.vatradar.app.data.remote.PositionsApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 실제로 존재하는 관제석 이름.
 *
 * 예전에는 공항마다 `<ICAO>_APP`을 만들어 목록에 넣었습니다. 그런데 인천 어프로치는
 * 존재하지 않습니다 — 인천과 김포의 접근관제는 서울 어프로치(RKSS_APP) 하나가 맡습니다.
 * 이런 예가 흔합니다 (하네다·나리타 → 도쿄, 히드로·개트윅은 반대로 서로 다른 디렉터).
 *
 * "어느 공항에 어느 접근관제가 붙는가"를 담은 전 세계 데이터는 없습니다.
 * VATGlasses가 그런 데이터를 모으지만 한국 파일이 아예 없고, VATSpy에는 공항 관제석이
 * 들어 있지 않습니다. 그래서 **짐작하지 않고 관찰한 것만** 씁니다: 서버가 VATSIM 피드와
 * 관제사들의 과거 관제 이력에서 실제로 쓰인 콜사인을 모아 두고, 앱은 그것을 받아 씁니다.
 *
 * 목록은 천천히 자라므로 하루에 한 번만 받아 파일에 캐시합니다. 받지 못하면 캐시를 쓰고,
 * 캐시도 없으면 빈 집합입니다 — 그 경우 화면에서 어프로치 후보를 아예 감춥니다.
 * 없는 것을 지어내느니 안 보여 주는 편이 낫습니다.
 */
class PositionRegistry(
    private val context: Context,
    private val api: PositionsApiService
) {

    private val mutex = Mutex()

    @Volatile
    private var cached: Set<String>? = null

    suspend fun callsigns(): Set<String> {
        cached?.let { return it }
        return mutex.withLock {
            cached ?: load().also { cached = it }
        }
    }

    private suspend fun load(): Set<String> = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, FILE_NAME)
        val fresh = file.exists() &&
            System.currentTimeMillis() - file.lastModified() < MAX_AGE_MILLIS

        if (fresh) {
            runCatching { file.readLines().filter { it.isNotBlank() }.toSet() }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { return@withContext it }
        }

        val downloaded = runCatching {
            val response = api.fetch()
            response.body()?.positions.takeIf { response.isSuccessful }.orEmpty()
        }.onFailure { Log.w("VATRadar", "관제석 목록 내려받기 실패", it) }
            .getOrDefault(emptyList())

        if (downloaded.isNotEmpty()) {
            runCatching { file.writeText(downloaded.joinToString("\n")) }
            return@withContext downloaded.toSet()
        }

        // 못 받았으면 오래된 캐시라도 씁니다. 어제 존재하던 관제석은 오늘도 존재합니다.
        runCatching { file.readLines().filter { it.isNotBlank() }.toSet() }
            .getOrDefault(emptySet())
    }

    private companion object {
        const val FILE_NAME = "positions.txt"
        const val MAX_AGE_MILLIS = 24L * 60 * 60 * 1000
    }
}
