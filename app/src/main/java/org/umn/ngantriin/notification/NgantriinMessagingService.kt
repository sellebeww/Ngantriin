package org.umn.ngantriin.notification

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.umn.ngantriin.NgantriinApplication
import org.umn.ngantriin.core.Constants
import org.umn.ngantriin.domain.model.AppNotification
import org.umn.ngantriin.domain.model.NotificationType
import java.time.Instant

/**
 * Receives pushes sent by the backend when queue state changes.
 *
 * The payload is read from `data`, not `notification`, so the app controls
 * rendering in both the foreground and the background and can persist the
 * event before showing it.
 *
 * Expected data keys:
 *   id, user_id, queue_id, title, body, type
 *   and, from 0012 on: queue_number, people_ahead, estimated_wait,
 *   check_in_minutes — so the text is rendered in the app's own language
 *   rather than whatever the server wrote.
 */
class NgantriinMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        Log.i(TAG, "New FCM token")
        val container = (application as? NgantriinApplication)?.container ?: return
        scope.launch { container.authRepository.syncPushToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val container = (application as? NgantriinApplication)?.container ?: return
        val data = message.data

        val userId = data["user_id"]
            ?: container.authRepository.currentUserOrNull()?.id
            ?: return

        val notification = localized(data, userId) ?: AppNotification(
            id = data["id"] ?: "${data[Constants.EXTRA_QUEUE_ID]}:${data["type"]}",
            userId = userId,
            queueId = data[Constants.EXTRA_QUEUE_ID],
            title = data["title"] ?: message.notification?.title.orEmpty(),
            body = data["body"] ?: message.notification?.body.orEmpty(),
            type = NotificationType.fromWire(data["type"]),
            isRead = false,
            createdAt = Instant.now()
        )

        if (notification.title.isBlank() && notification.body.isBlank()) return

        scope.launch {
            container.notificationRepository.record(notification)
            if (container.userPreferencesRepository.notificationsEnabled.first()) {
                container.notificationHelper.show(notification)
            }
            // The push is a hint that something changed; the authoritative
            // state still comes from the queue tables.
            container.queueRepository.refreshHistory(userId)
        }
    }

    /** Re-renders the push from its raw values, in the app's language. */
    private fun localized(data: Map<String, String>, userId: String): AppNotification? {
        val queueId = data[Constants.EXTRA_QUEUE_ID] ?: return null
        val queueNumber = data["queue_number"] ?: return null
        val type = NotificationType.fromWire(data["type"])
        val facts = QueueNotificationCopy.Facts(
            queueId = queueId,
            queueNumber = queueNumber,
            peopleAhead = data["people_ahead"]?.toIntOrNull() ?: 0,
            estimatedWaitMinutes = data["estimated_wait"]?.toIntOrNull() ?: 0,
            checkInWindowMinutes = data["check_in_minutes"]?.toIntOrNull()
                ?: org.umn.ngantriin.domain.model.QueueMath.DEFAULT_CHECK_IN_WINDOW_MINUTES
        )
        return QueueNotificationCopy.build(
            context = this,
            userId = userId,
            facts = facts,
            type = type,
            id = data["id"] ?: QueueNotificationCopy.idFor(queueId, type)
        )
    }

    private companion object {
        const val TAG = "Ngantriin"
    }
}
