package org.umn.ngantriin.domain.model

import java.time.Instant

/** Satu pengajuan restoran seperti yang dilihat reviewer. Meniru `admin_list_applications`. */
data class RestaurantApplication(
    val restaurantId: String,
    val name: String,
    val address: String,
    val category: String,
    val description: String,
    val imageUrl: String?,
    val status: VerificationStatus,
    /** Alasan yang diberikan saat pengajuan ditolak. */
    val note: String?,
    val applicantName: String,
    val applicantEmail: String,
    val contactPhone: String,
    val applicantRole: ApplicantRole?,
    /** Berkas di kiriman terbaru, putaran lama tetap tercatat. */
    val documentCount: Int,
    val submittedAt: Instant?
)

/** Berkas bukti pada pengajuan. [path] adalah path Storage, tidak pernah ditampilkan ke pelanggan. */
data class ApplicationDocument(
    val id: String,
    val type: ProofType,
    val path: String,
    /** 1 untuk kiriman pertama, bertambah satu tiap pengajuan ulang. */
    val round: Int,
    val uploadedAt: Instant?
) {
    val isPdf: Boolean get() = path.endsWith(".pdf", ignoreCase = true)
}
