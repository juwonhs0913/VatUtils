package com.vatradar.app

import com.google.android.gms.maps.model.LatLng
import com.vatradar.app.domain.BoundaryLabelPoint
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * 앱에 들어 있는 **모든** 관제 구역에 대해 이름표 위치를 검사합니다.
 *
 * 마가단(UHMM)이 어긋난 건 관제사가 접속해 있을 때만 화면에서 볼 수 있었습니다.
 * 그런 식으로는 1,000곳이 넘는 구역을 다 확인할 수 없어, 실제 자산 파일을 읽어
 * 전수로 돌립니다. 앱과 같은 BoundaryLabelPoint를 그대로 씁니다.
 */
class AllBoundaryLabelsTest {

    /**
     * 계산용 좌표.
     * LatLng는 생성자가 경도를 [-180,180)로 되감아 펴 놓은 값을 담을 수 없습니다.
     */
    private data class P(val lat: Double, val lon: Double)

    private data class Boundary(val id: String, val rings: List<List<LatLng>>)

    private fun loadAll(): List<Boundary> {
        // 테스트 작업 디렉터리는 모듈 루트(app/)입니다.
        val file = File("src/main/assets/fir_boundaries.txt")
        assertTrue("경계 파일을 찾지 못했습니다: ${file.absolutePath}", file.exists())

        return file.readLines().mapNotNull { line ->
            val parts = line.split('|')
            if (parts.size < 2) return@mapNotNull null
            val rings = parts.drop(1).mapNotNull { ring ->
                val points = ring.split(' ').mapNotNull { pair ->
                    val comma = pair.indexOf(',')
                    if (comma <= 0) return@mapNotNull null
                    val lat = pair.substring(0, comma).toDoubleOrNull() ?: return@mapNotNull null
                    val lon = pair.substring(comma + 1).toDoubleOrNull() ?: return@mapNotNull null
                    LatLng(lat, lon)
                }
                points.takeIf { it.size >= 3 }
            }
            rings.takeIf { it.isNotEmpty() }?.let { Boundary(parts[0].uppercase(), it) }
        }
    }

    /**
     * 점이 이 구역 안에 있는지.
     *
     * 검사 대상과 **다른 방식**으로 경도를 폅니다 — 생산 코드는 구역 전체의 원형
     * 평균을 기준으로 삼지만, 여기서는 링의 첫 점부터 이웃끼리 이어 붙입니다.
     * 같은 로직으로 검사하면 같은 실수를 함께 저지릅니다.
     */
    private fun containsAnyRing(rings: List<List<LatLng>>, point: LatLng): Boolean =
        rings.any { raw ->
            val ring = unwrapFromFirst(raw)
            listOf(0.0, 360.0, -360.0).any { shift ->
                contains(ring, point.latitude, point.longitude + shift)
            }
        }

    private fun unwrapFromFirst(ring: List<LatLng>): List<P> {
        val out = ArrayList<P>(ring.size)
        var previous = ring.first().longitude
        out += P(ring.first().latitude, previous)
        for (i in 1 until ring.size) {
            var lon = ring[i].longitude
            while (lon - previous > 180) lon -= 360
            while (previous - lon > 180) lon += 360
            out += P(ring[i].latitude, lon)
            previous = lon
        }
        return out
    }

    private fun contains(ring: List<P>, lat: Double, lon: Double): Boolean {
        var inside = false
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[(i + 1) % ring.size]
            if ((a.lat > lat) != (b.lat > lat)) {
                val crossing = a.lon + (lat - a.lat) * (b.lon - a.lon) / (b.lat - a.lat)
                if (lon < crossing) inside = !inside
            }
        }
        return inside
    }

    @Test
    fun `모든 관제 구역이 이름표 위치를 낸다`() {
        val all = loadAll()
        assertTrue("경계를 하나도 읽지 못했습니다", all.size > 500)

        val missing = all.filter { BoundaryLabelPoint.of(it.rings) == null }
        assertTrue(
            "이름표 위치를 못 내는 구역 ${missing.size}곳: ${missing.take(10).map { it.id }}",
            missing.isEmpty()
        )
        println("검사한 구역: ${all.size}곳")
    }

    /**
     * 이름표는 그 구역 안에 놓여야 합니다.
     *
     * 오목한 구역에서 무게중심이 밖으로 나가면 남의 구역에 얹히고, 마가단처럼
     * 경도가 되감기면 아예 다른 대륙에 찍힙니다.
     */
    @Test
    fun `이름표는 자기 구역 안에 놓인다`() {
        val outside = loadAll().filter { boundary ->
            val point = BoundaryLabelPoint.of(boundary.rings) ?: return@filter true
            !containsAnyRing(boundary.rings, point)
        }

        assertTrue(
            "구역 밖에 찍힌 이름표 ${outside.size}곳: " +
                outside.take(15).joinToString {
                    val p = BoundaryLabelPoint.of(it.rings)
                    "${it.id}@${p?.latitude?.toInt()},${p?.longitude?.toInt()}"
                },
            outside.isEmpty()
        )
    }

    /**
     * 날짜변경선에 걸친 구역이 반대편 반구로 튀지 않아야 합니다.
     * 되감김을 잘못 다루면 태평양 관제소가 대서양에, 마가단이 야쿠티야에 찍힙니다.
     */
    @Test
    fun `날짜변경선에 걸친 구역도 자기 경도 범위 안에 놓인다`() {
        val crossing = loadAll().filter { boundary ->
            val lons = boundary.rings.flatten().map { it.longitude }
            lons.any { it > 150 } && lons.any { it < -150 }
        }
        println("날짜변경선에 걸친 구역: ${crossing.size}곳")
        assertTrue("날짜변경선 표본이 없습니다", crossing.isNotEmpty())

        val wrong = crossing.filter { boundary ->
            val point = BoundaryLabelPoint.of(boundary.rings) ?: return@filter true
            // 태평양을 감싸는 구역인데 이름표가 유럽·아프리카(-60~120도)에 찍히면 잘못입니다.
            point.longitude > -60 && point.longitude < 120
        }
        assertTrue(
            "반대편 반구로 튄 이름표 ${wrong.size}곳: " +
                wrong.take(10).joinToString {
                    "${it.id}@${BoundaryLabelPoint.of(it.rings)?.longitude?.toInt()}"
                },
            wrong.isEmpty()
        )
    }

    /**
     * 극점에 정점을 둔 구역이 이름표를 극점으로 끌고 가면 안 됩니다.
     *
     * "실제 공역 위도 범위 안"으로 재면 남극 쐐기(NZCM)처럼 비극점 정점이 한 위도에만
     * 있는 도형이 걸립니다. 그 경우 -72도는 쐐기 한가운데라 옳습니다. 여기서는
     * **극점 근처가 아닌지**만 봅니다. 구역 안쪽인지는 위 테스트가 보장합니다.
     */
    @Test
    fun `극점을 닫는 구역이 이름표를 극점으로 끌고 가지 않는다`() {
        val polar = loadAll().filter { boundary ->
            boundary.rings.flatten().any { abs(it.latitude) >= 89.0 }
        }
        println("극점에 정점을 둔 구역: ${polar.size}곳")
        assertTrue("극점 표본이 없습니다", polar.isNotEmpty())

        val wrong = polar.filter { boundary ->
            val point = BoundaryLabelPoint.of(boundary.rings) ?: return@filter true
            // 지도(메르카토르)에서 85도를 넘으면 사실상 보이지 않습니다.
            abs(point.latitude) > 85.0
        }
        assertTrue(
            "극점 쪽으로 밀린 이름표 ${wrong.size}곳: " +
                wrong.take(10).joinToString {
                    "${it.id}@${BoundaryLabelPoint.of(it.rings)?.latitude?.toInt()}"
                },
            wrong.isEmpty()
        )
    }

    /** 화면에서 확인하기 어려운 구역들의 계산 결과를 남겨 둡니다. */
    @Test
    fun `주요 구역의 이름표 위치를 기록한다`() {
        val notable = setOf(
            "UHMM", "UHMM-O", "UHMM-N1", "NFFJ", "NZCM", "YIND", "YINS",
            "KZAK", "KZNY", "RKRR", "EGTT", "RJBG", "PAZA", "BGGL"
        )
        loadAll().filter { it.id in notable }.forEach { boundary ->
            val point = BoundaryLabelPoint.of(boundary.rings)
            val lats = boundary.rings.flatten().map { it.latitude }
            val lons = boundary.rings.flatten().map { it.longitude }
            println(
                "%-9s 이름표 %7.2f, %8.2f   구역 위도 %.0f~%.0f 경도 %.0f~%.0f".format(
                    boundary.id, point?.latitude ?: 0.0, point?.longitude ?: 0.0,
                    lats.min(), lats.max(), lons.min(), lons.max()
                )
            )
        }
    }
}
