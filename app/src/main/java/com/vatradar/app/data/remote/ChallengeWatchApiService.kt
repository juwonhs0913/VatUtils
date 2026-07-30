package com.vatradar.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.HTTP
import retrofit2.http.POST

/**
 * 챌린지 완주 감시 등록 (Cloudflare Worker).
 *
 * 기기에서 판정하면 앱을 오래 열지 않을 때 Android가 백그라운드 작업을 하루 한 번
 * 수준까지 미뤄 완주를 놓칩니다. 그래서 이미 1분마다 도는 Worker에 감시를 맡깁니다.
 * 완주하면 서버가 cid_<CID> 토픽으로 푸시를 보내고, 앱이 그때 포인트를 지급합니다.
 */
interface ChallengeWatchApiService {

    @POST("watch")
    suspend fun register(@Body request: WatchRequest): Response<WatchResponse>

    /** Retrofit은 본문 있는 DELETE를 @HTTP로 명시해야 허용합니다. */
    @HTTP(method = "DELETE", path = "watch", hasBody = true)
    suspend fun unregister(@Body request: UnwatchRequest): Response<WatchResponse>

    /** VATSIM 연결 해제. 서버에 남은 토큰을 지웁니다. */
    @POST("auth/revoke")
    suspend fun revoke(@Body request: RevokeRequest): Response<WatchResponse>
}

@Serializable
data class WatchRequest(
    @SerialName("cid") val cid: String,
    /**
     * VATSIM Connect 로그인 토큰. 있으면 서버가 이걸로 CID를 정하고
     * 위의 cid는 무시합니다. 남의 CID로 감시를 거는 걸 막는 지점입니다.
     */
    @SerialName("token") val token: String? = null,
    @SerialName("challengeId") val challengeId: Long,
    @SerialName("origin") val origin: String,
    @SerialName("destination") val destination: String,
    /** 도착 판정은 서버가 하므로 좌표를 함께 보냅니다 (서버에는 공항 DB가 없습니다). */
    @SerialName("arrLat") val arrLat: Double,
    @SerialName("arrLon") val arrLon: Double,
    @SerialName("arrElevFt") val arrElevFt: Int,
    @SerialName("baselineHours") val baselineHours: Double? = null
)

@Serializable
data class UnwatchRequest(
    @SerialName("cid") val cid: String,
    @SerialName("token") val token: String? = null,
    @SerialName("challengeId") val challengeId: Long
)

@Serializable
data class RevokeRequest(
    @SerialName("token") val token: String
)

@Serializable
data class WatchResponse(
    @SerialName("ok") val ok: Boolean = false,
    @SerialName("error") val error: String? = null
)
