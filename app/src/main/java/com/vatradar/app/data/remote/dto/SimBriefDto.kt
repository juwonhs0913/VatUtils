package com.vatradar.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * SimBrief xml.fetcher.php?json=1 응답.
 *
 * 유효하지 않은 사용자면 HTTP 400과 함께 fetch.status에 에러 문구만 담겨 오므로,
 * 나머지 필드는 전부 nullable이어야 합니다.
 */
@Serializable
data class SimBriefOfpDto(
    @SerialName("fetch") val fetch: SimBriefFetchDto? = null,
    @SerialName("general") val general: SimBriefGeneralDto? = null,
    @SerialName("origin") val origin: SimBriefAirportDto? = null,
    @SerialName("destination") val destination: SimBriefAirportDto? = null,
    @SerialName("alternate") val alternate: SimBriefAirportDto? = null,
    @SerialName("aircraft") val aircraft: SimBriefAircraftDto? = null,
    @SerialName("fuel") val fuel: SimBriefFuelDto? = null,
    @SerialName("times") val times: SimBriefTimesDto? = null,
    @SerialName("params") val params: SimBriefParamsDto? = null,
    @SerialName("files") val files: SimBriefFilesDto? = null
)

@Serializable
data class SimBriefFetchDto(
    @SerialName("userid") val userid: String? = null,
    @SerialName("status") val status: String? = null
)

@Serializable
data class SimBriefGeneralDto(
    @SerialName("icao_airline") val icaoAirline: String? = null,
    @SerialName("flight_number") val flightNumber: String? = null,
    @SerialName("initial_altitude") val initialAltitude: String? = null,
    @SerialName("cruise_profile") val cruiseProfile: String? = null,
    @SerialName("costindex") val costIndex: String? = null,
    @SerialName("route") val route: String? = null,
    @SerialName("air_distance") val airDistance: String? = null,
    @SerialName("route_distance") val routeDistance: String? = null
)

@Serializable
data class SimBriefAirportDto(
    @SerialName("icao_code") val icaoCode: String? = null,
    @SerialName("iata_code") val iataCode: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("plan_rwy") val planRwy: String? = null
)

@Serializable
data class SimBriefAircraftDto(
    @SerialName("icaocode") val icaoCode: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("reg") val registration: String? = null
)

@Serializable
data class SimBriefFuelDto(
    @SerialName("plan_ramp") val planRamp: String? = null,
    @SerialName("plan_takeoff") val planTakeoff: String? = null,
    @SerialName("enroute_burn") val enrouteBurn: String? = null,
    @SerialName("reserve") val reserve: String? = null,
    @SerialName("alternate_burn") val alternateBurn: String? = null
)

@Serializable
data class SimBriefTimesDto(
    @SerialName("est_time_enroute") val estTimeEnroute: String? = null,
    @SerialName("sched_out") val schedOut: String? = null,
    @SerialName("sched_in") val schedIn: String? = null
)

@Serializable
data class SimBriefParamsDto(
    @SerialName("units") val units: String? = null,
    @SerialName("time_generated") val timeGenerated: String? = null
)

@Serializable
data class SimBriefFilesDto(
    @SerialName("directory") val directory: String? = null,
    @SerialName("pdf") val pdf: SimBriefFileDto? = null
)

@Serializable
data class SimBriefFileDto(
    @SerialName("name") val name: String? = null,
    @SerialName("link") val link: String? = null
)
