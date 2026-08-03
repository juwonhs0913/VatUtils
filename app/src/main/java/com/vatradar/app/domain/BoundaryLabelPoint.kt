package com.vatradar.app.domain

import com.google.android.gms.maps.model.LatLng
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 관제 구역 폴리곤 위에 콜사인을 얹을 지점.
 *
 * 그냥 모든 점의 평균을 쓰면 세 가지가 깨집니다.
 *
 *  1. **날짜변경선.** Oakland Oceanic(KZAK)은 경도가 -180~180에 걸쳐 있어
 *     단순 평균이 대서양 한복판으로 나옵니다. 태평양 관제소인데요.
 *  2. **극점.** 북극권 FIR은 위도 90에 점을 찍어 폴리곤을 닫습니다. 극점은 선이
 *     아니라 점이라 그리기용 관례일 뿐인데, 위경도 평면에서는 거대한 쐐기로 잡혀
 *     라벨을 실제 공역보다 한참 북쪽으로 끌고 갑니다.
 *  3. **오목한 모양.** 무게중심이 폴리곤 바깥으로 나가면 라벨이 남의 구역에 얹힙니다.
 *
 * 경도는 각도라서 평균을 그냥 내면 안 됩니다. LatLng가 180을 -180으로 정규화하는
 * 것만으로도 값이 통째로 어긋납니다(마가단이 151E 대신 137E로 나왔습니다).
 * 그래서 경도는 **원형 평균**으로 구하고, 그 값을 기준으로 링을 한 프레임에
 * 펴 놓은 뒤 나머지 계산을 합니다.
 */
object BoundaryLabelPoint {

    /** 이 위도 이상은 극점 닫기용 정점으로 봅니다. */
    private const val POLE_LATITUDE = 89.0

    /**
     * 계산용 좌표.
     *
     * LatLng를 쓰면 안 됩니다 — 생성자가 경도를 [-180,180)로 되감아서,
     * 날짜변경선을 넘기려고 180으로 옮겨 놓은 값이 즉시 -180으로 되돌아갑니다.
     * 마가단 라벨이 151E 대신 137E로 나온 원인이 바로 이것이었습니다.
     */
    private data class Point(val lat: Double, val lon: Double)

    fun of(rings: List<List<LatLng>>): LatLng? {
        val biggest = biggestRing(rings) ?: return null

        var lat = biggest.sumOf { it.lat } / biggest.size
        var lon = biggest.sumOf { it.lon } / biggest.size   // 이미 한 프레임에 펴져 있습니다

        if (!contains(biggest, lat, lon)) {
            widestChord(biggest)?.let { (chordLat, chordLon) ->
                lat = chordLat
                lon = chordLon
            }
        }

        return LatLng(lat, normalizeLongitude(lon))
    }

    /** 링들이 차지하는 경도 폭. 화면에서 얼마나 넓은지 가늠하는 데 씁니다. */
    fun longitudeSpan(rings: List<List<LatLng>>): Double {
        val biggest = biggestRing(rings) ?: return 0.0
        val lons = biggest.map { it.lon }
        return (lons.max() - lons.min()).coerceAtMost(360.0)
    }

    /**
     * 극점 정점을 걷어내고, 경도를 한 프레임에 편 뒤, 가장 넓은 링을 고릅니다.
     * 돌려주는 링의 경도는 [-180,180]을 벗어날 수 있습니다 — 계산용 좌표입니다.
     */
    private fun biggestRing(rings: List<List<LatLng>>): List<Point>? =
        rings.map { dropPoleClosure(it) }
            .filter { it.size >= 3 }
            .map { alignLongitudes(it) }
            .maxByOrNull { area(it) }

    private fun dropPoleClosure(ring: List<LatLng>): List<LatLng> {
        val trimmed = ring.filter { abs(it.latitude) < POLE_LATITUDE }
        // 전부 극점인 링(있을 리 없지만)은 원본을 그대로 둡니다.
        return if (trimmed.size >= 3) trimmed else ring
    }

    /**
     * 링의 원형 평균 경도를 기준으로, 모든 점을 그 ±180도 안으로 옮깁니다.
     *
     * 이웃한 점끼리만 보고 펴면(누적 방식) 시작점이 어디냐에 따라 결과가 달라지고,
     * 180에 걸친 점 하나 때문에 링 전체가 한 바퀴 밀립니다. 기준점을 먼저 정하면
     * 그런 일이 없습니다.
     */
    private fun alignLongitudes(ring: List<LatLng>): List<Point> {
        val anchor = circularMeanLongitude(ring)
        return ring.map { point ->
            var lon = point.longitude
            while (lon - anchor > 180) lon -= 360
            while (anchor - lon > 180) lon += 360
            Point(point.latitude, lon)
        }
    }

    /** 각도의 평균. 단위벡터를 더해 방향을 구합니다 — 랩어라운드에 영향받지 않습니다. */
    private fun circularMeanLongitude(ring: List<LatLng>): Double {
        var x = 0.0
        var y = 0.0
        for (point in ring) {
            val radians = Math.toRadians(point.longitude)
            x += cos(radians)
            y += sin(radians)
        }
        return Math.toDegrees(atan2(y, x))
    }

    /** 신발끈 공식. 실제 넓이가 아니라 조각끼리 비교하는 용도입니다. */
    private fun area(ring: List<Point>): Double {
        var sum = 0.0
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[(i + 1) % ring.size]
            sum += a.lon * b.lat - b.lon * a.lat
        }
        return abs(sum) / 2
    }

    private fun contains(ring: List<Point>, lat: Double, lon: Double): Boolean {
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

    /**
     * 폴리곤을 가로로 잘랐을 때 가장 긴 선분의 중점.
     * 오목한 모양에서도 반드시 폴리곤 안쪽입니다.
     */
    private fun widestChord(ring: List<Point>): Pair<Double, Double>? {
        val candidates = ring.map { it.lat }.distinct().sorted()
        if (candidates.size < 3) return null

        var best: Triple<Double, Double, Double>? = null   // (길이, lat, lon)
        for (index in 1 until candidates.size - 1) {
            val lat = candidates[index]
            val crossings = ArrayList<Double>()
            for (i in ring.indices) {
                val a = ring[i]
                val b = ring[(i + 1) % ring.size]
                if ((a.lat > lat) != (b.lat > lat)) {
                    crossings += a.lon + (lat - a.lat) * (b.lon - a.lon) / (b.lat - a.lat)
                }
            }
            crossings.sort()
            var i = 0
            while (i + 1 < crossings.size) {
                val span = crossings[i + 1] - crossings[i]
                if (best == null || span > best.first) {
                    best = Triple(span, lat, (crossings[i] + crossings[i + 1]) / 2)
                }
                i += 2
            }
        }
        return best?.let { it.second to it.third }
    }

    private fun normalizeLongitude(value: Double): Double {
        var lon = value
        while (lon > 180) lon -= 360
        while (lon < -180) lon += 360
        return lon
    }
}
