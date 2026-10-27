package org.umn.ngantriin.notification

import android.content.Context
import org.umn.ngantriin.R
import org.umn.ngantriin.domain.model.ActiveQueue
import org.umn.ngantriin.domain.model.AppNotification
import org.umn.ngantriin.domain.model.NotificationType
import org.umn.ngantriin.domain.model.QueueEntry
import org.umn.ngantriin.domain.model.QueueMath
import java.time.Instant

/**
 * One place that decides what each notification says, shared by
 * the FCM path, the local scheduler and the live-tracking service so the
 * wording cannot drift apart.
 */
object QueueNotificationCopy {

    /** The numbers a message may mention. Nothing that names the venue. */
    data class Facts(
        val queueId: String,
        val queueNumber: String,
        val peopleAhead: Int = 0,
        val estimatedWaitMinutes: Int = 0,
        val checkInWindowMinutes: Int = QueueMath.DEFAULT_CHECK_IN_WINDOW_MINUTES
    )

    /**
     * Deterministic id, mirroring the `unique (queue_id, type)` constraint in
     * the schema: the same milestone for the same ticket is always the same
     * row, so it cannot be delivered twice.
     */
    fun idFor(queueId: String, type: NotificationType) = "$queueId:${type.name}"

    /**
     * Notifications are built outside Compose, so this mirrors
     * [org.umn.ngantriin.ui.components.waitTimeText] using [Context.getString]
     * directly instead of `stringResource` — [context] already carries
     * whichever language Settings -> Language selected.
     */
    fun waitTimeText(context: Context, minutes: Int): String = when {
        minutes <= 0 -> context.getString(R.string.wait_time_none)
        minutes < 60 -> context.getString(R.string.wait_time_minutes, minutes)
        minutes % 60 == 0 -> context.getString(R.string.wait_time_hours, minutes / 60)
        else -> context.getString(R.string.wait_time_hours_minutes, minutes / 60, minutes % 60)
    }

    fun build(
        context: Context,
        userId: String,
        queue: ActiveQueue,
        type: NotificationType
    ): AppNotification = build(
        context = context,
        userId = userId,
        facts = Facts(
            queueId = queue.entry.id,
            queueNumber = queue.queueNumber,
            peopleAhead = queue.peopleAhead,
            estimatedWaitMinutes = queue.estimatedWaitMinutes,
            checkInWindowMinutes = queue.restaurant.checkInWindowMinutes
        ),
        type = type
    )

    /** For a ticket that has already ended and so has no line around it. */
    fun build(
        context: Context,
        userId: String,
        entry: QueueEntry,
        checkInWindowMinutes: Int,
        type: NotificationType
    ): AppNotification = build(
        context = context,
        userId = userId,
        facts = Facts(
            queueId = entry.id,
            queueNumber = entry.queueNumber,
            checkInWindowMinutes = checkInWindowMinutes
        ),
        type = type
    )

    /**
     * 13 (privacy): a lock screen is a public surface — anyone
     * glancing at the phone can read a notification without unlocking it. So
     * nothing built here names the restaurant; that detail only appears once
     * the notification is tapped and the app itself is open.
     */
    fun build(
        context: Context,
        userId: String,
        facts: Facts,
        type: NotificationType,
        id: String = idFor(facts.queueId, type)
    ): AppNotification {
        val (title, body) = text(context, facts, type)
        return AppNotification(
            id = id,
            userId = userId,
            queueId = facts.queueId,
            title = title,
            body = body,
            type = type,
            isRead = false,
            createdAt = Instant.now()
        )
    }

    private fun text(context: Context, facts: Facts, type: NotificationType): Pair<String, String> =
        when (type) {
            NotificationType.ALMOST_THERE -> context.getString(R.string.push_almost_there_title) to
                aheadBody(context, facts)

            NotificationType.RETURN_NOW -> context.getString(R.string.push_return_now_title) to
                context.getString(R.string.push_return_now_body)

            NotificationType.CALLED -> context.getString(R.string.push_called_title) to
                context.getString(R.string.push_called_body, facts.queueNumber, facts.checkInWindowMinutes)

            NotificationType.CHECKED_IN -> context.getString(R.string.push_checked_in_title) to
                context.getString(R.string.push_checked_in_body)

            NotificationType.TABLE_READY -> context.getString(R.string.push_table_ready_title) to
                context.getString(R.string.push_table_ready_body, facts.queueNumber)

            NotificationType.COMPLETED -> context.getString(R.string.push_completed_title) to
                context.getString(R.string.push_completed_body)

            NotificationType.CANCELLED -> context.getString(R.string.push_cancelled_title) to
                context.getString(R.string.push_cancelled_body)

            NotificationType.EXPIRED -> context.getString(R.string.push_expired_title) to
                context.getString(R.string.push_expired_body, facts.queueNumber, facts.checkInWindowMinutes)

            NotificationType.QUEUE_JOINED -> context.getString(R.string.push_joined_title) to
                context.resources.getQuantityString(
                    R.plurals.push_joined_body,
                    facts.peopleAhead,
                    facts.queueNumber,
                    facts.peopleAhead,
                    waitTimeText(context, facts.estimatedWaitMinutes)
                )

            NotificationType.POSITION_UPDATE ->
                context.getString(R.string.push_queue_update_title) to aheadBody(context, facts)
        }

    private fun aheadBody(context: Context, facts: Facts): String =
        context.resources.getQuantityString(
            R.plurals.push_almost_there_body,
            facts.peopleAhead,
            facts.peopleAhead,
            waitTimeText(context, facts.estimatedWaitMinutes)
        )
}
