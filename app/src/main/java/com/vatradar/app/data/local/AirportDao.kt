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
     * F3 무작위 공항 추첨.
     * 대륙/국가는 null이면 전체를 뜻합니다(= 필터 미적용).
     * hardOnly가 true면 포장 활주로만 대상으로 합니다.
     */
    @Query(
        """
        SELECT * FROM airports
        WHERE maxRunwayFt >= :minRunwayFt
          AND (:continent IS NULL OR continent = :continent)
          AND (:country IS NULL OR country = :country)
          AND (:hardOnly = 0 OR hardSurface = 1)
        ORDER BY RANDOM()
        LIMIT :limit
        """
    )
    suspend fun randomAirports(
        minRunwayFt: Int,
        continent: String?,
        country: String?,
        hardOnly: Boolean,
        limit: Int
    ): List<AirportEntity>

    /** 후보 수를 미리 보여줘 필터가 너무 좁은지 사용자가 알 수 있게 합니다. */
    @Query(
        """
        SELECT COUNT(*) FROM airports
        WHERE maxRunwayFt >= :minRunwayFt
          AND (:continent IS NULL OR continent = :continent)
          AND (:country IS NULL OR country = :country)
          AND (:hardOnly = 0 OR hardSurface = 1)
        """
    )
    suspend fun countMatching(
        minRunwayFt: Int,
        continent: String?,
        country: String?,
        hardOnly: Boolean
    ): Int

    @Query(
        """
        SELECT DISTINCT country AS code, countryName AS name FROM airports
        WHERE (:continent IS NULL OR continent = :continent)
        ORDER BY countryName
        """
    )
    suspend fun countries(continent: String?): List<CountryRow>
}

data class CountryRow(val code: String, val name: String)
