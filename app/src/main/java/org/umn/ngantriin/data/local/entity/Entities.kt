package org.umn.ngantriin.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalTime

/**
 * Cermin Room dari tabel Supabase. Ini baris cache, bukan sumber data
 * kedua: panggilan network yang gagal tidak pernah menghapusnya, hanya
 * membiarkannya basi.
 */

@Entity(tableName = "restaurants")
data class RestaurantEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val category: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val imageUrl: String?,
    val rating: Double,
    val ratingCount: Int,
    val openingTime: LocalTime,
    val closingTime: LocalTime,
    val isOpen: Boolean,
    val averageServiceMinutes: Int,
    val queuePrefix: String,
    val queueCapacity: Int,
    val checkInRadiusMeters: Int,
    val availableSeats: Int,
    val cachedAt: Instant,
    val checkInWindowMinutes: Int = 10,
    val isActive: Boolean = true
)

@Entity(tableName = "queue_stats")
data class QueueStatsEntity(
    @PrimaryKey val restaurantId: String,
    val currentServingNumber: String?,
    val currentServingSequence: Int,
    val waitingCount: Int,
    val estimatedWaitMinutes: Int,
    val updatedAt: Instant
)

@Entity(
    tableName = "queue_entries",
    indices = [Index("userId"), Index("restaurantId"), Index("status")]
)
data class QueueEntryEntity(
    @PrimaryKey val id: String,
    val restaurantId: String,
    val userId: String,
    val queueNumber: String,
    val ticketSequence: Int,
    val partySize: Int,
    val status: String,
    val note: String?,
    val joinedAt: Instant,
    val calledAt: Instant?,
    val checkedInAt: Instant?,
    val completedAt: Instant?,
    val cancelledAt: Instant?,
    val checkInPhotoUrl: String? = null,
    val checkInDeadline: Instant? = null,
    val tableReadyAt: Instant? = null,
    val expiredAt: Instant? = null
)

@Entity(tableName = "reviews", indices = [Index("restaurantId"), Index(value = ["queueId"], unique = true)])
data class ReviewEntity(
    @PrimaryKey val id: String,
    val restaurantId: String,
    val userId: String,
    val queueId: String,
    val rating: Int,
    val comment: String,
    val createdAt: Instant,
    val authorName: String?
)

@Entity(tableName = "notifications", indices = [Index("userId")])
data class NotificationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val queueId: String?,
    val title: String,
    val body: String,
    val type: String,
    val isRead: Boolean,
    val createdAt: Instant
)

/**
 * Aksi tulis yang tidak sampai ke backend disimpan di sini dan
 * diputar ulang oleh [org.umn.ngantriin.work.SyncWorker] saat koneksi kembali.
 *
 * Antre sengaja *tidak* bisa diputar ulang: tiket yang terbit sepuluh menit
 * terlambat lebih buruk daripada tidak ada tiket. Lihat
 * [org.umn.ngantriin.work.PendingAction] untuk aksi apa saja yang diantrekan dan alasannya.
 */
@Entity(tableName = "pending_actions")
data class PendingActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val payload: String,
    val createdAt: Instant,
    val attempts: Int = 0
)

/** Baris hasil untuk layar riwayat: tiket beserta restorannya. */
data class QueueHistoryRow(
    @Embedded val queue: QueueEntryEntity,
    val restaurantName: String?,
    val restaurantImageUrl: String?,
    val hasReview: Boolean
)
