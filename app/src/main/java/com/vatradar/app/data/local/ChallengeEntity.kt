package com.vatradar.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 앱에서 뽑은 경로 하나 = 챌린지 하나.
 *
 * 뽑기 횟수 제한이 없으므로 카운터를 두지 않습니다.
 */
@Entity(
    tableName = "challenges",
    indices = [Index("createdAt"), Index("status")]
)
data class ChallengeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val origin: String,
    val destination: String,
    val distanceNm: Int,
    /** ACTIVE / COMPLETED / EXPIRED */
    val status: String,
    val createdAt: Long,
    val completedAt: Long? = null,
    /**
     * 실시간 피드에서 이 경로를 비행 중인 걸 한 번이라도 봤는지.
     * VATSIM에는 비행 기록 API가 없어, 접속 중인 모습을 관찰하는 것이 유일한 근거입니다.
     */
    val seenEnroute: Boolean = false,
    /** 챌린지 시작 시점의 VATSIM 누적 비행시간. 완주 판정의 보조 근거로 씁니다. */
    val baselinePilotHours: Double? = null
)

object ChallengeStatus {
    const val ACTIVE = "ACTIVE"
    const val COMPLETED = "COMPLETED"
    const val EXPIRED = "EXPIRED"
}
