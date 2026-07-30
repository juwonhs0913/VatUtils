package com.vatradar.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChallengeDao {

    @Insert
    suspend fun insert(challenge: ChallengeEntity): Long

    @Update
    suspend fun update(challenge: ChallengeEntity)

    /** 오늘(UTC 기준) 뽑은 횟수. 하루 5회 제한 판정에 씁니다. */
    @Query("SELECT COUNT(*) FROM challenges WHERE createdAt >= :sinceUtcMillis")
    suspend fun countCreatedSince(sinceUtcMillis: Long): Int

    @Query("SELECT * FROM challenges WHERE status = 'ACTIVE' ORDER BY createdAt DESC")
    suspend fun activeChallenges(): List<ChallengeEntity>

    @Query("SELECT * FROM challenges ORDER BY createdAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<ChallengeEntity>>

    @Query("SELECT COALESCE(SUM(points), 0) FROM challenges WHERE status = 'COMPLETED'")
    fun totalPoints(): Flow<Int>

    @Query("SELECT COUNT(*) FROM challenges WHERE status = 'COMPLETED'")
    fun completedCount(): Flow<Int>

    /** 기한이 지난 챌린지를 만료 처리합니다. */
    @Query("UPDATE challenges SET status = 'EXPIRED' WHERE status = 'ACTIVE' AND createdAt < :cutoffMillis")
    suspend fun expireOlderThan(cutoffMillis: Long): Int
}
