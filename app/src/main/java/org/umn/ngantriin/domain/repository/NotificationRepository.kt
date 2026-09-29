package org.umn.ngantriin.domain.repository

import kotlinx.coroutines.flow.Flow
import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.domain.model.AppNotification

interface NotificationRepository {

    fun observeNotifications(userId: String): Flow<List<AppNotification>>

    fun observeUnreadCount(userId: String): Flow<Int>

    suspend fun refresh(userId: String): Outcome<Unit>

    suspend fun markRead(notificationId: String): Outcome<Unit>

    suspend fun markAllRead(userId: String): Outcome<Unit>

    /** Records a notification that arrived by FCM while the app was running. */
    suspend fun record(notification: AppNotification)
}
