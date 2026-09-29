package org.umn.ngantriin.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.umn.ngantriin.NgantriinApplication
import org.umn.ngantriin.core.AppError
import org.umn.ngantriin.core.Outcome

/**
 * Section 21. Runs when the device is back online: replays parked writes,
 * then refreshes the caches the UI reads from.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? NgantriinApplication)?.container
            ?: return Result.success()

        var networkFailure = false

        container.pendingActionQueue.drain { action ->
            val outcome = when (action) {
                is PendingAction.LeaveQueue ->
                    container.queueRepository.leaveQueue(action.queueId)

                is PendingAction.SubmitReview -> container.reviewRepository.submitReview(
                    queueId = action.queueId,
                    restaurantId = action.restaurantId,
                    rating = action.rating,
                    comment = action.comment
                )

                is PendingAction.MarkNotificationRead ->
                    container.notificationRepository.markRead(action.notificationId)
            }

            when (outcome) {
                is Outcome.Success<*> -> true
                is Outcome.Failure -> {
                    // Still offline: keep the action and retry the whole run.
                    // Anything else is a decision the server already made, so
                    // replaying it will not change the answer.
                    if (outcome.error is AppError.Network) {
                        networkFailure = true
                        false
                    } else {
                        Log.i(TAG, "Dropping unreplayable action: ${outcome.error}")
                        true
                    }
                }
            }
        }

        val refresh = container.restaurantRepository.refreshRestaurants()
        if (refresh is Outcome.Failure && refresh.error is AppError.Network) networkFailure = true

        container.authRepository.currentUserOrNull()?.let { user ->
            val history = container.queueRepository.refreshHistory(user.id)
            if (history is Outcome.Failure && history.error is AppError.Network) {
                networkFailure = true
            }
            container.notificationRepository.refresh(user.id)
        }

        return if (networkFailure) Result.retry() else Result.success()
    }

    companion object {
        const val NAME = "SyncWorker"
        private const val TAG = "Ngantriin"
    }
}
