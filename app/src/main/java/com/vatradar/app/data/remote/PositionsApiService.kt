package com.vatradar.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET

/**
 * 실제로 접속한 적이 있는 관제석 목록 (VATFlight 서버).
 *
 * 왜 목록을 받아 오는가: 공항마다 `<ICAO>_APP`을 만들어 보여 주면 있지도 않은
 * 관제석이 뜹니다. 인천 어프로치는 없습니다 — 인천과 김포의 접근관제는 서울
 * 어프로치(RKSS_APP) 하나가 맡습니다. 어느 공항에 어느 접근관제가 붙는지를 담은
 * 전 세계 데이터는 존재하지 않아서(VATGlasses에도 한국 파일이 없습니다),
 * 서버가 VATSIM 피드와 관제 이력에서 **실제로 쓰인 콜사인만** 모읍니다.
 */
interface PositionsApiService {

    @GET("positions")
    suspend fun fetch(): Response<PositionsResponse>
}

@Serializable
data class PositionsResponse(
    @SerialName("updated") val updated: Long = 0,
    @SerialName("positions") val positions: List<String> = emptyList()
)
