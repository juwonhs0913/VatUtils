package com.vatradar.app.di

import com.vatradar.app.data.remote.SimBriefApiService
import com.vatradar.app.data.remote.VatsimApiService
import com.vatradar.app.data.remote.VatsimEventsApiService
import com.vatradar.app.data.remote.WeatherApiService
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    private const val VATSIM_DATA_URL = "https://data.vatsim.net/"
    private const val VATSIM_MY_URL = "https://my.vatsim.net/"
    private const val SIMBRIEF_URL = "https://www.simbrief.com/"

    const val METAR_URL = "https://metar.vatsim.net/"
    const val TAF_URL = "https://aviationweather.gov/api/data/taf"

    private val json = Json {
        ignoreUnknownKeys = true   // VATSIM이 필드를 추가해도 앱이 깨지지 않도록 방어
        isLenient = true
        coerceInputValues = true
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        // 평문(METAR/TAF)이 JSON 컨버터에 먼저 걸리지 않도록 Scalars를 앞에 둡니다.
        .addConverterFactory(ScalarsConverterFactory.create())
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    /** SimBrief 에러 바디를 직접 파싱할 때 재사용합니다. */
    val jsonParser: Json get() = json

    val vatsimApiService: VatsimApiService by lazy {
        retrofit(VATSIM_DATA_URL).create(VatsimApiService::class.java)
    }

    val eventsApiService: VatsimEventsApiService by lazy {
        retrofit(VATSIM_MY_URL).create(VatsimEventsApiService::class.java)
    }

    val weatherApiService: WeatherApiService by lazy {
        retrofit(METAR_URL).create(WeatherApiService::class.java)
    }

    val simBriefApiService: SimBriefApiService by lazy {
        retrofit(SIMBRIEF_URL).create(SimBriefApiService::class.java)
    }
}
