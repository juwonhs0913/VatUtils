package com.vatradar.app

import com.vatradar.app.util.formatZuluHhmm
import com.vatradar.app.util.hhmmToMinutes
import com.vatradar.app.util.plannedArrivalZulu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 비행계획 시각 처리.
 * VATSIM의 deptime/enroute_time은 자유 입력에 가까워 값이 지저분하게 들어옵니다.
 */
class FlightTimeTest {

    @Test
    fun `HHMM을 Zulu 표기로 바꾼다`() {
        assertEquals("14:30Z", formatZuluHhmm("1430"))
        assertEquals("00:05Z", formatZuluHhmm("0005"))
        assertEquals("23:59Z", formatZuluHhmm("2359"))
    }

    @Test
    fun `숫자가 아닌 문자가 섞여도 처리한다`() {
        assertEquals("14:30Z", formatZuluHhmm("14:30"))
        assertEquals("08:00Z", formatZuluHhmm("0800Z"))
    }

    @Test
    fun `형식이 어긋나면 null을 돌려준다`() {
        assertNull(formatZuluHhmm(null))
        assertNull(formatZuluHhmm(""))
        assertNull(formatZuluHhmm("14"))       // 자릿수 부족
        assertNull(formatZuluHhmm("143045"))   // 자릿수 초과
        assertNull(formatZuluHhmm("2530"))     // 25시
        assertNull(formatZuluHhmm("1470"))     // 70분
    }

    @Test
    fun `소요시간을 분으로 바꾼다`() {
        assertEquals(330, hhmmToMinutes("0530"))
        assertEquals(0, hhmmToMinutes("0000"))
        // 12시간을 넘는 장거리도 그대로 처리해야 합니다
        assertEquals(14 * 60 + 20, hhmmToMinutes("1420"))
    }

    @Test
    fun `출발시각과 소요시간으로 도착 예정을 계산한다`() {
        assertEquals("20:00Z", plannedArrivalZulu("1430", "0530"))
        assertEquals("09:15Z", plannedArrivalZulu("0800", "0115"))
    }

    @Test
    fun `자정을 넘는 도착도 정상 계산된다`() {
        // 22:00Z 출발 + 5시간 = 다음날 03:00Z
        assertEquals("03:00Z", plannedArrivalZulu("2200", "0500"))
    }

    @Test
    fun `값이 하나라도 없으면 계산하지 않는다`() {
        assertNull(plannedArrivalZulu(null, "0530"))
        assertNull(plannedArrivalZulu("1430", null))
        assertNull(plannedArrivalZulu("", ""))
    }
}
