package org.umn.ngantriin.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.umn.ngantriin.data.local.entity.ReviewEntity

@Dao
interface ReviewDao {

    @Upsert
    suspend fun upsert(reviews: List<ReviewEntity>)

    @Upsert
    suspend fun upsert(review: ReviewEntity)

    @Query("SELECT * FROM reviews WHERE restaurantId = :restaurantId ORDER BY createdAt DESC")
    fun observeForRestaurant(restaurantId: String): Flow<List<ReviewEntity>>

    @Query("SELECT * FROM reviews WHERE queueId = :queueId LIMIT 1")
    suspend fun findByQueue(queueId: String): ReviewEntity?

    @Query("DELETE FROM reviews")
    suspend fun clear()
}
