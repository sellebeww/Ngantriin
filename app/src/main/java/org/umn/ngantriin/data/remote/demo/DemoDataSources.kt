package org.umn.ngantriin.data.remote.demo

import org.umn.ngantriin.domain.model.ProofDocument
import org.umn.ngantriin.domain.model.ApplicantRole
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.first
import org.umn.ngantriin.data.local.prefs.UserPreferencesDataStore
import org.umn.ngantriin.data.remote.datasource.AdminRemoteDataSource
import org.umn.ngantriin.data.remote.datasource.AuthRemoteDataSource
import org.umn.ngantriin.data.remote.datasource.NotificationRemoteDataSource
import org.umn.ngantriin.domain.model.ApplicationDocument
import org.umn.ngantriin.domain.model.ProofType
import org.umn.ngantriin.domain.model.RestaurantApplication
import org.umn.ngantriin.domain.model.VerificationStatus
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
import org.umn.ngantriin.domain.model.RestaurantRegistration
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
 * you reach the restaurant dashboard without a backend. Which venues it runs
 * is a separate question, answered by the staff memberships in [DemoBackend]:
 * the sample logins in [DemoSeed.STAFF_ACCOUNTS] each run one sample venue, and
 * any account that registers a venue runs that venue.
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
        val id = existing?.takeIf { it.email == normalised }?.id ?: DemoSeed.demoUserId(normalised)
        val runsAVenue = preferences.demoStaffMemberships.first().any { it.startsWith("$id|") }
        val role = when {
            DemoSeed.isAdminEmail(normalised) -> UserRole.ADMIN
            normalised.startsWith("staff") || runsAVenue -> UserRole.STAFF
            else -> UserRole.CUSTOMER
        }
        val profile = UserPreferencesDataStore.DemoProfile(
            // Reuse the id when the same person signs back in, so their queue
            // history survives a sign-out.
            id = id,
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

    // Sample mode has no reset emails, so there is no link to receive.
    override fun handleRecoveryLink(intent: Intent, onRecovered: () -> Unit) = Unit

    override suspend fun updatePassword(newPassword: String) = Unit

    override suspend fun fetchProfile(userId: String): User =
        preferences.demoProfile.first()?.toUser() ?: error("NOT_AUTHENTICATED")

    override suspend fun updateProfile(userId: String, name: String, phone: String?): User {
        val current = preferences.demoProfile.first() ?: error("NOT_AUTHENTICATED")
        val updated = current.copy(name = name.trim())
        preferences.setDemoProfile(updated)
        return updated.toUser().copy(phone = phone)
    }

    override suspend fun updatePushToken(userId: String, token: String) = Unit

    override suspend fun clearPushToken(userId: String) = Unit

    override suspend fun updateLocale(userId: String, locale: String) = Unit

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
    private val backend: DemoBackend,
    private val auth: AuthRemoteDataSource,
    private val preferences: UserPreferencesDataStore,
    private val photoDir: File,
    private val proofDir: File = File(photoDir.parentFile, "demo-proofs")
) : RestaurantRemoteDataSource {

    private fun requireUser(): String = auth.currentUserId() ?: error("NOT_AUTHENTICATED")

    override suspend fun fetchRestaurants(): List<Restaurant> = backend.allRestaurants()

    override suspend fun fetchRestaurant(restaurantId: String): Restaurant =
        backend.restaurant(restaurantId, auth.currentUserId())

    override suspend fun fetchAllQueueStats(): List<QueueStats> = backend.allStats()

    override suspend fun fetchQueueStats(restaurantId: String): QueueStats =
        backend.stats(restaurantId)

    /** Only the venues this account was registered against, like `restaurant_staff`. */
    override suspend fun fetchManagedRestaurants(userId: String): List<Restaurant> =
        backend.managedRestaurants(userId)

    override suspend fun registerRestaurant(registration: RestaurantRegistration): Restaurant {
        val userId = requireUser()
        // No Storage in demo mode: the photo lives in the app's own files.
        val imageUrl = registration.photo?.let { bytes ->
            withContext(Dispatchers.IO) {
                photoDir.mkdirs()
                File(photoDir, "${UUID.randomUUID()}.jpg").apply { writeBytes(bytes) }
            }.let { Uri.fromFile(it).toString() }
        }
        val profile = preferences.demoProfile.first()
        // A missing or unreadable proof is refused before any file is written.
        requireProof(registration.documents)
        val venue = backend.registerRestaurant(
            userId = userId,
            registration = registration,
            imageUrl = imageUrl,
            proofs = storeProofs(userId, registration.documents),
            applicantName = profile?.name.orEmpty(),
            applicantEmail = profile?.email.orEmpty()
        )
        // register_restaurant promotes the account; do the same here.
        profile?.let { preferences.setDemoProfile(it.copy(role = UserRole.STAFF.wireValue)) }
        return venue
    }

    override suspend fun setRestaurantActive(restaurantId: String, active: Boolean): Restaurant =
        backend.setRestaurantActive(requireUser(), restaurantId, active)

    override suspend fun resubmitRestaurant(
        restaurantId: String,
        contactPhone: String,
        applicantRole: ApplicantRole,
        documents: List<ProofDocument>
    ): Restaurant {
        val userId = requireUser()
        requireProof(documents)
        return backend.resubmitRestaurant(
            userId = userId,
            restaurantId = restaurantId,
            contactPhone = contactPhone,
            applicantRole = applicantRole,
            documents = documents,
            proofs = storeProofs(userId, documents)
        )
    }

    /** The SQL rule: a business document is required and every file must be readable. */
    private fun requireProof(documents: List<ProofDocument>) {
        if (documents.none { it.type == ProofType.BUSINESS_LICENSE } || !documents.all { it.isValid }) {
            error("PROOF_REQUIRED")
        }
    }

    /** No Storage in demo mode: proofs live in the app's private files, per applicant. */
    private suspend fun storeProofs(userId: String, documents: List<ProofDocument>): List<DemoBackend.ProofFile> =
        withContext(Dispatchers.IO) {
            val dir = File(proofDir, userId).apply { mkdirs() }
            documents.map { doc ->
                val file = File(dir, "${UUID.randomUUID()}.${doc.extension}").apply { writeBytes(doc.bytes) }
                DemoBackend.ProofFile(doc.type, Uri.fromFile(file).toString())
            }
        }

    override fun observeQueueStatsChanges(restaurantId: String): Flow<Unit> =
        backend.changes
            .filter { it.restaurantId == null || it.restaurantId == restaurantId }
            .map { }
            .onStart { emit(Unit) }

    override fun observeAllQueueStatsChanges(): Flow<Unit> =
        backend.changes.map { }.onStart { emit(Unit) }
}

/** Sample-mode reviewer: the same rules as the `admin_*` functions, on the demo backend. */
class DemoAdminRemoteDataSource(
    private val backend: DemoBackend,
    private val auth: AuthRemoteDataSource
) : AdminRemoteDataSource {

    private fun requireUser(): String = auth.currentUserId() ?: error("NOT_AUTHENTICATED")

    override suspend fun fetchApplications(status: VerificationStatus): List<RestaurantApplication> =
        backend.listApplications(requireUser(), status)

    override suspend fun fetchDocuments(restaurantId: String): List<ApplicationDocument> =
        backend.applicationDocuments(requireUser(), restaurantId)

    // The "path" of a demo document already is a file URI on this device.
    override suspend fun documentUrl(document: ApplicationDocument): String = document.path

    override suspend fun review(restaurantId: String, approve: Boolean, note: String?) =
        backend.reviewRestaurant(requireUser(), restaurantId, approve, note)
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

    // Same rule as `is_restaurant_staff` in the SQL functions: being signed in
    // as staff is not enough, the account must run this venue.
    private suspend fun requireStaff(restaurantId: String) {
        if (!backend.isStaff(requireUser(), restaurantId)) error("NOT_RESTAURANT_STAFF")
    }

    override suspend fun callNext(restaurantId: String): QueueEntry {
        requireStaff(restaurantId)
        return backend.callNext(restaurantId)
    }

    override suspend fun updateStatus(queueId: String, status: QueueStatus): QueueEntry {
        val entry = backend.queue(queueId)
        // A customer may cancel their own ticket; anything else is staff-only.
        val ownCancel = status == QueueStatus.CANCELLED && entry.userId == requireUser()
        if (!ownCancel) requireStaff(entry.restaurantId)
        return backend.updateStatus(queueId, status)
    }

    override suspend fun expireOverdue(restaurantId: String) = backend.expireOverdue(restaurantId)

    override suspend fun fetchQueue(queueId: String): QueueEntry = backend.queue(queueId)

    override suspend fun fetchPosition(queueId: String): QueuePosition = backend.position(queueId)

    override suspend fun fetchActiveQueue(userId: String): QueueEntry? =
        backend.activeQueueFor(userId)

    override suspend fun fetchHistory(userId: String): List<QueueEntry> =
        backend.historyFor(userId)

    // Row level security in the real backend: staff read the whole line, anyone
    // else only their own tickets.
    override suspend fun fetchRestaurantQueue(restaurantId: String): List<QueueEntry> {
        val userId = requireUser()
        val line = backend.queueFor(restaurantId)
        return if (backend.isStaff(userId, restaurantId)) line else line.filter { it.userId == userId }
    }

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

    override suspend fun fetchUserReviews(userId: String): List<Review> =
        backend.reviewsByUser(userId)

    override suspend fun updateReview(reviewId: String, rating: Int, comment: String): Review =
        backend.updateReview(reviewId, rating, comment)

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
