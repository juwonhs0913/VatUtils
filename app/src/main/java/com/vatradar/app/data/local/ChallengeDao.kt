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

    @Query("SELECT * FROM challenges WHERE status = 'ACTIVE' ORDER BY createdAt DESC")
    suspend fun activeChallenges(): List<ChallengeEntity>

    @Query("SELECT * FROM challenges ORDER BY createdAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<ChallengeEntity>>

    @Query("SELECT COUNT(*) FROM challenges WHERE status = 'COMPLETED'")
    fun completedCount(): Flow<Int>

    /** 사용자가 X를 눌러 직접 지웁니다. */
    @Query("DELETE FROM challenges WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM challenges WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ChallengeEntity?

    /** 기한이 지난 챌린지를 만료 처리합니다. */
    @Query("UPDATE challenges SET status = 'EXPIRED' WHERE status = 'ACTIVE' AND createdAt < :cutoffMillis")
    suspend fun expireOlderThan(cutoffMillis: Long): Int
}
