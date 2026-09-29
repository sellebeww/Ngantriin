package org.umn.ngantriin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.umn.ngantriin.R
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Localized equivalents of [org.umn.ngantriin.core.Formatters]. That object
 * stays English-only on purpose — FormattersTest.kt is a plain JVM unit test
 * with no Android resources available to it — so anything the UI actually
 * shows goes through here instead, where `stringResource` picks up whichever
 * language Settings -> Language selected (section 43).
 */

@Composable
fun distanceText(meters: Double?): String = when {
    meters == null -> stringResource(R.string.distance_unknown)
    meters < 1_000 -> stringResource(R.string.distance_meters, meters.roundToInt())
    else -> stringResource(R.string.distance_km, meters / 1_000)
}

/** "~18 min", "~1 hr 5 min", or "No wait" for an empty queue. */
@Composable
fun waitTimeText(minutes: Int): String = when {
    minutes <= 0 -> stringResource(R.string.wait_time_none)
    minutes < 60 -> stringResource(R.string.wait_time_minutes, minutes)
    minutes % 60 == 0 -> stringResource(R.string.wait_time_hours, minutes / 60)
    else -> stringResource(R.string.wait_time_hours_minutes, minutes / 60, minutes % 60)
}

/** Compact variant for dense cards: "18 min". */
@Composable
fun waitTimeShortText(minutes: Int): String = when {
    minutes <= 0 -> stringResource(R.string.wait_time_none)
    minutes < 60 -> stringResource(R.string.wait_time_minutes_short, minutes)
    else -> stringResource(R.string.wait_time_hours_minutes_short, minutes / 60, minutes % 60)
}

@Composable
fun groupsText(count: Int): String = pluralStringResource(R.plurals.groups_count, count, count)

/**
 * "21 Sep 2026", in whatever language Settings -> Language selected.
 *
 * Reads the locale through [LocalConfiguration] rather than
 * `Locale.getDefault()` directly: that keeps this composable subscribed to
 * configuration changes, so it recomposes correctly if the locale ever
 * changes without a full activity recreation.
 */
@Composable
fun dateText(instant: Instant): String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    return formatter.format(instant.atZone(ZoneId.systemDefault()))
}

/** "21 Sep 2026 • 14:30", in whatever language Settings -> Language selected. */
@Composable
fun dateTimeText(instant: Instant): String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy • HH:mm", locale)
    return formatter.format(instant.atZone(ZoneId.systemDefault()))
}

/** "just now" / "12 min ago" / "3 hr ago" / a plain date past a week. */
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

/** "Good morning" / "Good afternoon" / "Good evening" / "Good night". */
@Composable
fun greetingText(hour: Int): String = when (hour) {
    in 4..10 -> stringResource(R.string.greeting_morning)
    in 11..14 -> stringResource(R.string.greeting_afternoon)
    in 15..18 -> stringResource(R.string.greeting_evening)
    else -> stringResource(R.string.greeting_night)
}
