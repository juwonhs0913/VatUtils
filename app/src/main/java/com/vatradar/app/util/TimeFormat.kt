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

/** 이벤트 기간을 "07-29 24:00 ~ 26:00 KST" 형태로 압축 표기합니다. */
fun formatEventPeriod(startMillis: Long, endMillis: Long): String {
    if (startMillis <= 0L) return "—"
    val start = startMillis.toKstDateTime()
    val end = if (endMillis > 0L) endMillis.toKstTime() else "—"
    return "$start ~ $end KST"
}
