package com.vatradar.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** https://my.vatsim.net/api/v2/events/latest */
@Serializable
data class EventsResponse(
    @SerialName("data") val data: List<EventDto> = emptyList()
)

@Serializable
data class EventDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("type") val type: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("link") val link: String = "",
    @SerialName("banner") val banner: String = "",
    @SerialName("organisers") val organisers: List<OrganiserDto> = emptyList(),
    @SerialName("airports") val airports: List<EventAirportDto> = emptyList(),
    @SerialName("routes") val routes: List<EventRouteDto> = emptyList(),
    @SerialName("start_time") val startTime: String = "",
    @SerialName("end_time") val endTime: String = "",
    @SerialName("short_description") val shortDescription: String = "",
    @SerialName("description") val description: String = ""
)

@Serializable
data class OrganiserDto(
    @SerialName("region") val region: String? = null,
    @SerialName("division") val division: String? = null,
    @SerialName("subdivision") val subdivision: String? = null,
    @SerialName("organised_by_vatsim") val organisedByVatsim: Boolean = false
)

@Serializable
data class EventAirportDto(
    @SerialName("icao") val icao: String = ""
)

@Serializable
data class EventRouteDto(
    @SerialName("departure") val departure: String? = null,
    @SerialName("arrival") val arrival: String? = null,
    @SerialName("route") val route: String? = null
)
