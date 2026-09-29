package org.umn.ngantriin.data.remote.demo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.first
import org.umn.ngantriin.data.local.prefs.UserPreferencesDataStore
import org.umn.ngantriin.data.remote.datasource.AuthRemoteDataSource
import org.umn.ngantriin.data.remote.datasource.NotificationRemoteDataSource
import org.umn.ngantriin.data.remote.datasource.QueueRemoteDataSource
import org.umn.ngantriin.data.remote.datasource.RemoteSession
import org.umn.ngantriin.data.remote.datasource.RestaurantRemoteDataSource
import org.umn.ngantriin.data.remote.datasource.ReviewRemoteDataSource
import org.umn.ngantriin.domain.model.AppNotification
import org.umn.ngantriin.domain.model.QueueEntry
import org.umn.ngantriin.domain.model.QueuePosition
import org.umn.ngantriin.domain.model.QueueStats
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.domain.model.Restaurant
import org.umn.ngantriin.domain.model.Review
import org.umn.ngantriin.domain.model.User
import org.umn.ngantriin.domain.model.UserRole
import java.util.UUID

/**
 * Demo-mode data sources. Each one satisfies the same interface its Supabase
 * twin does, so the repositories above cannot tell which is running.
 */

/**
 * There is no authentication in demo mode — any email signs you in and no
 * password is stored or checked. That is a deliberate choice over faking
 * credential handling: the real path is Supabase Auth, and pretending to
 * verify a password locally would only look like security.
 *
 * An email starting with "staff" signs in with the STAFF role, which is how
 * you reach the restaurant dashboard without a backend.
 */
class DemoAuthRemoteDataSource(
    private val preferences: UserPreferencesDataStore
) : AuthRemoteDataSource {

    @Volatile
    private var cachedUserId: String? = null

    override val session: Flow<RemoteSession> = preferences.demoProfile.map { profile ->
        cachedUserId = profile?.id
        if (profile == null) RemoteSession.SignedOut else RemoteSession.SignedIn(profile.id)
    }

    override fun currentUserId(): String? = cachedUserId

    override suspend fun signIn(email: String, password: String): User =
        signInternal(nameFrom(email), email)

    override suspend fun signUp(name: String, email: String, password: String): User =
        signInternal(name, email)

    private suspend fun signInternal(name: String, email: String): User {
        val normalised = email.trim().lowercase()
        val existing = preferences.demoProfile.first()
        val role = if (normalised.startsWith("staff")) UserRole.STAFF else UserRole.CUSTOMER
        val profile = UserPreferencesDataStore.DemoProfile(
            // Reuse the id when the same person signs back in, so their queue
            // history survives a sign-out.
            id = existing?.takeIf { it.email == normalised }?.id
                ?: UUID.nameUUIDFromBytes(normalised.toByteArray()).toString(),
            name = name.trim().ifBlank { nameFrom(normalised) },
            email = normalised,
            role = role.wireValue
        )
        preferences.setDemoProfile(profile)
        cachedUserId = profile.id
        return profile.toUser()
    }

    override suspend fun signOut() {
        preferences.clearDemoProfile()
        cachedUserId = null
    }

    override suspend fun sendPasswordReset(email: String) = Unit

    override suspend fun fetchProfile(userId: String): User =
        preferences.demoProfile.first()?.toUser() ?: error("NOT_AUTHENTICATED")

    override suspend fun updateProfile(userId: String, name: String, phone: String?): User {
        val current = preferences.demoProfile.first() ?: error("NOT_AUTHENTICATED")
        val updated = current.copy(name = name.trim())
        preferences.setDemoProfile(updated)
        return updated.toUser().copy(phone = phone)
    }

    override suspend fun updatePushToken(userId: String, token: String) = Unit

    private fun nameFrom(email: String) = email.substringBefore('@')
        .split('.', '_', '-')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
        .ifBlank { "Guest" }

    private fun UserPreferencesDataStore.DemoProfile.toUser() = User(
        id = id,
        name = name,
        email = email,
        role = UserRole.fromWire(role)
    )
}

class DemoRestaurantRemoteDataSource(
    private val backend: DemoBackend
) : RestaurantRemoteDataSource {

    override suspend fun fetchRestaurants(): List<Restaurant> = backend.allRestaurants()

    override suspend fun fetchRestaurant(restaurantId: String): Restaurant =
        backend.restaurant(restaurantId)

    override suspend fun fetchAllQueueStats(): List<QueueStats> = backend.allStats()

    override suspend fun fetchQueueStats(restaurantId: String): QueueStats =
        backend.stats(restaurantId)

    /** In demo mode a staff account manages every sample venue. */
    override suspend fun fetchManagedRestaurants(userId: String): List<Restaurant> =
        backend.allRestaurants()

    override fun observeQueueStatsChanges(restaurantId: String): Flow<Unit> =
        backend.changes
            .filter { it.restaurantId == null || it.restaurantId == restaurantId }
            .map { }
            .onStart { emit(Unit) }

    override fun observeAllQueueStatsChanges(): Flow<Unit> =
        backend.changes.map { }.onStart { emit(Unit) }
}

class DemoQueueRemoteDataSource(
    private val backend: DemoBackend,
    private val auth: AuthRemoteDataSource
) : QueueRemoteDataSource {

    private fun requireUser(): String = auth.currentUserId() ?: error("NOT_AUTHENTICATED")

    override suspend fun joinQueue(
        restaurantId: String,
        partySize: Int,
        note: String?
    ): QueueEntry = backend.joinQueue(requireUser(), restaurantId, partySize, note)

    override suspend fun leaveQueue(queueId: String): QueueEntry =
        backend.cancelQueue(requireUser(), queueId)

    // No real Storage in demo mode, so the photo bytes are discarded here —
    // what matters for exercising the state machine is that checkIn() still
    // requires a non-blank photo reference, same as the real RPC does.
    override suspend fun checkIn(queueId: String, restaurantId: String, photo: ByteArray): QueueEntry =
        backend.checkIn(requireUser(), queueId, photoUrl = "demo-checkin-photo:$queueId")

    override suspend fun resolveCheckInPhotoUrl(photoUrl: String): String = photoUrl

    override suspend fun callNext(restaurantId: String): QueueEntry =
        backend.callNext(restaurantId)

    override suspend fun updateStatus(queueId: String, status: QueueStatus): QueueEntry =
        backend.updateStatus(queueId, status)

    override suspend fun fetchQueue(queueId: String): QueueEntry = backend.queue(queueId)

    override suspend fun fetchPosition(queueId: String): QueuePosition = backend.position(queueId)

    override suspend fun fetchActiveQueue(userId: String): QueueEntry? =
        backend.activeQueueFor(userId)

    override suspend fun fetchHistory(userId: String): List<QueueEntry> =
        backend.historyFor(userId)

    override suspend fun fetchRestaurantQueue(restaurantId: String): List<QueueEntry> =
        backend.queueFor(restaurantId)

    override fun observeUserQueueChanges(userId: String): Flow<Unit> =
        backend.changes
            .filter { userId in it.userIds }
            .map { }
            .onStart { emit(Unit) }

    override fun observeRestaurantQueueChanges(restaurantId: String): Flow<Unit> =
        backend.changes
            .filter { it.restaurantId == restaurantId }
            .map { }
            .onStart { emit(Unit) }
}

class DemoReviewRemoteDataSource(
    private val backend: DemoBackend
) : ReviewRemoteDataSource {

    override suspend fun fetchReviews(restaurantId: String): List<Review> =
        backend.reviewsFor(restaurantId)

    override suspend fun fetchReviewForQueue(queueId: String): Review? =
        backend.reviewForQueue(queueId)

    override suspend fun submitReview(
        queueId: String,
        restaurantId: String,
        userId: String,
        rating: Int,
        comment: String
    ): Review = backend.submitReview(userId, queueId, restaurantId, rating, comment)
}

/**
 * Demo notifications are produced locally by
 * [org.umn.ngantriin.notification.QueueNotificationScheduler] rather than
 * pushed from a server, so this source only has to accept them.
 */
class DemoNotificationRemoteDataSource : NotificationRemoteDataSource {
    override suspend fun fetchNotifications(userId: String): List<AppNotification> = emptyList()
    override suspend fun markRead(notificationId: String) = Unit
    override suspend fun markAllRead(userId: String) = Unit
    override suspend fun record(notification: AppNotification) = Unit
}
