package org.umn.ngantriin.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalTime

/**
 * Room mirrors of the Supabase tables (section 21). These are cache rows, not
 * a second source of truth: a failed network call never deletes them, it just
 * leaves them stale.
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
    val cachedAt: Instant
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
    val checkInPhotoUrl: String? = null
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
 * Section 21. A write that could not reach the backend is parked here and
 * replayed by [org.umn.ngantriin.work.SyncWorker] once connectivity returns.
 *
 * Joining a queue is deliberately *not* replayable: a ticket issued ten
 * minutes late is worse than no ticket. See
 * [org.umn.ngantriin.work.PendingAction] for what does queue up and why.
 */
@Entity(tableName = "pending_actions")
data class PendingActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val payload: String,
    val createdAt: Instant,
    val attempts: Int = 0
)

/** Result row for the history screen: a ticket plus the venue it belongs to. */
data class QueueHistoryRow(
    @Embedded val queue: QueueEntryEntity,
    val restaurantName: String?,
    val restaurantImageUrl: String?,
    val hasReview: Boolean
)

/** A live ticket joined with its venue and counters, for the active-queue flow. */
data class ActiveQueueRow(
    @Embedded val queue: QueueEntryEntity,
    @Embedded(prefix = "r_") val restaurant: RestaurantEntity?,
    @Embedded(prefix = "s_") val stats: QueueStatsEntity?
)
