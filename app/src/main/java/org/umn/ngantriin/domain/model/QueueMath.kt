package org.umn.ngantriin.domain.model

/**
 * The one place that turns a queue into the numbers the product is
 * about. Both the Supabase-backed repository and the demo backend call through
 * here, and every rule mirrors a SQL function in
 * 0012_check_in_window_and_table_ready.sql, so an estimate can never differ
 * depending on where the data came from.
 */
object QueueMath {

    /**
     * Groups in front of [ticketSequence] that have not been seated yet.
     * A CALLED or CHECKED_IN ticket still counts: that table is not free yet.
     * Mirrors `queue_position`.
     */
    fun peopleAhead(entries: List<QueueEntry>, ticketSequence: Int): Int =
        entries.count { it.ticketSequence < ticketSequence && it.status.isInLine }

    /** average service time x people ahead. */
    fun estimatedWaitMinutes(peopleAhead: Int, averageServiceMinutes: Int): Int =
        (peopleAhead.coerceAtLeast(0) * averageServiceMinutes.coerceAtLeast(1))

    /**
     * Groups the restaurant has not called yet — the "12 groups waiting" on a
     * restaurant card. Mirrors `refresh_queue_stats`.
     */
    fun waitingCount(entries: List<QueueEntry>): Int =
        entries.count { it.isWaitingToBeCalled }

    /** The ticket `call_next` would pick: lowest number not yet called. */
    fun nextToCall(entries: List<QueueEntry>): QueueEntry? =
        entries.filter { it.isWaitingToBeCalled }.minByOrNull { it.ticketSequence }

    /**
     * The number on the "NOW SERVING" board: the highest ticket the restaurant
     * has called and not finished, falling back to the last one it completed.
     */
    fun currentServing(entries: List<QueueEntry>): QueueEntry? =
        entries.filter {
            it.hasBeenCalled && (
                it.status == QueueStatus.CALLED ||
                    it.status == QueueStatus.CHECKED_IN ||
                    it.status == QueueStatus.TABLE_READY
                )
        }.maxByOrNull { it.ticketSequence }
            ?: entries.filter { it.status == QueueStatus.COMPLETED }
                .maxByOrNull { it.ticketSequence }

    /**
     * Notification thresholds. Returns the milestone a ticket has reached, or
     * null when it is still too far back to be worth a notification.
     * Mirrors `milestoneFor` in the queue-notifier Edge Function.
     */
    fun notificationMilestone(status: QueueStatus, peopleAhead: Int): NotificationType? = when (status) {
        QueueStatus.CALLED -> NotificationType.CALLED
        QueueStatus.CHECKED_IN -> NotificationType.CHECKED_IN
        QueueStatus.TABLE_READY -> NotificationType.TABLE_READY
        QueueStatus.COMPLETED -> NotificationType.COMPLETED
        QueueStatus.CANCELLED -> NotificationType.CANCELLED
        QueueStatus.EXPIRED -> NotificationType.EXPIRED
        QueueStatus.WAITING, QueueStatus.ALMOST_THERE -> when {
            peopleAhead <= RETURN_NOW_THRESHOLD -> NotificationType.RETURN_NOW
            peopleAhead <= ALMOST_THERE_THRESHOLD -> NotificationType.ALMOST_THERE
            else -> null
        }
    }

    /** At or below this many groups ahead the ticket becomes ALMOST_THERE. */
    const val ALMOST_THERE_THRESHOLD = 3

    /** At or below this, the customer is told to head back now. */
    const val RETURN_NOW_THRESHOLD = 1

    /** Default check-in window once called (restaurants.checkin_window_minutes). */
    const val DEFAULT_CHECK_IN_WINDOW_MINUTES = 10
}
