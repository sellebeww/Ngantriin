package org.umn.ngantriin.domain.model

import java.time.Instant

/**
 * Setiap push disimpan beserta tipenya supaya satu milestone paling banyak
 * sekali per tiket. Yang benar-benar menegakkannya adalah unique index
 * `(queue_id, type)` di skema.
 */
enum class NotificationType {
    QUEUE_JOINED,
    POSITION_UPDATE,
    ALMOST_THERE,
    RETURN_NOW,
    CALLED,
    CHECKED_IN,
    TABLE_READY,
    COMPLETED,
    CANCELLED,
    EXPIRED;

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
