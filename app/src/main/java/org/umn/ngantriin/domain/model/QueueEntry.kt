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
    val checkInPhotoUrl: String? = null,
    /** Set when CALLED: check in before this or the ticket EXPIRES. */
    val checkInDeadline: Instant? = null,
    val tableReadyAt: Instant? = null,
    val expiredAt: Instant? = null
) {
    val isActive: Boolean get() = status.isActive

    /** The restaurant has reached this ticket at least once. */
    val hasBeenCalled: Boolean get() = calledAt != null

    /**
     * Still waiting for the restaurant to reach it — including a customer who
     * checked in early and is sitting in the waiting area.
     */
    val isWaitingToBeCalled: Boolean
        get() = status == QueueStatus.WAITING || status == QueueStatus.ALMOST_THERE ||
            (status == QueueStatus.CHECKED_IN && !hasBeenCalled)

    /** Checked in before the restaurant called the ticket. */
    val checkedInEarly: Boolean
        get() = status == QueueStatus.CHECKED_IN && !hasBeenCalled

    /** When the ticket reached its terminal state, if it has. */
    val finishedAt: Instant? get() = completedAt ?: cancelledAt ?: expiredAt

    /** Seconds left in the check-in window, or null when no window is running. */
    fun checkInSecondsLeft(now: Instant = Instant.now()): Long? {
        if (status != QueueStatus.CALLED) return null
        val deadline = checkInDeadline ?: return null
        return Duration.between(now, deadline).seconds.coerceAtLeast(0)
    }

    /** True when a CALLED ticket has run out its window (expiry may lag). */
    fun isCheckInOverdue(now: Instant = Instant.now()): Boolean =
        checkInSecondsLeft(now) == 0L

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
    /** Groups still in front of this ticket. */
    val peopleAhead: Int,
    val estimatedWaitMinutes: Int
) {
    /** 1-based place in line — "#7" when six groups are ahead. */
    val position: Int get() = peopleAhead + 1

    val queueNumber: String get() = entry.queueNumber
    val status: QueueStatus get() = entry.status

    /**
     * The customer can check in as soon as they're at the
     * restaurant — early, or once called and still inside the window.
     */
    val canCheckIn: Boolean
        get() = entry.status.allowsCheckIn && !entry.isCheckInOverdue()

    /** It's this ticket's turn and the clock is running. */
    val isCheckInCountdown: Boolean get() = entry.status == QueueStatus.CALLED

    /** Any live ticket can be given up. */
    val canLeave: Boolean get() = entry.status.isActive

    /** Position and wait only mean something until the restaurant calls you. */
    val showsLinePosition: Boolean get() = entry.isWaitingToBeCalled
}

/** A past ticket, as rendered by the history screen. */
data class QueueHistoryItem(
    val entry: QueueEntry,
    val restaurantName: String,
    val restaurantImageUrl: String?,
    val hasReview: Boolean
)
