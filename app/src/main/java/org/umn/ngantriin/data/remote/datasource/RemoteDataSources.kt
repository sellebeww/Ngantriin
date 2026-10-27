package org.umn.ngantriin.data.remote.datasource

import org.umn.ngantriin.domain.model.ProofDocument
import org.umn.ngantriin.domain.model.ApplicantRole
import android.content.Intent
import kotlinx.coroutines.flow.Flow
import org.umn.ngantriin.domain.model.AppNotification
import org.umn.ngantriin.domain.model.ApplicationDocument
import org.umn.ngantriin.domain.model.RestaurantApplication
import org.umn.ngantriin.domain.model.VerificationStatus
import org.umn.ngantriin.domain.model.QueueEntry
import org.umn.ngantriin.domain.model.QueuePosition
import org.umn.ngantriin.domain.model.QueueStats
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.domain.model.Restaurant
import org.umn.ngantriin.domain.model.RestaurantRegistration
import org.umn.ngantriin.domain.model.Review
import org.umn.ngantriin.domain.model.User

/**
 * Kontrak backend, dinyatakan di satu tempat.
 *
 * `Supabase*` mengimplementasikannya ke proyek asli, `Demo*` ke tiruan
 * in-process supaya aplikasi bisa jalan sebelum ada kredensial. Repository
 * hanya melihat interface ini, jadi kedua jalur tetap jujur: demo tidak bisa
 * diam-diam berbeda state machine-nya karena harus memenuhi signature yang sama.
 *
 * Semua fungsi di sini melempar exception kalau gagal. Mengubahnya jadi
 * [org.umn.ngantriin.core.AppError] adalah tugas repository.
 */

sealed interface RemoteSession {
    /** Memulihkan sesi yang tersimpan, jangan arahkan ke layar mana pun dulu. */
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

    /**
     * Mengimpor sesi dari tautan reset password. [onRecovered] dipanggil
     * sekali sesinya siap dipakai, tautan yang bukan milik kita diabaikan.
     */
    fun handleRecoveryLink(intent: Intent, onRecovered: () -> Unit)

    suspend fun updatePassword(newPassword: String)

    suspend fun fetchProfile(userId: String): User
    suspend fun updateProfile(userId: String, name: String, phone: String?): User
    suspend fun updatePushToken(userId: String, token: String)

    /** Melepas akun ini dari perangkat supaya push-nya tidak masuk ke sini lagi. */
    suspend fun clearPushToken(userId: String)

    /** Bahasa yang dipakai server untuk menulis push ("id" atau "en"). */
    suspend fun updateLocale(userId: String, locale: String)
}

interface RestaurantRemoteDataSource {
    suspend fun fetchRestaurants(): List<Restaurant>
    suspend fun fetchRestaurant(restaurantId: String): Restaurant
    suspend fun fetchAllQueueStats(): List<QueueStats>
    suspend fun fetchQueueStats(restaurantId: String): QueueStats?
    suspend fun fetchManagedRestaurants(userId: String): List<Restaurant>

    /** Membuat restoran dan menjadikan akun yang login sebagai stafnya. */
    suspend fun registerRestaurant(registration: RestaurantRegistration): Restaurant

    suspend fun setRestaurantActive(restaurantId: String, active: Boolean): Restaurant

    /** Memperbaiki pengajuan REJECTED lalu mengembalikannya ke review. */
    suspend fun resubmitRestaurant(
        restaurantId: String,
        contactPhone: String,
        applicantRole: ApplicantRole,
        documents: List<ProofDocument>
    ): Restaurant

    /** Menyala tiap kali counter restoran ini berubah. */
    fun observeQueueStatsChanges(restaurantId: String): Flow<Unit>

    /** Menyala tiap kali counter restoran mana pun berubah, untuk daftar pencarian. */
    fun observeAllQueueStatsChanges(): Flow<Unit>
}

/** Review restoran. Database yang mengotorisasi tiap panggilan, aplikasi hanya meminta. */
interface AdminRemoteDataSource {
    suspend fun fetchApplications(status: VerificationStatus): List<RestaurantApplication>
    suspend fun fetchDocuments(restaurantId: String): List<ApplicationDocument>

    /** Berkas bukti bersifat privat, ini tautan yang berlaku beberapa menit. */
    suspend fun documentUrl(document: ApplicationDocument): String

    suspend fun review(restaurantId: String, approve: Boolean, note: String?)
}

interface QueueRemoteDataSource {
    suspend fun joinQueue(restaurantId: String, partySize: Int, note: String?): QueueEntry
    suspend fun leaveQueue(queueId: String): QueueEntry

    /**
     * Mengunggah foto check-in sekaligus memanggil `check_in_queue`.
     * Upload butuh [restaurantId] (untuk path storage) yang baru dikembalikan
     * baris antrean setelah RPC berhasil.
     */
    suspend fun checkIn(queueId: String, restaurantId: String, photo: ByteArray): QueueEntry

    /**
     * Mengubah referensi foto check-in yang tersimpan menjadi sesuatu yang bisa
     * dimuat. Implementasi asli menandatangani path Storage privat, implementasi
     * demo tidak punya Storage jadi nilainya dikembalikan apa adanya (sudah berupa
     * URI lokal, lihat [DemoQueueRemoteDataSource]).
     */
    suspend fun resolveCheckInPhotoUrl(photoUrl: String): String

    suspend fun callNext(restaurantId: String): QueueEntry
    suspend fun updateStatus(queueId: String, status: QueueStatus): QueueEntry

    /**
     * Menandai tiket CALLED yang lewat batas check-in sebagai EXPIRED.
     * Backend juga melakukannya terjadwal, memanggil ini hanya menyelesaikan
     * hitung mundur yang sudah nol tanpa menunggu sapuan berikutnya.
     */
    suspend fun expireOverdue(restaurantId: String)

    suspend fun fetchQueue(queueId: String): QueueEntry

    /**
     * Dihitung di server. Pelanggan tidak bisa melihat tiket di depannya,
     * jadi menghitung antrean adalah tugas backend.
     */
    suspend fun fetchPosition(queueId: String): QueuePosition
    suspend fun fetchActiveQueue(userId: String): QueueEntry?
    suspend fun fetchHistory(userId: String): List<QueueEntry>
    suspend fun fetchRestaurantQueue(restaurantId: String): List<QueueEntry>

    /**
     * Sinyal perubahan, bukan isi perubahan: repository membaca ulang baris
     * yang otoritatif di setiap tick, jadi event yang hilang atau tidak
     * berurutan tidak bisa membuat UI menampilkan antrean yang tidak pernah ada.
     */
    fun observeUserQueueChanges(userId: String): Flow<Unit>
    fun observeRestaurantQueueChanges(restaurantId: String): Flow<Unit>
}

interface ReviewRemoteDataSource {
    suspend fun fetchReviews(restaurantId: String): List<Review>
    suspend fun fetchReviewForQueue(queueId: String): Review?

    /** Semua ulasan yang ditulis pelanggan ini, supaya riwayat tahu mana yang sudah diulas. */
    suspend fun fetchUserReviews(userId: String): List<Review>

    /** Hanya rating dan teks yang bisa diubah, kunjungannya tetap. */
    suspend fun updateReview(reviewId: String, rating: Int, comment: String): Review
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
