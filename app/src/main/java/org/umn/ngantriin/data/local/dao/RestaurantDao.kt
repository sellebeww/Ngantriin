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

    @Query("SELECT DISTINCT category FROM restaurants WHERE category != '' AND isActive = 1 ORDER BY category")
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
     * Mengganti katalog setelah refresh berhasil. Baris yang tidak lagi dikirim
     * backend dibuang, tapi hanya di dalam transaksi ini. Request yang gagal tidak
     * sampai sini, jadi cache tetap aman.
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
}
