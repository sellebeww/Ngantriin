package org.umn.ngantriin.core

import androidx.annotation.StringRes
import org.umn.ngantriin.R

/**
 * Every failure the UI can show, from section 28. Screens render
 * [messageRes] rather than raw exception text, so an error always reads like
 * something a person wrote.
 */
sealed class AppError(
    @param:StringRes val messageRes: Int,
    val formatArgs: List<Any> = emptyList()
) {
    /** 28.5 */
    data object Network : AppError(R.string.error_network)

    /** 28.6 */
    data class Server(val detail: String) : AppError(R.string.error_server)

    /** 28.11 */
    data object SessionExpired : AppError(R.string.error_session_expired)

    data object InvalidCredentials : AppError(R.string.error_invalid_credentials)

    data class SignUpRejected(val detail: String) :
        AppError(R.string.error_signup_rejected, listOf(detail))

    /** 28.1 */
    data object RestaurantClosed : AppError(R.string.error_restaurant_closed)

    /** 28.2 */
    data object QueueFull : AppError(R.string.error_queue_full)

    /** 44: seats are open right now, so there's nothing to queue for. */
    data object SeatsAvailable : AppError(R.string.error_seats_available)

    /** 28.3 / 28.4 */
    data object AlreadyInQueue : AppError(R.string.error_already_in_queue)

    /** 28.10: a brief cooldown after cancelling, to stop join/cancel spam. */
    data object JoinCooldown : AppError(R.string.error_join_cooldown)

    data object QueueNotFound : AppError(R.string.error_queue_not_found)

    /** Check-in attempted before the restaurant called the ticket. */
    data object QueueNotCalled : AppError(R.string.error_queue_not_called)

    data object QueueNotCancellable : AppError(R.string.error_queue_not_cancellable)

    data object QueueEmpty : AppError(R.string.error_queue_empty)

    /** 28.7 — still used by Home/Search/Detail's nearby-restaurant sorting. */
    data object LocationPermissionDenied : AppError(R.string.error_location_permission)

    data object LocationUnavailable : AppError(R.string.error_location_unavailable)

    /** 28.8 — check-in requires a photo; nothing was captured or uploaded. */
    data object PhotoRequired : AppError(R.string.error_photo_required)

    data object NotRestaurantStaff : AppError(R.string.error_not_staff)

    /** 20: reviews are gated on a completed ticket. */
    data object ReviewNotAllowed : AppError(R.string.error_review_not_allowed)

    data object OfflineActionQueued : AppError(R.string.error_offline_action_queued)

    data class Unknown(val detail: String) : AppError(R.string.error_unknown)
}
