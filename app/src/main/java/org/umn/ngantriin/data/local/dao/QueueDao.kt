package org.umn.ngantriin.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.umn.ngantriin.data.local.entity.QueueEntryEntity
import org.umn.ngantriin.data.local.entity.QueueHistoryRow

@Dao
interface QueueDao {

    @Upsert
    suspend fun upsert(entries: List<QueueEntryEntity>)

    @Upsert
    suspend fun upsert(entry: QueueEntryEntity)

    @Query("SELECT * FROM queue_entries WHERE id = :queueId")
    suspend fun findById(queueId: String): QueueEntryEntity?

    @Query("SELECT * FROM queue_entries WHERE id = :queueId")
    fun observeById(queueId: String): Flow<QueueEntryEntity?>

    /**
     * The customer's single live ticket. The schema guarantees at most one,
     * but LIMIT 1 keeps the UI honest if a stale cached row lingers.
     */
    @Query(
        """
        SELECT * FROM queue_entries
        WHERE userId = :userId
          AND status IN ('WAITING', 'ALMOST_THERE', 'CALLED', 'CHECKED_IN')
        ORDER BY joinedAt DESC
        LIMIT 1
        """
    )
    fun observeActive(userId: String): Flow<QueueEntryEntity?>

    @Query(
        """
        SELECT * FROM queue_entries
        WHERE restaurantId = :restaurantId
          AND status IN ('WAITING', 'ALMOST_THERE', 'CALLED', 'CHECKED_IN')
        ORDER BY ticketSequence
        """
    )
    fun observeLiveForRestaurant(restaurantId: String): Flow<List<QueueEntryEntity>>

    @Query(
        """
        SELECT * FROM queue_entries
        WHERE restaurantId = :restaurantId
        ORDER BY ticketSequence
        """
    )
    suspend fun findAllForRestaurant(restaurantId: String): List<QueueEntryEntity>

    @Transaction
    @Query(
        """
        SELECT q.*,
               r.name AS restaurantName,
               r.imageUrl AS restaurantImageUrl,
               EXISTS(SELECT 1 FROM reviews rv WHERE rv.queueId = q.id) AS hasReview
        FROM queue_entries q
        LEFT JOIN restaurants r ON r.id = q.restaurantId
        WHERE q.userId = :userId
        ORDER BY q.joinedAt DESC
        """
    )
    fun observeHistory(userId: String): Flow<List<QueueHistoryRow>>

    @Query("DELETE FROM queue_entries WHERE restaurantId = :restaurantId AND id NOT IN (:keepIds)")
    suspend fun deleteStaleForRestaurant(restaurantId: String, keepIds: List<String>)

    @Transaction
    suspend fun replaceRestaurantQueue(restaurantId: String, entries: List<QueueEntryEntity>) {
        upsert(entries)
        deleteStaleForRestaurant(restaurantId, entries.map { it.id })
    }

    @Query("DELETE FROM queue_entries")
    suspend fun clear()
}
