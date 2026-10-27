package org.umn.ngantriin.domain.model

/**
 * Satu-satunya tempat yang mengubah antrean menjadi angka-angka utama produk.
 * Repository berbasis Supabase dan backend demo sama-sama lewat sini, dan
 * setiap aturannya meniru fungsi SQL di 0012_check_in_window_and_table_ready.sql,
 * jadi estimasi tidak akan berbeda tergantung asal datanya.
 */
object QueueMath {

    /**
     * Rombongan di depan [ticketSequence] yang belum didudukkan.
     * Tiket CALLED atau CHECKED_IN tetap dihitung karena mejanya belum kosong.
     * Meniru `queue_position`.
     */
    fun peopleAhead(entries: List<QueueEntry>, ticketSequence: Int): Int =
        entries.count { it.ticketSequence < ticketSequence && it.status.isInLine }

    /** Rata-rata waktu layanan x jumlah orang di depan. */
    fun estimatedWaitMinutes(peopleAhead: Int, averageServiceMinutes: Int): Int =
        (peopleAhead.coerceAtLeast(0) * averageServiceMinutes.coerceAtLeast(1))

    /**
     * Estimasi untuk rombongan yang bergabung sekarang: semua yang masih
     * punya tempat di antrean dikali kecepatan layanan. Sama dengan
     * [estimatedWaitMinutes] untuk tiket berikutnya yang akan terbit, jadi kartu
     * restoran dan tiket baru tidak pernah berbeda. Meniru `refresh_queue_stats`.
     */
    fun estimatedWaitForNewGroup(entries: List<QueueEntry>, averageServiceMinutes: Int): Int =
        estimatedWaitMinutes(entries.count { it.status.isInLine }, averageServiceMinutes)

    /**
     * Rombongan yang belum dipanggil restoran, yaitu "12 rombongan menunggu" di
     * kartu restoran. Meniru `refresh_queue_stats`.
     */
    fun waitingCount(entries: List<QueueEntry>): Int =
        entries.count { it.isWaitingToBeCalled }

    /** Tiket yang akan dipilih `call_next`: nomor terkecil yang belum dipanggil. */
    fun nextToCall(entries: List<QueueEntry>): QueueEntry? =
        entries.filter { it.isWaitingToBeCalled }.minByOrNull { it.ticketSequence }

    /**
     * Nomor di papan "SEDANG DILAYANI": tiket tertinggi yang sudah dipanggil dan
     * belum selesai, atau yang terakhir selesai kalau tidak ada.
     */
    fun currentServing(entries: List<QueueEntry>): QueueEntry? =
        entries.filter {
            it.hasBeenCalled && (
                it.status == QueueStatus.CALLED ||
                    it.status == QueueStatus.CHECKED_IN ||
                    it.status == QueueStatus.TABLE_READY
                )
        }.maxByOrNull { it.ticketSequence }
            ?: entries.filter { it.status == QueueStatus.COMPLETED }
                .maxByOrNull { it.ticketSequence }

    /**
     * Ambang notifikasi. Mengembalikan milestone yang dicapai tiket, atau null
     * kalau masih terlalu jauh untuk perlu notifikasi.
     * Meniru `milestoneFor` di Edge Function queue-notifier.
     */
    fun notificationMilestone(status: QueueStatus, peopleAhead: Int): NotificationType? = when (status) {
        QueueStatus.CALLED -> NotificationType.CALLED
        QueueStatus.CHECKED_IN -> NotificationType.CHECKED_IN
        QueueStatus.TABLE_READY -> NotificationType.TABLE_READY
        QueueStatus.COMPLETED -> NotificationType.COMPLETED
        QueueStatus.CANCELLED -> NotificationType.CANCELLED
        QueueStatus.EXPIRED -> NotificationType.EXPIRED
        QueueStatus.WAITING, QueueStatus.ALMOST_THERE -> when {
            peopleAhead <= RETURN_NOW_THRESHOLD -> NotificationType.RETURN_NOW
            peopleAhead <= ALMOST_THERE_THRESHOLD -> NotificationType.ALMOST_THERE
            else -> null
        }
    }

    /** Pada jumlah rombongan di depan sebanyak ini atau kurang, tiket menjadi ALMOST_THERE. */
    const val ALMOST_THERE_THRESHOLD = 3

    /** Pada angka ini atau kurang, pelanggan disuruh segera kembali. */
    const val RETURN_NOW_THRESHOLD = 1

    /** Jendela check-in bawaan setelah dipanggil (restaurants.checkin_window_minutes). */
    const val DEFAULT_CHECK_IN_WINDOW_MINUTES = 10
}
