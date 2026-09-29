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
import org.umn.ngantriin.domain.model.AppNotification
import org.umn.ngantriin.domain.model.NotificationType

/**
 * Section 15. Owns the notification channels and turns an [AppNotification]
 * into something the system tray can show.
 *
 * Urgency is split across two channels on purpose: "your turn is coming" and
 * "your table is ready" are not the same interruption, and a user who mutes
 * the first should still hear the second.
 */
class NotificationHelper(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    /** True once a google-services.json has been wired up (section 15). */
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

        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannels(listOf(updates, called))
    }

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Posts [notification]. Silently does nothing when the user declined the
     * permission (section 28.12) — the in-app notification list still has the
     * record, so nothing is lost.
     */
    @SuppressLint("MissingPermission")
    fun show(notification: AppNotification) {
        // hasPermission() is the check lint is asking for; it cannot see
        // through the helper, hence the suppression rather than a duplicate
        // inline check.
        if (!hasPermission()) return

        val channel = when (notification.type) {
            NotificationType.CALLED, NotificationType.RETURN_NOW -> Constants.CHANNEL_QUEUE_CALLED
            else -> Constants.CHANNEL_QUEUE_UPDATES
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
        // repeat of the same event replaces rather than stacks (section 37).
        runCatching { manager.notify(notification.id.hashCode(), built) }
    }

    private fun priorityFor(type: NotificationType): Int = when (type) {
        NotificationType.CALLED, NotificationType.RETURN_NOW -> NotificationCompat.PRIORITY_HIGH
        NotificationType.ALMOST_THERE -> NotificationCompat.PRIORITY_DEFAULT
        else -> NotificationCompat.PRIORITY_LOW
    }

    private fun openAppIntent(notification: AppNotification): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(Constants.EXTRA_QUEUE_ID, notification.queueId)
            putExtra(Constants.EXTRA_NOTIFICATION_TYPE, notification.type.name)
        }
        return PendingIntent.getActivity(
            context,
            notification.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    @Suppress("unused")
    private fun Notification.unused() = Unit
}
