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

    /**
     * 미주 VATSIM 콜사인은 ICAO가 아니라 IATA를 씁니다 (KBOS가 아니라 BOS_TWR).
     * 그래서 ICAO로 못 찾은 접두사는 IATA로 한 번 더 조회해야 합니다.
     */
    @Query("SELECT * FROM airports WHERE iata != '' AND iata IN (:codes)")
    suspend fun findAllByIata(codes: List<String>): List<AirportEntity>

    /**
     * 이 나라의 공항 전부.
     *
     * 접근관제 콜사인의 앞머리를 되짚는 데 씁니다. 국제공항급만 보면
     * 지방 공항·군 비행장에 붙는 접근관제석이 통째로 빠집니다.
     */
    @Query("SELECT * FROM airports WHERE country = :country")
    suspend fun airportsInCountry(country: String): List<AirportEntity>

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
