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
import com.vatradar.app.domain.model.Controller

/** 마커에 붙여 클릭 시 어떤 대상인지 구분합니다. */
sealed interface MarkerTag {
    data class AircraftTag(val callsign: String) : MarkerTag
    data class BadgeTag(val airport: String) : MarkerTag
}

/** 항공기 아이콘. IFR/VFR 두 장만 만들고 기수 방향은 마커 회전으로 처리합니다. */
private object PlaneIcons {
    val ifr: BitmapDescriptor by lazy { draw(Color.rgb(0x15, 0x65, 0xC0)) }
    val vfr: BitmapDescriptor by lazy { draw(Color.rgb(0x2E, 0x7D, 0x32)) }

    private fun draw(color: Int): BitmapDescriptor {
        val size = 44
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f
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
    private var listenerInstalled = false

    fun installClickListener(
        map: GoogleMap,
        onAircraft: (String) -> Unit,
        onBadge: (String) -> Unit
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
                else -> false
            }
        }
        listenerInstalled = true
    }

    /** 항공기 마커를 현재 스냅샷에 맞춥니다. 있는 건 갱신, 없어진 건 제거. */
    fun syncAircraft(map: GoogleMap, aircraft: List<Aircraft>) {
        val seen = HashSet<String>(aircraft.size)

        aircraft.forEach { a ->
            seen += a.callsign
            val icon = if (a.flightRules == "V") PlaneIcons.vfr else PlaneIcons.ifr
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
