package com.vatradar.app.domain.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 비행 거리 구분.
 *
 * 경계값은 실제 기재 운용 관행을 따랐습니다.
 *  - 단거리: 협동체(A320/B737)가 주로 도는 구간
 *  - 중거리: 협동체 장거리형 ~ 소형 광동체(B787/A330)
 *  - 장거리: 대형 광동체(B777/A350/B747) 구간
 *
 * [minRunwayFt]는 화면에 노출하지 않는 내부 제약입니다.
 * 이게 없으면 대서양 횡단 노선의 도착지로 6,000ft급 지방공항이 뽑혀,
 * 정작 그 거리를 날 수 있는 기재가 착륙할 수 없는 조합이 나옵니다.
 */
enum class HaulRange(val minNm: Int, val maxNm: Int, val minRunwayFt: Int) {
    SHORT(0, 1500, 5500),
    MEDIUM(1500, 3500, 7500),
    LONG(3500, 20000, 9000);

    fun contains(distanceNm: Double): Boolean =
        distanceNm >= minNm && distanceNm < maxNm

    fun admits(airport: Airport): Boolean =
        airport.international && airport.maxRunwayFt >= minRunwayFt
}

/** 두 지점의 대권거리(해리). */
fun greatCircleNm(
    lat1: Double, lon1: Double,
    lat2: Double, lon2: Double
): Double {
    val earthRadiusNm = 3440.065
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 2 * earthRadiusNm * asin(sqrt(a).coerceAtMost(1.0))
}

fun Airport.distanceNmTo(other: Airport): Double =
    greatCircleNm(latitude, longitude, other.latitude, other.longitude)
