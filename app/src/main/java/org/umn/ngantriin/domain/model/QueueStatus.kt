package org.umn.ngantriin.domain.model

/**
 * The queue state machine (section 14). These names match the `queue_status`
 * Postgres enum one-for-one — that is the whole point of having an enum here
 * rather than passing raw strings around.
 *
 *   WAITING -> ALMOST_THERE -> CALLED -> CHECKED_IN -> COMPLETED
 *   WAITING | ALMOST_THERE | CALLED -> CANCELLED
 */
enum class QueueStatus {
    /** In line, far enough from the front that the customer can be elsewhere. */
    WAITING,

    /** Within the notification threshold — time to head back. */
    ALMOST_THERE,

    /** The restaurant has called this ticket. */
    CALLED,

    /** Customer returned and passed the GPS check-in. */
    CHECKED_IN,

    /** Served. Terminal, and the only state that unlocks a review. */
    COMPLETED,

    /** Left the queue, or the restaurant dropped the ticket. Terminal. */
    CANCELLED;

    /** True while the ticket still occupies a place in line. */
    val isActive: Boolean
        get() = this == WAITING || this == ALMOST_THERE || this == CALLED || this == CHECKED_IN

    /** Terminal states never transition again (section 18). */
    val isTerminal: Boolean
        get() = this == COMPLETED || this == CANCELLED

    /** True once the customer must be physically at the restaurant. */
    val requiresPresence: Boolean
        get() = this == CALLED || this == CHECKED_IN

    /**
     * Mirrors `is_valid_queue_transition` in 0002_queue_functions.sql. Keeping
     * the rule in both places means an illegal move is rejected before it
     * leaves the phone *and* if it somehow reaches the database.
     */
    fun canTransitionTo(next: QueueStatus): Boolean = when (this) {
        WAITING -> next == ALMOST_THERE || next == CALLED || next == CANCELLED
        ALMOST_THERE -> next == CALLED || next == CANCELLED
        CALLED -> next == CHECKED_IN || next == COMPLETED || next == CANCELLED
        CHECKED_IN -> next == COMPLETED
        COMPLETED, CANCELLED -> false
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
    }
}
