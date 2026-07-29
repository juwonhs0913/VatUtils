package com.vatradar.app.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import com.google.maps.android.ui.IconGenerator

/**
 * 항공기 클러스터 렌더러.
 *
 * maps-compose의 `clusterItemContent`(Composable → Bitmap 변환)는 항공기 한 대마다
 * 컴포지션을 돌려 비트맵을 만들기 때문에, 1,000대가 넘는 VATSIM 트래픽에서는
 * 메인 스레드가 수 초씩 멈춥니다.
 *
 * 대신 아이콘 비트맵은 2종(IFR/VFR)만 미리 만들고, 기수 방향은 Google Maps 마커의
 * 네이티브 rotation으로 처리합니다. flat(true)라 지도를 회전해도 기수가 유지됩니다.
 */
class AircraftRenderer(
    context: Context,
    map: GoogleMap,
    clusterManager: ClusterManager<AircraftClusterItem>
) : DefaultClusterRenderer<AircraftClusterItem>(context, map, clusterManager) {

    private val ifrIcon: BitmapDescriptor by lazy { planeIcon(Color.rgb(0x15, 0x65, 0xC0)) }
    private val vfrIcon: BitmapDescriptor by lazy { planeIcon(Color.rgb(0x2E, 0x7D, 0x32)) }

    private val clusterIconGenerator = IconGenerator(context).apply {
        setColor(Color.rgb(0x15, 0x65, 0xC0))
        setTextAppearance(android.R.style.TextAppearance_Material_Small_Inverse)
    }

    override fun onBeforeClusterItemRendered(
        item: AircraftClusterItem,
        markerOptions: MarkerOptions
    ) {
        markerOptions
            .icon(if (item.aircraft.flightRules == "V") vfrIcon else ifrIcon)
            .rotation(item.aircraft.heading)
            .flat(true)          // 지도 회전과 무관하게 기수 방향 유지
            .anchor(0.5f, 0.5f)
            .title(item.title)
            .snippet(item.snippet)
    }

    override fun onBeforeClusterRendered(
        cluster: Cluster<AircraftClusterItem>,
        markerOptions: MarkerOptions
    ) {
        val icon = clusterIconGenerator.makeIcon(cluster.size.toString())
        markerOptions.icon(BitmapDescriptorFactory.fromBitmap(icon))
    }

    /** 축소 상태에서 개별 마커가 과도하게 그려지지 않도록 최소 묶음 크기를 둡니다. */
    override fun shouldRenderAsCluster(cluster: Cluster<AircraftClusterItem>): Boolean =
        cluster.size >= 4

    /** 위쪽을 향하는 단순 항공기 실루엣. 회전은 마커가 처리하므로 한 방향만 그립니다. */
    private fun planeIcon(color: Int): BitmapDescriptor {
        val size = 48
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
