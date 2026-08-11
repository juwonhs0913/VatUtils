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
 * 이런 예가 흔합니다 (하네다·나리타 → 도쿄, 히드로와 개트윅은 반대로 서로 다른 디렉터).
 *
 * "어느 공항에 어느 접근관제가 붙는가"를 담은 전 세계 데이터는 없습니다. 커뮤니티가 만든
 * 두 데이터셋(VATGlasses, vacs)을 확인해 봤지만 둘 다 유럽에 치우쳐 있고 한국은 아예
 * 없습니다. 그래서 **짐작하지 않고 관찰한 것만** 씁니다: 서버가 VATSIM 피드와 관제사들의
 * 과거 관제 이력에서 실제로 쓰인 콜사인을 모아 두고, 앱은 그것을 받아 씁니다.
 *
 * ## 목록은 세 겹입니다
 *
 * 1. **에셋 시드** — 빌드 시점의 목록을 APK에 넣어 둡니다. 설치 직후에도, 비행기 안에서도
 *    후보가 비지 않습니다.
 * 2. **파일 캐시** — 한 번이라도 받아 봤으면 그것을 씁니다.
 * 3. **내려받기** — [refresh]로 하루에 한 번. **화면을 막지 않습니다.**
 *
 * 3번을 화면 경로에 두었던 것이 문제였습니다. 후보 목록 전체가 내려받기가 끝날 때까지
 * 빈 채로 있었고(느린 회선에서 9초가 걸렸습니다) 그동안 아무 표시도 없어서, 한국처럼
 * 데이터가 멀쩡히 있는 나라도 "관제석이 없다"로 보였습니다.
 */
class PositionRegistry(
    private val context: Context,
    private val api: PositionsApiService
) {

    private val mutex = Mutex()

    @Volatile
    private var cached: Set<String>? = null

    /** 지금 당장 쓸 수 있는 목록. 네트워크를 타지 않습니다. */
    suspend fun callsigns(): Set<String> {
        cached?.let { return it }
        return mutex.withLock {
            cached ?: load().also { cached = it }
        }
    }

    /**
     * 캐시가 하루 넘게 묵었으면 새로 받습니다.
     *
     * @return 목록이 실제로 달라졌으면 true. 호출한 쪽에서 화면을 다시 그리라는 뜻입니다.
     */
    suspend fun refresh(): Boolean {
        val file = cacheFile()
        val fresh = file.exists() &&
            System.currentTimeMillis() - file.lastModified() < MAX_AGE_MILLIS
        if (fresh) return false

        // 내려받기는 자물쇠 **밖에서** 합니다. 안에서 하면 그동안 callsigns()가
        // 통째로 막힙니다 — 네트워크를 화면 경로에서 뺀 의미가 없어지고,
        // 비행기 모드에서는 응답이 끊길 때까지 후보 목록이 비어 있게 됩니다.
        val downloaded = withContext(Dispatchers.IO) {
            runCatching {
                val response = api.fetch()
                response.body()?.positions.takeIf { response.isSuccessful }.orEmpty()
            }.onFailure { Log.w(TAG, "관제석 목록 내려받기 실패", it) }
                .getOrDefault(emptyList())
        }
        if (downloaded.isEmpty()) return false

        // 시드와 합칩니다. 서버가 잠깐 잘못된 응답을 주더라도 이미 알던 자리가
        // 목록에서 사라지지는 않게요.
        val merged = withContext(Dispatchers.IO) {
            runCatching { file.writeText(downloaded.joinToString("\n")) }
            downloaded.toSet() + seed()
        }

        return mutex.withLock {
            val changed = merged != cached
            cached = merged
            Log.d(TAG, "관제석 목록 갱신: ${merged.size}개 (바뀜=$changed)")
            changed
        }
    }

    private suspend fun load(): Set<String> = withContext(Dispatchers.IO) {
        val fromCache = runCatching { cacheFile().readLines() }
            .getOrDefault(emptyList())
            .filter { it.isNotBlank() }
        // 캐시가 시드보다 오래된 빌드에서 남았을 수 있어 늘 합쳐 둡니다.
        fromCache.toSet() + seed()
    }

    /** APK에 넣어 둔 목록. 없으면(빌드 실수) 빈 집합 — 그 경우 어프로치 칸이 숨겨집니다. */
    private fun seed(): Set<String> = runCatching {
        context.assets.open(SEED_ASSET).bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.toSet()
        }
    }.onFailure { Log.w(TAG, "관제석 시드 읽기 실패", it) }
        .getOrDefault(emptySet())

    private fun cacheFile() = File(context.filesDir, FILE_NAME)

    private companion object {
        const val TAG = "VATFlight"
        const val FILE_NAME = "positions.txt"
        const val SEED_ASSET = "positions.txt"
        const val MAX_AGE_MILLIS = 24L * 60 * 60 * 1000
    }
}
