package com.vatradar.app.data.remote

import com.vatradar.app.data.remote.dto.EventsResponse
import com.vatradar.app.data.remote.dto.SimBriefOfpDto
import com.vatradar.app.data.remote.dto.VatsimDataResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

/** https://data.vatsim.net/v3/vatsim-data.json */
interface VatsimApiService {
    @GET("v3/vatsim-data.json")
    suspend fun getVatsimData(): VatsimDataResponse
}

/** https://my.vatsim.net/api/v2/events/latest */
interface VatsimEventsApiService {
    @GET("api/v2/events/latest")
    suspend fun getLatestEvents(): EventsResponse
}

/**
 * METAR / TAF는 JSON이 아니라 평문으로 옵니다 (converter-scalars 사용).
 * METAR: https://metar.vatsim.net/{icao}
 * TAF  : https://aviationweather.gov/api/data/taf?ids={icao}&format=raw
 */
interface WeatherApiService {
    @GET
    suspend fun getRaw(@Url url: String): String
}

/**
 * SimBrief는 알 수 없는 사용자에 대해 HTTP 400 + 에러 바디를 돌려주므로
 * Response로 감싸 에러 바디까지 읽습니다.
 *
 * 식별자 파라미터가 두 가지입니다.
 *   username=<별칭>      계정 설정의 Alias
 *   userid=<숫자 ID>     숫자 Pilot ID
 * 숫자 ID를 username으로 보내면 그 숫자를 별칭으로 검색해 "Unknown UserID"가 납니다.
 * 둘 중 하나만 채우고 나머지는 null로 두면 Retrofit이 빈 파라미터를 생략합니다.
 */
interface SimBriefApiService {
    @GET("api/xml.fetcher.php")
    suspend fun fetchLatestOfp(
        @Query("username") username: String?,
        @Query("userid") userId: String?,
        @Query("json") json: Int = 1
    ): Response<SimBriefOfpDto>
}
