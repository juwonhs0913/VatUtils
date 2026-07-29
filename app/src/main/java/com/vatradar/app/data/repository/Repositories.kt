package com.vatradar.app.data.repository

import com.vatradar.app.data.local.AirportDao
import com.vatradar.app.data.local.CountryRow
import com.vatradar.app.data.remote.SimBriefApiService
import com.vatradar.app.data.remote.VatsimEventsApiService
import com.vatradar.app.data.remote.WeatherApiService
import com.vatradar.app.di.NetworkModule
import com.vatradar.app.domain.metar.DecodedMetar
import com.vatradar.app.domain.metar.MetarDecoder
import com.vatradar.app.domain.model.Airport
import com.vatradar.app.domain.model.OfpSummary
import com.vatradar.app.domain.model.VatsimEvent
import com.vatradar.app.domain.model.toDomain
import com.vatradar.app.util.toEpochMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** 모든 저장소가 공유하는 결과 타입. */
sealed interface Outcome<out T> {
    data class Success<T>(val data: T) : Outcome<T>
    data class Failure(val message: String) : Outcome<Nothing>
}

/** 취소는 다시 던지고, 그 외 예외만 실패로 감쌉니다. */
private inline fun <T> runCatchingOutcome(prefix: String, block: () -> T): Outcome<T> = try {
    Outcome.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Outcome.Failure("$prefix: ${e.message ?: e::class.simpleName}")
}

// ---------------------------------------------------------------- F1 이벤트

class EventsRepository(private val api: VatsimEventsApiService) {

    suspend fun fetchEvents(): Outcome<List<VatsimEvent>> =
        runCatchingOutcome("이벤트를 불러오지 못했습니다") {
            api.getLatestEvents().data.map { dto ->
                VatsimEvent(
                    id = dto.id,
                    name = dto.name,
                    type = dto.type,
                    link = dto.link,
                    bannerUrl = dto.banner,
                    airports = dto.airports.map { it.icao }.filter { it.isNotBlank() },
                    organisers = dto.organisers.mapNotNull { o ->
                        listOfNotNull(o.division, o.subdivision).joinToString(" / ").ifBlank { o.region }
                    },
                    shortDescription = dto.shortDescription,
                    startEpochMillis = dto.startTime.toEpochMillis(),
                    endEpochMillis = dto.endTime.toEpochMillis()
                )
            }.sortedBy { it.startEpochMillis }
        }
}

// ---------------------------------------------------------------- F6 기상

data class WeatherReport(
    val icao: String,
    val metar: DecodedMetar?,
    val taf: String?,
    val metarError: String?
)

class WeatherRepository(private val api: WeatherApiService) {

    suspend fun fetch(icao: String): WeatherReport = coroutineScope {
        val code = icao.uppercase()

        val metarDeferred = async {
            runCatchingOutcome("METAR") {
                api.getRaw(NetworkModule.METAR_URL + code).trim()
            }
        }
        // TAF는 없는 공항이 많아 실패해도 METAR 표시를 막지 않습니다.
        val tafDeferred = async {
            runCatchingOutcome("TAF") {
                api.getRaw("${NetworkModule.TAF_URL}?ids=$code&format=raw").trim()
            }
        }

        val metarOutcome = metarDeferred.await()
        val tafOutcome = tafDeferred.await()

        val metarRaw = (metarOutcome as? Outcome.Success)?.data
        val metarError = when {
            metarOutcome is Outcome.Failure -> metarOutcome.message
            metarRaw.isNullOrBlank() -> "$code 의 METAR가 제공되지 않습니다."
            else -> null
        }

        WeatherReport(
            icao = code,
            metar = metarRaw?.takeIf { it.isNotBlank() }?.let { MetarDecoder.decode(it) },
            taf = (tafOutcome as? Outcome.Success)?.data?.takeIf { it.isNotBlank() },
            metarError = metarError
        )
    }
}

// ---------------------------------------------------------------- F3 공항

class AirportRepository(private val dao: AirportDao) {

    suspend fun random(
        minRunwayFt: Int,
        continent: String?,
        country: String?,
        hardOnly: Boolean
    ): Pair<Airport, Airport>? = withContext(Dispatchers.IO) {
        // 출발/도착이 같은 공항이 되지 않도록 2개를 한 번에 뽑습니다.
        val picked = dao.randomAirports(minRunwayFt, continent, country, hardOnly, 2)
        if (picked.size < 2) null else picked[0].toDomain() to picked[1].toDomain()
    }

    suspend fun countMatching(
        minRunwayFt: Int,
        continent: String?,
        country: String?,
        hardOnly: Boolean
    ): Int = withContext(Dispatchers.IO) {
        dao.countMatching(minRunwayFt, continent, country, hardOnly)
    }

    suspend fun countries(continent: String?): List<CountryRow> = withContext(Dispatchers.IO) {
        dao.countries(continent)
    }

    suspend fun find(icao: String): Airport? = withContext(Dispatchers.IO) {
        dao.findByIcao(icao.uppercase())?.toDomain()
    }

    suspend fun search(query: String): List<Airport> = withContext(Dispatchers.IO) {
        dao.search(query.uppercase()).map { it.toDomain() }
    }
}

// ---------------------------------------------------------------- F5 SimBrief

class SimBriefRepository(private val api: SimBriefApiService) {

    /**
     * SimBrief는 서드파티 앱이 OFP를 직접 생성할 수 있는 공개 엔드포인트를 제공하지 않습니다.
     * 표준 연동 방식은 디스패치 페이지를 파라미터로 채워 열고, 사용자가 Generate를 누른 뒤
     * xml.fetcher.php로 결과를 가져오는 흐름입니다.
     */
    fun buildDispatchUrl(
        origin: String,
        destination: String,
        aircraftType: String,
        airline: String?,
        flightNumber: String?
    ): String = buildString {
        append("https://dispatch.simbrief.com/options/custom")
        append("?orig=${origin.uppercase()}")
        append("&dest=${destination.uppercase()}")
        append("&type=${aircraftType.uppercase()}")
        if (!airline.isNullOrBlank()) append("&airline=${airline.uppercase()}")
        if (!flightNumber.isNullOrBlank()) append("&fltnum=$flightNumber")
        // 등록부호/항로는 SimBrief가 자동 산출하도록 둡니다.
        append("&planformat=lido&units=KGS")
    }

    suspend fun fetchLatestOfp(username: String): Outcome<OfpSummary> =
        runCatchingOutcome("OFP를 가져오지 못했습니다") {
            val response = api.fetchLatestOfp(username.trim())

            if (!response.isSuccessful) {
                // 알 수 없는 사용자면 400 + {"fetch":{"status":"Error: Unknown UserID"}}
                val body = response.errorBody()?.string().orEmpty()
                val status = Regex("\"status\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)
                error(status ?: "SimBrief 응답 오류 (HTTP ${response.code()})")
            }

            val ofp = response.body() ?: error("SimBrief 응답이 비어 있습니다.")
            val status = ofp.fetch?.status
            if (status != null && status.startsWith("Error", ignoreCase = true)) error(status)
            if (ofp.general == null || ofp.origin == null || ofp.destination == null) {
                error("아직 생성된 비행 계획이 없습니다. SimBrief에서 먼저 Generate 해주세요.")
            }

            val units = ofp.params?.units ?: "KGS"
            val pdf = ofp.files?.let { f ->
                val dir = f.directory
                val link = f.pdf?.link
                if (!dir.isNullOrBlank() && !link.isNullOrBlank()) dir + link else null
            }

            OfpSummary(
                flightNumber = listOfNotNull(
                    ofp.general.icaoAirline?.takeIf { it.isNotBlank() },
                    ofp.general.flightNumber?.takeIf { it.isNotBlank() }
                ).joinToString("").ifBlank { "—" },
                origin = ofp.origin.icaoCode ?: "—",
                originName = ofp.origin.name ?: "",
                destination = ofp.destination.icaoCode ?: "—",
                destinationName = ofp.destination.name ?: "",
                aircraft = listOfNotNull(
                    ofp.aircraft?.icaoCode,
                    ofp.aircraft?.registration?.takeIf { it.isNotBlank() }
                ).joinToString(" · ").ifBlank { "—" },
                cruiseAltitude = ofp.general.initialAltitude
                    ?.toIntOrNull()?.let { "FL${it / 100} (${"%,d".format(it)}ft)" }
                    ?: "—",
                route = ofp.general.route?.takeIf { it.isNotBlank() } ?: "DCT",
                blockFuel = ofp.fuel?.planRamp?.toIntOrNull()?.let { "%,d %s".format(it, units) } ?: "—",
                enrouteBurn = ofp.fuel?.enrouteBurn?.toIntOrNull()?.let { "%,d %s".format(it, units) } ?: "—",
                timeEnroute = ofp.times?.estTimeEnroute?.toIntOrNull()
                    ?.let { "%d시간 %02d분".format(it / 3600, (it % 3600) / 60) } ?: "—",
                distanceNm = ofp.general.airDistance?.toIntOrNull()?.let { "%,d nm".format(it) } ?: "—",
                costIndex = ofp.general.costIndex ?: "—",
                pdfUrl = pdf
            )
        }
}
