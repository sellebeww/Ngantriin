package org.umn.ngantriin.core

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Presentation-only formatting. Keeps number-to-string rules out of screens. */
object Formatters {

    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy • HH:mm", Locale.ENGLISH)

    /** "850 m" below a kilometre, "1.4 km" above it, "—" when unknown. */
    fun distance(meters: Double?): String = when {
        meters == null -> "—"
        meters < 1_000 -> "${meters.roundToInt()} m"
        else -> String.format(Locale.ENGLISH, "%.1f km", meters / 1_000)
    }

    /** "~18 min", "~1 hr 5 min", or "No wait" for an empty queue. */
    fun waitTime(minutes: Int): String = when {
        minutes <= 0 -> "No wait"
        minutes < 60 -> "~$minutes min"
        minutes % 60 == 0 -> "~${minutes / 60} hr"
        else -> "~${minutes / 60} hr ${minutes % 60} min"
    }

    /** Compact variant for dense cards: "18 min". */
    fun waitTimeShort(minutes: Int): String = when {
        minutes <= 0 -> "No wait"
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }

    fun groups(count: Int): String = if (count == 1) "1 group" else "$count groups"

    fun rating(value: Double): String = String.format(Locale.ENGLISH, "%.1f", value)

    fun date(instant: Instant): String =
        dateFormatter.format(instant.atZone(ZoneId.systemDefault()))

    fun time(instant: Instant): String =
        timeFormatter.format(instant.atZone(ZoneId.systemDefault()))

    fun dateTime(instant: Instant): String =
        dateTimeFormatter.format(instant.atZone(ZoneId.systemDefault()))

    /** "just now" / "12 min ago" / "3 hr ago" / "21 Sep 2026". */
    fun relative(instant: Instant, now: Instant = Instant.now()): String {
        val minutes = Duration.between(instant, now).toMinutes()
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 60 * 24 -> "${minutes / 60} hr ago"
            minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)} d ago"
            else -> date(instant)
        }
    }

    /** "Good morning" / "Good afternoon" / "Good evening" for the home header. */
    fun greeting(hour: Int): String = when (hour) {
        in 4..10 -> "Good morning"
        in 11..14 -> "Good afternoon"
        in 15..18 -> "Good evening"
        else -> "Good night"
    }
}
