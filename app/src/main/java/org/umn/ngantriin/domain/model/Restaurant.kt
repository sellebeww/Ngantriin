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
    /** The multiplier behind every wait estimate. */
    val averageServiceMinutes: Int,
    val queuePrefix: String,
    val queueCapacity: Int,
    /** How close a customer must be to check in. */
    val checkInRadiusMeters: Int,
    /** Open tables right now — a walk-in, not a queue. */
    val availableSeats: Int = 0,
    /** Minutes a called customer has to check in before expiry. */
    val checkInWindowMinutes: Int = QueueMath.DEFAULT_CHECK_IN_WINDOW_MINUTES,
    /** Listed to customers. Staff can pause it; the Room catalogue only ever holds active venues. */
    val isActive: Boolean = true,
    /** Staff access and listing both wait for APPROVED. */
    val verificationStatus: VerificationStatus = VerificationStatus.APPROVED,
    /** The reviewer's reason, shown to the applicant after a rejection. */
    val verificationNote: String? = null
) {
    val isApproved: Boolean get() = verificationStatus == VerificationStatus.APPROVED

    val location: GeoPoint get() = GeoPoint(latitude, longitude)

    /** Seats are open, so there's nothing to queue for yet. */
    val hasAvailableSeats: Boolean get() = availableSeats > 0

    /** Opening equal to closing means the venue never closes. */
    val isOpen24Hours: Boolean get() = openingTime == closingTime

    /** "10:00 - 22:00", or "24 Jam" */
    val operatingHours: String
        get() = if (isOpen24Hours) "24 Jam" else "%02d:%02d - %02d:%02d".format(
            openingTime.hour, openingTime.minute, closingTime.hour, closingTime.minute
        )

    /**
     * `is_open` is the manual switch the restaurant controls; the clock is the
     * schedule. A venue counts as open only when both agree, so staff can shut
     * the queue early without editing their opening hours.
     */
    fun isAcceptingQueue(now: LocalTime = LocalTime.now(RESTAURANT_ZONE)): Boolean {
        if (!isOpen) return false
        return if (closingTime > openingTime) {
            now >= openingTime && now < closingTime
        } else {
            // Past-midnight closing, e.g. 18:00 - 02:00.
            now >= openingTime || now < closingTime
        }
    }

    companion object {
        /**
         * Opening hours are restaurant-local wall-clock times. Judged in the
         * venue's zone (restaurants.timezone in SQL) rather than the phone's,
         * so a traveller's phone agrees with what `join_queue` decides.
         */
        val RESTAURANT_ZONE: java.time.ZoneId = java.time.ZoneId.of("Asia/Jakarta")
    }
}

/**
 * A restaurant as it appears in a list: the venue itself plus the two facts
 * that make this app worth opening — how long the line is and how far away it
 * is. Distance is null until a location fix is available.
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

/** Where a restaurant application stands. Mirrors `restaurants.verification_status`. */
enum class VerificationStatus {
    PENDING_VERIFICATION, APPROVED, REJECTED;

    companion object {
        /** Unknown values read as pending: never grant access on a value we can't read. */
        fun fromWire(value: String?): VerificationStatus =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: PENDING_VERIFICATION
    }
}

/** The applicant's relationship to the restaurant. Mirrors `restaurants.applicant_role`. */
enum class ApplicantRole { OWNER, MANAGER, AUTHORIZED_STAFF }

/** Which slot a proof file fills. At least one BUSINESS_LICENSE is required. */
enum class ProofType { BUSINESS_LICENSE, OWNERSHIP_PROOF }

/** A proof file picked on the phone: JPEG (already downscaled) or PDF. */
class ProofDocument(
    val type: ProofType,
    val bytes: ByteArray,
    val isPdf: Boolean
) {
    val extension: String get() = if (isPdf) "pdf" else "jpg"

    /**
     * The file really is what it claims: within the size limit and starting with the
     * PDF or JPEG signature. A renamed executable fails here before it is uploaded;
     * the bucket then enforces the extension and MIME type on the server.
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
 * What staff fill in to put a venue on the app. The photo is JPEG bytes, not
 * a URL: where it ends up (Storage, or the device in sample mode) is the data
 * source's business.
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
