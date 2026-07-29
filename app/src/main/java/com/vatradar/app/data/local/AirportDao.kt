package com.vatradar.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AirportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(airports: List<AirportEntity>)

    @Query("SELECT COUNT(*) FROM airports")
    suspend fun count(): Int

    @Query("SELECT * FROM airports WHERE icao = :icao LIMIT 1")
    suspend fun findByIcao(icao: String): AirportEntity?

    @Query("SELECT * FROM airports WHERE icao IN (:icaos)")
    suspend fun findAllByIcao(icaos: List<String>): List<AirportEntity>

    @Query(
        """
        SELECT * FROM airports
        WHERE icao LIKE :q || '%' OR iata = :q OR name LIKE '%' || :q || '%'
        ORDER BY maxRunwayFt DESC
        LIMIT 30
        """
    )
    suspend fun search(q: String): List<AirportEntity>

    /**
     * 국제공항급 전체.
     *
     * SQLite에는 삼각함수가 없어 대권거리를 쿼리에서 계산할 수 없습니다.
     * 대상이 2천여 곳뿐이라 한 번 읽어 메모리에서 거리 계산을 합니다.
     */
    @Query("SELECT * FROM airports WHERE international = 1")
    suspend fun internationalAirports(): List<AirportEntity>

    @Query("SELECT COUNT(*) FROM airports WHERE international = 1")
    suspend fun internationalCount(): Int
}
