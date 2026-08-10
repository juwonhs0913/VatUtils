package com.vatradar.app.data.repository

import android.content.Context
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
    val distanceNm: Int
)

class AirportRepository(
    private val dao: AirportDao,
    private val context: Context
) {

    /** 국제공항급 목록은 2천여 곳뿐이라 한 번 읽어 캐시합니다. */
    private var internationalCache: List<Airport>? = null

    /** 국가 코드 → 대륙 코드. [continentByCountry] 참고. */
    private var continentCache: Map<String, String>? = null

    private suspend fun international(): List<Airport> =
        internationalCache ?: withContext(Dispatchers.IO) {
            dao.internationalAirports().map { it.toDomain() }.also { internationalCache = it }
        }

    /**
     * 국제공항 중에서 출발지와 도착지를 무작위로 고릅니다.
     *
     * [filter]는 **출발지**에만 겁니다. 도착지까지 같은 범위로 묶으면 국내선만 나와서
     * 나라를 고르는 의미가 없어집니다.
     */
    suspend fun randomRoute(filter: RouteFilter): RandomRoute? = withContext(Dispatchers.Default) {
        val all = international()
        val scope = scopeOf(filter)
        val origins = all.filter(scope)
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

    /** 해당 범위에서 출발지가 될 수 있는 공항 수. */
    suspend fun routeCount(filter: RouteFilter): Int =
        international().count(scopeOf(filter))

    /**
     * 국가 코드 → 대륙 코드. **나라 하나는 대륙 하나에만 올립니다.**
     *
     * 원본(OurAirports)은 대륙을 나라가 아니라 **공항마다** 붙입니다. 그래서 두 대륙에
     * 걸친 나라는 양쪽 목록에 다 나타납니다. 아시아 칩에 이집트가 뜨던 게 이것 때문입니다 —
     * 시나이반도의 샤름엘셰이크·타바·엘아리시가 AS로 되어 있어서였습니다.
     * 스페인(카나리아·세우타 → AF), 그리스(로도스·코스 → AS), 미국(괌·사모아 → OC),
     * 콜롬비아·베네수엘라(카리브 섬 → NA)도 같은 이유로 두 곳에 뜹니다.
     *
     * 공항이 많은 쪽을 그 나라의 대륙으로 삼습니다. 위 다섯 나라는 이것만으로 답이 나옵니다.
     * 러시아와 튀르키예는 공항 수로는 아시아가 많지만 [CONTINENT_OVERRIDES]에서 유럽으로
     * 못 박았습니다 — 두 곳 다 VATSIM 유럽 리전 소속이라 관제소를 찾는 사람은 유럽에서 찾습니다.
     */
    private suspend fun continentByCountry(): Map<String, String> =
        continentCache ?: withContext(Dispatchers.Default) {
            val majority = international()
                .groupBy { it.country }
                .mapValues { (_, group) ->
                    group.groupingBy { it.continent }.eachCount().maxByOrNull { it.value }!!.key
                }
            (majority + CONTINENT_OVERRIDES.filterKeys { it in majority })
                .also { continentCache = it }
        }

    /**
     * 범위 판정을 미리 한 번 만들어 둡니다.
     *
     * [RouteFilter.matches]를 쓰지 않는 이유는 그쪽이 공항에 붙은 대륙을 그대로 보기 때문입니다.
     * 나라를 대륙 하나에 못 박은 이상, 스페인을 유럽에 놓았으면 카나리아 공항도 유럽에서
     * 나와야 합니다 — 아프리카를 골랐다고 스페인 공항이 섞여 나오면 목록이 어긋납니다.
     */
    private suspend fun scopeOf(filter: RouteFilter): (Airport) -> Boolean {
        if (filter.country != null) return { it.country == filter.country }
        val continent = filter.continent ?: return { true }
        val byCountry = continentByCountry()
        return { (byCountry[it.country] ?: it.continent) == continent }
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
            .filter(scopeOf(RouteFilter(continent = continent, country = country)))
            .sortedBy { it.name }

    /** 국제공항이 있는 나라만 목록에 올립니다 (코드 -> 표시 이름). */
    suspend fun countries(continent: String?): List<Pair<String, String>> {
        val byCountry = continentByCountry()
        return international()
            .filter { continent == null || byCountry[it.country] == continent }
            .distinctBy { it.country }
            .map { it.country to it.countryName }
            .sortedBy { it.second }
    }

    /**
     * 이 나라의 공항 전부 — 국제공항급이 아닌 곳까지.
     *
     * 관제 콜사인 앞머리를 되짚는 데 씁니다. 접근관제석이 붙는 공항이 늘 국제공항급인 건
     * 아니어서(군 비행장, 지방 공항) 국제공항 목록만 보면 후보가 통째로 빠집니다.
     */
    suspend fun airportsInCountry(country: String): List<Airport> = withContext(Dispatchers.IO) {
        dao.airportsInCountry(country).map { it.toDomain() }
    }

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

        /**
         * 공항 수만으로는 답이 갈리는 나라.
         *
         * 러시아는 아시아 쪽 공항이 71곳, 유럽 쪽이 41곳이고 튀르키예는 43대 2입니다.
         * 그래도 둘 다 유럽에 둡니다 — VATSIM에서 두 나라 모두 유럽 리전 소속이라
         * 모스크바나 이스탄불 관제소를 찾는 사람은 유럽 칩을 누릅니다.
         */
        val CONTINENT_OVERRIDES = mapOf(
            "RU" to "EU",
            "TR" to "EU"
        )
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
