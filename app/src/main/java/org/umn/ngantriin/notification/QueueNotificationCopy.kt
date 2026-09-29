package org.umn.ngantriin.notification

import android.content.Context
import org.umn.ngantriin.R
import org.umn.ngantriin.domain.model.ActiveQueue
import org.umn.ngantriin.domain.model.AppNotification
import org.umn.ngantriin.domain.model.NotificationType
import java.time.Instant

/**
 * Section 15. One place that decides what each notification says, shared by
 * the FCM path and the local scheduler so the wording cannot drift apart.
 */
object QueueNotificationCopy {

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
     * whichever language Settings -> Language selected (section 43).
     */
    private fun waitTimeText(context: Context, minutes: Int): String = when {
        minutes <= 0 -> context.getString(R.string.wait_time_none)
        minutes < 60 -> context.getString(R.string.wait_time_minutes, minutes)
        minutes % 60 == 0 -> context.getString(R.string.wait_time_hours, minutes / 60)
        else -> context.getString(R.string.wait_time_hours_minutes, minutes / 60, minutes % 60)
    }

    /**
     * Section 28.13 (privacy): a lock screen is a public surface — anyone
     * glancing at the phone can read a notification without unlocking it. So
     * nothing built here names the restaurant; that detail only appears once
     * the notification is tapped and the app itself is open.
     */
    fun build(
        context: Context,
        userId: String,
        queue: ActiveQueue,
        type: NotificationType
    ): AppNotification {
        val (title, body) = when (type) {
            NotificationType.ALMOST_THERE -> context.getString(R.string.push_almost_there_title) to
                context.resources.getQuantityString(
                    R.plurals.push_almost_there_body,
                    queue.peopleAhead,
                    queue.peopleAhead,
                    waitTimeText(context, queue.estimatedWaitMinutes)
                )

            NotificationType.RETURN_NOW -> context.getString(R.string.push_return_now_title) to
                context.getString(R.string.push_return_now_body)

            NotificationType.CALLED -> context.getString(R.string.push_called_title) to
                context.getString(R.string.push_called_body, queue.queueNumber)

            NotificationType.CHECKED_IN -> context.getString(R.string.push_checked_in_title) to
                context.getString(R.string.push_checked_in_body)

            NotificationType.COMPLETED -> context.getString(R.string.push_completed_title) to
                context.getString(R.string.push_completed_body)

            NotificationType.CANCELLED -> context.getString(R.string.push_cancelled_title) to
                context.getString(R.string.push_cancelled_body)

            NotificationType.QUEUE_JOINED, NotificationType.POSITION_UPDATE ->
                context.getString(R.string.push_queue_update_title) to
                    context.resources.getQuantityString(
                        R.plurals.push_almost_there_body,
                        queue.peopleAhead,
                        queue.peopleAhead,
                        waitTimeText(context, queue.estimatedWaitMinutes)
                    )
        }

        return AppNotification(
            id = idFor(queue.entry.id, type),
            userId = userId,
            queueId = queue.entry.id,
            title = title,
            body = body,
            type = type,
            isRead = false,
            createdAt = Instant.now()
        )
    }
}
