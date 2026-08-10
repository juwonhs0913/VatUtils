package com.vatradar.app.domain.model

import com.google.android.gms.maps.model.LatLng
import com.vatradar.app.data.local.AirportEntity

data class Aircraft(
    val cid: Int,
    val callsign: String,
    val pilotName: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Int,
    val groundSpeed: Int,
    val heading: Float,          // 마커 회전에 바로 쓰기 위해 Float
    val aircraftType: String?,   // 예: "A333"
    val departure: String?,
    val arrival: String?,
    val route: String?,
    val flightRules: String?,    // "I" / "V" / null
    /** 비행계획상 출발 예정 시각. "1430" (HHMM, Zulu) 형식입니다. */
    val plannedDepartureHhmm: String?,
    /** 비행계획상 순항 소요 시간. "0530" (HHMM) 형식입니다. */
    val enrouteTimeHhmm: String?
)

enum class FacilityType(val code: Int, val label: String) {
    OBS(0, "OBS"), FSS(1, "FSS"), DEL(2, "DEL"), GND(3, "GND"),
    TWR(4, "TWR"), APP(5, "APP"), CTR(6, "CTR");

    companion object {
        fun fromCode(code: Int): FacilityType = entries.find { it.code == code } ?: OBS
    }
}

data class Controller(
    val cid: Int,
    val name: String,
    val callsign: String,
    val frequency: String,
    val facility: FacilityType,
    val textAtis: List<String>,
    val visualRangeNm: Int,
    /**
     * VATSIM 데이터 피드는 관제사 좌표를 제공하지 않습니다.
     * 콜사인 접두사(예: RKSI_TWR → RKSI)를 공항 DB에서 찾아 위치를 추정합니다.
     * 매칭 실패 시 null이며, 이 경우 지도에는 표시하지 않고 목록에만 남습니다.
     */
    val latitude: Double?,
    val longitude: Double?,
    val airportName: String?,
    /**
     * VATSpy 기반 관제 구역 폴리곤 (CTR/FSS 등 광역 관제만 채워집니다).
     * 공항 단위 관제(TWR/GND/DEL/APP)는 비어 있고 마커로만 표시합니다.
     */
    val boundary: List<List<LatLng>> = emptyList(),
    /** 이름표를 얹을 도형. 합쳐 넣은 조각을 뺀 주 도형입니다. */
    val labelBoundary: List<List<LatLng>> = emptyList()
) {
    /** 콜사인에서 앞부분 ICAO/FIR 코드를 뽑습니다. RKSI_TWR → RKSI, RKRR_CTR → RKRR */
    val prefix: String get() = callsign.substringBefore('_')

    val hasBoundary: Boolean get() = boundary.isNotEmpty()

    /** 지도에 표시할 수 있는가 — 폴리곤이 있거나 공항 좌표가 있으면 표시 가능 */
    val isMappable: Boolean get() = hasBoundary || (latitude != null && longitude != null)
}

data class VatsimEvent(
    val id: Int,
    val name: String,
    val type: String,
    val link: String,
    val bannerUrl: String,
    val airports: List<String>,
    val organisers: List<String>,
    val shortDescription: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long
)

data class Airport(
    val icao: String,
    val name: String,
    val iata: String,
    val country: String,
    val countryName: String,
    val continent: String,
    val latitude: Double,
    val longitude: Double,
    val elevationFt: Int,
    val maxRunwayFt: Int,
    val hardSurface: Boolean,
    val international: Boolean
) {
    val maxRunwayMeters: Int get() = (maxRunwayFt * 0.3048).toInt()
}

fun AirportEntity.toDomain() = Airport(
    icao = icao,
    name = name,
    iata = iata,
    country = country,
    countryName = countryName,
    continent = continent,
    latitude = latitude,
    longitude = longitude,
    elevationFt = elevationFt,
    maxRunwayFt = maxRunwayFt,
    hardSurface = hardSurface,
    international = international
)

/** F5 결과 요약. PRD 요구: 순항 고도, 연료량, 항로, PDF 링크. */
data class OfpSummary(
    val flightNumber: String,
    val origin: String,
    val originName: String,
    val destination: String,
    val destinationName: String,
    val aircraft: String,
    val cruiseAltitude: String,
    val route: String,
    val blockFuel: String,
    val enrouteBurn: String,
    val timeEnroute: String,
    val distanceNm: String,
    val costIndex: String,
    val pdfUrl: String?
)

