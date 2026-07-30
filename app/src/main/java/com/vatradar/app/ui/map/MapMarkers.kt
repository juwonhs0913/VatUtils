package com.vatradar.app.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.vatradar.app.domain.model.Aircraft
import com.vatradar.app.domain.model.Airport
import com.vatradar.app.domain.model.Controller

/** 마커에 붙여 클릭 시 어떤 대상인지 구분합니다. */
sealed interface MarkerTag {
    data class AircraftTag(val callsign: String) : MarkerTag
    data class BadgeTag(val airport: String) : MarkerTag
    data class AirportTag(val icao: String) : MarkerTag
}

/** 항공기 아이콘. IFR/VFR 두 장만 만들고 기수 방향은 마커 회전으로 처리합니다. */
private object PlaneIcons {
    val ifr: BitmapDescriptor by lazy { draw(Color.rgb(0x15, 0x65, 0xC0)) }
    val vfr: BitmapDescriptor by lazy { draw(Color.rgb(0x2E, 0x7D, 0x32)) }

    /**
     * 내 항공기는 등급 색으로, 더 크게, 발광 테두리를 둘러 눈에 띄게 그립니다.
     * 수천 대 사이에서 자기 기체를 바로 찾을 수 있어야 하기 때문입니다.
     */
    private val ownCache = HashMap<Int, BitmapDescriptor>()
    fun own(tierColor: Int): BitmapDescriptor =
        ownCache.getOrPut(tierColor) { draw(tierColor, size = 68, halo = true) }

    private fun draw(color: Int, size: Int = 44, halo: Boolean = false): BitmapDescriptor {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (halo) {
            // 등급 색 발광 링. 지도 배경이 밝든 어둡든 기체가 도드라집니다.
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                alpha = 70
                style = Paint.Style.FILL
            }
            canvas.drawCircle(size / 2f, size / 2f, size / 2f - 1f, glow)
        }

        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = if (halo) 3f else 2f
        }

        val s = size / 24f
        val path = Path().apply {
            moveTo(12 * s, 2 * s)      // 기수
            lineTo(13.5f * s, 9 * s)
            lineTo(22 * s, 14 * s)     // 오른쪽 날개
            lineTo(22 * s, 16 * s)
            lineTo(13.5f * s, 13.5f * s)
            lineTo(13.5f * s, 19 * s)
            lineTo(16 * s, 21 * s)     // 오른쪽 꼬리
            lineTo(16 * s, 22 * s)
            lineTo(12 * s, 20.5f * s)
            lineTo(8 * s, 22 * s)      // 왼쪽 꼬리
            lineTo(8 * s, 21 * s)
            lineTo(10.5f * s, 19 * s)
            lineTo(10.5f * s, 13.5f * s)
            lineTo(2 * s, 16 * s)      // 왼쪽 날개
            lineTo(2 * s, 14 * s)
            lineTo(10.5f * s, 9 * s)
            close()
        }

        canvas.drawPath(path, body)
        canvas.drawPath(path, outline)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}

/**
 * 지도 마커를 Compose 밖에서 직접 관리합니다.
 *
 * 이렇게 하는 이유가 둘 있습니다.
 *
 * 1) 클러스터링 없이 항공기 1,500대를 전부 보여줘야 하는데,
 *    Compose Marker 컴포저블 1,500개는 매 갱신마다 컴포지션 비용이 큽니다.
 *    여기서는 마커 객체를 재사용하고 위치·회전만 갱신합니다.
 *
 * 2) 클릭 처리를 한 곳으로 모읍니다. GoogleMap의 OnMarkerClickListener는 하나뿐이라
 *    ClusterManager를 걸면 maps-compose가 설치한 리스너가 덮여, Compose Marker의
 *    onClick이 전혀 호출되지 않고 기본 정보창(제목 문자열)만 뜹니다.
 *    T/G/D 배지를 눌렀을 때 관제사 정보 대신 공항 코드만 보이던 게 이 문제였습니다.
 */
class MapMarkerController {

    private val aircraftMarkers = HashMap<String, Marker>()
    private val badgeMarkers = HashMap<String, Marker>()
    private val airportMarkers = HashMap<String, Marker>()
    private var listenerInstalled = false

    fun installClickListener(
        map: GoogleMap,
        onAircraft: (String) -> Unit,
        onBadge: (String) -> Unit,
        onAirport: (String) -> Unit
    ) {
        if (listenerInstalled) return
        map.setOnMarkerClickListener { marker ->
            when (val tag = marker.tag) {
                is MarkerTag.AircraftTag -> {
                    onAircraft(tag.callsign)
                    true    // true를 돌려 기본 정보창이 뜨지 않게 합니다
                }
                is MarkerTag.BadgeTag -> {
                    onBadge(tag.airport)
                    true
                }
                is MarkerTag.AirportTag -> {
                    onAirport(tag.icao)
                    true
                }
                else -> false
            }
        }
        listenerInstalled = true
    }

    /**
     * 비행계획 출도착 공항 라벨을 갱신합니다.
     * 배율이 낮으면 빈 목록이 들어와 전부 제거됩니다.
     */
    fun syncAirports(map: GoogleMap, airports: List<Airport>) {
        val seen = HashSet<String>(airports.size)

        airports.forEach { airport ->
            seen += airport.icao
            if (airportMarkers.containsKey(airport.icao)) return@forEach

            val marker = map.addMarker(
                MarkerOptions()
                    .position(LatLng(airport.latitude, airport.longitude))
                    .icon(AirportBadgeIcons.airportLabel(airport.icao))
                    // 라벨 안의 점이 실제 공항 좌표에 오도록 앵커를 맞춥니다.
                    .anchor(AirportBadgeIcons.airportLabelAnchorX(airport.icao), 0.5f)
                    .zIndex(0.5f)   // 항공기 위, 관제 배지 아래
            ) ?: return@forEach
            marker.tag = MarkerTag.AirportTag(airport.icao)
            airportMarkers[airport.icao] = marker
        }

        val gone = airportMarkers.keys - seen
        gone.forEach { airportMarkers.remove(it)?.remove() }
    }

    /** 항공기 마커를 현재 스냅샷에 맞춥니다. 있는 건 갱신, 없어진 건 제거. */
    fun syncAircraft(
        map: GoogleMap,
        aircraft: List<Aircraft>,
        ownCid: Int? = null,
        ownTierColor: Int? = null
    ) {
        val seen = HashSet<String>(aircraft.size)

        aircraft.forEach { a ->
            seen += a.callsign
            val isMine = ownCid != null && a.cid == ownCid
            val icon = when {
                isMine && ownTierColor != null -> PlaneIcons.own(ownTierColor)
                a.flightRules == "V" -> PlaneIcons.vfr
                else -> PlaneIcons.ifr
            }
            val position = LatLng(a.latitude, a.longitude)

            val existing = aircraftMarkers[a.callsign]
            if (existing == null) {
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(position)
                        .icon(icon)
                        .rotation(a.heading)
                        .flat(true)             // 지도를 회전해도 기수 방향 유지
                        .anchor(0.5f, 0.5f)
                        .zIndex(if (isMine) 0.9f else 0f)   // 내 기체는 다른 기체 위에
                        .title(a.callsign)
                ) ?: return@forEach
                marker.tag = MarkerTag.AircraftTag(a.callsign)
                aircraftMarkers[a.callsign] = marker
            } else {
                existing.position = position
                existing.rotation = a.heading
                existing.setIcon(icon)
            }
        }

        val gone = aircraftMarkers.keys - seen
        gone.forEach { aircraftMarkers.remove(it)?.remove() }
    }

    /** 공항 관제석(T/G/D) 배지를 갱신합니다. */
    fun syncBadges(map: GoogleMap, groups: Map<String, List<Controller>>) {
        val seen = HashSet<String>(groups.size)

        groups.forEach { (airport, controllers) ->
            val first = controllers.firstOrNull() ?: return@forEach
            val lat = first.latitude ?: return@forEach
            val lon = first.longitude ?: return@forEach
            val icon = AirportBadgeIcons.forFacilities(controllers.map { it.facility })
                ?: return@forEach

            seen += airport
            val position = LatLng(lat, lon)

            // 열려 있는 관제석 조합이 바뀌면 아이콘이 달라지므로 매번 갱신합니다.
            val existing = badgeMarkers[airport]
            if (existing == null) {
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(position)
                        .icon(icon)
                        .anchor(0f, 0.5f)       // 공항 점 오른쪽에 붙도록
                        .zIndex(1f)             // 항공기보다 위에
                ) ?: return@forEach
                marker.tag = MarkerTag.BadgeTag(airport)
                badgeMarkers[airport] = marker
            } else {
                existing.position = position
                existing.setIcon(icon)
            }
        }

        val gone = badgeMarkers.keys - seen
        gone.forEach { badgeMarkers.remove(it)?.remove() }
    }

    fun clearAircraft() {
        aircraftMarkers.values.forEach { it.remove() }
        aircraftMarkers.clear()
    }

    fun clearBadges() {
        badgeMarkers.values.forEach { it.remove() }
        badgeMarkers.clear()
    }
}
