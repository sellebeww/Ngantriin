package org.umn.ngantriin.domain.model

import java.time.Duration
import java.time.Instant

/** One customer group's ticket for one visit. */
data class QueueEntry(
    val id: String,
    val restaurantId: String,
    val userId: String,
    /** Human-facing label, e.g. "A-027". Always allocated by the backend. */
    val queueNumber: String,
    /** Monotonic per-restaurant counter behind [queueNumber]; drives ordering. */
    val ticketSequence: Int,
    val partySize: Int,
    val status: QueueStatus,
    val note: String? = null,
    val joinedAt: Instant,
    val calledAt: Instant? = null,
    val checkedInAt: Instant? = null,
    val completedAt: Instant? = null,
    val cancelledAt: Instant? = null,
    /** Storage path of the check-in verification photo, set once CHECKED_IN. */
    val checkInPhotoUrl: String? = null
) {
    val isActive: Boolean get() = status.isActive

    /** When the ticket reached its terminal state, if it has. */
    val finishedAt: Instant? get() = completedAt ?: cancelledAt

    /**
     * How long the customer actually waited — joined until called, or until
     * the ticket ended if it was never called.
     */
    val waitedMinutes: Int?
        get() {
            val end = calledAt ?: finishedAt ?: return null
            return Duration.between(joinedAt, end).toMinutes().toInt().coerceAtLeast(0)
        }
}

/**
 * Everything the Active Queue screen needs in one object, so the UI never has
 * to join three flows together itself.
 */
data class ActiveQueue(
    val entry: QueueEntry,
    val restaurant: Restaurant,
    val stats: QueueStats,
    /** Section 23: groups still in front of this ticket. */
    val peopleAhead: Int,
    val estimatedWaitMinutes: Int
) {
    /** 1-based place in line — "#7" when six groups are ahead. */
    val position: Int get() = peopleAhead + 1

    val queueNumber: String get() = entry.queueNumber
    val status: QueueStatus get() = entry.status

    /** Section 17: the check-in CTA only appears once the ticket is called. */
    val canCheckIn: Boolean get() = entry.status == QueueStatus.CALLED

    /** Section 18. */
    val canLeave: Boolean
        get() = entry.status == QueueStatus.WAITING ||
            entry.status == QueueStatus.ALMOST_THERE ||
            entry.status == QueueStatus.CALLED
}

/** A past ticket, as rendered by the history screen (section 19). */
data class QueueHistoryItem(
    val entry: QueueEntry,
    val restaurantName: String,
    val restaurantImageUrl: String?,
    val hasReview: Boolean
)
