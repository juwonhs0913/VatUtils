package com.vatradar.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GeneralInfoDto(
    @SerialName("version") val version: Int = 0,
    @SerialName("reload") val reload: Int = 1,           // 서버 권장 갱신 주기(분)
    @SerialName("update") val update: String = "",        // 데이터 스냅샷 시각
    @SerialName("update_timestamp") val updateTimestamp: String = "",
    @SerialName("connected_clients") val connectedClients: Int = 0,
    @SerialName("unique_users") val uniqueUsers: Int = 0
)
