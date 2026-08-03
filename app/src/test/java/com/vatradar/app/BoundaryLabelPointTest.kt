package com.vatradar.app

import com.google.android.gms.maps.model.LatLng
import com.vatradar.app.domain.BoundaryLabelPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 관제 구역 이름표 위치.
 *
 * 여기 좌표는 VATSpy Boundaries.geojson 원본에서 그대로 가져온 것입니다.
 * 실제로 화면에서 어긋났던 사례를 그대로 재현합니다.
 */
class BoundaryLabelPointTest {

    private fun ring(vararg pairs: Pair<Double, Double>): List<LatLng> =
        pairs.map { LatLng(it.first, it.second) }

    /**
     * 마가단(UHMM). 두 가지가 겹쳐 라벨이 야쿠티야까지 밀려났던 구역입니다.
     *  - 위도 90(북극)에 폴리곤을 닫는 정점이 있음
     *  - 경도 180에 걸쳐 있어 LatLng가 -180으로 되감음
     */
    private val magadan = ring(
        90.0 to 180.0, 60.0 to 180.0, 54.817 to 170.2, 54.0 to 169.0,
        50.083 to 159.0, 45.0 to 150.0, 54.867 to 150.0, 56.0 to 146.0,
        56.417 to 143.417, 58.0 to 145.75, 59.0 to 146.0, 60.0 to 145.0,
        62.0 to 145.5, 64.0 to 149.7, 66.0 to 158.0, 68.0 to 158.0,
        70.0 to 162.0, 71.0 to 165.0, 72.0 to 168.0, 74.0 to 168.0,
        75.0 to 158.0, 76.0 to 143.6, 76.333 to 132.0, 78.0 to 114.75,
        90.0 to 169.0, 90.0 to 180.0
    )

    private val magadanOceanic = ring(
        90.0 to -168.973, 65.0 to -168.973, 62.583 to -175.0,
        61.367 to -177.752, 60.0 to -180.0, 90.0 to -180.0
    )

    @Test
    fun `마가단 라벨은 실제 공역 위에 놓인다`() {
        val point = BoundaryLabelPoint.of(listOf(magadan, magadanOceanic))
        assertNotNull(point)
        // 마가단 시는 59.6N/150.8E. 라벨은 그 부근 본체 위여야 합니다.
        assertEquals(63.5, point!!.latitude, 1.5)
        assertEquals(151.7, point.longitude, 3.0)
    }

    /**
     * 경도 180 정점이 있으면 LatLng가 -180으로 되감습니다. 그 한 점 때문에
     * 평균이 통째로 15도 틀어졌었습니다 (151.7E → 136.7E).
     */
    @Test
    fun `날짜변경선에 걸린 정점이 평균을 끌어내리지 않는다`() {
        val point = BoundaryLabelPoint.of(listOf(magadan))
        assertNotNull(point)
        assertTrue(
            "경도가 서쪽으로 밀렸습니다: ${point!!.longitude}",
            point.longitude > 145.0
        )
    }

    /** 극점 정점이 라벨을 북극 쪽으로 끌고 가면 안 됩니다. */
    @Test
    fun `극점을 닫는 정점은 무시한다`() {
        val point = BoundaryLabelPoint.of(listOf(magadan))
        assertNotNull(point)
        assertTrue("라벨이 북극으로 밀렸습니다: ${point!!.latitude}", point.latitude < 70.0)
    }

    /**
     * 태평양을 감싸 -180과 180에 모두 점이 있는 구역.
     * 단순 평균을 쓰면 대서양(-44도)으로 나옵니다.
     */
    @Test
    fun `태평양을 감싸는 구역은 태평양에 라벨이 놓인다`() {
        val pacific = ring(
            40.0 to 170.0, 40.0 to 180.0, 40.0 to -170.0, 40.0 to -160.0,
            10.0 to -160.0, 10.0 to -170.0, 10.0 to 180.0, 10.0 to 170.0
        )
        val point = BoundaryLabelPoint.of(listOf(pacific))
        assertNotNull(point)
        // 175E ~ 175W 어딘가여야 합니다. 대서양(0도 부근)이면 실패입니다.
        assertTrue(
            "라벨이 태평양 밖입니다: ${point!!.longitude}",
            point.longitude > 160.0 || point.longitude < -160.0
        )
    }

    /** 날짜변경선·극점과 무관한 평범한 구역은 그대로 무게중심에 놓입니다. */
    @Test
    fun `평범한 구역은 그대로 중심에 놓인다`() {
        val incheon = ring(
            39.0 to 124.0, 39.0 to 131.0, 33.0 to 131.0, 33.0 to 124.0
        )
        val point = BoundaryLabelPoint.of(listOf(incheon))
        assertNotNull(point)
        assertEquals(36.0, point!!.latitude, 0.5)
        assertEquals(127.5, point.longitude, 0.5)
    }

    @Test
    fun `점이 모자란 구역은 위치를 내지 않는다`() {
        assertEquals(null, BoundaryLabelPoint.of(emptyList()))
        assertEquals(null, BoundaryLabelPoint.of(listOf(ring(10.0 to 10.0, 20.0 to 20.0))))
    }

    @Test
    fun `경도 폭은 날짜변경선을 넘어도 실제 폭으로 잰다`() {
        val pacific = ring(
            40.0 to 175.0, 40.0 to -175.0, 10.0 to -175.0, 10.0 to 175.0
        )
        // 175E에서 175W는 10도이지, 350도가 아닙니다.
        assertEquals(10.0, BoundaryLabelPoint.longitudeSpan(listOf(pacific)), 0.5)
    }
}
