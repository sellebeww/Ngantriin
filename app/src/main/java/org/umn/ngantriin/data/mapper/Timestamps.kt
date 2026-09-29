package org.umn.ngantriin.data.mapper

import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Postgres hands back `timestamptz` as `2026-09-21T10:30:00.123456+00:00`,
 * `...Z`, or occasionally without an offset at all. Rather than trusting one
 * parser, try the three shapes in order and fall back to [fallback] so a
 * single odd row can never crash a list screen.
 */
internal fun parseTimestamp(value: String?, fallback: Instant = Instant.EPOCH): Instant {
    if (value.isNullOrBlank()) return fallback
    return runCatching { OffsetDateTime.parse(value).toInstant() }
        .recoverCatching { Instant.parse(value) }
        .recoverCatching { LocalDateTime.parse(value).toInstant(ZoneOffset.UTC) }
        .getOrDefault(fallback)
}

internal fun parseTimestampOrNull(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    val parsed = parseTimestamp(value, Instant.EPOCH)
    return parsed.takeIf { it != Instant.EPOCH }
}

/** `"10:00:00"` or `"10:00"`. */
internal fun parseTime(value: String?, fallback: LocalTime): LocalTime {
    if (value.isNullOrBlank()) return fallback
    return runCatching { LocalTime.parse(value) }.getOrDefault(fallback)
}

internal fun Instant.toIsoString(): String = OffsetDateTime.ofInstant(this, ZoneOffset.UTC).toString()
