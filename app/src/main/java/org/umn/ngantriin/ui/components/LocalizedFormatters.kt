package org.umn.ngantriin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.umn.ngantriin.R
import org.umn.ngantriin.core.Formatters
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Padanan [org.umn.ngantriin.core.Formatters] yang sudah dilokalkan. Object itu
 * sengaja tetap hanya berbahasa Inggris, karena FormattersTest.kt adalah unit
 * test JVM biasa tanpa resource Android. Jadi apa pun yang benar-benar
 * ditampilkan UI lewat sini, tempat `stringResource` mengikuti bahasa yang
 * dipilih di Pengaturan -> Bahasa.
 */

@Composable
fun distanceText(meters: Double?): String = when {
    meters == null -> stringResource(R.string.distance_unknown)
    meters < 1_000 -> stringResource(R.string.distance_meters, meters.roundToInt())
    // Lokasi di benua lain (atau lokasi palsu) bukan angka yang berguna.
    meters >= Formatters.FAR_DISTANCE_METERS -> stringResource(R.string.distance_far)
    else -> stringResource(R.string.distance_km, meters / 1_000)
}

/** "~18 mnt", "~1 jam 5 mnt", atau "Tanpa tunggu" untuk antrean kosong. */
@Composable
fun waitTimeText(minutes: Int): String = when {
    minutes <= 0 -> stringResource(R.string.wait_time_none)
    minutes < 60 -> stringResource(R.string.wait_time_minutes, minutes)
    minutes % 60 == 0 -> stringResource(R.string.wait_time_hours, minutes / 60)
    else -> stringResource(R.string.wait_time_hours_minutes, minutes / 60, minutes % 60)
}

/** Versi ringkas untuk kartu padat: "18 mnt". */
@Composable
fun waitTimeShortText(minutes: Int): String = when {
    minutes <= 0 -> stringResource(R.string.wait_time_none)
    minutes < 60 -> stringResource(R.string.wait_time_minutes_short, minutes)
    else -> stringResource(R.string.wait_time_hours_minutes_short, minutes / 60, minutes % 60)
}

@Composable
fun groupsText(count: Int): String = pluralStringResource(R.plurals.groups_count, count, count)

/**
 * "21 Sep 2026", dalam bahasa yang dipilih di Pengaturan -> Bahasa.
 *
 * Locale dibaca lewat [LocalConfiguration], bukan langsung dari
 * `Locale.getDefault()`: dengan begitu composable ini tetap berlangganan
 * perubahan konfigurasi dan recompose dengan benar kalau locale berubah
 * tanpa activity dibuat ulang.
 */
@Composable
fun dateText(instant: Instant): String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    return formatter.format(instant.atZone(ZoneId.systemDefault()))
}

/** "21 Sep 2026 • 14:30", dalam bahasa yang dipilih di Pengaturan -> Bahasa. */
@Composable
fun dateTimeText(instant: Instant): String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy • HH:mm", locale)
    return formatter.format(instant.atZone(ZoneId.systemDefault()))
}

/** "baru saja" / "12 mnt lalu" / "3 jam lalu" / tanggal biasa lewat seminggu. */
@Composable
fun relativeText(instant: Instant, now: Instant = Instant.now()): String {
    val minutes = Duration.between(instant, now).toMinutes()
    return when {
        minutes < 1 -> stringResource(R.string.relative_just_now)
        minutes < 60 -> pluralStringResource(R.plurals.relative_minutes_ago, minutes.toInt(), minutes.toInt())
        minutes < 60 * 24 -> {
            val hours = (minutes / 60).toInt()
            pluralStringResource(R.plurals.relative_hours_ago, hours, hours)
        }
        minutes < 60 * 24 * 7 -> {
            val days = (minutes / (60 * 24)).toInt()
            pluralStringResource(R.plurals.relative_days_ago, days, days)
        }
        else -> dateText(instant)
    }
}

/** "Selamat pagi" / "Selamat siang" / "Selamat sore" / "Selamat malam". */
@Composable
fun greetingText(hour: Int): String = when (hour) {
    in 4..10 -> stringResource(R.string.greeting_morning)
    in 11..14 -> stringResource(R.string.greeting_afternoon)
    in 15..18 -> stringResource(R.string.greeting_evening)
    else -> stringResource(R.string.greeting_night)
}
