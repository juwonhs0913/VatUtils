package com.vatradar.app.domain

import com.google.android.gms.maps.model.LatLng
import kotlin.math.abs

/**
 * 관제 구역 폴리곤 위에 콜사인을 얹을 지점.
 *
 * 단순히 모든 점의 평균을 쓰면 두 가지가 깨집니다.
 *
 *  1. **날짜변경선.** Oakland Oceanic(KZAK)은 경도가 -180~180에 걸쳐 있어
 *     평균이 -44도, 즉 대서양 한복판으로 나옵니다. 태평양 관제소인데요.
 *  2. **오목한 모양.** 무게중심이 폴리곤 바깥으로 나가면 라벨이 남의 구역에 얹힙니다.
 *
 * 그래서 경도를 이어 붙여 펴고, 가장 넓은 조각을 고르고, 무게중심이 밖으로 나가면
 * 가장 긴 가로 현의 중점으로 물러섭니다.
 */
object BoundaryLabelPoint {

    fun of(rings: List<List<LatLng>>): LatLng? {
        val usable = rings.filter { it.size >= 3 }
        if (usable.isEmpty()) return null

        val biggest = usable.map { unwrap(it) }.maxByOrNull { area(it) } ?: return null

        var lat = biggest.sumOf { it.latitude } / biggest.size
        var lon = biggest.sumOf { it.longitude } / biggest.size

        if (!contains(biggest, lat, lon)) {
            widestChord(biggest)?.let { (chordLat, chordLon) ->
                lat = chordLat
                lon = chordLon
            }
        }

        return LatLng(lat, normalizeLongitude(lon))
    }

    /**
     * 날짜변경선을 넘는 링의 경도를 연속되게 폅니다.
     * 이웃한 두 점의 차가 180도를 넘으면 한 바퀴 돈 것으로 보고 보정합니다.
     */
    private fun unwrap(ring: List<LatLng>): List<LatLng> {
        val out = ArrayList<LatLng>(ring.size)
        out += ring.first()
        for (i in 1 until ring.size) {
            val previous = out.last().longitude
            var lon = ring[i].longitude
            while (lon - previous > 180) lon -= 360
            while (previous - lon > 180) lon += 360
            out += LatLng(ring[i].latitude, lon)
        }
        return out
    }

    /** 신발끈 공식. 실제 넓이가 아니라 조각끼리 비교하는 용도입니다. */
    private fun area(ring: List<LatLng>): Double {
        var sum = 0.0
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[(i + 1) % ring.size]
            sum += a.longitude * b.latitude - b.longitude * a.latitude
        }
        return abs(sum) / 2
    }

    private fun contains(ring: List<LatLng>, lat: Double, lon: Double): Boolean {
        var inside = false
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[(i + 1) % ring.size]
            if ((a.latitude > lat) != (b.latitude > lat)) {
                val crossing = a.longitude +
                    (lat - a.latitude) * (b.longitude - a.longitude) / (b.latitude - a.latitude)
                if (lon < crossing) inside = !inside
            }
        }
        return inside
    }

    /**
     * 폴리곤을 가로로 잘랐을 때 가장 긴 선분의 중점.
     * 오목한 모양에서도 반드시 폴리곤 안쪽입니다.
     */
    private fun widestChord(ring: List<LatLng>): Pair<Double, Double>? {
        val candidates = ring.map { it.latitude }.distinct().sorted()
        if (candidates.size < 3) return null

        var best: Triple<Double, Double, Double>? = null   // (길이, lat, lon)
        for (index in 1 until candidates.size - 1) {
            val lat = candidates[index]
            val crossings = ArrayList<Double>()
            for (i in ring.indices) {
                val a = ring[i]
                val b = ring[(i + 1) % ring.size]
                if ((a.latitude > lat) != (b.latitude > lat)) {
                    crossings += a.longitude +
                        (lat - a.latitude) * (b.longitude - a.longitude) / (b.latitude - a.latitude)
                }
            }
            crossings.sort()
            var i = 0
            while (i + 1 < crossings.size) {
                val span = crossings[i + 1] - crossings[i]
                if (best == null || span > best!!.first) {
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

    /** 링들이 차지하는 경도 폭. 화면에서 얼마나 넓은지 가늠하는 데 씁니다. */
    fun longitudeSpan(rings: List<List<LatLng>>): Double {
        val biggest = rings.filter { it.size >= 3 }.map { unwrap(it) }
            .maxByOrNull { area(it) } ?: return 0.0
        val lons = biggest.map { it.longitude }
        return (lons.max() - lons.min()).coerceAtMost(360.0)
    }
}
