package com.vatradar.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * https://api.vatsim.net/v2/members/{cid}/stats
 * 예: {"id":1823584,"atc":0.0,"pilot":3131.77, ...}
 */
@Serializable
data class VatsimMemberStatsDto(
    @SerialName("id") val id: Long = 0,
    /** 누적 조종 시간(시간 단위). */
    @SerialName("pilot") val pilot: Double = 0.0,
    @SerialName("atc") val atc: Double = 0.0
)
