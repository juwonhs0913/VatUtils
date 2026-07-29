package com.vatradar.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vatradar.app.data.local.AirportSeeder
import com.vatradar.app.data.local.AppDatabase
import com.vatradar.app.data.local.FirBoundaryStore
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

        // F3 핵심 제약: 활주로 길이 필터가 실제로 걸러내는지
        val b777Capable = dao.countMatching(8000, null, null, true)
        val small = dao.countMatching(2000, null, null, false)
        assertTrue("활주로 필터가 동작하지 않습니다", b777Capable < small)
        assertTrue("B777급 공항 수가 비정상: $b777Capable", b777Capable in 1000..5000)
    }

    @Test
    fun 무작위_추첨이_필터를_지킨다() = runBlocking {
        val database = AppDatabase.get(context)
        val dao = database.airportDao()
        AirportSeeder.seedIfNeeded(context, database)

        repeat(20) {
            val picked = dao.randomAirports(9000, "AS", null, true, 2)
            assertEquals(2, picked.size)
            picked.forEach {
                assertTrue("활주로 길이 위반: ${it.icao} ${it.maxRunwayFt}ft", it.maxRunwayFt >= 9000)
                assertEquals("대륙 필터 위반: ${it.icao}", "AS", it.continent)
                assertTrue("포장 필터 위반: ${it.icao}", it.hardSurface)
            }
            assertTrue("출발지와 도착지가 같습니다", picked[0].icao != picked[1].icao)
        }
    }
}
