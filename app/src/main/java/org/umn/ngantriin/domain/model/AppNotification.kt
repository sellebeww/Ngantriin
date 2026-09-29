package org.umn.ngantriin.domain.model

import java.time.Instant

/**
 * Section 37. Every push is persisted with its type so a given milestone can
 * fire at most once per ticket — the `(queue_id, type)` unique index in the
 * schema is what actually enforces that.
 */
enum class NotificationType {
    QUEUE_JOINED,
    POSITION_UPDATE,
    ALMOST_THERE,
    RETURN_NOW,
    CALLED,
    CHECKED_IN,
    COMPLETED,
    CANCELLED;

    companion object {
        fun fromWire(value: String?): NotificationType =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
                ?: POSITION_UPDATE
    }
}

data class AppNotification(
    val id: String,
    val userId: String,
    val queueId: String?,
    val title: String,
    val body: String,
    val type: NotificationType,
    val isRead: Boolean,
    val createdAt: Instant
)
