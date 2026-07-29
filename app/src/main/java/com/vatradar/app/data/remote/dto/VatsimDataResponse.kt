package com.vatradar.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VatsimDataResponse(
    @SerialName("general") val general: GeneralInfoDto = GeneralInfoDto(),
    @SerialName("pilots") val pilots: List<PilotDto> = emptyList(),
    @SerialName("controllers") val controllers: List<ControllerDto> = emptyList()
    // atis, prefiles, servers 등은 이후 Phase에서 필요 시 확장
)
