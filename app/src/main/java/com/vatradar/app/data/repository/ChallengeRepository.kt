package com.vatradar.app.data.repository

import android.util.Log
import com.vatradar.app.data.local.ChallengeDao
import com.vatradar.app.data.remote.ChallengeWatchApiService
import com.vatradar.app.data.remote.RevokeRequest
import com.vatradar.app.data.remote.UnwatchRequest
import com.vatradar.app.data.remote.WatchRequest
import com.vatradar.app.data.local.ChallengeEntity
import com.vatradar.app.data.local.ChallengeStatus
import com.vatradar.app.domain.model.Airport
import com.vatradar.app.domain.model.pointsForDistance
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class ChallengeRepository(
    private val dao: ChallengeDao,
    private val watchApi: ChallengeWatchApiService
) {

    /**
     * 서버에 완주 감시를 맡깁니다.
     * 실패해도 앱의 기기 판정이 남아 있으므로 조용히 넘어갑니다.
     */
    suspend fun registerWatch(
        cid: String,
        linkToken: String,
        challengeId: Long,
        origin: Airport,
        destination: Airport,
        baselineHours: Double?
    ) {
        if (cid.isBlank()) return
        runCatching {
            watchApi.register(
                WatchRequest(
                    cid = cid.trim(),
                    token = linkToken.ifBlank { null },
                    challengeId = challengeId,
                    origin = origin.icao,
                    destination = destination.icao,
                    arrLat = destination.latitude,
                    arrLon = destination.longitude,
                    arrElevFt = destination.elevationFt,
                    baselineHours = baselineHours
                )
            )
        }.onFailure { Log.w("VATRadar", "완주 감시 등록 실패 (기기 판정으로 계속)", it) }
    }

    suspend fun unregisterWatch(cid: String, linkToken: String, challengeId: Long) {
        if (cid.isBlank()) return
        runCatching {
            watchApi.unregister(
                UnwatchRequest(cid.trim(), linkToken.ifBlank { null }, challengeId)
            )
        }.onFailure { Log.w("VATRadar", "완주 감시 해제 실패", it) }
    }

    /** VATSIM 연결 해제. 서버에 남은 토큰까지 지웁니다. */
    suspend fun revokeLink(linkToken: String) {
        if (linkToken.isBlank()) return
        runCatching { watchApi.revoke(RevokeRequest(linkToken)) }
            .onFailure { Log.w("VATRadar", "VATSIM 연결 해제 실패", it) }
    }


    val totalPoints: Flow<Int> = dao.totalPoints()
    val completedCount: Flow<Int> = dao.completedCount()
    fun recent(limit: Int = 20): Flow<List<ChallengeEntity>> = dao.recent(limit)

    /**
     * 오늘 남은 뽑기 횟수.
     *
     * 기준은 **UTC 자정(00:00Z)** 입니다. 기기의 현지 시간대와 무관하게 같은 순간에
     * 초기화되므로, 시간대를 옮겨 다녀도 하루에 5번이라는 규칙이 흔들리지 않습니다.
     */
    suspend fun remainingRollsToday(): Int {
        val used = dao.countCreatedSince(startOfUtcDay())
        return (DAILY_ROLL_LIMIT - used).coerceAtLeast(0)
    }

    /** 다음 초기화까지 남은 밀리초. 화면에 "N시간 뒤 초기화"로 보여줍니다. */
    fun millisUntilReset(): Long = startOfUtcDay() + DAY_MILLIS - System.currentTimeMillis()

    suspend fun create(
        origin: Airport,
        destination: Airport,
        distanceNm: Int,
        baselinePilotHours: Double?
    ): Long = dao.insert(
        ChallengeEntity(
            origin = origin.icao,
            destination = destination.icao,
            distanceNm = distanceNm,
            points = pointsForDistance(distanceNm),
            status = ChallengeStatus.ACTIVE,
            createdAt = System.currentTimeMillis(),
            baselinePilotHours = baselinePilotHours
        )
    )

    suspend fun activeChallenges(): List<ChallengeEntity> = dao.activeChallenges()

    suspend fun markSeenEnroute(challenge: ChallengeEntity) {
        if (challenge.seenEnroute) return
        dao.update(challenge.copy(seenEnroute = true))
    }

    /** 챌린지 ID로 완주 처리 (서버 푸시로 들어오는 경로). */
    suspend fun completeById(challengeId: Long): ChallengeEntity? {
        val challenge = dao.activeChallenges().firstOrNull { it.id == challengeId } ?: return null
        markCompleted(challenge)
        return challenge
    }

    suspend fun markCompleted(challenge: ChallengeEntity) {
        dao.update(
            challenge.copy(
                status = ChallengeStatus.COMPLETED,
                completedAt = System.currentTimeMillis()
            )
        )
    }

    /** 기한이 지난 챌린지를 정리합니다. */
    suspend fun expireStale(): Int =
        dao.expireOlderThan(System.currentTimeMillis() - CHALLENGE_LIFETIME_MILLIS)

    private fun startOfUtcDay(): Long =
        Instant.now().truncatedTo(ChronoUnit.DAYS).atZone(ZoneOffset.UTC).toInstant().toEpochMilli()

    companion object {
        const val DAILY_ROLL_LIMIT = 5

        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

        /**
         * 챌린지 유효 기간. 장거리는 14시간을 넘기도 하고 사용자가 바로 날지 않을 수도 있어
         * 하루보다 넉넉하게 잡습니다.
         */
        private const val CHALLENGE_LIFETIME_MILLIS = 48 * 60 * 60 * 1000L
    }
}
