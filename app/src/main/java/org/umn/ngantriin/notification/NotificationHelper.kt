package org.umn.ngantriin.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import org.umn.ngantriin.MainActivity
import org.umn.ngantriin.R
import org.umn.ngantriin.core.Constants
import org.umn.ngantriin.domain.model.ActiveQueue
import org.umn.ngantriin.domain.model.AppNotification
import org.umn.ngantriin.domain.model.NotificationType
import org.umn.ngantriin.domain.model.QueueStatus

/**
 * Owns the notification channels and turns an [AppNotification]
 * into something the system tray can show.
 *
 * Urgency is split across two channels on purpose: "your turn is coming" and
 * "your table is ready" are not the same interruption, and a user who mutes
 * the first should still hear the second.
 */
class NotificationHelper(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    /** True once a google-services.json has been wired up. */
    val isPushConfigured: Boolean
        get() = FirebaseApp.getApps(context).isNotEmpty()

    fun createChannels() {
        val updates = NotificationChannel(
            Constants.CHANNEL_QUEUE_UPDATES,
            context.getString(R.string.channel_queue_updates_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_queue_updates_description)
        }

        val called = NotificationChannel(
            Constants.CHANNEL_QUEUE_CALLED,
            context.getString(R.string.channel_queue_called_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_queue_called_description)
            enableVibration(true)
        }

        // Silent and low: it updates every time the line moves, and must
        // never buzz for that. The milestones above do the interrupting.
        val live = NotificationChannel(
            Constants.CHANNEL_QUEUE_LIVE,
            context.getString(R.string.channel_live_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_live_description)
            setShowBadge(false)
        }

        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannels(listOf(updates, called, live))
    }

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Posts [notification]. Silently does nothing when the user declined the
     * permission — the in-app notification list still has the
     * record, so nothing is lost.
     */
    @SuppressLint("MissingPermission")
    fun show(notification: AppNotification) {
        // hasPermission() is the check lint is asking for; it cannot see
        // through the helper, hence the suppression rather than a duplicate
        // inline check.
        if (!hasPermission()) return

        val channel = if (notification.type.isUrgent) {
            Constants.CHANNEL_QUEUE_CALLED
        } else {
            Constants.CHANNEL_QUEUE_UPDATES
        }

        val built = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
            .setPriority(priorityFor(notification.type))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(notification))
            .build()

        // One notification per ticket per milestone: the id is stable, so a
        // repeat of the same event replaces rather than stacks.
        runCatching { manager.notify(notification.id.hashCode(), built) }
    }

    /** Replaces the ongoing live-queue notification in place. */
    @SuppressLint("MissingPermission")
    fun updateLive(notification: Notification) {
        if (!hasPermission()) return
        runCatching { manager.notify(Constants.LIVE_NOTIFICATION_ID, notification) }
    }

    /**
     * The ongoing "live queue" notification held by [QueueTrackingService]:
     * number, what to do now, and a running countdown while the check-in
     * window is open. Like every other notification here it never names the
     * restaurant.
     */
    fun buildLive(queue: ActiveQueue): Notification {
        val entry = queue.entry
        val text = when {
            entry.status == QueueStatus.CALLED -> context.getString(R.string.live_check_in_now)
            entry.status == QueueStatus.TABLE_READY -> context.getString(R.string.live_table_ready)
            entry.status == QueueStatus.CHECKED_IN && entry.hasBeenCalled ->
                context.getString(R.string.live_checked_in)
            queue.peopleAhead == 0 -> context.getString(R.string.live_youre_next)
            else -> context.getString(
                R.string.live_ahead_and_wait,
                queue.peopleAhead,
                QueueNotificationCopy.waitTimeText(context, queue.estimatedWaitMinutes)
            )
        }

        val builder = NotificationCompat.Builder(context, Constants.CHANNEL_QUEUE_LIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.live_title, queue.queueNumber))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent(entry.id, type = null, requestCode = LIVE_REQUEST_CODE))

        val deadline = entry.checkInDeadline
        if (entry.status == QueueStatus.CALLED && deadline != null) {
            // The system ticks the countdown itself; no wake-ups needed.
            builder.setWhen(deadline.toEpochMilli())
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        } else if (queue.showsLinePosition) {
            val total = (queue.peopleAhead + 1).coerceAtLeast(1)
            builder.setProgress(total, 1, false)
        }

        return builder.build()
    }

    private val NotificationType.isUrgent: Boolean
        get() = this == NotificationType.CALLED || this == NotificationType.RETURN_NOW ||
            this == NotificationType.TABLE_READY || this == NotificationType.EXPIRED

    private fun priorityFor(type: NotificationType): Int = when {
        type.isUrgent -> NotificationCompat.PRIORITY_HIGH
        type == NotificationType.ALMOST_THERE -> NotificationCompat.PRIORITY_DEFAULT
        else -> NotificationCompat.PRIORITY_LOW
    }

    private fun openAppIntent(notification: AppNotification): PendingIntent =
        openAppIntent(notification.queueId, notification.type, notification.id.hashCode())

    private fun openAppIntent(
        queueId: String?,
        type: NotificationType?,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(Constants.EXTRA_QUEUE_ID, queueId)
            type?.let { putExtra(Constants.EXTRA_NOTIFICATION_TYPE, it.name) }
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private companion object {
        const val LIVE_REQUEST_CODE = 7_001
    }
}
