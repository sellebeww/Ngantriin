package org.umn.ngantriin.domain.repository

import kotlinx.coroutines.flow.Flow
import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.domain.model.ActiveQueue
import org.umn.ngantriin.domain.model.QueueEntry
import org.umn.ngantriin.domain.model.QueueHistoryItem
import org.umn.ngantriin.domain.model.QueueStatus

interface QueueRepository {

    /**
     * Satu tiket aktif milik pelanggan, atau null. Didukung langganan
     * Supabase Realtime, tanpa polling dan tanpa tarik untuk refresh.
     */
    fun observeActiveQueue(userId: String): Flow<ActiveQueue?>

    /** Semua tiket aktif di sebuah restoran, urut nomor tiket. */
    fun observeRestaurantQueue(restaurantId: String): Flow<List<QueueEntry>>

    /**
     * Tiket terbaru milik pelanggan, aktif atau sudah selesai. Berbeda dengan
     * [observeActiveQueue], ini terus mengirim setelah tiket berakhir, jadi
     * aplikasi tahu tiketnya "selesai" atau "kedaluwarsa" dan bisa bereaksi.
     */
    fun observeLatestTicket(userId: String): Flow<QueueEntry?>

    fun observeHistory(userId: String): Flow<List<QueueHistoryItem>>

    /** Satu tiket lama (atau aktif) beserta restorannya, untuk halaman detail riwayat. */
    fun observeHistoryItem(queueId: String): Flow<QueueHistoryItem?>

    suspend fun refreshHistory(userId: String): Outcome<Unit>

    suspend fun getQueue(queueId: String): Outcome<QueueEntry>

    /**
     * Backend yang memberi nomor, ini mengembalikan apa pun yang diberikannya.
     * Gagal dengan [org.umn.ngantriin.core.AppError.AlreadyInQueue]
     * kalau pelanggan sudah memegang tiket aktif.
     */
    suspend fun joinQueue(
        restaurantId: String,
        partySize: Int,
        note: String?
    ): Outcome<QueueEntry>

    /** Status aktif apa pun -> CANCELLED, tidak bisa kembali. */
    suspend fun leaveQueue(queueId: String): Outcome<QueueEntry>

    /**
     * True kalau perangkat ini sendiri yang membatalkan [queueId], supaya tiket
     * CANCELLED bisa dibedakan dari yang dihapus restoran.
     */
    fun wasLeftByCustomer(queueId: String): Boolean

    /**
     * Pelanggan memotret dirinya di restoran, lebih awal (WAITING / ALMOST_THERE)
     * atau setelah CALLED selama masih di jendela check-in. Backend memastikan
     * objek yang diunggah ada sebelum mengizinkan CHECKED_IN, jadi client tidak
     * bisa melewati foto.
     */
    suspend fun checkIn(queueId: String, restaurantId: String, photo: ByteArray): Outcome<QueueEntry>

    /** Mengubah referensi foto check-in yang tersimpan menjadi URL yang bisa dimuat. */
    suspend fun resolveCheckInPhotoUrl(photoUrl: String): Outcome<String>

    /** Staf memanggil tiket yang paling lama menunggu. */
    suspend fun callNext(restaurantId: String): Outcome<QueueEntry>

    /** Staf memindahkan satu tiket sepanjang state machine. */
    suspend fun updateStatus(queueId: String, status: QueueStatus): Outcome<QueueEntry>

    /**
     * Menyelesaikan tiket CALLED yang jendela check-in-nya sudah habis.
     * Dipanggil saat hitung mundur di layar sampai nol, supaya UI tidak
     * menunggu sapuan terjadwal dari backend.
     */
    suspend fun expireOverdue(restaurantId: String): Outcome<Unit>
}
