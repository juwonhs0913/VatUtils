package com.vatradar.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * VATSIM v3 data feed의 pilot 엔트리.
 *
 * 기본값을 넉넉히 준 이유:
 * NetworkModule의 `coerceInputValues = true`는 **기본값이 선언된 프로퍼티에만** 적용됩니다.
 * 기본값 없는 non-null 필드에 null이 들어오면 그대로 파싱 예외가 나므로,
 * 필드 누락/null 가능성이 있는 항목에는 전부 기본값을 둡니다.
 */
@Serializable
data class PilotDto(
    @SerialName("cid") val cid: Int = 0,
    @SerialName("name") val name: String = "",
    @SerialName("callsign") val callsign: String = "",
    @SerialName("server") val server: String = "",
    @SerialName("pilot_rating") val pilotRating: Int = 0,
    @SerialName("latitude") val latitude: Double = 0.0,
    @SerialName("longitude") val longitude: Double = 0.0,
    @SerialName("altitude") val altitude: Int = 0,
    @SerialName("groundspeed") val groundspeed: Int = 0,
    @SerialName("transponder") val transponder: String = "",
    @SerialName("heading") val heading: Int = 0,
    @SerialName("qnh_i_hg") val qnhInHg: Double? = null,
    @SerialName("qnh_mb") val qnhMb: Int? = null,
    @SerialName("flight_plan") val flightPlan: FlightPlanDto? = null, // 비행계획 없는 조종사는 null
    @SerialName("logon_time") val logonTime: String = "",
    @SerialName("last_updated") val lastUpdated: String = ""
)
