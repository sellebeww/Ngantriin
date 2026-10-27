package org.umn.ngantriin.core

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Format tampilan saja, supaya aturan angka ke teks tidak ada di layar. */
object Formatters {

    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    /** Lewat batas ini jarak persis tidak berguna lagi ("14004.2 km"). */
    const val FAR_DISTANCE_METERS = 100_000.0

    /** "850 m" di bawah satu km, "1.4 km" di atasnya, "100+ km" kalau jauh, "—" kalau tidak diketahui. */
    fun distance(meters: Double?): String = when {
        meters == null -> "—"
        meters < 1_000 -> "${meters.roundToInt()} m"
        meters >= FAR_DISTANCE_METERS -> "100+ km"
        else -> String.format(Locale.ENGLISH, "%.1f km", meters / 1_000)
    }

    /** "~18 mnt", "~1 jam 5 mnt", atau "Tanpa tunggu" untuk antrean kosong. */
    fun waitTime(minutes: Int): String = when {
        minutes <= 0 -> "No wait"
        minutes < 60 -> "~$minutes min"
        minutes % 60 == 0 -> "~${minutes / 60} hr"
        else -> "~${minutes / 60} hr ${minutes % 60} min"
    }

    fun groups(count: Int): String = if (count == 1) "1 group" else "$count groups"

    fun rating(value: Double): String = String.format(Locale.ENGLISH, "%.1f", value)

    fun date(instant: Instant): String =
        dateFormatter.format(instant.atZone(ZoneId.systemDefault()))

    fun time(instant: Instant): String =
        timeFormatter.format(instant.atZone(ZoneId.systemDefault()))

    /** "baru saja" / "12 mnt lalu" / "3 jam lalu" / "21 Sep 2026". */
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

    /** "Selamat pagi" / "Selamat siang" / "Selamat malam" untuk header beranda. */
    fun greeting(hour: Int): String = when (hour) {
        in 4..10 -> "Good morning"
        in 11..14 -> "Good afternoon"
        in 15..18 -> "Good evening"
        else -> "Good night"
    }
}
