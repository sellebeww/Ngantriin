package org.umn.ngantriin.domain.model

/**
 * The queue state machine (section 14). These names match the `queue_status`
 * Postgres enum one-for-one — that is the whole point of having an enum here
 * rather than passing raw strings around.
 *
 *   WAITING -> ALMOST_THERE -> CALLED -> CHECKED_IN -> TABLE_READY -> COMPLETED
 *
 *   - WAITING / ALMOST_THERE may check in early (already at the restaurant).
 *   - CALLED that misses its check-in window becomes EXPIRED.
 *   - Any live state may be CANCELLED (customer leaves, or staff removes).
 */
enum class QueueStatus {
    /** In line, far enough from the front that the customer can be elsewhere. */
    WAITING,

    /** Within the notification threshold — time to head back. */
    ALMOST_THERE,

    /** It's this ticket's turn; the check-in window is running. */
    CALLED,

    /** Customer is at the restaurant (photo verified), table being prepared. */
    CHECKED_IN,

    /** Table is set; the customer goes to the host to be seated. */
    TABLE_READY,

    /** Served. Terminal, and the only state that unlocks a review. */
    COMPLETED,

    /** Left the queue, or the restaurant dropped the ticket. Terminal. */
    CANCELLED,

    /** Called, but never checked in within the window. Terminal. */
    EXPIRED;

    /** True while the customer still holds a ticket. */
    val isActive: Boolean
        get() = this == WAITING || this == ALMOST_THERE || this == CALLED ||
            this == CHECKED_IN || this == TABLE_READY

    /** Still holds a place in line in front of later tickets. */
    val isInLine: Boolean
        get() = this == WAITING || this == ALMOST_THERE || this == CALLED || this == CHECKED_IN

    /** The customer may check in from here (early, or once called). */
    val allowsCheckIn: Boolean
        get() = this == WAITING || this == ALMOST_THERE || this == CALLED

    /** The customer is physically at the restaurant. */
    val isAtRestaurant: Boolean
        get() = this == CHECKED_IN || this == TABLE_READY

    /**
     * Mirrors `is_valid_queue_transition` in 0012_check_in_window_and_table_ready.sql.
     * Keeping the rule in both places means an illegal move is rejected before
     * it leaves the phone *and* if it somehow reaches the database.
     */
    fun canTransitionTo(next: QueueStatus): Boolean = when (this) {
        WAITING -> next == ALMOST_THERE || next == CALLED || next == CHECKED_IN || next == CANCELLED
        ALMOST_THERE -> next == CALLED || next == CHECKED_IN || next == CANCELLED
        CALLED -> next == CHECKED_IN || next == CANCELLED || next == EXPIRED
        CHECKED_IN -> next == TABLE_READY || next == CANCELLED
        TABLE_READY -> next == COMPLETED || next == CANCELLED
        COMPLETED, CANCELLED, EXPIRED -> false
    }

    /** Wire name. Identical to [name]; spelled out so callers never guess. */
    val wireValue: String get() = name

    companion object {
        /**
         * Decodes a status coming from the backend or a push payload. Unknown
         * values fall back to [WAITING] rather than crashing the screen — an
         * unrecognised state still means "you are in line".
         */
        fun fromWire(value: String?): QueueStatus =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: WAITING

        /** Statuses that still hold a ticket, as a list for queries. */
        val ACTIVE: List<QueueStatus> = entries.filter { it.isActive }
    }
}
