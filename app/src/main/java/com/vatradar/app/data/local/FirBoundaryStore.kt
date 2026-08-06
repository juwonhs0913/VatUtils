package com.vatradar.app.data.local

import android.content.Context
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.vatradar.app.domain.BoundaryLabelPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * VATSpy 기반 FIR 경계 저장소.
 *
 * VATSIM 데이터 피드는 관제사 좌표를 주지 않으므로, 콜사인에서 FIR을 역추적해
 * 실제 관제 구역 폴리곤을 그립니다.
 *
 * assets:
 *   firs.txt            ICAO|콜사인접두사|경계ID|이름
 *   uirs.txt            ID|이름|FIR1,FIR2,...
 *   fir_boundaries.txt  경계ID|lat,lon lat,lon ...|(링 구분)
 *
 * 경계 좌표는 요청된 것만 파싱해 캐시합니다. 전부 올리면 메모리 낭비이고,
 * 실제로 접속 중인 관제소는 보통 수십~수백 곳뿐입니다.
 */
/**
 * 담당 구역과 소속 ACC 전역.
 *
 * [label]을 따로 두는 이유: 그리기용 [rings]에는 뉴욕처럼 떨어져 있는 조각을 더해
 * 넣는데, 그 조각들이 라벨 위치 계산을 끌고 갑니다. 마가단(UHMM)은 그 때문에
 * 라벨이 야쿠티야까지 밀려났습니다. 이름표는 그 구역의 **주 도형** 위에 놓입니다.
 */
data class BoundaryMatch(
    val rings: List<List<LatLng>>,
    val parent: List<List<LatLng>>,
    val label: List<List<LatLng>> = rings
)

class FirBoundaryStore(private val context: Context) {

    private data class FirRecord(
        val icao: String,
        val callsignPrefix: String,
        val boundaryId: String,
        val name: String
    )

    private val mutex = Mutex()
    private var loaded = false

    /** 콜사인 접두사(대문자) → FIR */
    private val byCallsignPrefix = HashMap<String, FirRecord>()
    /** FIR ICAO → FIR */
    private val byIcao = HashMap<String, FirRecord>()
    /** UIR ID → 소속 FIR ICAO 목록 */
    private val uirs = HashMap<String, List<String>>()
    /** 경계 ID → 아직 파싱하지 않은 원본 라인 */
    private val rawBoundaries = HashMap<String, String>()

    /** 루트 경계 ID -> 그 아래 섹터 경계 ID들. KZNY -> [KZNY-W, KZNY-BDA] */
    private val sectorsByRoot = HashMap<String, MutableList<String>>()
    /** 경계 ID → 파싱된 폴리곤 (링 여러 개 가능) */
    private val parsedBoundaries = HashMap<String, List<List<LatLng>>>()

    private suspend fun ensureLoaded() {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            withContext(Dispatchers.IO) {
                runCatching {
                    context.assets.open("firs.txt").bufferedReader().forEachLine { line ->
                        val p = line.split("|")
                        if (p.size >= 4) {
                            val record = FirRecord(p[0], p[1], p[2], p[3])
                            byIcao[record.icao.uppercase()] = record
                            if (record.callsignPrefix.isNotBlank()) {
                                byCallsignPrefix[record.callsignPrefix.uppercase()] = record
                            }
                        }
                    }
                    context.assets.open("uirs.txt").bufferedReader().forEachLine { line ->
                        val p = line.split("|")
                        if (p.size >= 3) {
                            uirs[p[0].uppercase()] = p[2].split(",").map { it.trim().uppercase() }
                        }
                    }
                    context.assets.open("fir_boundaries.txt").bufferedReader().forEachLine { line ->
                        val id = line.substringBefore('|')
                        if (id.isNotBlank()) {
                            val upper = id.uppercase()
                            rawBoundaries[upper] = line
                            // KZNY-W 는 KZNY 의 조각입니다. 루트로 접속했을 때
                            // 조각까지 함께 그리려고 미리 묶어 둡니다.
                            val root = upper.substringBefore('-')
                            if (root != upper) {
                                sectorsByRoot.getOrPut(root) { mutableListOf() } += upper
                            }
                        }
                    }
                }.onFailure { Log.e("VATRadar", "FIR 데이터 로드 실패", it) }
            }
            loaded = true
        }
    }

    /**
     * 관제사 콜사인에 해당하는 폴리곤을 찾습니다.
     *
     * VATSpy의 FIR 레코드는 `ICAO|콜사인접두사|경계ID|이름` 형태이고, 둘이 다를 수 있습니다.
     * 예) `KZHU|HOU|KZHU|Houston` — 미주 ARTCC는 콜사인이 ICAO가 아니라 3글자 약어입니다.
     * 그래서 ICAO와 콜사인 접두사를 **양쪽 다** 조회해야 합니다.
     *
     * 매칭 예)
     *   RKRR_CTR    → FIR RKRR (ICAO 일치)
     *   RKRR_N_CTR  → 섹터 RKRR-N (콜사인 접두사 RKRR_N), 없으면 RKRR로 폴백
     *   HOU_46_CTR  → 콜사인 접두사 HOU → KZHU (섹터 번호 46은 무시)
     *   AFRE_CTR    → UIR AFRE → 소속 FIR 폴리곤 전체
     */
    /**
     * 담당 구역과, 그것이 세부 섹터일 때의 소속 ACC 전역.
     *
     * VATJPN SOP는 "より狭域を担当するものが優先される"(좁은 쪽이 우선)라고 정하고 있어
     * 담당 구역은 섹터가 맞습니다. 다만 섹터만 덩그러니 그리면 그 관제사가 어느 ACC
     * 소속인지 지도에서 알 수 없습니다. vattastic 같은 다른 도구가 ACC 전역을 칠하는
     * 것도 그래서입니다. 전역은 옅게 깔고 담당 섹터를 진하게 얹습니다.
     */
    suspend fun boundaryMatch(callsign: String): BoundaryMatch {
        val rings = boundariesFor(callsign)
        if (rings.isEmpty()) return BoundaryMatch(emptyList(), emptyList())

        val matchedId = matchedBoundaryId(callsign)
        // 라벨은 매칭된 경계 그 자체 위에 놓습니다 (합쳐 넣은 조각은 빼고).
        val labelRings = matchedId?.let { boundaryById(it) }?.takeIf { it.isNotEmpty() } ?: rings

        val parentId = matchedId?.takeIf { '-' in it }?.substringBefore('-')
            ?: return BoundaryMatch(rings, emptyList(), labelRings)

        val parent = boundaryById(parentId)
        // 전역을 못 찾거나 담당 구역과 같으면 겹쳐 그릴 이유가 없습니다.
        return if (parent.isEmpty() || parent == rings) BoundaryMatch(rings, emptyList(), labelRings)
        else BoundaryMatch(rings, parent, labelRings)
    }

    /** boundariesFor가 어떤 경계 ID로 매칭했는지. 소속 ACC를 되짚는 데 씁니다. */
    private suspend fun matchedBoundaryId(callsign: String): String? {
        ensureLoaded()
        val upper = callsign.uppercase()
        val base = upper.substringBeforeLast('_')
        val root = base.substringBefore('_')
        listOf(
            byCallsignPrefix[base], byIcao[base],
            byCallsignPrefix[root], byIcao[root]
        ).forEach { record ->
            if (record != null && boundaryById(record.boundaryId).isNotEmpty()) {
                return record.boundaryId.uppercase()
            }
        }
        return null
    }

    suspend fun boundariesFor(callsign: String): List<List<LatLng>> {
        ensureLoaded()

        val upper = callsign.uppercase()
        // 뒤쪽 시설 접미사를 떼어냅니다. RKRR_N_CTR → RKRR_N
        val base = upper.substringBeforeLast('_')
        val root = base.substringBefore('_')

        // 좁은 것부터 넓은 것 순으로 조회합니다.
        // 섹터 단위(RKRR_N)가 있으면 그걸 쓰고, 없으면 상위 FIR로 내려갑니다.
        listOf(
            byCallsignPrefix[base],
            byIcao[base],
            byCallsignPrefix[root],   // 미주 ARTCC(HOU, ABQ, ZAB…)가 여기서 잡힙니다
            byIcao[root]
        ).forEach { record ->
            if (record != null) {
                val rings = withSectors(record.boundaryId)
                if (rings.isNotEmpty()) return rings
            }
        }

        // UIR (여러 FIR의 합집합)
        uirs[base]?.let { members ->
            val rings = unionOf(members)
            if (rings.isNotEmpty()) return rings
        }
        uirs[root]?.let { members ->
            val rings = unionOf(members)
            if (rings.isNotEmpty()) return rings
        }

        // 경계 ID가 콜사인과 그대로 같은 경우
        return boundaryById(root)
    }

    /**
     * 루트 경계에 그 아래 섹터를 더합니다.
     *
     * 뉴욕이 이게 필요한 이유: KZNY 자체는 **뉴욕 오세아닉**(대서양)이고, 정작
     * 뉴욕 상공은 KZNY-W 에 들어 있습니다. 루트만 그리면 NY_CTR 이 대서양에만
     * 표시되어 "관제소가 안 뜬다"로 보입니다.
     *
     * 반대로 마가단(UHMM)처럼 섹터가 루트를 잘게 나눈 것뿐인 경우에는 더하면 안 됩니다.
     * 겹쳐 칠해질 뿐 아니라, 링이 늘어나면서 라벨 위치 계산이 엉뚱한 곳으로 끌려갑니다
     * (UHMM 라벨이 야쿠티야까지 밀려났습니다).
     *
     * 판정은 **섹터의 대표점이 루트 안에 있는가**로 합니다. 경계 상자로 보면
     * 날짜변경선을 걸친 도형에서 상자가 지구 전체로 벌어져 판정이 무너집니다.
     */
    private suspend fun withSectors(boundaryId: String): List<List<LatLng>> {
        val rootId = boundaryId.uppercase()
        val rootRings = boundaryById(rootId)
        val sectorIds = sectorsByRoot[rootId] ?: return rootRings
        if (rootRings.isEmpty()) return sectorIds.flatMap { boundaryById(it) }

        val extra = sectorIds.flatMap { id ->
            val rings = boundaryById(id)
            val point = representativePoint(rings)
            // 루트 밖에 있는 조각만 더합니다.
            if (point == null || rootRings.any { contains(it, point) }) emptyList() else rings
        }
        return rootRings + extra
    }

    /** 조각을 대표하는 점 — 가장 점이 많은 링의 평균. */
    private fun representativePoint(rings: List<List<LatLng>>): LatLng? {
        val biggest = rings.maxByOrNull { it.size } ?: return null
        if (biggest.isEmpty()) return null
        return LatLng(
            biggest.sumOf { it.latitude } / biggest.size,
            biggest.sumOf { it.longitude } / biggest.size
        )
    }

    private fun contains(ring: List<LatLng>, point: LatLng): Boolean {
        var inside = false
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[(i + 1) % ring.size]
            if ((a.latitude > point.latitude) != (b.latitude > point.latitude)) {
                val crossing = a.longitude +
                    (point.latitude - a.latitude) * (b.longitude - a.longitude) /
                    (b.latitude - a.latitude)
                if (point.longitude < crossing) inside = !inside
            }
        }
        return inside
    }

    private suspend fun unionOf(firIcaos: List<String>): List<List<LatLng>> =
        firIcaos.flatMap { icao ->
            byIcao[icao]?.let { boundaryById(it.boundaryId) } ?: boundaryById(icao)
        }

    private suspend fun boundaryById(boundaryId: String): List<List<LatLng>> {
        val id = boundaryId.uppercase()
        parsedBoundaries[id]?.let { return it }

        val raw = rawBoundaries[id] ?: return emptyList()
        val parsed = withContext(Dispatchers.Default) { parse(raw) }

        mutex.withLock { parsedBoundaries[id] = parsed }
        return parsed
    }

    private fun parse(line: String): List<List<LatLng>> =
        line.split('|')
            .drop(1) // 첫 토큰은 경계 ID
            .mapNotNull { ring ->
                val points = ring.split(' ').mapNotNull { pair ->
                    val comma = pair.indexOf(',')
                    if (comma <= 0) return@mapNotNull null
                    val lat = pair.substring(0, comma).toDoubleOrNull() ?: return@mapNotNull null
                    val lon = pair.substring(comma + 1).toDoubleOrNull() ?: return@mapNotNull null
                    LatLng(lat, lon)
                }
                points.takeIf { it.size >= 3 }
            }

    /** 폴리곤 무게중심 — 마커/라벨 위치로 씁니다. */
    /**
     * 구역의 대표 지점 — 관제사 마커 위치와 검색 결과 카메라에 씁니다.
     *
     * 이름표와 **같은 계산**을 씁니다. 예전에는 여기만 단순 평균이라
     * 마가단(UHMM)을 고르면 카메라가 야쿠츠크로 날아갔습니다. 날짜변경선 건너편
     * 조각(-180~-169)이 평균을 서쪽으로 끌어당겼기 때문입니다.
     */
    fun centroid(rings: List<List<LatLng>>): LatLng? = BoundaryLabelPoint.of(rings)
}
