package org.umn.ngantriin.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import org.umn.ngantriin.data.local.entity.PendingActionEntity

@Dao
interface PendingActionDao {

    @Insert
    suspend fun insert(action: PendingActionEntity): Long

    @Query("SELECT * FROM pending_actions ORDER BY createdAt")
    suspend fun all(): List<PendingActionEntity>

    @Query("SELECT COUNT(*) FROM pending_actions")
    suspend fun count(): Int

    @Delete
    suspend fun delete(action: PendingActionEntity)

    @Query("UPDATE pending_actions SET attempts = attempts + 1 WHERE id = :id")
    suspend fun recordAttempt(id: Long)

    /** Gives up on an action after enough failures so it cannot loop forever. */
    @Query("DELETE FROM pending_actions WHERE attempts >= :maxAttempts")
    suspend fun dropExhausted(maxAttempts: Int)

    @Query("DELETE FROM pending_actions")
    suspend fun clear()
}
