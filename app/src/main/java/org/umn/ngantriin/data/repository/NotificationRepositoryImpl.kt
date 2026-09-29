package org.umn.ngantriin.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.core.runCatchingOutcome
import org.umn.ngantriin.data.local.dao.NotificationDao
import org.umn.ngantriin.data.mapper.toDomain
import org.umn.ngantriin.data.mapper.toEntity
import org.umn.ngantriin.data.remote.datasource.NotificationRemoteDataSource
import org.umn.ngantriin.domain.model.AppNotification
import org.umn.ngantriin.domain.repository.NotificationRepository

class NotificationRepositoryImpl(
    private val remote: NotificationRemoteDataSource,
    private val dao: NotificationDao
) : NotificationRepository {

    override fun observeNotifications(userId: String): Flow<List<AppNotification>> =
        dao.observeForUser(userId)
            .map { entities -> entities.map { it.toDomain() } }
            .distinctUntilChanged()

    override fun observeUnreadCount(userId: String): Flow<Int> =
        dao.observeUnreadCount(userId).distinctUntilChanged()

    override suspend fun refresh(userId: String): Outcome<Unit> = runCatchingOutcome {
        val notifications = remote.fetchNotifications(userId)
        if (notifications.isNotEmpty()) dao.upsert(notifications.map { it.toEntity() })
    }

    override suspend fun markRead(notificationId: String): Outcome<Unit> = runCatchingOutcome {
        // Local first so the badge clears immediately; the server call is
        // best-effort and will be re-applied by the next refresh if it fails.
        dao.markRead(notificationId)
        remote.markRead(notificationId)
    }

    override suspend fun markAllRead(userId: String): Outcome<Unit> = runCatchingOutcome {
        dao.markAllRead(userId)
        remote.markAllRead(userId)
    }

    override suspend fun record(notification: AppNotification) {
        dao.upsert(notification.toEntity())
    }
}
