package org.umn.ngantriin.data.remote.datasource

import kotlinx.coroutines.flow.Flow
import org.umn.ngantriin.domain.model.AppNotification
import org.umn.ngantriin.domain.model.QueueEntry
import org.umn.ngantriin.domain.model.QueuePosition
import org.umn.ngantriin.domain.model.QueueStats
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.domain.model.Restaurant
import org.umn.ngantriin.domain.model.Review
import org.umn.ngantriin.domain.model.User

/**
 * The backend contract, stated once.
 *
 * `Supabase*` implements it against the real project; `Demo*` implements it
 * against an in-process fake so the app is runnable before any credentials
 * exist. Repositories only ever see these interfaces, which is what keeps the
 * two paths honest — the demo cannot quietly drift into a different state
 * machine, because it has to satisfy the same signatures.
 *
 * Everything here throws on failure; converting to
 * [org.umn.ngantriin.core.AppError] is the repository's job.
 */

sealed interface RemoteSession {
    /** Restoring a persisted session; do not route yet. */
    data object Initializing : RemoteSession
    data object SignedOut : RemoteSession
    data class SignedIn(val userId: String) : RemoteSession
}

interface AuthRemoteDataSource {
    val session: Flow<RemoteSession>
    fun currentUserId(): String?

    suspend fun signIn(email: String, password: String): User
    suspend fun signUp(name: String, email: String, password: String): User
    suspend fun signOut()
    suspend fun sendPasswordReset(email: String)

    suspend fun fetchProfile(userId: String): User
    suspend fun updateProfile(userId: String, name: String, phone: String?): User
    suspend fun updatePushToken(userId: String, token: String)
}

interface RestaurantRemoteDataSource {
    suspend fun fetchRestaurants(): List<Restaurant>
    suspend fun fetchRestaurant(restaurantId: String): Restaurant
    suspend fun fetchAllQueueStats(): List<QueueStats>
    suspend fun fetchQueueStats(restaurantId: String): QueueStats?
    suspend fun fetchManagedRestaurants(userId: String): List<Restaurant>

    /** Section 36: fires whenever this venue's counters change. */
    fun observeQueueStatsChanges(restaurantId: String): Flow<Unit>

    /** Fires whenever any venue's counters change, for the discovery list. */
    fun observeAllQueueStatsChanges(): Flow<Unit>
}

interface QueueRemoteDataSource {
    suspend fun joinQueue(restaurantId: String, partySize: Int, note: String?): QueueEntry
    suspend fun leaveQueue(queueId: String): QueueEntry

    /**
     * Uploads the check-in photo and calls `check_in_queue` in one step —
     * uploading needs [restaurantId] (for the storage path) that the queue
     * row itself doesn't hand back until after the RPC succeeds.
     */
    suspend fun checkIn(queueId: String, restaurantId: String, photo: ByteArray): QueueEntry

    /**
     * Turns a stored check-in photo reference into something a viewer can
     * actually load. The real implementation signs a private Storage path;
     * the demo implementation has no Storage, so it hands the value straight
     * back (already a locally-loadable URI — see [DemoQueueRemoteDataSource]).
     */
    suspend fun resolveCheckInPhotoUrl(photoUrl: String): String

    suspend fun callNext(restaurantId: String): QueueEntry
    suspend fun updateStatus(queueId: String, status: QueueStatus): QueueEntry

    suspend fun fetchQueue(queueId: String): QueueEntry

    /**
     * Section 23, computed server side. The customer cannot see the tickets in
     * front of them, so counting the line is the backend's job.
     */
    suspend fun fetchPosition(queueId: String): QueuePosition
    suspend fun fetchActiveQueue(userId: String): QueueEntry?
    suspend fun fetchHistory(userId: String): List<QueueEntry>
    suspend fun fetchRestaurantQueue(restaurantId: String): List<QueueEntry>

    /**
     * Change signals rather than deltas: the repository re-reads the
     * authoritative rows on every tick, so a dropped or out-of-order event
     * cannot leave the UI showing a queue that never existed.
     */
    fun observeUserQueueChanges(userId: String): Flow<Unit>
    fun observeRestaurantQueueChanges(restaurantId: String): Flow<Unit>
}

interface ReviewRemoteDataSource {
    suspend fun fetchReviews(restaurantId: String): List<Review>
    suspend fun fetchReviewForQueue(queueId: String): Review?
    suspend fun submitReview(
        queueId: String,
        restaurantId: String,
        userId: String,
        rating: Int,
        comment: String
    ): Review
}

interface NotificationRemoteDataSource {
    suspend fun fetchNotifications(userId: String): List<AppNotification>
    suspend fun markRead(notificationId: String)
    suspend fun markAllRead(userId: String)
    suspend fun record(notification: AppNotification)
}
