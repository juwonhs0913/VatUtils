package com.vatradar.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vatradar.app.data.local.AirportSeeder
import com.vatradar.app.data.local.AppDatabase
import com.vatradar.app.data.local.FirBoundaryStore
import com.vatradar.app.data.repository.AirportRepository
import com.vatradar.app.domain.model.HaulRange
import com.vatradar.app.domain.model.distanceNmTo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * assets 실물이 필요한 검증 (VATSpy FIR 경계 + 공항 DB 시딩).
 * 실행: ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class FirBoundaryStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = FirBoundaryStore(context)

    @Test
    fun 인천ACC_콜사인이_FIR_폴리곤으로_해석된다() = runBlocking {
        val rings = store.boundariesFor("RKRR_CTR")
        assertTrue("인천 FIR 경계를 찾지 못했습니다", rings.isNotEmpty())

        val points = rings.flatten()
        assertTrue("좌표가 너무 적습니다: ${points.size}", points.size >= 10)

        // 인천 FIR은 대략 북위 31~38, 동경 124~132 범위에 있습니다.
        val lat = points.map { it.latitude }
        val lon = points.map { it.longitude }
        assertTrue("위도 범위 이상: ${lat.min()}~${lat.max()}", lat.min() > 28 && lat.max() < 40)
        assertTrue("경도 범위 이상: ${lon.min()}~${lon.max()}", lon.min() > 120 && lon.max() < 136)
    }

    @Test
    fun 세부구역_콜사인이_해당_섹터로_해석된다() = runBlocking {
        // RKRR_N_CTR → FIR RKRR-N (콜사인 접두사 RKRR_N)
        val north = store.boundariesFor("RKRR_N_CTR")
        assertTrue("RKRR_N 섹터 경계를 찾지 못했습니다", north.isNotEmpty())
    }

    @Test
    fun 알_수_없는_섹터는_상위_FIR로_폴백된다() = runBlocking {
        // 존재하지 않는 섹터 접미사 → 첫 토큰(RKRR)으로 폴백되어야 합니다.
        val fallback = store.boundariesFor("RKRR_ZZZ_CTR")
        assertTrue("상위 FIR 폴백이 동작하지 않습니다", fallback.isNotEmpty())
    }

    @Test
    fun 주요_FIR들이_모두_해석된다() = runBlocking {
        listOf("RJJJ_CTR", "EGTT_CTR", "KZLA_CTR", "LFFF_CTR", "EDGG_CTR").forEach { callsign ->
            assertTrue("$callsign 경계 없음", store.boundariesFor(callsign).isNotEmpty())
        }
    }

    @Test
    fun 존재하지_않는_콜사인은_빈_결과를_돌려준다() = runBlocking {
        assertTrue(store.boundariesFor("ZZZZ_CTR").isEmpty())
    }

    /**
     * 미주 ARTCC는 콜사인이 ICAO가 아니라 3글자 약어입니다.
     * VATSpy 레코드가 `KZHU|HOU|KZHU|Houston` 형태라, ICAO만 조회하면 전부 누락됩니다.
     */
    @Test
    fun 미주_ARTCC_콜사인이_FIR로_해석된다() = runBlocking {
        val houston = store.boundariesFor("HOU_46_CTR")
        assertTrue("HOU_46_CTR(휴스턴 센터) 경계를 찾지 못했습니다", houston.isNotEmpty())

        // 휴스턴 FIR은 대략 북위 25~33, 서경 88~100 범위입니다.
        val points = houston.flatten()
        val lat = points.map { it.latitude }
        val lon = points.map { it.longitude }
        assertTrue("위도 범위 이상: ${lat.min()}~${lat.max()}", lat.min() > 20 && lat.max() < 38)
        assertTrue("경도 범위 이상: ${lon.min()}~${lon.max()}", lon.min() > -105 && lon.max() < -82)

        assertTrue("ABQ_46_CTR 경계를 찾지 못했습니다", store.boundariesFor("ABQ_46_CTR").isNotEmpty())
    }

    @Test
    fun 섹터_번호가_붙어도_상위_FIR로_해석된다() = runBlocking {
        // 섹터 번호만 다른 콜사인은 같은 FIR로 귀결되어야 합니다.
        val a = store.boundariesFor("HOU_CTR")
        val b = store.boundariesFor("HOU_46_CTR")
        assertTrue(a.isNotEmpty() && b.isNotEmpty())
        assertEquals(a.flatten().size, b.flatten().size)
    }

    /**
     * VATSpy의 미주 ARTCC 접두사는 ZLA/ZAB 같은 센터 코드가 아니라
     * LAX·ABQ·CHI 같은 도시 약어입니다. 실제 VATSIM 콜사인도 이 형태를 씁니다.
     */
    @Test
    fun 주요_미주_ARTCC가_모두_해석된다() = runBlocking {
        listOf(
            "LAX_CTR",      // KZLA Los Angeles
            "CHI_CTR",      // KZAU Chicago
            "BOS_CTR",      // KZBW Boston
            "DEN_35_CTR",   // KZDV Denver (섹터 번호 포함)
            "FTW_CTR",      // KZFW Fort Worth
            "JAX_C_CTR"     // KZJX-C Jacksonville (Central) — 세부 섹터
        ).forEach {
            assertTrue("$it 경계 없음", store.boundariesFor(it).isNotEmpty())
        }
    }

    @Test
    fun 세부_섹터가_상위_FIR과_다른_경계를_가진다() = runBlocking {
        // JAX_C(Central)는 JAX 전체와 다른 폴리곤이어야 합니다.
        val whole = store.boundariesFor("JAX_CTR").flatten().size
        val central = store.boundariesFor("JAX_C_CTR").flatten().size
        assertTrue("세부 섹터가 상위 FIR로 폴백됐습니다", whole != central)
    }

    @Test
    fun 무게중심이_폴리곤_범위_안에_있다() = runBlocking {
        val rings = store.boundariesFor("RKRR_CTR")
        val center = store.centroid(rings)
        assertNotNull(center)
        assertTrue("무게중심 위도 이상: ${center!!.latitude}", center.latitude in 30.0..39.0)
        assertTrue("무게중심 경도 이상: ${center.longitude}", center.longitude in 122.0..134.0)
    }

    @Test
    fun 공항_DB가_정상_시딩된다() = runBlocking {
        val database = AppDatabase.get(context)
        val dao = database.airportDao()
        AirportSeeder.seedIfNeeded(context, database)

        assertTrue("공항 수가 부족합니다: ${dao.count()}", dao.count() > 12000)

        val incheon = dao.findByIcao("RKSI")
        assertNotNull("RKSI를 찾지 못했습니다", incheon)
        assertEquals("Incheon International Airport", incheon!!.name)
        assertEquals(13123, incheon.maxRunwayFt)
        assertTrue(incheon.hardSurface)
        assertTrue("RKSI가 국제공항으로 분류되지 않았습니다", incheon.international)
    }

    /**
     * 미주 VATSIM 하위 관제는 ICAO가 아니라 IATA를 콜사인에 씁니다
     * (KBOS가 아니라 BOS_TWR, KLGA가 아니라 LGA_GND).
     * ICAO로만 조회하면 미주 TWR/GND/DEL/APP이 전부 지도에서 사라집니다.
     */
    @Test
    fun 미주_하위관제_콜사인이_IATA로_해석된다() = runBlocking {
        val database = AppDatabase.get(context)
        val dao = database.airportDao()
        AirportSeeder.seedIfNeeded(context, database)

        val codes = listOf("BOS", "LGA", "MIA", "OAK", "PDX", "MDW")
        val found = dao.findAllByIata(codes).associateBy { it.iata }

        codes.forEach { assertNotNull("$it 를 IATA로 찾지 못했습니다", found[it]) }

        // ICAO로는 찾히지 않아야 정상입니다 (그래서 폴백이 필요합니다)
        assertTrue("BOS가 ICAO로 조회됩니다", dao.findAllByIcao(listOf("BOS")).isEmpty())

        assertEquals("KBOS", found["BOS"]!!.icao)
        assertEquals("KLGA", found["LGA"]!!.icao)
    }

    @Test
    fun IATA_조회가_빈_코드를_걸러낸다() = runBlocking {
        val database = AppDatabase.get(context)
        AirportSeeder.seedIfNeeded(context, database)
        // IATA가 없는 공항이 빈 문자열로 매칭되면 안 됩니다.
        assertTrue(database.airportDao().findAllByIata(listOf("")).isEmpty())
    }

    @Test
    fun 국제공항_분류가_군용_비행장을_제외한다() = runBlocking {
        val database = AppDatabase.get(context)
        val dao = database.airportDao()
        AirportSeeder.seedIfNeeded(context, database)

        // 활주로는 9,301ft로 충분히 길지만 정기편이 없는 해군 보조 활주로
        val naval = dao.findByIcao("KNUC")
        assertNotNull(naval)
        assertTrue("군용 비행장이 국제공항으로 잡혔습니다", !naval!!.international)

        val count = dao.internationalCount()
        assertTrue("국제공항 수가 비정상: $count", count in 1500..4000)
    }

    @Test
    fun 거리_구간별_추천이_구간을_지킨다() = runBlocking {
        val database = AppDatabase.get(context)
        AirportSeeder.seedIfNeeded(context, database)
        val repo = AirportRepository(database.airportDao())

        HaulRange.entries.forEach { haul ->
            repeat(10) {
                val route = repo.randomRoute(haul)
                assertNotNull("$haul 구간 추천 실패", route)
                route!!

                assertTrue("출발지와 도착지가 같습니다", route.origin.icao != route.destination.icao)
                assertTrue("국제공항이 아닙니다: ${route.origin.icao}", route.origin.international)
                assertTrue("국제공항이 아닙니다: ${route.destination.icao}", route.destination.international)

                val d = route.origin.distanceNmTo(route.destination)
                assertTrue(
                    "$haul 구간 위반: ${route.origin.icao}→${route.destination.icao} ${d.toInt()}nm",
                    haul.contains(d)
                )
            }
        }
    }
}
