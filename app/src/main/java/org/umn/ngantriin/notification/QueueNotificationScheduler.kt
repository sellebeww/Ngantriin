package org.umn.ngantriin.notification

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.umn.ngantriin.data.local.dao.NotificationDao
import org.umn.ngantriin.domain.model.ActiveQueue
import org.umn.ngantriin.domain.model.NotificationType
import org.umn.ngantriin.domain.model.QueueEntry
import org.umn.ngantriin.domain.model.QueueMath
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.domain.repository.AuthRepository
import org.umn.ngantriin.domain.repository.NotificationRepository
import org.umn.ngantriin.domain.repository.QueueRepository
import org.umn.ngantriin.domain.repository.RestaurantRepository
import org.umn.ngantriin.domain.repository.SessionState
import org.umn.ngantriin.domain.repository.UserPreferencesRepository
import java.time.Duration
import java.time.Instant

/**
 * Sisi lokal.
 *
 * Push adalah jalur utama: Edge Function Supabase memantau tabel `queues` dan
 * mengirim lewat FCM, yang sampai ke HP baik aplikasi jalan maupun tidak. Scheduler
 * ini menutup kasus saat push belum terpasang (tanpa google-services.json, atau
 * mode demo) dengan menurunkan milestone yang sama dari state antrean yang
 * sama selama proses hidup. [QueueTrackingService] yang menjaga proses tetap
 * hidup setelah aplikasi masuk background.
 *
 * Kedua jalur menulis lewat [NotificationRepository] dengan id
 * `"<queueId>:<TYPE>"`, cermin di sisi client dari constraint `unique (queue_id, type)`.
 * Itulah yang mencegah notifikasi muncul lagi tiap posisi kebetulan berubah.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QueueNotificationScheduler(
    private val context: Context,
    private val authRepository: AuthRepository,
    private val queueRepository: QueueRepository,
    private val restaurantRepository: RestaurantRepository,
    private val notificationRepository: NotificationRepository,
    private val notificationDao: NotificationDao,
    private val notificationHelper: NotificationHelper,
    private val preferences: UserPreferencesRepository
) {

    fun start(scope: CoroutineScope) {
        if (notificationHelper.isPushConfigured) {
            Log.i(TAG, "FCM configured; leaving queue notifications to push.")
            return
        }

        val userIds = authRepository.sessionState
            .map { (it as? SessionState.SignedIn)?.user?.id }
            .distinctUntilChanged()

        // Milestone live: butuh antrean di sekitar tiket.
        scope.launch {
            userIds
                .flatMapLatest { id -> id?.let { queueRepository.observeActiveQueue(it) } ?: flowOf(null) }
                .distinctUntilChanged()
                .collect { queue -> queue?.let { notifyMilestone(it) } }
        }

        // Akhir tiket: tiket yang selesai keluar dari flow aktif, jadi pantau
        // tiket terbaru.
        scope.launch {
            userIds
                .flatMapLatest { id -> id?.let { queueRepository.observeLatestTicket(it) } ?: flowOf(null) }
                .distinctUntilChanged()
                .collect { entry -> entry?.let { notifyEnding(it) } }
        }
    }

    private suspend fun notifyMilestone(queue: ActiveQueue) {
        val userId = authRepository.currentUserOrNull()?.id ?: return
        // Posisi tebakan tidak boleh menghabiskan milestone sekali pakai: tanpa
        // counter, hasilnya "0 di depan" dan "segera kembali" langsung terkirim
        // begitu tiket dibuat.
        if (!queue.isPositionConfirmed && queue.entry.isWaitingToBeCalled) return

        // Konfirmasi join. Hanya tiket baru, supaya memulihkan tiket lama di
        // instalasi baru tidak mengumumkan join berjam-jam lalu.
        val justJoined = Duration.between(queue.entry.joinedAt, Instant.now()) <= JOIN_RECENT &&
            emit(QueueNotificationCopy.build(context, userId, queue, NotificationType.QUEUE_JOINED))

        val milestone = QueueMath.notificationMilestone(queue.status, queue.peopleAhead) ?: return
        // Join ke antrean pendek sudah menyebut berapa yang di depan. Catat
        // milestone supaya tidak terkirim lagi nanti, tapi jangan bunyi dua kali sekaligus.
        emit(QueueNotificationCopy.build(context, userId, queue, milestone), silent = justJoined)

        // Mencapai CALLED berarti milestone sebelumnya sudah terjadi, walau aplikasi
        // tertutup saat itu. Mengisi ulang membuat daftar di aplikasi terbaca sebagai
        // riwayat, bukan satu entri yatim.
        if (milestone == NotificationType.CALLED) {
            emit(QueueNotificationCopy.build(context, userId, queue, NotificationType.RETURN_NOW), silent = true)
            emit(QueueNotificationCopy.build(context, userId, queue, NotificationType.ALMOST_THERE), silent = true)
        }
    }

    private suspend fun notifyEnding(entry: QueueEntry) {
        val type = when (entry.status) {
            QueueStatus.COMPLETED -> NotificationType.COMPLETED
            QueueStatus.EXPIRED -> NotificationType.EXPIRED
            // Pergi dengan sengaja tidak perlu notifikasi "antreanmu dibatalkan".
            QueueStatus.CANCELLED ->
                if (queueRepository.wasLeftByCustomer(entry.id)) return else NotificationType.CANCELLED
            else -> return
        }
        // Baru jadi kabar kalau baru saja terjadi. Instalasi baru yang menyinkronkan
        // riwayat minggu lalu tidak boleh menyalakan tray.
        val finishedAt = entry.finishedAt ?: return
        if (Duration.between(finishedAt, Instant.now()) > RECENT) return

        val userId = authRepository.currentUserOrNull()?.id ?: return
        if (entry.userId != userId) return
        val window = restaurantRepository.observeRestaurant(entry.restaurantId).first()
            ?.checkInWindowMinutes ?: QueueMath.DEFAULT_CHECK_IN_WINDOW_MINUTES
        emit(QueueNotificationCopy.build(context, userId, entry, window, type))
    }

    private suspend fun emit(
        notification: org.umn.ngantriin.domain.model.AppNotification,
        silent: Boolean = false
    ): Boolean {
        if (notificationDao.countById(notification.id) > 0) return false
        notificationRepository.record(notification)
        // Pengaturan -> Notifikasi antrean: daftar di aplikasi tetap mencatatnya,
        // hanya system tray yang diam.
        if (!silent && preferences.notificationsEnabled.first()) {
            notificationHelper.show(notification)
        }
        return true
    }

    private companion object {
        const val TAG = "Ngantriin"
        val RECENT: Duration = Duration.ofMinutes(30)
        val JOIN_RECENT: Duration = Duration.ofMinutes(5)
    }
}
