package org.umn.ngantriin.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Bentuk data tabel Supabase. Timestamp tetap string di sini dan di-parse di
 * lapisan mapper, karena Postgres mengirim `timestamptz` dengan offset numerik
 * yang tidak selalu diterima parser java.time pada percobaan pertama.
 */

@Serializable
data class UserDto(
    val id: String,
    val name: String = "",
    val email: String = "",
    val phone: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val role: String = "CUSTOMER",
    @SerialName("fcm_token") val fcmToken: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class UserProfileUpdateDto(
    val name: String? = null,
    val phone: String? = null
)

@Serializable
data class FcmTokenUpdateDto(
    @SerialName("fcm_token") val fcmToken: String
)

@Serializable
data class RestaurantDto(
    val id: String,
    val name: String,
    val description: String = "",
    val category: String = "",
    val address: String = "",
    val latitude: Double,
    val longitude: Double,
    @SerialName("image_url") val imageUrl: String? = null,
    val rating: Double = 0.0,
    @SerialName("rating_count") val ratingCount: Int = 0,
    @SerialName("opening_time") val openingTime: String = "10:00:00",
    @SerialName("closing_time") val closingTime: String = "22:00:00",
    @SerialName("is_open") val isOpen: Boolean = true,
    @SerialName("average_service_minutes") val averageServiceMinutes: Int = 3,
    @SerialName("queue_prefix") val queuePrefix: String = "A",
    @SerialName("queue_capacity") val queueCapacity: Int = 50,
    @SerialName("checkin_radius_meters") val checkInRadiusMeters: Int = 150,
    @SerialName("available_seats") val availableSeats: Int = 0,
    @SerialName("checkin_window_minutes") val checkInWindowMinutes: Int = 10,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("verification_status") val verificationStatus: String = "APPROVED",
    @SerialName("verification_note") val verificationNote: String? = null
)

@Serializable
data class RestaurantActiveUpdateDto(
    @SerialName("is_active") val isActive: Boolean
)

/** Satu baris dari `admin_list_applications` (0021). */
@Serializable
data class RestaurantApplicationDto(
    @SerialName("restaurant_id") val restaurantId: String,
    val name: String,
    val address: String = "",
    val category: String = "",
    val description: String = "",
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("verification_status") val verificationStatus: String = "PENDING_VERIFICATION",
    @SerialName("verification_note") val verificationNote: String? = null,
    @SerialName("contact_phone") val contactPhone: String? = null,
    @SerialName("applicant_role") val applicantRole: String? = null,
    @SerialName("applicant_name") val applicantName: String? = null,
    @SerialName("applicant_email") val applicantEmail: String? = null,
    @SerialName("document_count") val documentCount: Int = 0,
    @SerialName("submitted_at") val submittedAt: String? = null
)

/** Satu baris dari `admin_application_documents` (0021). */
@Serializable
data class ApplicationDocumentDto(
    val id: String,
    @SerialName("doc_type") val docType: String,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("submission_round") val submissionRound: Int = 1,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class QueueStatsDto(
    @SerialName("restaurant_id") val restaurantId: String,
    @SerialName("current_serving_number") val currentServingNumber: String? = null,
    @SerialName("current_serving_seq") val currentServingSequence: Int = 0,
    @SerialName("waiting_count") val waitingCount: Int = 0,
    @SerialName("estimated_wait_time") val estimatedWaitMinutes: Int = 0,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class QueueDto(
    val id: String,
    @SerialName("restaurant_id") val restaurantId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("queue_number") val queueNumber: String,
    @SerialName("ticket_sequence") val ticketSequence: Int,
    @SerialName("party_size") val partySize: Int = 1,
    val status: String = "WAITING",
    val note: String? = null,
    @SerialName("joined_at") val joinedAt: String? = null,
    @SerialName("called_at") val calledAt: String? = null,
    @SerialName("checked_in_at") val checkedInAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("cancelled_at") val cancelledAt: String? = null,
    @SerialName("check_in_photo_url") val checkInPhotoUrl: String? = null,
    @SerialName("check_in_deadline") val checkInDeadline: String? = null,
    @SerialName("table_ready_at") val tableReadyAt: String? = null,
    @SerialName("expired_at") val expiredAt: String? = null
)

/** Bentuk hasil fungsi SQL `queue_position`. */
@Serializable
data class QueuePositionDto(
    @SerialName("people_ahead") val peopleAhead: Int = 0,
    val position: Int = 1,
    @SerialName("estimated_wait") val estimatedWait: Int = 0,
    @SerialName("current_serving") val currentServing: String? = null,
    @SerialName("waiting_count") val waitingCount: Int = 0,
    @SerialName("server_now") val serverNow: String? = null
)

@Serializable
data class ReviewDto(
    val id: String,
    @SerialName("restaurant_id") val restaurantId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("queue_id") val queueId: String,
    val rating: Int,
    val review: String = "",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class ReviewInsertDto(
    @SerialName("restaurant_id") val restaurantId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("queue_id") val queueId: String,
    val rating: Int,
    val review: String
)

/** Hanya kolom yang boleh diubah (0012 memberi UPDATE hanya pada ini). */
@Serializable
data class ReviewUpdateDto(
    val rating: Int,
    val review: String
)

@Serializable
data class LocaleUpdateDto(
    val locale: String
)

@Serializable
data class NotificationDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("queue_id") val queueId: String? = null,
    val title: String,
    val body: String,
    val type: String,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class RestaurantStaffDto(
    @SerialName("restaurant_id") val restaurantId: String,
    @SerialName("user_id") val userId: String
)
