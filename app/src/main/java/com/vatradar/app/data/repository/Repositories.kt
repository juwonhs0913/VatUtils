package com.vatradar.app.data.repository

import android.content.Context
import com.vatradar.app.data.local.AirlineRouteStore
import com.vatradar.app.data.local.AirportDao
import com.vatradar.app.data.remote.SimBriefApiService
import com.vatradar.app.data.remote.VatsimEventsApiService
import com.vatradar.app.data.remote.WeatherApiService
import com.vatradar.app.di.NetworkModule
import com.vatradar.app.domain.metar.DecodedMetar
import com.vatradar.app.domain.metar.MetarDecoder
import com.vatradar.app.domain.model.Airport
import com.vatradar.app.domain.model.RouteFilter
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
    val distanceNm: Int,
    /** 이 구간을 실제로 운항하는 항공사. 실재 노선에서 뽑히지 않았으면 null입니다. */
    val airline: String? = null
)

class AirportRepository(
    private val dao: AirportDao,
    private val context: Context
) {

    /** 국제공항급 목록은 2천여 곳뿐이라 한 번 읽어 캐시합니다. */
    private var internationalCache: List<Airport>? = null

    private suspend fun international(): List<Airport> =
        internationalCache ?: withContext(Dispatchers.IO) {
            dao.internationalAirports().map { it.toDomain() }.also { internationalCache = it }
        }

    /**
     * 실제로 운항되는 국제선 중에서 무작위로 하나 고릅니다.
     *
     * [filter]는 **출발지**에만 겁니다. 도착지까지 같은 범위로 묶으면 국내선만 나와서
     * 나라를 고르는 의미가 없어집니다.
     *
     * 조건에 맞는 실재 노선이 없으면 (예: 정기 국제선이 거의 없는 나라) 그 범위의
     * 공항끼리 임의로 잇는 방식으로 물러섭니다. 빈손으로 돌려주는 것보다 낫습니다.
     */
    suspend fun randomRoute(filter: RouteFilter): RandomRoute? = withContext(Dispatchers.Default) {
        val byIcao = international().associateBy { it.icao }

        val real = AirlineRouteStore.routes(context).filter { route ->
            val origin = byIcao[route.origin] ?: return@filter false
            byIcao.containsKey(route.destination) && filter.matches(origin)
        }

        if (real.isNotEmpty()) {
            val picked = real.random()
            val origin = byIcao.getValue(picked.origin)
            val destination = byIcao.getValue(picked.destination)
            return@withContext RandomRoute(
                origin = origin,
                destination = destination,
                distanceNm = origin.distanceNmTo(destination).roundToInt(),
                airline = picked.airline.takeIf { it.isNotBlank() }
            )
        }

        // 실재 노선이 없는 범위 — 그 범위의 공항에서 아무 곳으로나 잇습니다.
        val origins = international().filter { filter.matches(it) }
        val all = internationalCache ?: return@withContext null
        if (origins.isEmpty() || all.size < 2) return@withContext null

        repeat(MAX_ATTEMPTS) {
            val origin = origins.random()
            val destination = all.random()
            if (destination.icao != origin.icao) {
                return@withContext RandomRoute(
                    origin = origin,
                    destination = destination,
                    distanceNm = origin.distanceNmTo(destination).roundToInt()
                )
            }
        }
        null
    }

    /** 해당 범위에서 실제로 뽑힐 수 있는 노선 수. 화면에 후보 규모를 보여줍니다. */
    suspend fun routeCount(filter: RouteFilter): Int = withContext(Dispatchers.Default) {
        val byIcao = international().associateBy { it.icao }
        AirlineRouteStore.routes(context).count { route ->
            val origin = byIcao[route.origin]
            origin != null && byIcao.containsKey(route.destination) && filter.matches(origin)
        }
    }

    /**
     * ICAO 접두사 → 국가 코드.
     *
     * FIR 코드(RKRR, EGTT)에는 나라 정보가 없어서, 같은 접두사를 쓰는 공항에서
     * 되짚습니다. 2글자로 먼저 보고(RK → KR), 없으면 1글자로 봅니다(K → US).
     * 접두사 하나가 여러 나라에 걸리는 경우가 있어 가장 흔한 나라를 택합니다.
     */
    suspend fun icaoPrefixToCountry(): Map<String, String> = withContext(Dispatchers.Default) {
        val all = international()
        fun mostCommon(width: Int): Map<String, String> =
            all.groupBy { it.icao.take(width) }
                .mapValues { (_, group) ->
                    group.groupingBy { it.country }.eachCount().maxByOrNull { it.value }!!.key
                }
        mostCommon(1) + mostCommon(2)   // 2글자가 1글자를 덮어씁니다
    }

    /** 나라 코드 → 표시 이름 (국제공항이 있는 나라만). */
    suspend fun countryNames(): Map<String, String> =
        international().associate { it.country to it.countryName }

    /** 알림 등록용 공항 목록. */
    suspend fun airportsIn(continent: String?, country: String?): List<Airport> =
        international()
            .filter { country != null && it.country == country || country == null && (continent == null || it.continent == continent) }
            .sortedBy { it.name }

    /** 국제공항이 있는 나라만 목록에 올립니다 (코드 -> 표시 이름). */
    suspend fun countries(continent: String?): List<Pair<String, String>> =
        international()
            .filter { continent == null || it.continent == continent }
            .distinctBy { it.country }
            .map { it.country to it.countryName }
            .sortedBy { it.second }

    suspend fun find(icao: String): Airport? = withContext(Dispatchers.IO) {
        dao.findByIcao(icao.uppercase())?.toDomain()
    }

    /** 여러 ICAO를 한 번에 조회합니다 (비행계획의 출도착지 표시용). */
    suspend fun findAll(icaos: Collection<String>): List<Airport> =
        if (icaos.isEmpty()) emptyList()
        else withContext(Dispatchers.IO) {
            dao.findAllByIcao(icaos.map { it.uppercase() }.distinct()).map { it.toDomain() }
        }

    suspend fun search(query: String): List<Airport> = withContext(Dispatchers.IO) {
        dao.search(query.uppercase()).map { it.toDomain() }
    }

    private companion object {
        const val MAX_ATTEMPTS = 40
    }
}

// ---------------------------------------------------------------- F5 SimBrief

/**
 * 숫자만으로 이루어져 있으면 Pilot ID로 봅니다.
 * SimBrief 별칭은 숫자만으로 만들 수도 있지만 드물고, 그런 경우 사용자는
 * 자기 Pilot ID를 쓰면 되므로 숫자를 ID로 해석하는 쪽이 실패가 적습니다.
 */
internal fun isNumericPilotId(value: String): Boolean =
    value.isNotEmpty() && value.all { it.isDigit() }

/** SimBrief 원문 오류를 사용자가 무엇을 해야 하는지 알 수 있는 문장으로 바꿉니다. */
internal fun friendlyMessage(status: String?): String? = when {
    status == null -> null
    status.contains("Unknown UserID", ignoreCase = true) ->
        "SimBrief에서 이 ID를 찾지 못했습니다. 설정의 값이 계정의 Alias 또는 숫자 Pilot ID와 일치하는지 확인해 주세요."
    status.contains("No flight plan on file", ignoreCase = true) ->
        "이 계정에 저장된 비행 계획이 없습니다. SimBrief에서 Generate를 눌러 OFP를 만든 뒤 다시 시도해 주세요."
    else -> status
}

class SimBriefRepository(private val api: SimBriefApiService) {

    /**
     * SimBrief는 서드파티 앱이 OFP를 직접 생성할 수 있는 공개 엔드포인트를 제공하지 않습니다.
     * 표준 연동 방식은 디스패치 페이지를 파라미터로 채워 열고, 사용자가 Generate를 누른 뒤
     * xml.fetcher.php로 결과를 가져오는 흐름입니다.
     */
    fun buildDispatchUrl(origin: String, destination: String): String = buildString {
        append("https://dispatch.simbrief.com/options/custom")
        append("?orig=${origin.uppercase()}")
        append("&dest=${destination.uppercase()}")
        // 기종·항공사·등록부호·항로는 SimBrief 화면에서 사용자가 고르거나
        // 계정 기본값이 채웁니다. 앱에서 강제하지 않습니다.
    }

    suspend fun fetchLatestOfp(identifier: String): Outcome<OfpSummary> =
        runCatchingOutcome("OFP를 가져오지 못했습니다") {
            val id = identifier.trim()
            val numeric = isNumericPilotId(id)

            val response = api.fetchLatestOfp(
                username = if (numeric) null else id,
                userId = if (numeric) id else null
            )

            if (!response.isSuccessful) {
                // 알 수 없는 사용자면 400 + {"fetch":{"status":"Error: Unknown UserID"}}
                val body = response.errorBody()?.string().orEmpty()
                val status = Regex("\"status\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)
                error(friendlyMessage(status) ?: "SimBrief 응답 오류 (HTTP ${response.code()})")
            }

            val ofp = response.body() ?: error("SimBrief 응답이 비어 있습니다.")
            val status = ofp.fetch?.status
            if (status != null && status.startsWith("Error", ignoreCase = true)) {
                error(friendlyMessage(status) ?: status)
            }
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
