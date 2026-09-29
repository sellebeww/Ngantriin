package org.umn.ngantriin.data.remote.datasource

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import org.umn.ngantriin.data.mapper.toDomain
import org.umn.ngantriin.data.remote.dto.NotificationDto
import org.umn.ngantriin.domain.model.AppNotification

class SupabaseNotificationRemoteDataSource(
    private val client: SupabaseClient
) : NotificationRemoteDataSource {

    override suspend fun fetchNotifications(userId: String): List<AppNotification> =
        client.from("notifications")
            .select {
                filter { eq("user_id", userId) }
                order("created_at", Order.DESCENDING)
                limit(100)
            }
            .decodeList<NotificationDto>()
            .map { it.toDomain() }

    override suspend fun markRead(notificationId: String) {
        client.from("notifications").update(mapOf("is_read" to true)) {
            filter { eq("id", notificationId) }
        }
    }

    override suspend fun markAllRead(userId: String) {
        client.from("notifications").update(mapOf("is_read" to true)) {
            filter {
                eq("user_id", userId)
                eq("is_read", false)
            }
        }
    }

    /**
     * Notifications are written by the backend, not the client — the row
     * already exists by the time the push arrives, so there is nothing to
     * insert here. The local mirror is handled by the repository.
     */
    override suspend fun record(notification: AppNotification) = Unit
}
