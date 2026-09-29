package org.umn.ngantriin.domain.model

import java.time.LocalTime

data class Restaurant(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val imageUrl: String?,
    val rating: Double,
    val ratingCount: Int,
    val openingTime: LocalTime,
    val closingTime: LocalTime,
    val isOpen: Boolean,
    /** Section 23: the multiplier behind every wait estimate. */
    val averageServiceMinutes: Int,
    val queuePrefix: String,
    val queueCapacity: Int,
    /** Section 16: how close a customer must be to check in. */
    val checkInRadiusMeters: Int,
    /** Section 44: open tables right now — a walk-in, not a queue. */
    val availableSeats: Int = 0
) {
    val location: GeoPoint get() = GeoPoint(latitude, longitude)

    /** Section 44: seats are open, so there's nothing to queue for yet. */
    val hasAvailableSeats: Boolean get() = availableSeats > 0

    /** "10:00 - 22:00" */
    val operatingHours: String
        get() = "%02d:%02d - %02d:%02d".format(
            openingTime.hour, openingTime.minute, closingTime.hour, closingTime.minute
        )

    /**
     * `is_open` is the manual switch the restaurant controls; the clock is the
     * schedule. A venue counts as open only when both agree, so staff can shut
     * the queue early without editing their opening hours.
     */
    fun isAcceptingQueue(now: LocalTime = LocalTime.now()): Boolean {
        if (!isOpen) return false
        return if (closingTime > openingTime) {
            now >= openingTime && now < closingTime
        } else {
            // Past-midnight closing, e.g. 18:00 - 02:00.
            now >= openingTime || now < closingTime
        }
    }
}

/**
 * A restaurant as it appears in a list: the venue itself plus the two facts
 * that make this app worth opening — how long the line is and how far away it
 * is. Distance is null until a location fix is available (section 38).
 */
data class RestaurantListing(
    val restaurant: Restaurant,
    val stats: QueueStats?,
    val distanceMeters: Double?
) {
    val id: String get() = restaurant.id
    val waitingCount: Int get() = stats?.waitingCount ?: 0
    val estimatedWaitMinutes: Int
        get() = stats?.estimatedWaitMinutes ?: (waitingCount * restaurant.averageServiceMinutes)
}
