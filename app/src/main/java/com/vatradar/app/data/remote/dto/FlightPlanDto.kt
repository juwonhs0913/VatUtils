package com.vatradar.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FlightPlanDto(
    @SerialName("flight_rules") val flightRules: String? = null,   // "I" or "V"
    @SerialName("aircraft") val aircraft: String? = null,
    @SerialName("aircraft_faa") val aircraftFaa: String? = null,
    @SerialName("aircraft_short") val aircraftShort: String? = null, // 예: "A333"
    @SerialName("departure") val departure: String? = null,
    @SerialName("arrival") val arrival: String? = null,
    @SerialName("alternate") val alternate: String? = null,
    @SerialName("cruise_tas") val cruiseTas: String? = null,
    @SerialName("altitude") val plannedAltitude: String? = null,
    @SerialName("deptime") val deptime: String? = null,
    @SerialName("enroute_time") val enrouteTime: String? = null,
    @SerialName("fuel_time") val fuelTime: String? = null,
    @SerialName("remarks") val remarks: String? = null,
    @SerialName("route") val route: String? = null
)
