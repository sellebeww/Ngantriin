package org.umn.ngantriin.notification

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.umn.ngantriin.NgantriinApplication
import org.umn.ngantriin.core.Constants
import org.umn.ngantriin.domain.model.AppNotification
import org.umn.ngantriin.domain.model.NotificationType
import java.time.Instant

/**
 * Section 15. Receives pushes sent by the backend when queue state changes.
 *
 * The payload is read from `data`, not `notification`, so the app controls
 * rendering in both the foreground and the background and can persist the
 * event before showing it.
 *
 * Expected data keys:
 *   id, user_id, queue_id, title, body, type
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

        val notification = AppNotification(
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
            container.notificationHelper.show(notification)
            // The push is a hint that something changed; the authoritative
            // state still comes from the queue tables.
            container.queueRepository.refreshHistory(userId)
        }
    }

    private companion object {
        const val TAG = "Ngantriin"
    }
}
