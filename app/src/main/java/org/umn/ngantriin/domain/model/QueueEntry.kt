package org.umn.ngantriin.domain.model

import java.time.Duration
import java.time.Instant

/** Tiket satu rombongan pelanggan untuk satu kunjungan. */
data class QueueEntry(
    val id: String,
    val restaurantId: String,
    val userId: String,
    /** Label untuk pengguna, misalnya "A-027". Selalu dibuat backend. */
    val queueNumber: String,
    /** Counter naik per restoran di balik [queueNumber], dipakai untuk urutan. */
    val ticketSequence: Int,
    val partySize: Int,
    val status: QueueStatus,
    val note: String? = null,
    val joinedAt: Instant,
    val calledAt: Instant? = null,
    val checkedInAt: Instant? = null,
    val completedAt: Instant? = null,
    val cancelledAt: Instant? = null,
    /** Path storage foto verifikasi check-in, terisi sekali saat CHECKED_IN. */
    val checkInPhotoUrl: String? = null,
    /** Terisi saat CALLED: check-in sebelum ini atau tiket EXPIRED. */
    val checkInDeadline: Instant? = null,
    val tableReadyAt: Instant? = null,
    val expiredAt: Instant? = null
) {
    val isActive: Boolean get() = status.isActive

    /** Restoran sudah pernah sampai ke tiket ini. */
    val hasBeenCalled: Boolean get() = calledAt != null

    /**
     * Masih menunggu restoran sampai ke tiketnya, termasuk pelanggan yang
     * check-in lebih awal dan duduk di ruang tunggu.
     */
    val isWaitingToBeCalled: Boolean
        get() = status == QueueStatus.WAITING || status == QueueStatus.ALMOST_THERE ||
            (status == QueueStatus.CHECKED_IN && !hasBeenCalled)

    /** Check-in sebelum tiket dipanggil restoran. */
    val checkedInEarly: Boolean
        get() = status == QueueStatus.CHECKED_IN && !hasBeenCalled

    /** Kapan tiket mencapai status akhir, kalau sudah. */
    val finishedAt: Instant? get() = completedAt ?: cancelledAt ?: expiredAt

    /** Sisa detik di jendela check-in, atau null kalau tidak ada jendela yang berjalan. */
    fun checkInSecondsLeft(now: Instant = ServerClock.now()): Long? {
        if (status != QueueStatus.CALLED) return null
        val deadline = checkInDeadline ?: return null
        return Duration.between(now, deadline).seconds.coerceAtLeast(0)
    }

    /** True kalau tiket CALLED sudah melewati jendelanya (kedaluwarsa bisa telat). */
    fun isCheckInOverdue(now: Instant = ServerClock.now()): Boolean =
        checkInSecondsLeft(now) == 0L

    /**
     * Berapa lama pelanggan sebenarnya menunggu: dari join sampai dipanggil,
     * atau sampai tiket berakhir kalau tidak pernah dipanggil.
     */
    val waitedMinutes: Int?
        get() {
            val end = calledAt ?: finishedAt ?: return null
            return Duration.between(joinedAt, end).toMinutes().toInt().coerceAtLeast(0)
        }
}

/**
 * Semua yang dibutuhkan layar Antrean Aktif dalam satu objek, supaya UI tidak
 * perlu menggabungkan tiga flow sendiri.
 */
data class ActiveQueue(
    val entry: QueueEntry,
    val restaurant: Restaurant,
    val stats: QueueStats,
    /** Rombongan yang masih di depan tiket ini. */
    val peopleAhead: Int,
    val estimatedWaitMinutes: Int,
    /**
     * False selama [peopleAhead] baru perkiraan dari counter cache (posisi
     * dari server belum datang). Cukup untuk ditampilkan, belum cukup untuk
     * memicu notifikasi "segera kembali".
     */
    val isPositionConfirmed: Boolean = true
) {
    /** Urutan mulai dari 1: "#7" kalau ada enam rombongan di depan. */
    val position: Int get() = peopleAhead + 1

    val queueNumber: String get() = entry.queueNumber
    val status: QueueStatus get() = entry.status

    /**
     * Pelanggan bisa check-in begitu sudah di restoran, lebih awal atau
     * setelah dipanggil selama masih dalam jendela.
     */
    val canCheckIn: Boolean
        get() = entry.status.allowsCheckIn && !entry.isCheckInOverdue()

    /** Sekarang giliran tiket ini dan waktunya berjalan. */
    val isCheckInCountdown: Boolean get() = entry.status == QueueStatus.CALLED

    /** Tiket yang masih aktif boleh dilepas. */
    val canLeave: Boolean get() = entry.status.isActive

    /** Posisi dan waktu tunggu baru berarti sampai restoran memanggilmu. */
    val showsLinePosition: Boolean get() = entry.isWaitingToBeCalled
}

/** Tiket lama, seperti ditampilkan di layar riwayat. */
data class QueueHistoryItem(
    val entry: QueueEntry,
    val restaurantName: String,
    val restaurantImageUrl: String?,
    val hasReview: Boolean
)
