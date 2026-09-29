package org.umn.ngantriin.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.umn.ngantriin.data.local.entity.QueueStatsEntity
import org.umn.ngantriin.data.local.entity.RestaurantEntity

@Dao
interface RestaurantDao {

    @Query("SELECT * FROM restaurants ORDER BY name")
    fun observeAll(): Flow<List<RestaurantEntity>>

    @Query("SELECT * FROM restaurants WHERE id = :id")
    fun observeById(id: String): Flow<RestaurantEntity?>

    @Query("SELECT * FROM restaurants WHERE id = :id")
    suspend fun findById(id: String): RestaurantEntity?

    @Query("SELECT DISTINCT category FROM restaurants WHERE category != '' ORDER BY category")
    fun observeCategories(): Flow<List<String>>

    @Query("SELECT * FROM queue_stats")
    fun observeAllStats(): Flow<List<QueueStatsEntity>>

    @Query("SELECT * FROM queue_stats WHERE restaurantId = :restaurantId")
    fun observeStats(restaurantId: String): Flow<QueueStatsEntity?>

    @Upsert
    suspend fun upsertRestaurants(restaurants: List<RestaurantEntity>)

    @Upsert
    suspend fun upsertStats(stats: List<QueueStatsEntity>)

    @Upsert
    suspend fun upsertStats(stats: QueueStatsEntity)

    /**
     * Replaces the catalogue after a successful refresh. Rows the backend no
     * longer returns are dropped, but only inside this transaction — a failed
     * request never reaches here, so the cache survives (section 21).
     */
    @Transaction
    suspend fun replaceCatalogue(
        restaurants: List<RestaurantEntity>,
        stats: List<QueueStatsEntity>
    ) {
        upsertRestaurants(restaurants)
        upsertStats(stats)
        deleteMissing(restaurants.map { it.id })
    }

    @Query("DELETE FROM restaurants WHERE id NOT IN (:keepIds)")
    suspend fun deleteMissing(keepIds: List<String>)

    @Query("SELECT MAX(cachedAt) FROM restaurants")
    suspend fun lastCachedAt(): Long?
}
