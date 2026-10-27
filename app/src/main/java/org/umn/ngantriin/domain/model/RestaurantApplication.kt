package org.umn.ngantriin.domain.model

import java.time.Instant

/** One restaurant application as a reviewer sees it. Mirrors `admin_list_applications`. */
data class RestaurantApplication(
    val restaurantId: String,
    val name: String,
    val address: String,
    val category: String,
    val description: String,
    val imageUrl: String?,
    val status: VerificationStatus,
    /** The reason given when the application was rejected. */
    val note: String?,
    val applicantName: String,
    val applicantEmail: String,
    val contactPhone: String,
    val applicantRole: ApplicantRole?,
    /** Files in the newest submission; older rounds stay on record. */
    val documentCount: Int,
    val submittedAt: Instant?
)

/** A proof file on an application. [path] is a Storage path, never shown to customers. */
data class ApplicationDocument(
    val id: String,
    val type: ProofType,
    val path: String,
    /** 1 for the first submission, one more for every resubmission. */
    val round: Int,
    val uploadedAt: Instant?
) {
    val isPdf: Boolean get() = path.endsWith(".pdf", ignoreCase = true)
}
