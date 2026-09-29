package org.umn.ngantriin.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shapes for the Supabase tables. Timestamps stay as strings here and are
 * parsed in the mapper layer: Postgres returns `timestamptz` with a numeric
 * offset, which not every java.time parser accepts on the first try.
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
    @SerialName("available_seats") val availableSeats: Int = 0
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
    @SerialName("check_in_photo_url") val checkInPhotoUrl: String? = null
)

/** Return shape of the `queue_position` SQL function. */
@Serializable
data class QueuePositionDto(
    @SerialName("people_ahead") val peopleAhead: Int = 0,
    val position: Int = 1,
    @SerialName("estimated_wait") val estimatedWait: Int = 0,
    @SerialName("current_serving") val currentServing: String? = null,
    @SerialName("waiting_count") val waitingCount: Int = 0
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
