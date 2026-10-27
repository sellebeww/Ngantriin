package org.umn.ngantriin.core

import android.util.Log
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Turns whatever the network layer throws into an [AppError].
 *
 * Supabase surfaces a `raise exception 'QUEUE_FULL'` from Postgres as an HTTP
 * error whose body contains that token, so matching on the token is how the
 * server-side rules in 0002_queue_functions.sql reach the user as real copy.
 */
object ErrorMapper {

    private const val TAG = "Ngantriin"

    fun map(throwable: Throwable): AppError {
        if (throwable is CancellationException) throw throwable
        Log.w(TAG, "Mapping failure: ${throwable::class.simpleName}", throwable)

        val message = buildString {
            append(throwable.message.orEmpty())
            append(' ')
            append(throwable.cause?.message.orEmpty())
        }

        return when {
            throwable is UnknownHostException ||
                throwable is SocketTimeoutException ||
                throwable is IOException -> AppError.Network

            message.contains("ALREADY_IN_QUEUE") ||
                message.contains("queues_one_active_per_user_idx") -> AppError.AlreadyInQueue

            message.contains("JOIN_COOLDOWN") -> AppError.JoinCooldown

            message.contains("RESTAURANT_CLOSED") -> AppError.RestaurantClosed
            message.contains("SEATS_AVAILABLE") -> AppError.SeatsAvailable
            message.contains("QUEUE_FULL") -> AppError.QueueFull
            message.contains("QUEUE_NOT_FOUND") -> AppError.QueueNotFound
            message.contains("QUEUE_NOT_CALLED") -> AppError.QueueNotCalled
            message.contains("QUEUE_NOT_CANCELLABLE") -> AppError.QueueNotCancellable
            message.contains("QUEUE_EMPTY") -> AppError.QueueEmpty
            message.contains("NOT_RESTAURANT_STAFF") -> AppError.NotRestaurantStaff
            message.contains("INVALID_RESTAURANT") -> AppError.InvalidRestaurant
            message.contains("TOO_MANY_RESTAURANTS") -> AppError.TooManyRestaurants
            message.contains("PROOF_REQUIRED") -> AppError.ProofRequired
            message.contains("APPLICATION_PENDING") -> AppError.ApplicationPending
            message.contains("NOT_RESUBMITTABLE") -> AppError.NotResubmittable
            message.contains("NOT_ADMIN") -> AppError.NotAdmin
            message.contains("NOT_PENDING") -> AppError.NotPending
            message.contains("REJECTION_NOTE_REQUIRED") -> AppError.RejectionNoteRequired
            message.contains("RESTAURANT_NOT_FOUND") -> AppError.QueueNotFound
            message.contains("ILLEGAL_TRANSITION") -> AppError.Server("illegal transition")

            message.contains("PHOTO_REQUIRED") -> AppError.PhotoRequired
            message.contains("PHOTO_NOT_UPLOADED") -> AppError.PhotoNotUploaded
            message.contains("CHECK_IN_EXPIRED") -> AppError.CheckInExpired
            message.contains("CHECK_IN_NOT_ALLOWED") -> AppError.CheckInNotAllowed
            message.contains("INVALID_PARTY_SIZE") -> AppError.InvalidPartySize
            message.contains("REVIEW_EXISTS") ||
                message.contains("reviews_queue_id_key") -> AppError.ReviewNotAllowed

            message.contains("NOT_AUTHENTICATED", ignoreCase = true) ||
                message.contains("JWT expired", ignoreCase = true) ||
                message.contains("invalid_token", ignoreCase = true) -> AppError.SessionExpired

            message.contains("Invalid login credentials", ignoreCase = true) ||
                message.contains("invalid_grant", ignoreCase = true) -> AppError.InvalidCredentials

            message.contains("violates row-level security", ignoreCase = true) ->
                AppError.NotRestaurantStaff

            message.isBlank() -> AppError.Unknown(throwable::class.simpleName.orEmpty())
            else -> AppError.Server(message.trim())
        }
    }
}

/** Runs [block], converting any throw into an [Outcome.Failure]. */
inline fun <T> runCatchingOutcome(block: () -> T): Outcome<T> = try {
    Outcome.Success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    Outcome.Failure(ErrorMapper.map(throwable))
}
