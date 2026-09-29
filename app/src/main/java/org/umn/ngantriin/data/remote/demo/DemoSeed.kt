package org.umn.ngantriin.data.remote.demo

import org.umn.ngantriin.domain.model.QueueEntry
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.domain.model.Restaurant
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Sample data for demo mode (sections 39 and 40). Deliberately identical to
 * supabase/migrations/0004_seed.sql — same ids, same coordinates — so moving
 * to the real backend does not change what you see on screen.
 */
object DemoSeed {

    const val WAROENG_ID = "11111111-1111-4111-8111-111111111111"

    val RESTAURANTS: List<Restaurant> = listOf(
        Restaurant(
            id = WAROENG_ID,
            name = "Waroeng Nusantara",
            description = "Masakan rumahan Indonesia dengan menu sambal harian dan nasi liwet.",
            category = "Indonesian",
            address = "Ruko Sentra Gading Serpong, Jl. Boulevard Raya, Tangerang",
            latitude = -6.238800, longitude = 106.626500,
            imageUrl = "https://images.unsplash.com/photo-1555126634-323283e090fa?w=800",
            rating = 4.7, ratingCount = 128,
            openingTime = LocalTime.of(10, 0), closingTime = LocalTime.of(22, 0),
            isOpen = true, averageServiceMinutes = 3,
            queuePrefix = "A", queueCapacity = 50, checkInRadiusMeters = 150
        ),
        Restaurant(
            id = "22222222-2222-4222-8222-222222222222",
            name = "Kopi Sempurna",
            description = "Specialty coffee bar, manual brew, dan pastry yang dipanggang tiap pagi.",
            category = "Coffee",
            address = "Jl. Kelapa Gading Barat No. 8, Gading Serpong",
            latitude = -6.243100, longitude = 106.630900,
            imageUrl = "https://images.unsplash.com/photo-1554118811-1e0d58224f24?w=800",
            rating = 4.5, ratingCount = 86,
            openingTime = LocalTime.of(7, 0), closingTime = LocalTime.of(21, 0),
            isOpen = true, averageServiceMinutes = 2,
            queuePrefix = "B", queueCapacity = 40, checkInRadiusMeters = 120,
            availableSeats = 4
        ),
        Restaurant(
            id = "33333333-3333-4333-8333-333333333333",
            name = "Sakura Tei",
            description = "Omakase sushi dan ramen dengan kaldu tonkotsu 18 jam.",
            category = "Japanese",
            address = "Paramount Plaza, Jl. Gading Serpong Boulevard, Tangerang",
            latitude = -6.232400, longitude = 106.620700,
            imageUrl = "https://images.unsplash.com/photo-1579871494447-9811cf80d66c?w=800",
            rating = 4.8, ratingCount = 211,
            openingTime = LocalTime.of(11, 0), closingTime = LocalTime.of(22, 0),
            isOpen = true, averageServiceMinutes = 4,
            queuePrefix = "S", queueCapacity = 60, checkInRadiusMeters = 150
        ),
        Restaurant(
            id = "44444444-4444-4444-8444-444444444444",
            name = "Seoul Bunsik",
            description = "Korean street food: tteokbokki, corn dog, dan Korean fried chicken.",
            category = "Korean",
            address = "Ruko Pasar Modern Paramount, Gading Serpong",
            latitude = -6.246900, longitude = 106.624200,
            imageUrl = "https://images.unsplash.com/photo-1590301157890-4810ed352733?w=800",
            rating = 4.4, ratingCount = 64,
            openingTime = LocalTime.of(11, 0), closingTime = LocalTime.of(21, 30),
            isOpen = true, averageServiceMinutes = 3,
            queuePrefix = "K", queueCapacity = 45, checkInRadiusMeters = 130,
            availableSeats = 2
        ),
        Restaurant(
            id = "55555555-5555-4555-8555-555555555555",
            name = "Bakmi Pelita",
            description = "Bakmi ayam jamur legendaris, buka sejak 1998.",
            category = "Chinese",
            address = "Jl. Raya Kelapa Dua No. 21, Tangerang",
            latitude = -6.251300, longitude = 106.617800,
            imageUrl = "https://images.unsplash.com/photo-1569718212165-3a8278d5f624?w=800",
            rating = 4.6, ratingCount = 152,
            openingTime = LocalTime.of(8, 0), closingTime = LocalTime.of(20, 0),
            // Closed on purpose, so the "restaurant is closed" path (28.1) is
            // reachable without waiting for opening hours.
            isOpen = false, averageServiceMinutes = 2,
            queuePrefix = "P", queueCapacity = 40, checkInRadiusMeters = 120
        ),
        Restaurant(
            id = "66666666-6666-4666-8666-666666666666",
            name = "Taco Libre",
            description = "Mexican street tacos, quesadilla, dan agua fresca.",
            category = "Mexican",
            address = "Scientia Square Park, Gading Serpong",
            latitude = -6.256700, longitude = 106.618900,
            imageUrl = "https://images.unsplash.com/photo-1565299585323-38d6b0865b47?w=800",
            rating = 4.2, ratingCount = 41,
            openingTime = LocalTime.of(11, 0), closingTime = LocalTime.of(22, 0),
            isOpen = true, averageServiceMinutes = 3,
            queuePrefix = "T", queueCapacity = 35, checkInRadiusMeters = 150
        )
    )

    /** Groups already in line when the demo starts, per restaurant. */
    private val WAITING_GROUPS = mapOf(
        WAROENG_ID to 6,
        "22222222-2222-4222-8222-222222222222" to 4,
        "33333333-3333-4333-8333-333333333333" to 18,
        "44444444-4444-4444-8444-444444444444" to 7,
        "55555555-5555-4555-8555-555555555555" to 0,
        "66666666-6666-4666-8666-666666666666" to 2
    )

    /** How many tickets the venue has already worked through today. */
    private val SERVED_TODAY = mapOf(
        WAROENG_ID to 20,
        "22222222-2222-4222-8222-222222222222" to 12,
        "33333333-3333-4333-8333-333333333333" to 31,
        "44444444-4444-4444-8444-444444444444" to 9,
        "55555555-5555-4555-8555-555555555555" to 0,
        "66666666-6666-4666-8666-666666666666" to 5
    )

    /**
     * Builds the starting queue for one venue.
     *
     * Waroeng Nusantara lands exactly on the worked example in section 40:
     * A-020 is at the table, A-021 to A-026 are still in line, so the first
     * ticket you take is A-027 with 6 groups ahead and a ~18 minute estimate.
     */
    fun seedQueue(restaurant: Restaurant): List<QueueEntry> {
        val served = SERVED_TODAY[restaurant.id] ?: 0
        val waiting = WAITING_GROUPS[restaurant.id] ?: 0
        if (served == 0 && waiting == 0) return emptyList()

        val now = Instant.now()
        val entries = mutableListOf<QueueEntry>()

        for (sequence in 1..served) {
            val isAtTheTable = sequence == served
            entries += demoEntry(
                restaurant = restaurant,
                sequence = sequence,
                status = if (isAtTheTable) QueueStatus.CHECKED_IN else QueueStatus.COMPLETED,
                joinedAt = now.minus((served - sequence + waiting + 1) * 4L, ChronoUnit.MINUTES)
            )
        }

        for (offset in 1..waiting) {
            val sequence = served + offset
            entries += demoEntry(
                restaurant = restaurant,
                sequence = sequence,
                status = QueueStatus.WAITING,
                joinedAt = now.minus((waiting - offset + 1) * 4L, ChronoUnit.MINUTES)
            )
        }

        return entries
    }

    private fun demoEntry(
        restaurant: Restaurant,
        sequence: Int,
        status: QueueStatus,
        joinedAt: Instant
    ): QueueEntry {
        val number = "${restaurant.queuePrefix}-${sequence.toString().padStart(3, '0')}"
        return QueueEntry(
            // Deterministic ids keep re-seeding idempotent.
            id = "demo-${restaurant.id.take(8)}-$sequence",
            restaurantId = restaurant.id,
            userId = "demo-guest-${restaurant.id.take(4)}-$sequence",
            queueNumber = number,
            ticketSequence = sequence,
            partySize = 1 + (sequence % 3),
            status = status,
            note = null,
            joinedAt = joinedAt,
            calledAt = if (status != QueueStatus.WAITING) joinedAt.plus(8, ChronoUnit.MINUTES) else null,
            checkedInAt = if (status == QueueStatus.CHECKED_IN || status == QueueStatus.COMPLETED) {
                joinedAt.plus(10, ChronoUnit.MINUTES)
            } else {
                null
            },
            completedAt = if (status == QueueStatus.COMPLETED) {
                joinedAt.plus(25, ChronoUnit.MINUTES)
            } else {
                null
            },
            cancelledAt = null
        )
    }
}
