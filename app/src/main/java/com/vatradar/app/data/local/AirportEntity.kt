package com.vatradar.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * assets/airports.csv (OurAirports 가공본)에서 시딩되는 전 세계 공항.
 * maxRunwayFt는 F3의 '최소 활주로 길이' 필터의 핵심 컬럼이라 인덱스를 겁니다.
 */
@Entity(
    tableName = "airports",
    indices = [
        Index("continent"),
        Index("country"),
        Index("maxRunwayFt"),
        Index("international")
    ]
)
data class AirportEntity(
    @PrimaryKey val icao: String,
    val name: String,
    val iata: String,
    val country: String,
    @ColumnInfo(name = "countryName") val countryName: String,
    val continent: String,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "elevationFt") val elevationFt: Int,
    @ColumnInfo(name = "maxRunwayFt") val maxRunwayFt: Int,
    @ColumnInfo(name = "hardSurface") val hardSurface: Boolean,
    /** 정기 여객편이 취항하는 국제공항급인지. Route 탭 추천 대상의 기준입니다. */
    @ColumnInfo(name = "international") val international: Boolean
)
