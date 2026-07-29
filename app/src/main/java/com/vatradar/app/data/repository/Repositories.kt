package com.vatradar.app.data.repository

import com.vatradar.app.data.local.AirportDao
import com.vatradar.app.data.remote.SimBriefApiService
import com.vatradar.app.data.remote.VatsimEventsApiService
import com.vatradar.app.data.remote.WeatherApiService
import com.vatradar.app.di.NetworkModule
import com.vatradar.app.domain.metar.DecodedMetar
import com.vatradar.app.domain.metar.MetarDecoder
import com.vatradar.app.domain.model.Airport
import com.vatradar.app.domain.model.HaulRange
import com.vatradar.app.domain.model.OfpSummary
import com.vatradar.app.domain.model.VatsimEvent
import com.vatradar.app.domain.model.distanceNmTo
import com.vatradar.app.domain.model.toDomain
import com.vatradar.app.util.toEpochMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

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

data class RandomRoute(
    val origin: Airport,
    val destination: Airport,
    val distanceNm: Int
)

class AirportRepository(private val dao: AirportDao) {

    /** 국제공항급 목록은 2천여 곳뿐이라 한 번 읽어 캐시합니다. */
    private var internationalCache: List<Airport>? = null

    private suspend fun international(): List<Airport> =
        internationalCache ?: withContext(Dispatchers.IO) {
            dao.internationalAirports().map { it.toDomain() }.also { internationalCache = it }
        }

    /**
     * 거리 구간에 맞는 출발/도착 공항을 무작위로 뽑습니다.
     *
     * 출발지를 먼저 정하고 그 기준으로 후보를 좁히기 때문에, 출발지가 외딴 곳이면
     * 해당 구간 후보가 없을 수 있습니다. 그럴 때는 다른 출발지로 몇 번 다시 시도합니다.
     */
    suspend fun randomRoute(haul: HaulRange): RandomRoute? = withContext(Dispatchers.Default) {
        // 거리 구간에 맞는 기재가 실제로 뜨고 내릴 수 있는 공항만 후보로 둡니다.
        val airports = international().filter { haul.admits(it) }
        if (airports.size < 2) return@withContext null

        repeat(MAX_ATTEMPTS) {
            val origin = airports.random()
            val candidates = airports.filter {
                it.icao != origin.icao && haul.contains(origin.distanceNmTo(it))
            }
            if (candidates.isNotEmpty()) {
                val destination = candidates.random()
                return@withContext RandomRoute(
                    origin = origin,
                    destination = destination,
                    distanceNm = origin.distanceNmTo(destination).roundToInt()
                )
            }
        }
        null
    }

    /** 해당 거리 구간에서 실제 후보가 되는 공항 수. */
    suspend fun poolSize(haul: HaulRange): Int = international().count { haul.admits(it) }

    suspend fun find(icao: String): Airport? = withContext(Dispatchers.IO) {
        dao.findByIcao(icao.uppercase())?.toDomain()
    }

    suspend fun search(query: String): List<Airport> = withContext(Dispatchers.IO) {
        dao.search(query.uppercase()).map { it.toDomain() }
    }

    private companion object {
        const val MAX_ATTEMPTS = 40
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
