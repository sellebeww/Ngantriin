package org.umn.ngantriin.domain.model

import java.time.LocalTime

data class Restaurant(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val imageUrl: String?,
    val rating: Double,
    val ratingCount: Int,
    val openingTime: LocalTime,
    val closingTime: LocalTime,
    val isOpen: Boolean,
    /** Pengali di balik setiap estimasi waktu tunggu. */
    val averageServiceMinutes: Int,
    val queuePrefix: String,
    val queueCapacity: Int,
    /** Seberapa dekat pelanggan harus berada untuk check-in. */
    val checkInRadiusMeters: Int,
    /** Meja kosong sekarang, jadi walk-in dan bukan antrean. */
    val availableSeats: Int = 0,
    /** Menit yang dimiliki pelanggan yang dipanggil untuk check-in sebelum kedaluwarsa. */
    val checkInWindowMinutes: Int = QueueMath.DEFAULT_CHECK_IN_WINDOW_MINUTES,
    /** Tampil ke pelanggan. Staf bisa menjedanya, katalog Room hanya menyimpan restoran aktif. */
    val isActive: Boolean = true,
    /** Akses staf dan tampil di daftar sama-sama menunggu APPROVED. */
    val verificationStatus: VerificationStatus = VerificationStatus.APPROVED,
    /** Alasan reviewer, ditampilkan ke pendaftar setelah penolakan. */
    val verificationNote: String? = null
) {
    val isApproved: Boolean get() = verificationStatus == VerificationStatus.APPROVED

    val location: GeoPoint get() = GeoPoint(latitude, longitude)

    /** Ada kursi kosong, jadi belum perlu antre. */
    val hasAvailableSeats: Boolean get() = availableSeats > 0

    /** Jam buka sama dengan jam tutup berarti restoran tidak pernah tutup. */
    val isOpen24Hours: Boolean get() = openingTime == closingTime

    /** "10:00 - 22:00", atau "24 Jam" */
    val operatingHours: String
        get() = if (isOpen24Hours) "24 Jam" else "%02d:%02d - %02d:%02d".format(
            openingTime.hour, openingTime.minute, closingTime.hour, closingTime.minute
        )

    /**
     * `is_open` adalah saklar manual yang dipegang restoran, jam adalah jadwalnya.
     * Restoran dianggap buka hanya kalau keduanya setuju, jadi staf bisa menutup
     * antrean lebih awal tanpa mengubah jam buka.
     */
    fun isAcceptingQueue(now: LocalTime = LocalTime.now(RESTAURANT_ZONE)): Boolean {
        if (!isOpen) return false
        return if (closingTime > openingTime) {
            now >= openingTime && now < closingTime
        } else {
            // Tutup lewat tengah malam, misalnya 18:00 - 02:00.
            now >= openingTime || now < closingTime
        }
    }

    companion object {
        /**
         * Jam buka adalah waktu dinding lokal restoran. Dinilai dengan zona waktu
         * restoran (restaurants.timezone di SQL), bukan zona HP, supaya HP orang yang
         * bepergian tetap sama dengan keputusan `join_queue`.
         */
        val RESTAURANT_ZONE: java.time.ZoneId = java.time.ZoneId.of("Asia/Jakarta")
    }
}

/**
 * Restoran seperti yang tampil di daftar: restoran itu sendiri ditambah dua
 * fakta yang membuat aplikasi ini layak dibuka, yaitu seberapa panjang
 * antrean dan seberapa jauh. Jarak null sampai lokasi tersedia.
 */
data class RestaurantListing(
    val restaurant: Restaurant,
    val stats: QueueStats?,
    val distanceMeters: Double?
) {
    val id: String get() = restaurant.id
    val waitingCount: Int get() = stats?.waitingCount ?: 0
    val estimatedWaitMinutes: Int
        get() = stats?.estimatedWaitMinutes ?: (waitingCount * restaurant.averageServiceMinutes)
}

/** Posisi pengajuan restoran. Meniru `restaurants.verification_status`. */
enum class VerificationStatus {
    PENDING_VERIFICATION, APPROVED, REJECTED;

    companion object {
        /** Nilai yang tidak dikenal dibaca sebagai pending: jangan beri akses berdasarkan nilai yang tidak terbaca. */
        fun fromWire(value: String?): VerificationStatus =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: PENDING_VERIFICATION
    }
}

/** Hubungan pendaftar dengan restoran. Meniru `restaurants.applicant_role`. */
enum class ApplicantRole { OWNER, MANAGER, AUTHORIZED_STAFF }

/** Slot yang diisi sebuah berkas bukti. Minimal satu BUSINESS_LICENSE wajib ada. */
enum class ProofType { BUSINESS_LICENSE, OWNERSHIP_PROOF }

/** Berkas bukti yang dipilih di HP: JPEG (sudah diperkecil) atau PDF. */
class ProofDocument(
    val type: ProofType,
    val bytes: ByteArray,
    val isPdf: Boolean
) {
    val extension: String get() = if (isPdf) "pdf" else "jpg"

    /**
     * Berkas benar-benar sesuai klaimnya: dalam batas ukuran dan diawali
     * signature PDF atau JPEG. Executable yang diganti namanya gagal di sini sebelum
     * diunggah, lalu bucket menegakkan ekstensi dan tipe MIME di server.
     */
    val isValid: Boolean
        get() = bytes.size in 1..MAX_BYTES && bytes.startsWith(if (isPdf) PDF_MAGIC else JPEG_MAGIC)

    companion object {
        const val MAX_BYTES = 5 * 1024 * 1024
        private val PDF_MAGIC = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D)
        private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())

        private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
            size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
    }
}

/**
 * Yang diisi staf untuk memasukkan restoran ke aplikasi. Foto berupa byte JPEG,
 * bukan URL: ke mana foto itu disimpan (Storage, atau perangkat di mode contoh)
 * urusan data source.
 */
class RestaurantRegistration(
    val name: String,
    val address: String,
    val category: String,
    val description: String,
    val photo: ByteArray?,
    val latitude: Double,
    val longitude: Double,
    val openingTime: LocalTime,
    val closingTime: LocalTime,
    val averageServiceMinutes: Int,
    val queueCapacity: Int,
    val contactPhone: String,
    val applicantRole: ApplicantRole,
    val documents: List<ProofDocument>
)
