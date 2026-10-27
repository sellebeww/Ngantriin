package org.umn.ngantriin.domain.model

/**
 * State machine antrean. Nama-namanya sama satu-satu dengan enum Postgres
 * `queue_status`, justru itu gunanya ada enum di sini daripada mengoper
 * string mentah.
 *
 * WAITING -> ALMOST_THERE -> CALLED -> CHECKED_IN -> TABLE_READY -> COMPLETED
 *
 * - WAITING / ALMOST_THERE boleh check-in lebih awal (sudah di restoran).
 * - CALLED yang melewatkan jendela check-in menjadi EXPIRED.
 * - Status aktif mana pun bisa CANCELLED (pelanggan pergi, atau staf menghapus).
 */
enum class QueueStatus {
    /** Dalam antrean, cukup jauh dari depan sehingga pelanggan boleh di tempat lain. */
    WAITING,

    /** Sudah dalam ambang notifikasi, saatnya kembali. */
    ALMOST_THERE,

    /** Giliran tiket ini, jendela check-in berjalan. */
    CALLED,

    /** Pelanggan sudah di restoran (foto terverifikasi), meja sedang disiapkan. */
    CHECKED_IN,

    /** Meja siap, pelanggan menuju host untuk didudukkan. */
    TABLE_READY,

    /** Sudah dilayani. Status akhir, dan satu-satunya yang membuka ulasan. */
    COMPLETED,

    /** Keluar dari antrean, atau tiket dibuang restoran. Status akhir. */
    CANCELLED,

    /** Dipanggil tapi tidak check-in dalam jendela. Status akhir. */
    EXPIRED;

    /** True selama pelanggan masih memegang tiket. */
    val isActive: Boolean
        get() = this == WAITING || this == ALMOST_THERE || this == CALLED ||
            this == CHECKED_IN || this == TABLE_READY

    /** Masih punya tempat di antrean, di depan tiket yang datang belakangan. */
    val isInLine: Boolean
        get() = this == WAITING || this == ALMOST_THERE || this == CALLED || this == CHECKED_IN

    /** Pelanggan boleh check-in dari sini (lebih awal, atau setelah dipanggil). */
    val allowsCheckIn: Boolean
        get() = this == WAITING || this == ALMOST_THERE || this == CALLED

    /** Pelanggan secara fisik ada di restoran. */
    val isAtRestaurant: Boolean
        get() = this == CHECKED_IN || this == TABLE_READY

    /**
     * Meniru `is_valid_queue_transition` di 0012_check_in_window_and_table_ready.sql.
     * Aturan ada di dua tempat supaya perpindahan ilegal ditolak sebelum
     * keluar dari HP, *dan* kalau entah bagaimana sampai ke database.
     */
    fun canTransitionTo(next: QueueStatus): Boolean = when (this) {
        WAITING -> next == ALMOST_THERE || next == CALLED || next == CHECKED_IN || next == CANCELLED
        ALMOST_THERE -> next == CALLED || next == CHECKED_IN || next == CANCELLED
        CALLED -> next == CHECKED_IN || next == CANCELLED || next == EXPIRED
        CHECKED_IN -> next == TABLE_READY || next == CANCELLED
        TABLE_READY -> next == COMPLETED || next == CANCELLED
        COMPLETED, CANCELLED, EXPIRED -> false
    }

    /** Nama di wire. Sama dengan [name], ditulis eksplisit supaya pemanggil tidak menebak. */
    val wireValue: String get() = name

    companion object {
        /**
         * Membaca status dari backend atau payload push. Nilai yang tidak dikenal
         * jatuh ke [WAITING] daripada membuat layar crash, karena status
         * yang tidak dikenali tetap berarti "kamu ada di antrean".
         */
        fun fromWire(value: String?): QueueStatus =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: WAITING

        /** Status yang masih memegang tiket, dalam bentuk list untuk query. */
        val ACTIVE: List<QueueStatus> = entries.filter { it.isActive }
    }
}
