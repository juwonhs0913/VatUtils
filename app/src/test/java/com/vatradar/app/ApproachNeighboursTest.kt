package com.vatradar.app

import com.vatradar.app.domain.ApproachNeighbours
import com.vatradar.app.domain.model.Airport
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 같은 접근관제를 받는 이웃 공항 판정.
 *
 * 인천만 등록해 둔 사람이 김포 어프로치(=서울 어프로치) 접속을 놓치던 문제를 막습니다.
 */
class ApproachNeighboursTest {

    private fun airport(icao: String, lat: Double, lon: Double) = Airport(
        icao = icao, name = icao, iata = "", country = "", countryName = "",
        continent = "", latitude = lat, longitude = lon, elevationFt = 0,
        maxRunwayFt = 12000, hardSurface = true, international = true
    )

    private val airports = listOf(
        airport("RKSI", 37.4691, 126.4505),   // 인천
        airport("RKSS", 37.5583, 126.7906),   // 김포     — RKSI에서 17nm
        airport("RJTT", 35.5533, 139.7811),   // 하네다
        airport("RJAA", 35.7647, 140.3864),   // 나리타   — RJTT에서 32nm
        airport("EDDF", 50.0333, 8.5706),     // 프랑크푸르트
        airport("EDDK", 50.8659, 7.1427),     // 쾰른     — EDDF에서 74nm
        airport("RKPC", 33.5113, 126.4930)    // 제주     — RKSI에서 238nm
    )

    @Test
    fun `가까운 공항의 어프로치를 함께 지켜본다`() {
        val extra = ApproachNeighbours.extraCallsigns("RKSI", airports)
        assertTrue("김포 어프로치가 빠졌습니다: $extra", "RKSS_APP" in extra)
        assertTrue("김포 디파처가 빠졌습니다: $extra", "RKSS_DEP" in extra)
    }

    @Test
    fun `도쿄도 같은 규칙으로 묶인다`() {
        assertTrue("RJAA_APP" in ApproachNeighbours.extraCallsigns("RJTT", airports))
        assertTrue("RJTT_APP" in ApproachNeighbours.extraCallsigns("RJAA", airports))
    }

    /** 74해리는 다른 TMA입니다. 여기까지 알리면 등록하지 않은 공항의 알림이 됩니다. */
    @Test
    fun `멀리 있는 공항은 묶지 않는다`() {
        val extra = ApproachNeighbours.extraCallsigns("EDDF", airports)
        assertFalse("쾰른이 섞였습니다: $extra", extra.any { it.startsWith("EDDK") })

        val fromIncheon = ApproachNeighbours.extraCallsigns("RKSI", airports)
        assertFalse("제주가 섞였습니다: $fromIncheon", fromIncheon.any { it.startsWith("RKPC") })
    }

    /** 타워·그라운드·딜리버리는 공항마다 따로라 넓히면 안 됩니다. */
    @Test
    fun `어프로치와 디파처만 넓힌다`() {
        val extra = ApproachNeighbours.extraCallsigns("RKSI", airports)
        assertTrue(extra.isNotEmpty())
        assertTrue(
            "어프로치 외 시설이 섞였습니다: $extra",
            extra.all { it.endsWith("_APP") || it.endsWith("_DEP") }
        )
    }

    @Test
    fun `관제석까지 붙여 등록해도 같은 공항으로 본다`() {
        assertTrue("RKSS_APP" in ApproachNeighbours.extraCallsigns("RKSI_TWR", airports))
    }

    /** FIR 코드는 공항이 아니므로 넓힐 대상이 없습니다. */
    @Test
    fun `공항이 아닌 등록값은 넓히지 않는다`() {
        assertTrue(ApproachNeighbours.extraCallsigns("RKRR_CTR", airports).isEmpty())
        assertTrue(ApproachNeighbours.extraCallsigns("LON", airports).isEmpty())
    }

    @Test
    fun `자기 자신은 포함하지 않는다`() {
        val extra = ApproachNeighbours.extraCallsigns("RKSI", airports)
        assertFalse(extra.any { it.startsWith("RKSI") })
    }
}
