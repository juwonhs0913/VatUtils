package com.vatradar.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * 나의 비행 기록 (Cloudflare Worker).
 *
 * VATSIM이 지난 비행의 출도착 공항을 공개하지 않기 때문에, 서버가 1분마다 받는
 * 피드에서 직접 기록해 둔 것을 읽어 옵니다. 등록한 시점 이후만 존재합니다.
 */
interface LogbookApiService {

    /** CID를 저장하면 그때부터 기록이 시작됩니다. */
    @POST("logbook")
    suspend fun register(@Body request: LogbookRegisterRequest): Response<LogbookResponse>

    @GET("logbook")
    suspend fun fetch(@Query("cid") cid: String): Response<LogbookResponse>
}

@Serializable
data class LogbookRegisterRequest(
    @SerialName("cid") val cid: String
)

@Serializable
data class LogbookResponse(
    @SerialName("ok") val ok: Boolean = false,
    @SerialName("error") val error: String? = null,
    /** 기록을 시작한 시각(epoch millis). null이면 아직 등록 전입니다. */
    @SerialName("since") val since: Long? = null,
    @SerialName("flights") val flights: List<LoggedFlight> = emptyList()
)

@Serializable
data class LoggedFlight(
    @SerialName("callsign") val callsign: String = "",
    @SerialName("departure") val departure: String = "",
    @SerialName("arrival") val arrival: String = "",
    @SerialName("aircraft") val aircraft: String? = null,
    @SerialName("started_at") val startedAt: Long = 0,
    @SerialName("ended_at") val endedAt: Long? = null,
    @SerialName("landed") val landed: Int = 0
) {
    val isFinished: Boolean get() = endedAt != null

    /** 비행 시간(시간 단위). 진행 중이면 지금까지의 경과 시간입니다. */
    val hours: Double
        get() = ((endedAt ?: System.currentTimeMillis()) - startedAt)
            .coerceAtLeast(0) / 3_600_000.0
}
