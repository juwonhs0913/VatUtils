package com.vatradar.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ControllerDto(
    @SerialName("cid") val cid: Int = 0,
    @SerialName("name") val name: String = "",
    @SerialName("callsign") val callsign: String = "",   // 예: "ZLA_CTR", "SEL_APP"
    @SerialName("frequency") val frequency: String = "",
    @SerialName("facility") val facility: Int = 0,       // 0=OBS,1=FSS,2=DEL,3=GND,4=TWR,5=APP,6=CTR
    @SerialName("rating") val rating: Int = 0,
    @SerialName("server") val server: String = "",
    @SerialName("visual_range") val visualRange: Int = 0,
    @SerialName("text_atis") val textAtis: List<String>? = null,
    @SerialName("logon_time") val logonTime: String = "",
    @SerialName("last_updated") val lastUpdated: String = ""
)
