package org.umn.ngantriin.core

import androidx.annotation.StringRes
import org.umn.ngantriin.R

/**
 * Semua kegagalan yang bisa tampil di UI. Layar menampilkan [messageRes],
 * bukan teks exception mentah, jadi pesan error selalu terbaca wajar.
 */
sealed class AppError(
    @param:StringRes val messageRes: Int,
    val formatArgs: List<Any> = emptyList()
) {
    /** Tidak ada koneksi. */
    data object Network : AppError(R.string.error_network)

    /** Server error. */
    data class Server(val detail: String) : AppError(R.string.error_server)

    /** Sesi login habis. */
    data object SessionExpired : AppError(R.string.error_session_expired)

    data object InvalidCredentials : AppError(R.string.error_invalid_credentials)

    /** Restoran sedang tutup. */
    data object RestaurantClosed : AppError(R.string.error_restaurant_closed)

    /** Antrean penuh. */
    data object QueueFull : AppError(R.string.error_queue_full)

    /** Masih ada kursi kosong sekarang, jadi tidak perlu antre. */
    data object SeatsAvailable : AppError(R.string.error_seats_available)

    /** Sudah punya tiket aktif. */
    data object AlreadyInQueue : AppError(R.string.error_already_in_queue)

    /** Jeda singkat setelah membatalkan, supaya tidak bisa join/batal berulang. */
    data object JoinCooldown : AppError(R.string.error_join_cooldown)

    data object QueueNotFound : AppError(R.string.error_queue_not_found)

    /** Check-in dicoba sebelum tiket dipanggil restoran. */
    data object QueueNotCalled : AppError(R.string.error_queue_not_called)

    /** Sudah dipanggil, tapi batas waktu check-in habis. */
    data object CheckInExpired : AppError(R.string.error_check_in_expired)

    /** Check-in dicoba dari status yang tidak membolehkannya. */
    data object CheckInNotAllowed : AppError(R.string.error_check_in_not_allowed)

    /** Backend tidak menemukan foto yang diunggah untuk tiket ini. */
    data object PhotoNotUploaded : AppError(R.string.error_photo_not_uploaded)

    data object InvalidPartySize : AppError(R.string.error_invalid_party_size)

    data object QueueNotCancellable : AppError(R.string.error_queue_not_cancellable)

    data object QueueEmpty : AppError(R.string.error_queue_empty)

    /** Masih dipakai untuk urutan restoran terdekat di Beranda/Cari/Detail. */
    data object LocationPermissionDenied : AppError(R.string.error_location_permission)

    data object LocationUnavailable : AppError(R.string.error_location_unavailable)

    /** Check-in wajib pakai foto, tapi belum ada yang diambil atau diunggah. */
    data object PhotoRequired : AppError(R.string.error_photo_required)

    data object NotRestaurantStaff : AppError(R.string.error_not_staff)

    /** Form restoran ditolak oleh validasi di server. */
    data object InvalidRestaurant : AppError(R.string.error_invalid_restaurant)

    data object TooManyRestaurants : AppError(R.string.error_too_many_restaurants)

    /** Pendaftaran butuh dokumen usaha yang diunggah. */
    data object ProofRequired : AppError(R.string.error_proof_required)

    /** Hanya boleh satu pengajuan dalam satu waktu. */
    data object ApplicationPending : AppError(R.string.error_application_pending)

    data object NotResubmittable : AppError(R.string.error_not_resubmittable)

    /** Database menolak panggilan admin karena akunnya bukan admin. */
    data object NotAdmin : AppError(R.string.error_not_admin)

    /** Pengajuan ini sudah diputuskan orang lain. */
    data object NotPending : AppError(R.string.error_not_pending)

    data object RejectionNoteRequired : AppError(R.string.error_rejection_note_required)

    /** Ulasan hanya boleh untuk tiket yang sudah selesai. */
    data object ReviewNotAllowed : AppError(R.string.error_review_not_allowed)

    data object OfflineActionQueued : AppError(R.string.error_offline_action_queued)

    data class Unknown(val detail: String) : AppError(R.string.error_unknown)
}
