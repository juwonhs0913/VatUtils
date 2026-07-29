package com.vatradar.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * VATSIM은 ISO-8601 UTC 문자열(예: "2026-07-29T10:00:00.0000000Z")을 내려줍니다.
 * minSdk 26이라 java.time을 desugaring 없이 그대로 쓸 수 있습니다.
 */
private val KST: ZoneId = ZoneId.of("Asia/Seoul")
private val DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val TIME_ONLY = DateTimeFormatter.ofPattern("HH:mm")
private val FULL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/** ISO-8601 UTC 문자열을 "yyyy-MM-dd HH:mm:ss KST"로 변환합니다. 파싱 실패 시 원문을 그대로 돌려줍니다. */
fun String.toKstDisplay(): String = try {
    "${FULL.format(Instant.parse(this).atZone(KST))} KST"
} catch (e: DateTimeParseException) {
    this
}

/** ISO-8601 → epoch millis. 실패 시 0. */
fun String.toEpochMillis(): Long = try {
    Instant.parse(this).toEpochMilli()
} catch (e: DateTimeParseException) {
    0L
}

/** epoch millis → "MM-dd HH:mm" (KST) */
fun Long.toKstDateTime(): String =
    if (this <= 0L) "—" else DATE_TIME.format(Instant.ofEpochMilli(this).atZone(KST))

/** epoch millis → "HH:mm" (KST) */
fun Long.toKstTime(): String =
    if (this <= 0L) "—" else TIME_ONLY.format(Instant.ofEpochMilli(this).atZone(KST))

/**
 * 비행계획의 HHMM 문자열("1430")을 "14:30Z"로 표기합니다.
 *
 * 비행계획 시각은 항상 Zulu(UTC)로 제출되고 조종사도 그 기준으로 움직이므로
 * 현지 시간으로 바꾸지 않고 Z 표기를 그대로 씁니다.
 * 값이 비었거나 형식이 어긋나면 null을 돌려줍니다.
 */
fun formatZuluHhmm(raw: String?): String? {
    val digits = raw?.filter { it.isDigit() } ?: return null
    if (digits.length != 4) return null
    val hour = digits.substring(0, 2).toIntOrNull() ?: return null
    val minute = digits.substring(2, 4).toIntOrNull() ?: return null
    if (hour > 23 || minute > 59) return null
    return "%02d:%02dZ".format(hour, minute)
}

/** HHMM 소요시간("0530")을 분 단위로 바꿉니다. */
fun hhmmToMinutes(raw: String?): Int? {
    val digits = raw?.filter { it.isDigit() } ?: return null
    if (digits.length != 4) return null
    val hour = digits.substring(0, 2).toIntOrNull() ?: return null
    val minute = digits.substring(2, 4).toIntOrNull() ?: return null
    if (minute > 59) return null
    return hour * 60 + minute
}

/** 현재 UTC 시각에서 [minutesFromNow]분 뒤를 "14:30Z"로 표기합니다. */
fun zuluAfterMinutes(minutesFromNow: Int): String =
    TIME_ONLY.format(Instant.now().plusSeconds(minutesFromNow * 60L).atZone(ZoneId.of("UTC"))) + "Z"

/** 비행계획상 출발시각 + 소요시간으로 도착 예정시각을 계산합니다. */
fun plannedArrivalZulu(departureHhmm: String?, enrouteHhmm: String?): String? {
    val depMinutes = hhmmToMinutes(departureHhmm) ?: return null
    val enrouteMinutes = hhmmToMinutes(enrouteHhmm) ?: return null
    val total = (depMinutes + enrouteMinutes) % (24 * 60)
    return "%02d:%02dZ".format(total / 60, total % 60)
}

/** 이벤트 기간을 "07-29 24:00 ~ 26:00 KST" 형태로 압축 표기합니다. */
fun formatEventPeriod(startMillis: Long, endMillis: Long): String {
    if (startMillis <= 0L) return "—"
    val start = startMillis.toKstDateTime()
    val end = if (endMillis > 0L) endMillis.toKstTime() else "—"
    return "$start ~ $end KST"
}
