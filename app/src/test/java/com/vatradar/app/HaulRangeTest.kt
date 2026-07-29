package com.vatradar.app

import com.vatradar.app.domain.model.Airport
import com.vatradar.app.domain.model.HaulRange
import com.vatradar.app.domain.model.greatCircleNm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class HaulRangeTest {

    private fun airport(icao: String, runwayFt: Int, intl: Boolean = true) = Airport(
        icao = icao, name = icao, iata = "", country = "XX", countryName = "X",
        continent = "AS", latitude = 0.0, longitude = 0.0, elevationFt = 0,
        maxRunwayFt = runwayFt, hardSurface = true, international = intl
    )

    // --- 대권거리 ---

    @Test
    fun `인천에서 로스앤젤레스까지 거리가 실제와 맞는다`() {
        // RKSI(37.4692, 126.4505) → KLAX(33.9425, -118.4081), 실제 약 5,100해리
        val d = greatCircleNm(37.4692, 126.4505, 33.9425, -118.4081)
        assertTrue("실제: ${d.toInt()}nm", abs(d - 5100) < 120)
    }

    @Test
    fun `인천에서 김포까지는 매우 가깝다`() {
        val d = greatCircleNm(37.4692, 126.4505, 37.5583, 126.7906)
        assertTrue("실제: ${d.toInt()}nm", d < 25)
    }

    @Test
    fun `같은 지점의 거리는 0이다`() {
        assertEquals(0.0, greatCircleNm(37.0, 127.0, 37.0, 127.0), 0.001)
    }

    @Test
    fun `날짜변경선을 넘는 거리도 정상 계산된다`() {
        // 도쿄(35.55, 139.78) → 호놀룰루(21.32, -157.92), 실제 약 3,340해리
        val d = greatCircleNm(35.55, 139.78, 21.32, -157.92)
        assertTrue("실제: ${d.toInt()}nm", abs(d - 3340) < 120)
    }

    // --- 구간 판정 ---

    @Test
    fun `구간 경계가 겹치지 않는다`() {
        assertTrue(HaulRange.SHORT.contains(1499.0))
        assertFalse(HaulRange.SHORT.contains(1500.0))
        assertTrue(HaulRange.MEDIUM.contains(1500.0))
        assertTrue(HaulRange.MEDIUM.contains(3499.0))
        assertFalse(HaulRange.MEDIUM.contains(3500.0))
        assertTrue(HaulRange.LONG.contains(3500.0))
    }

    @Test
    fun `모든 거리는 정확히 한 구간에만 속한다`() {
        listOf(0.0, 500.0, 1499.9, 1500.0, 2000.0, 3499.9, 3500.0, 6000.0, 9000.0).forEach { d ->
            assertEquals(
                "거리 $d 의 구간 매칭이 잘못됐습니다",
                1,
                HaulRange.entries.count { it.contains(d) }
            )
        }
    }

    // --- 활주로 제약 (에뮬레이터에서 발견한 회귀) ---

    /**
     * LEAS → KBKW(6,750ft)로 3,319해리 대서양 횡단이 추천된 적이 있습니다.
     * 그 거리를 나는 광동체는 그 활주로에 착륙할 수 없습니다.
     */
    @Test
    fun `장거리 구간은 단거리용 활주로 공항을 배제한다`() {
        val regional = airport("KBKW", 6750)
        assertFalse("6,750ft 공항이 장거리 후보로 잡혔습니다", HaulRange.LONG.admits(regional))
        assertFalse("6,750ft 공항이 중거리 후보로 잡혔습니다", HaulRange.MEDIUM.admits(regional))
        assertTrue("6,750ft 공항은 단거리에는 적합합니다", HaulRange.SHORT.admits(regional))
    }

    @Test
    fun `대형 허브는 모든 구간에서 허용된다`() {
        val hub = airport("RKSI", 13123)
        HaulRange.entries.forEach {
            assertTrue("$it 구간에서 인천이 배제됐습니다", it.admits(hub))
        }
    }

    @Test
    fun `국제공항이 아니면 어떤 구간에서도 배제된다`() {
        // 활주로는 충분히 길지만 정기편이 없는 군용 비행장
        val naval = airport("KNUC", 9301, intl = false)
        HaulRange.entries.forEach {
            assertFalse("$it 구간에서 군용 비행장이 허용됐습니다", it.admits(naval))
        }
    }

    @Test
    fun `구간이 길수록 요구 활주로가 길어진다`() {
        assertTrue(HaulRange.SHORT.minRunwayFt < HaulRange.MEDIUM.minRunwayFt)
        assertTrue(HaulRange.MEDIUM.minRunwayFt < HaulRange.LONG.minRunwayFt)
    }
}
