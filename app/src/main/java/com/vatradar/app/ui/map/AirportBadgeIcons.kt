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

    /**
     * 비행계획 출도착지 표시용 아이콘 — 공항 위치의 점과 ICAO 코드 라벨.
     * 코드마다 한 장씩 만들지만 화면에 보이는 공항만 만들어지고 캐시됩니다.
     */
    private val labelCache = HashMap<String, BitmapDescriptor>()

    fun airportLabel(icao: String): BitmapDescriptor {
        labelCache[icao]?.let { return it }

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val dotRadius = 9f
        val gap = 8f
        val padH = 8f
        val padV = 5f

        val textWidth = text.measureText(icao)
        val metrics = text.fontMetrics
        val textHeight = metrics.descent - metrics.ascent

        val width = (dotRadius * 2 + gap + textWidth + padH * 2).toInt()
        val height = (maxOf(dotRadius * 2, textHeight) + padV * 2).toInt()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centerY = height / 2f

        // 라벨 배경 (지도 위에서 글자가 읽히도록)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(215, 255, 255, 255)
            style = Paint.Style.FILL
        }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val rect = RectF(1f, 1f, width - 1f, height - 1f)
        canvas.drawRoundRect(rect, 8f, 8f, background)
        canvas.drawRoundRect(rect, 8f, 8f, border)

        // 공항 위치를 나타내는 점
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0x0B, 0x1D, 0x2E)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(padH + dotRadius, centerY, dotRadius, dot)

        text.color = Color.rgb(0x0B, 0x1D, 0x2E)
        canvas.drawText(
            icao,
            padH + dotRadius * 2 + gap,
            centerY - (metrics.ascent + metrics.descent) / 2,
            text
        )

        return BitmapDescriptorFactory.fromBitmap(bitmap).also { labelCache[icao] = it }
    }

    /** 라벨 안에서 점이 차지하는 가로 비율 — 마커 앵커를 점 위치에 맞추는 데 씁니다. */
    fun airportLabelAnchorX(icao: String): Float {
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val width = 9f * 2 + 8f + text.measureText(icao) + 8f * 2
        return (8f + 9f) / width
    }

    /**
     * 관제 구역(CTR/FSS/APP) 한가운데에 얹는 콜사인 라벨.
     *
     * 폴리곤만으로는 어느 구역이 누구 담당인지 눌러 봐야 알 수 있습니다.
     * 색을 옅게 깔고 테두리를 줘서 지도 위 지명과 섞이지 않게 합니다.
     */
    private val boundaryLabelCache = HashMap<String, BitmapDescriptor>()

    fun boundaryLabel(callsign: String, argb: Int): BitmapDescriptor {
        val key = callsign + '#' + argb
        boundaryLabelCache[key]?.let { return it }

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val padH = 10f
        val padV = 6f
        val metrics = text.fontMetrics
        val width = (text.measureText(callsign) + padH * 2).toInt()
        val height = (metrics.descent - metrics.ascent + padV * 2).toInt()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(225, 255, 255, 255)
            style = Paint.Style.FILL
        }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = argb
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val rect = RectF(1.5f, 1.5f, width - 1.5f, height - 1.5f)
        canvas.drawRoundRect(rect, 7f, 7f, background)
        canvas.drawRoundRect(rect, 7f, 7f, border)

        text.color = argb
        canvas.drawText(
            callsign,
            padH,
            height / 2f - (metrics.ascent + metrics.descent) / 2,
            text
        )

        return BitmapDescriptorFactory.fromBitmap(bitmap).also { boundaryLabelCache[key] = it }
    }

    /** MapScreen의 facilityColor와 같은 색을 android.graphics 쪽에서도 씁니다. */
    fun facilityArgb(facility: FacilityType): Int = when (facility) {
        FacilityType.TWR -> Color.rgb(0x19, 0x76, 0xD2)
        FacilityType.GND -> Color.rgb(0x38, 0x8E, 0x3C)
        FacilityType.DEL -> Color.rgb(0x7B, 0x1F, 0xA2)
        FacilityType.APP -> Color.rgb(0xF5, 0x7C, 0x00)
        FacilityType.CTR -> Color.rgb(0xC6, 0x28, 0x28)
        FacilityType.FSS -> Color.rgb(0x00, 0x69, 0x5C)
        else -> Color.GRAY
    }
}
