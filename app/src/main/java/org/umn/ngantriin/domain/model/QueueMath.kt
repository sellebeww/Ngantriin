package org.umn.ngantriin.domain.model

/**
 * Section 23. The one place that turns a queue into the three numbers the
 * product is about. Both the Supabase-backed repository and the demo backend
 * call through here, so an estimate can never differ depending on where the
 * data came from.
 */
object QueueMath {

    /**
     * Groups in front of [ticketSequence]. A CALLED ticket still counts: that
     * table is not free yet.
     */
    fun peopleAhead(entries: List<QueueEntry>, ticketSequence: Int): Int =
        entries.count {
            it.ticketSequence < ticketSequence &&
                it.status in OCCUPYING_STATUSES
        }

    /** average service time x people ahead, clamped to a whole minute. */
    fun estimatedWaitMinutes(peopleAhead: Int, averageServiceMinutes: Int): Int =
        (peopleAhead.coerceAtLeast(0) * averageServiceMinutes.coerceAtLeast(1))

    /** Groups still queueing, i.e. the "12 groups waiting" on a restaurant card. */
    fun waitingCount(entries: List<QueueEntry>): Int =
        entries.count { it.status == QueueStatus.WAITING || it.status == QueueStatus.ALMOST_THERE }

    /**
     * The number on the "NOW SERVING" board: the highest ticket the restaurant
     * has already called, falling back to the last one it completed.
     */
    fun currentServing(entries: List<QueueEntry>): QueueEntry? =
        entries.filter { it.status == QueueStatus.CALLED || it.status == QueueStatus.CHECKED_IN }
            .maxByOrNull { it.ticketSequence }
            ?: entries.filter { it.status == QueueStatus.COMPLETED }
                .maxByOrNull { it.ticketSequence }

    /**
     * Section 37 thresholds. Returns the milestone a ticket has reached, or
     * null when it is still too far back to be worth a notification.
     */
    fun notificationMilestone(status: QueueStatus, peopleAhead: Int): NotificationType? = when {
        status == QueueStatus.CALLED -> NotificationType.CALLED
        status == QueueStatus.CHECKED_IN -> NotificationType.CHECKED_IN
        status == QueueStatus.COMPLETED -> NotificationType.COMPLETED
        !status.isActive -> null
        peopleAhead <= RETURN_NOW_THRESHOLD -> NotificationType.RETURN_NOW
        peopleAhead <= ALMOST_THERE_THRESHOLD -> NotificationType.ALMOST_THERE
        else -> null
    }

    /** At or below this many groups ahead the ticket becomes ALMOST_THERE. */
    const val ALMOST_THERE_THRESHOLD = 3

    /** At or below this, the customer is told to head back now. */
    const val RETURN_NOW_THRESHOLD = 1

    private val OCCUPYING_STATUSES = setOf(
        QueueStatus.WAITING,
        QueueStatus.ALMOST_THERE,
        QueueStatus.CALLED
    )
}
