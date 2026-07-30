package com.vatradar.app.di

import android.content.Context
import com.vatradar.app.data.local.AppDatabase
import com.vatradar.app.data.local.FirBoundaryStore
import com.vatradar.app.data.prefs.SettingsRepository
import com.vatradar.app.data.repository.AirportRepository
import com.vatradar.app.data.repository.ChallengeRepository
import com.vatradar.app.data.repository.FlightProgressRepository
import com.vatradar.app.data.repository.EventsRepository
import com.vatradar.app.data.repository.SimBriefRepository
import com.vatradar.app.data.repository.VatsimRepository
import com.vatradar.app.data.repository.WeatherRepository

/**
 * Hilt 도입 전까지 쓰는 수동 DI 컨테이너.
 * WorkManager처럼 ViewModel 밖에서도 저장소가 필요한 곳이 있어 Application 스코프로 둡니다.
 */
object ServiceLocator {

    @Volatile private var settings: SettingsRepository? = null
    @Volatile private var vatsim: VatsimRepository? = null
    @Volatile private var events: EventsRepository? = null
    @Volatile private var weather: WeatherRepository? = null
    @Volatile private var airports: AirportRepository? = null
    @Volatile private var simBrief: SimBriefRepository? = null
    @Volatile private var firBoundaries: FirBoundaryStore? = null
    @Volatile private var challenges: ChallengeRepository? = null
    @Volatile private var flightProgress: FlightProgressRepository? = null

    fun challengeRepository(context: Context): ChallengeRepository =
        challenges ?: synchronized(this) {
            challenges ?: ChallengeRepository(AppDatabase.get(context).challengeDao())
                .also { challenges = it }
        }

    fun flightProgressRepository(context: Context): FlightProgressRepository =
        flightProgress ?: synchronized(this) {
            flightProgress ?: FlightProgressRepository(
                NetworkModule.vatsimApiService,
                NetworkModule.memberApiService,
                challengeRepository(context),
                airportRepository(context)
            ).also { flightProgress = it }
        }

    /** VATSpy FIR 경계 — assets 파싱 결과를 캐시하므로 앱 전체에서 하나만 둡니다. */
    fun firBoundaryStore(context: Context): FirBoundaryStore =
        firBoundaries ?: synchronized(this) {
            firBoundaries ?: FirBoundaryStore(context.applicationContext).also { firBoundaries = it }
        }

    fun settingsRepository(context: Context): SettingsRepository =
        settings ?: synchronized(this) {
            settings ?: SettingsRepository(context.applicationContext).also { settings = it }
        }

    fun vatsimRepository(context: Context): VatsimRepository =
        vatsim ?: synchronized(this) {
            vatsim ?: VatsimRepository(
                NetworkModule.vatsimApiService,
                AppDatabase.get(context).airportDao(),
                firBoundaryStore(context)
            ).also { vatsim = it }
        }

    fun eventsRepository(): EventsRepository =
        events ?: synchronized(this) {
            events ?: EventsRepository(NetworkModule.eventsApiService).also { events = it }
        }

    fun weatherRepository(): WeatherRepository =
        weather ?: synchronized(this) {
            weather ?: WeatherRepository(NetworkModule.weatherApiService).also { weather = it }
        }

    fun airportRepository(context: Context): AirportRepository =
        airports ?: synchronized(this) {
            airports ?: AirportRepository(AppDatabase.get(context).airportDao()).also { airports = it }
        }

    fun simBriefRepository(): SimBriefRepository =
        simBrief ?: synchronized(this) {
            simBrief ?: SimBriefRepository(NetworkModule.simBriefApiService).also { simBrief = it }
        }
}
