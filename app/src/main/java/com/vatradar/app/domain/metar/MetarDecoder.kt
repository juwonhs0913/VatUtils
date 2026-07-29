package com.vatradar.app.domain.metar

import kotlin.math.roundToInt

/** 디코딩된 METAR 한 줄(라벨 + 값 + 아이콘 힌트). */
data class MetarField(
    val label: String,
    val value: String,
    val icon: MetarIcon
)

enum class MetarIcon { WIND, VISIBILITY, CLOUD, TEMPERATURE, PRESSURE, WEATHER, TIME, RUNWAY, INFO }

data class DecodedMetar(
    val station: String,
    val raw: String,
    val fields: List<MetarField>,
    /** 비행 규칙 구분 (시정/운고 기준). 지도·목록에서 색으로 표시합니다. */
    val flightCategory: FlightCategory
)

enum class FlightCategory { VFR, MVFR, IFR, LIFR, UNKNOWN }

/**
 * METAR 평문을 한국어로 디코딩합니다.
 *
 * 전체 METAR 문법을 다 다루지는 않고, VATSIM에서 실제로 자주 보이는 요소를 우선합니다.
 * 해석하지 못한 토큰은 버리지 않고 '기타'로 모아 보여줍니다 — 조종사가 원문을 놓치지 않도록.
 */
object MetarDecoder {

    fun decode(raw: String): DecodedMetar {
        val text = raw.trim().removeSuffix("=").trim()
        val tokens = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) {
            return DecodedMetar("", raw, emptyList(), FlightCategory.UNKNOWN)
        }

        val fields = mutableListOf<MetarField>()
        val unknown = mutableListOf<String>()

        var station = ""
        var visibilityMeters: Int? = null
        var ceilingFt: Int? = null
        val clouds = mutableListOf<String>()
        var index = 0

        // METAR / SPECI 접두사
        if (tokens[0] == "METAR" || tokens[0] == "SPECI") index++
        if (index < tokens.size && tokens[index].matches(Regex("[A-Z]{4}"))) {
            station = tokens[index]; index++
        }

        var isAuto = false
        var isCavok = false

        while (index < tokens.size) {
            val t = tokens[index]
            when {
                t == "AUTO" -> isAuto = true
                t == "NIL" -> Unit

                // 추이 예보(trend). 이후 토큰은 '현재 관측'이 아니라 향후 2시간 예보이므로
                // 본문 파싱을 여기서 끊습니다. 섞어 읽으면 기상 현상이 중복 표시됩니다.
                t == "NOSIG" || t == "TEMPO" || t == "BECMG" ||
                    t.matches(Regex("PROB\\d{2}")) || t.matches(Regex("FM\\d{4,6}")) -> {
                    val rest = tokens.drop(index).joinToString(" ")
                    fields += MetarField("추이 (향후 2시간)", decodeTrend(rest), MetarIcon.TIME)
                    index = tokens.size
                }

                t == "COR" -> Unit
                t == "RMK" -> {
                    // RMK 이후는 비표준 비고이므로 원문으로만 남깁니다.
                    val remark = tokens.drop(index + 1).joinToString(" ")
                    if (remark.isNotBlank()) {
                        fields += MetarField("비고", remark, MetarIcon.INFO)
                    }
                    index = tokens.size
                }

                // 관측 시각: 291030Z (일자 포함)
                t.matches(Regex("\\d{6}Z")) -> {
                    val day = t.substring(0, 2)
                    val hh = t.substring(2, 4)
                    val mm = t.substring(4, 6)
                    fields += MetarField("관측 시각", "${day}일 ${hh}:${mm} UTC", MetarIcon.TIME)
                }

                // 관측 시각: 1300Z (일자를 생략하는 관측소가 있습니다)
                t.matches(Regex("\\d{4}Z")) -> {
                    fields += MetarField(
                        "관측 시각",
                        "${t.substring(0, 2)}:${t.substring(2, 4)} UTC",
                        MetarIcon.TIME
                    )
                }

                // 바람: 24010KT, 24010G25KT, VRB03KT, 00000KT
                t.matches(Regex("(\\d{3}|VRB)\\d{2,3}(G\\d{2,3})?(KT|MPS|KMH)")) -> {
                    fields += MetarField("바람", decodeWind(t), MetarIcon.WIND)
                }

                // 풍향 변동: 200V280
                t.matches(Regex("\\d{3}V\\d{3}")) -> {
                    fields += MetarField(
                        "풍향 변동",
                        "${t.substring(0, 3)}도 ~ ${t.substring(4, 7)}도 사이",
                        MetarIcon.WIND
                    )
                }

                t == "CAVOK" -> {
                    isCavok = true
                    visibilityMeters = 10000
                    fields += MetarField("시정", "10km 이상, 운량 양호 (CAVOK)", MetarIcon.VISIBILITY)
                }

                // 시정(미터): 9999, 0800
                t.matches(Regex("\\d{4}")) -> {
                    val m = t.toInt()
                    visibilityMeters = m
                    fields += MetarField("시정", formatVisibilityMeters(m), MetarIcon.VISIBILITY)
                }

                // 시정(마일): 10SM, 1/2SM, 2 1/2SM의 뒷부분
                t.matches(Regex("(M)?\\d+(/\\d+)?SM")) -> {
                    val meters = statuteMilesToMeters(t)
                    visibilityMeters = meters
                    fields += MetarField("시정", formatVisibilityStatute(t, meters), MetarIcon.VISIBILITY)
                }

                // 활주로 가시거리: R33L/0600
                t.startsWith("R") && t.contains("/") && t.matches(Regex("R\\d{2}[LCR]?/[MP]?\\d{4}[VNDU]?[MP]?\\d{0,4}[UDN]?")) -> {
                    fields += MetarField("활주로 가시거리", t.removePrefix("R").replace("/", " 활주로: ") + "m", MetarIcon.RUNWAY)
                }

                // 구름: FEW008, SCT010, BKN006, OVC003, NSC, NCD, SKC, CLR, VV003
                t.matches(Regex("(FEW|SCT|BKN|OVC)\\d{3}(CB|TCU)?")) -> {
                    val amount = t.substring(0, 3)
                    val heightFt = t.substring(3, 6).toInt() * 100
                    val type = t.substring(6)
                    clouds += "${cloudAmountKo(amount)} ${heightFt}ft" + cloudTypeKo(type)
                    // 운고(ceiling)는 BKN 이상부터
                    if (amount == "BKN" || amount == "OVC") {
                        if (ceilingFt == null || heightFt < ceilingFt!!) ceilingFt = heightFt
                    }
                }
                t.matches(Regex("VV\\d{3}")) -> {
                    val h = t.substring(2, 5).toInt() * 100
                    clouds += "수직 시정 ${h}ft"
                    if (ceilingFt == null || h < ceilingFt!!) ceilingFt = h
                }
                t == "NSC" || t == "NCD" || t == "SKC" || t == "CLR" -> clouds += "구름 없음"

                // 기온/이슬점: 27/25, M03/M08
                t.matches(Regex("M?\\d{2}/M?\\d{2}")) -> {
                    val (tmp, dew) = t.split("/")
                    val tc = parseTemp(tmp)
                    val dc = parseTemp(dew)
                    fields += MetarField("기온 / 이슬점", "${tc}°C / ${dc}°C" + humidityHint(tc, dc), MetarIcon.TEMPERATURE)
                }

                // 기압: Q1010 (hPa) / A2992 (inHg)
                t.matches(Regex("Q\\d{4}")) -> {
                    val hpa = t.substring(1).toInt()
                    val inhg = hpa * 0.02953
                    fields += MetarField("기압 (QNH)", "${hpa} hPa (${"%.2f".format(inhg)} inHg)", MetarIcon.PRESSURE)
                }
                t.matches(Regex("A\\d{4}")) -> {
                    val inhg = t.substring(1).toInt() / 100.0
                    val hpa = (inhg / 0.02953).roundToInt()
                    fields += MetarField("기압 (QNH)", "${"%.2f".format(inhg)} inHg (${hpa} hPa)", MetarIcon.PRESSURE)
                }

                // 기상 현상: -RA, +TSRA, VCSH, BR, FG ...
                isWeatherToken(t) -> {
                    fields += MetarField("기상 현상", decodeWeather(t), MetarIcon.WEATHER)
                }

                else -> unknown += t
            }
            index++
        }

        if (isAuto) fields += MetarField("관측 방식", "자동 관측 (AUTO)", MetarIcon.INFO)
        if (clouds.isNotEmpty() && !isCavok) {
            fields += MetarField("구름", clouds.joinToString(", "), MetarIcon.CLOUD)
        }
        if (unknown.isNotEmpty()) {
            fields += MetarField("기타", unknown.joinToString(" "), MetarIcon.INFO)
        }

        return DecodedMetar(
            station = station,
            raw = text,
            fields = fields,
            flightCategory = category(visibilityMeters, ceilingFt)
        )
    }

    // --- 추이(trend) ---

    /**
     * 추이 예보를 사람이 읽을 수 있게 풀어씁니다.
     * 본문과 달리 요약 한 줄로만 보여줍니다 — 조종사가 알아야 할 건 "곧 나빠지는가" 정도입니다.
     */
    private fun decodeTrend(raw: String): String {
        val tokens = raw.split(Regex("\\s+")).filter { it.isNotBlank() }
        val parts = mutableListOf<String>()

        tokens.forEach { t ->
            when {
                t == "NOSIG" -> parts += "유의미한 변화 없음"
                t == "TEMPO" -> parts += "일시적으로"
                t == "BECMG" -> parts += "점차"
                t.matches(Regex("PROB\\d{2}")) -> parts += "확률 ${t.substring(4)}%"
                t.matches(Regex("FM\\d{4,6}")) ->
                    parts += "${t.substring(1, 3).takeIf { t.length >= 5 } ?: ""}시부터".ifBlank { "이후" }
                t.matches(Regex("(\\d{3}|VRB)\\d{2,3}(G\\d{2,3})?(KT|MPS|KMH)")) -> parts += decodeWind(t)
                t == "CAVOK" -> parts += "시정 양호(CAVOK)"
                t.matches(Regex("\\d{4}")) -> parts += "시정 ${formatVisibilityMeters(t.toInt())}"
                t.matches(Regex("(FEW|SCT|BKN|OVC)\\d{3}(CB|TCU)?")) -> {
                    val h = t.substring(3, 6).toInt() * 100
                    parts += "${cloudAmountKo(t.substring(0, 3))} ${h}ft"
                }
                t == "NSW" -> parts += "기상 현상 소멸"
                isWeatherToken(t) -> parts += decodeWeather(t)
                else -> parts += t
            }
        }
        return parts.joinToString(" ")
    }

    // --- 바람 ---

    private fun decodeWind(t: String): String {
        val unit = when {
            t.endsWith("KT") -> "노트"
            t.endsWith("MPS") -> "m/s"
            else -> "km/h"
        }
        val body = t.removeSuffix("KT").removeSuffix("MPS").removeSuffix("KMH")
        val dir = body.substring(0, 3)
        val rest = body.substring(3)
        val gustIdx = rest.indexOf('G')
        val speed = if (gustIdx >= 0) rest.substring(0, gustIdx) else rest
        val gust = if (gustIdx >= 0) rest.substring(gustIdx + 1) else null

        if (dir == "000" && speed.toIntOrNull() == 0) return "무풍"

        val dirText = if (dir == "VRB") "가변" else "${dir.toInt()}도(${compass(dir.toInt())})"
        val gustText = gust?.let { ", 순간최대 ${it.toInt()}$unit" } ?: ""
        return "$dirText ${speed.toInt()}$unit$gustText"
    }

    private fun compass(deg: Int): String {
        val dirs = listOf("북", "북북동", "북동", "동북동", "동", "동남동", "남동", "남남동",
            "남", "남남서", "남서", "서남서", "서", "서북서", "북서", "북북서")
        return dirs[(((deg % 360) + 11.25) / 22.5).toInt() % 16]
    }

    // --- 시정 ---

    private fun formatVisibilityMeters(m: Int): String = when {
        m >= 9999 -> "10km 이상"
        m >= 1000 -> "${"%.1f".format(m / 1000.0)}km"
        else -> "${m}m"
    }

    private fun statuteMilesToMeters(t: String): Int {
        val body = t.removeSuffix("SM").removePrefix("M")
        val value = if (body.contains("/")) {
            val (n, d) = body.split("/")
            (n.toDoubleOrNull() ?: 0.0) / (d.toDoubleOrNull() ?: 1.0)
        } else {
            body.toDoubleOrNull() ?: 0.0
        }
        return (value * 1609.34).roundToInt()
    }

    private fun formatVisibilityStatute(t: String, meters: Int): String {
        val prefix = if (t.startsWith("M")) "미만 " else ""
        return "$prefix${t.removePrefix("M").removeSuffix("SM")}마일 (약 ${formatVisibilityMeters(meters)})"
    }

    // --- 기온 ---

    private fun parseTemp(s: String): Int =
        if (s.startsWith("M")) -s.substring(1).toInt() else s.toInt()

    private fun humidityHint(t: Int, d: Int): String =
        if (t - d <= 2) " — 기온과 이슬점이 가까워 안개 가능" else ""

    // --- 기상 현상 ---

    private val DESCRIPTORS = mapOf(
        "MI" to "얕은", "BC" to "조각", "PR" to "부분", "DR" to "낮게 날리는",
        "BL" to "높게 날리는", "SH" to "소나기성", "TS" to "뇌전 동반", "FZ" to "결빙성"
    )
    private val PHENOMENA = mapOf(
        "DZ" to "이슬비", "RA" to "비", "SN" to "눈", "SG" to "싸락눈", "IC" to "얼음 결정",
        "PL" to "얼음 알갱이", "GR" to "우박", "GS" to "작은 우박", "UP" to "미상 강수",
        "BR" to "박무", "FG" to "안개", "FU" to "연기", "VA" to "화산재", "DU" to "먼지",
        "SA" to "모래", "HZ" to "연무", "PY" to "물보라",
        "PO" to "먼지 회오리", "SQ" to "스콜", "FC" to "깔때기 구름", "SS" to "모래폭풍", "DS" to "먼지폭풍"
    )

    private fun isWeatherToken(t: String): Boolean {
        val body = t.removePrefix("-").removePrefix("+").removePrefix("VC")
        if (body.isEmpty() || body.length % 2 != 0) return false
        return body.chunked(2).all { it in DESCRIPTORS || it in PHENOMENA }
    }

    private fun decodeWeather(t: String): String {
        val intensity = when {
            t.startsWith("-") -> "약한 "
            t.startsWith("+") -> "강한 "
            else -> ""
        }
        val vicinity = t.contains("VC")
        val body = t.removePrefix("-").removePrefix("+").removePrefix("VC")
        val parts = body.chunked(2).mapNotNull { DESCRIPTORS[it] ?: PHENOMENA[it] }
        val suffix = if (vicinity) " (공항 주변)" else ""
        return intensity + parts.joinToString(" ") + suffix
    }

    // --- 구름 ---

    private fun cloudAmountKo(a: String): String = when (a) {
        "FEW" -> "few 1~2/8"
        "SCT" -> "scattered 3~4/8"
        "BKN" -> "broken 5~7/8"
        "OVC" -> "overcast 8/8"
        else -> a
    }

    private fun cloudTypeKo(t: String): String = when (t) {
        "CB" -> " (적란운·주의)"
        "TCU" -> " (탑적운)"
        else -> ""
    }

    // --- 비행 규칙 구분 ---

    /**
     * 미국 FAA 기준(시정 statute mile, 운고 ft):
     *  LIFR < 1mi 또는 < 500ft / IFR < 3mi 또는 < 1000ft
     *  MVFR <= 5mi 또는 <= 3000ft / 그 외 VFR
     */
    private fun category(visMeters: Int?, ceilFt: Int?): FlightCategory {
        if (visMeters == null && ceilFt == null) return FlightCategory.UNKNOWN
        val visMi = visMeters?.let { it / 1609.34 } ?: Double.MAX_VALUE
        val ceil = ceilFt ?: Int.MAX_VALUE
        return when {
            visMi < 1 || ceil < 500 -> FlightCategory.LIFR
            visMi < 3 || ceil < 1000 -> FlightCategory.IFR
            visMi <= 5 || ceil <= 3000 -> FlightCategory.MVFR
            else -> FlightCategory.VFR
        }
    }
}
