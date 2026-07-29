package com.vatradar.app

import com.vatradar.app.domain.metar.FlightCategory
import com.vatradar.app.domain.metar.MetarDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetarDecoderTest {

    private fun valueOf(raw: String, label: String): String? =
        MetarDecoder.decode(raw).fields.firstOrNull { it.label == label }?.value

    /** 실제 인천공항 관측값 (PRD F6 예시와 동일 형식) */
    private val rksi = "RKSI 291030Z 24010KT 9999 FEW008 27/25 Q1010 NOSIG"

    @Test
    fun `관측소 코드를 인식한다`() {
        assertEquals("RKSI", MetarDecoder.decode(rksi).station)
    }

    @Test
    fun `바람을 방위와 함께 디코딩한다`() {
        assertEquals("240도(서남서) 10노트", valueOf(rksi, "바람"))
    }

    @Test
    fun `돌풍을 인식한다`() {
        assertEquals(
            "250도(서남서) 15노트, 순간최대 25노트",
            valueOf("RKSI 291030Z 25015G25KT", "바람")
        )
    }

    @Test
    fun `무풍을 인식한다`() {
        assertEquals("무풍", valueOf("RKSI 291030Z 00000KT", "바람"))
    }

    @Test
    fun `가변 풍향을 인식한다`() {
        assertEquals("가변 3노트", valueOf("RKSI 291030Z VRB03KT", "바람"))
    }

    @Test
    fun `9999는 10km 이상으로 표기한다`() {
        assertEquals("10km 이상", valueOf(rksi, "시정"))
    }

    @Test
    fun `미터 단위 저시정을 표기한다`() {
        assertEquals("800m", valueOf("RKSI 291030Z 24010KT 0800", "시정"))
    }

    @Test
    fun `마일 단위 시정을 변환한다`() {
        val v = valueOf("KLAX 291030Z 24010KT 10SM", "시정")
        assertTrue("실제: $v", v!!.startsWith("10마일"))
    }

    @Test
    fun `기온과 이슬점을 디코딩하고 안개 가능성을 알린다`() {
        val v = valueOf(rksi, "기온 / 이슬점")
        assertEquals("27°C / 25°C — 기온과 이슬점이 가까워 안개 가능", v)
    }

    @Test
    fun `영하 기온을 처리한다`() {
        assertEquals(
            "-3°C / -8°C",
            valueOf("RKSI 291030Z 24010KT 9999 M03/M08 Q1010", "기온 / 이슬점")
        )
    }

    @Test
    fun `hPa 기압을 inHg와 함께 표기한다`() {
        assertEquals("1010 hPa (29.83 inHg)", valueOf(rksi, "기압 (QNH)"))
    }

    @Test
    fun `inHg 기압을 hPa와 함께 표기한다`() {
        assertEquals("29.92 inHg (1013 hPa)", valueOf("KLAX 291030Z A2992", "기압 (QNH)"))
    }

    @Test
    fun `구름을 층별로 디코딩한다`() {
        val v = valueOf("RKSI 291030Z 24010KT 9999 FEW008 BKN020 OVC050 27/25 Q1010", "구름")
        assertEquals("few 1~2/8 800ft, broken 5~7/8 2000ft, overcast 8/8 5000ft", v)
    }

    @Test
    fun `기상 현상을 한국어로 디코딩한다`() {
        assertEquals("약한 비", valueOf("RKSI 291030Z 24010KT 9999 -RA 20/18 Q1010", "기상 현상"))
        assertEquals("강한 뇌전 동반 비", valueOf("RKSI 291030Z 24010KT 9999 +TSRA 20/18 Q1010", "기상 현상"))
        assertEquals("안개", valueOf("RKSI 291030Z 24010KT 0300 FG 20/20 Q1010", "기상 현상"))
    }

    @Test
    fun `CAVOK을 인식한다`() {
        val d = MetarDecoder.decode("RKSI 291030Z 24010KT CAVOK 27/15 Q1010")
        assertTrue(d.fields.any { it.label == "시정" && it.value.contains("CAVOK") })
        assertEquals(FlightCategory.VFR, d.flightCategory)
    }

    @Test
    fun `AUTO 관측을 표시한다`() {
        assertEquals("자동 관측 (AUTO)", valueOf("RKSI 291030Z AUTO 24010KT 9999 27/25 Q1010", "관측 방식"))
    }

    @Test
    fun `관측 시각을 디코딩한다`() {
        assertEquals("29일 10:30 UTC", valueOf(rksi, "관측 시각"))
    }

    @Test
    fun `끝의 등호를 제거한다`() {
        assertTrue(MetarDecoder.decode("RKSI 291030Z 24010KT 9999 27/25 Q1010=").raw.endsWith("Q1010"))
    }

    // --- 비행 규칙 구분 ---

    @Test
    fun `시정과 운고가 좋으면 VFR이다`() {
        assertEquals(FlightCategory.VFR, MetarDecoder.decode(rksi).flightCategory)
    }

    @Test
    fun `낮은 운고는 IFR로 분류한다`() {
        // 시정은 좋지만 운고 600ft → IFR
        assertEquals(
            FlightCategory.IFR,
            MetarDecoder.decode("RKSI 291030Z 24010KT 9999 BKN006 20/18 Q1010").flightCategory
        )
    }

    @Test
    fun `저시정과 초저운고는 LIFR로 분류한다`() {
        assertEquals(
            FlightCategory.LIFR,
            MetarDecoder.decode("RKSI 291030Z 24010KT 0300 FG OVC002 20/20 Q1010").flightCategory
        )
    }

    @Test
    fun `FEW 구름만으로는 운고가 잡히지 않는다`() {
        // FEW는 ceiling이 아니므로 시정 기준으로만 판단 → VFR
        assertEquals(
            FlightCategory.VFR,
            MetarDecoder.decode("RKSI 291030Z 24010KT 9999 FEW002 27/15 Q1010").flightCategory
        )
    }

    @Test
    fun `해석하지 못한 토큰은 버리지 않고 기타로 남긴다`() {
        val v = valueOf("RKSI 291030Z 24010KT 9999 27/25 Q1010 WXYZ123", "기타")
        assertTrue("실제: $v", v!!.contains("WXYZ123"))
    }

    // --- 추이(trend) 분리: 에뮬레이터 실행 중 발견한 회귀 ---

    /**
     * 실제 TLPL 관측값. 끝의 "TEMPO SHRA"는 향후 2시간 예보이지 현재 관측이 아닙니다.
     * 예전에는 본문과 섞여 "기상 현상"이 두 번 표시됐습니다.
     */
    private val tlpl = "TLPL 1300Z 09017KT 9999 -SHRA SCT016 BKN040 28/26 Q1017 TEMPO SHRA"

    @Test
    fun `TEMPO 추이는 현재 관측과 분리된다`() {
        val fields = MetarDecoder.decode(tlpl).fields
        val weather = fields.filter { it.label == "기상 현상" }
        assertEquals("기상 현상이 중복 표시됩니다: ${weather.map { it.value }}", 1, weather.size)
        assertEquals("약한 소나기성 비", weather.first().value)
    }

    @Test
    fun `TEMPO 내용이 추이 항목으로 표시된다`() {
        val v = valueOf(tlpl, "추이 (향후 2시간)")
        assertEquals("일시적으로 소나기성 비", v)
    }

    @Test
    fun `NOSIG도 추이로 표시된다`() {
        assertEquals(
            "유의미한 변화 없음",
            valueOf("RKSI 291330Z 26009KT 9999 FEW007 26/26 Q1011 NOSIG", "추이 (향후 2시간)")
        )
    }

    @Test
    fun `BECMG 추이를 해석한다`() {
        val v = valueOf("RKSI 291330Z 26009KT 9999 Q1011 BECMG 28015KT", "추이 (향후 2시간)")
        assertEquals("점차 280도(서) 15노트", v)
    }

    @Test
    fun `일자를 생략한 관측 시각을 인식한다`() {
        assertEquals("13:00 UTC", valueOf(tlpl, "관측 시각"))
    }

    @Test
    fun `추이가 있어도 기타 항목이 생기지 않는다`() {
        val other = MetarDecoder.decode(tlpl).fields.filter { it.label == "기타" }
        assertTrue("해석되지 않은 토큰이 남았습니다: ${other.map { it.value }}", other.isEmpty())
    }

    @Test
    fun `추이는 비행규칙 판정에 영향을 주지 않는다`() {
        // TEMPO 구간의 악화 예보 때문에 현재 상태가 IFR로 잘못 잡히면 안 됩니다.
        val d = MetarDecoder.decode("RKSI 291330Z 26009KT 9999 SCT030 26/20 Q1011 TEMPO 0500 FG OVC002")
        assertEquals(FlightCategory.VFR, d.flightCategory)
    }

    @Test
    fun `빈 문자열에도 예외 없이 동작한다`() {
        val d = MetarDecoder.decode("")
        assertEquals(FlightCategory.UNKNOWN, d.flightCategory)
        assertTrue(d.fields.isEmpty())
    }
}
