package com.vatradar.app.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.vatradar.app.domain.model.FacilityType

/**
 * 공항 관제석(TWR/GND/DEL)을 공항 옆 작은 사각 배지로 표시합니다.
 *
 * 한 공항에 여러 관제석이 동시에 열리는 게 보통이라, 각각을 별도 마커로 두면
 * 서로 겹쳐 읽을 수 없습니다. 그래서 공항 하나당 배지 한 줄을 통째로 한 장의
 * 비트맵으로 만들어 마커 하나만 놓습니다.
 *
 * 조합 수가 한정적(T/G/D의 부분집합 = 7가지)이라 결과를 캐시합니다.
 */
object AirportBadgeIcons {

    private const val BOX = 46
    private const val GAP = 5
    private const val LEAD = 10          // 공항 점과 배지 사이 여백
    private const val RADIUS = 9f

    private val cache = HashMap<String, BitmapDescriptor>()

    /** TWR → T, GND → G, DEL → D. 그 외 시설은 배지 대상이 아닙니다. */
    fun letterFor(facility: FacilityType): String? = when (facility) {
        FacilityType.TWR -> "T"
        FacilityType.GND -> "G"
        FacilityType.DEL -> "D"
        else -> null
    }

    /** 표시 우선순위: 관제 권한이 큰 순서(T → G → D)로 왼쪽부터 놓습니다. */
    fun sortKey(facility: FacilityType): Int = when (facility) {
        FacilityType.TWR -> 0
        FacilityType.GND -> 1
        FacilityType.DEL -> 2
        else -> 9
    }

    fun forFacilities(facilities: List<FacilityType>): BitmapDescriptor? {
        val ordered = facilities
            .distinct()
            .filter { letterFor(it) != null }
            .sortedBy { sortKey(it) }
        if (ordered.isEmpty()) return null

        val key = ordered.joinToString("") { letterFor(it)!! }
        cache[key]?.let { return it }

        val count = ordered.size
        val width = LEAD + count * BOX + (count - 1) * GAP
        val bitmap = Bitmap.createBitmap(width, BOX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.WHITE
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = BOX * 0.62f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        ordered.forEachIndexed { index, facility ->
            val left = (LEAD + index * (BOX + GAP)).toFloat()
            val rect = RectF(left + 1.5f, 1.5f, left + BOX - 1.5f, BOX - 1.5f)

            fill.color = facilityArgb(facility)
            canvas.drawRoundRect(rect, RADIUS, RADIUS, fill)
            canvas.drawRoundRect(rect, RADIUS, RADIUS, border)

            // 글자를 상자 중앙에 수직 정렬합니다.
            val metrics = text.fontMetrics
            val baseline = rect.centerY() - (metrics.ascent + metrics.descent) / 2
            canvas.drawText(letterFor(facility)!!, rect.centerX(), baseline, text)
        }

        return BitmapDescriptorFactory.fromBitmap(bitmap).also { cache[key] = it }
    }

    /** MapScreen의 facilityColor와 같은 색을 android.graphics 쪽에서도 씁니다. */
    private fun facilityArgb(facility: FacilityType): Int = when (facility) {
        FacilityType.TWR -> Color.rgb(0x19, 0x76, 0xD2)
        FacilityType.GND -> Color.rgb(0x38, 0x8E, 0x3C)
        FacilityType.DEL -> Color.rgb(0x7B, 0x1F, 0xA2)
        else -> Color.GRAY
    }
}
