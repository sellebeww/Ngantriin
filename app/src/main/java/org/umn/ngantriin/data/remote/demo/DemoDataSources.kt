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
 * Data source mode demo. Masing-masing memenuhi interface yang sama dengan
 * pasangannya di Supabase, jadi repository di atasnya tidak bisa tahu mana yang jalan.
 */

/**
 * Mode demo tidak punya autentikasi: email apa pun bisa masuk dan tidak ada
 * password yang disimpan atau dicek. Ini pilihan sengaja daripada memalsukan
 * penanganan kredensial. Jalur aslinya Supabase Auth, dan pura-pura memverifikasi
 * password secara lokal hanya terlihat seperti keamanan.
 *
 * Email berawalan "staff" masuk dengan role STAFF, supaya dashboard restoran bisa
 * dibuka tanpa backend. Restoran mana yang dikelola ditentukan terpisah oleh
 * keanggotaan staf di [DemoBackend]: tiap login contoh di [DemoSeed.STAFF_ACCOUNTS]
 * mengelola satu restoran contoh, dan akun yang mendaftarkan restoran mengelola
 * restoran itu.
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
            // Pakai id yang sama kalau orang yang sama masuk lagi, supaya riwayat
            // antreannya tetap ada setelah keluar.
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

    // Mode contoh tidak mengirim email reset, jadi tidak ada tautan yang diterima.
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

    /** Hanya restoran yang didaftarkan atas akun ini, seperti `restaurant_staff`. */
    override suspend fun fetchManagedRestaurants(userId: String): List<Restaurant> =
        backend.managedRestaurants(userId)

    override suspend fun registerRestaurant(registration: RestaurantRegistration): Restaurant {
        val userId = requireUser()
        // Mode demo tidak punya Storage: foto disimpan di file milik aplikasi.
        val imageUrl = registration.photo?.let { bytes ->
            withContext(Dispatchers.IO) {
                photoDir.mkdirs()
                File(photoDir, "${UUID.randomUUID()}.jpg").apply { writeBytes(bytes) }
            }.let { Uri.fromFile(it).toString() }
        }
        val profile = preferences.demoProfile.first()
        // Bukti yang hilang atau tidak terbaca ditolak sebelum ada file yang ditulis.
        requireProof(registration.documents)
        val venue = backend.registerRestaurant(
            userId = userId,
            registration = registration,
            imageUrl = imageUrl,
            proofs = storeProofs(userId, registration.documents),
            applicantName = profile?.name.orEmpty(),
            applicantEmail = profile?.email.orEmpty()
        )
        // register_restaurant menaikkan role akun, di sini dibuat sama.
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

    /** Aturan SQL: dokumen usaha wajib ada dan semua berkas harus terbaca. */
    private fun requireProof(documents: List<ProofDocument>) {
        if (documents.none { it.type == ProofType.BUSINESS_LICENSE } || !documents.all { it.isValid }) {
            error("PROOF_REQUIRED")
        }
    }

    /** Mode demo tidak punya Storage: bukti disimpan di file privat aplikasi, per pendaftar. */
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

/** Reviewer mode contoh: aturannya sama dengan fungsi `admin_*`, di backend demo. */
class DemoAdminRemoteDataSource(
    private val backend: DemoBackend,
    private val auth: AuthRemoteDataSource
) : AdminRemoteDataSource {

    private fun requireUser(): String = auth.currentUserId() ?: error("NOT_AUTHENTICATED")

    override suspend fun fetchApplications(status: VerificationStatus): List<RestaurantApplication> =
        backend.listApplications(requireUser(), status)

    override suspend fun fetchDocuments(restaurantId: String): List<ApplicationDocument> =
        backend.applicationDocuments(requireUser(), restaurantId)

    // "Path" dokumen demo sudah berupa URI file di perangkat ini.
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

    // Mode demo tidak punya Storage asli, jadi byte foto dibuang di sini.
    // Yang penting untuk menguji state machine: checkIn() tetap mewajibkan
    // referensi foto yang tidak kosong, sama seperti RPC aslinya.
    override suspend fun checkIn(queueId: String, restaurantId: String, photo: ByteArray): QueueEntry =
        backend.checkIn(requireUser(), queueId, photoUrl = "demo-checkin-photo:$queueId")

    override suspend fun resolveCheckInPhotoUrl(photoUrl: String): String = photoUrl

    // Aturan sama dengan `is_restaurant_staff` di fungsi SQL: login sebagai
    // staf saja tidak cukup, akunnya harus mengelola restoran ini.
    private suspend fun requireStaff(restaurantId: String) {
        if (!backend.isStaff(requireUser(), restaurantId)) error("NOT_RESTAURANT_STAFF")
    }

    override suspend fun callNext(restaurantId: String): QueueEntry {
        requireStaff(restaurantId)
        return backend.callNext(restaurantId)
    }

    override suspend fun updateStatus(queueId: String, status: QueueStatus): QueueEntry {
        val entry = backend.queue(queueId)
        // Pelanggan boleh membatalkan tiketnya sendiri, selain itu khusus staf.
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

    // Row level security di backend asli: staf membaca seluruh antrean,
    // yang lain hanya tiketnya sendiri.
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
 * Notifikasi demo dibuat lokal oleh
 * [org.umn.ngantriin.notification.QueueNotificationScheduler], bukan
 * dikirim dari server, jadi source ini cukup menerimanya.
 */
class DemoNotificationRemoteDataSource : NotificationRemoteDataSource {
    override suspend fun fetchNotifications(userId: String): List<AppNotification> = emptyList()
    override suspend fun markRead(notificationId: String) = Unit
    override suspend fun markAllRead(userId: String) = Unit
    override suspend fun record(notification: AppNotification) = Unit
}
