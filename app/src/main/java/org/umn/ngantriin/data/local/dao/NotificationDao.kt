package org.umn.ngantriin.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.umn.ngantriin.data.local.entity.NotificationEntity

@Dao
interface NotificationDao {

    @Upsert
    suspend fun upsert(notifications: List<NotificationEntity>)

    @Upsert
    suspend fun upsert(notification: NotificationEntity)

    @Query("SELECT * FROM notifications WHERE userId = :userId ORDER BY createdAt DESC LIMIT 100")
    fun observeForUser(userId: String): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE userId = :userId AND isRead = 0")
    fun observeUnreadCount(userId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM notifications WHERE id = :id")
    suspend fun countById(id: String): Int

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("UPDATE notifications SET isRead = 1 WHERE userId = :userId")
    suspend fun markAllRead(userId: String)

    @Query("DELETE FROM notifications")
    suspend fun clear()
}
