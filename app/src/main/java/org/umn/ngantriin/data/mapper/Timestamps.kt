package org.umn.ngantriin.data.mapper

import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Postgres mengirim `timestamptz` sebagai `2026-09-21T10:30:00.123456+00:00`,
 * `...Z`, atau kadang tanpa offset sama sekali. Daripada percaya satu parser,
 * ketiga bentuk dicoba berurutan lalu jatuh ke [fallback], supaya satu baris
 * aneh tidak bisa membuat layar daftar crash.
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

