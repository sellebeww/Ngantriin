package org.umn.ngantriin.notification

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.umn.ngantriin.data.local.dao.NotificationDao
import org.umn.ngantriin.domain.model.ActiveQueue
import org.umn.ngantriin.domain.model.NotificationType
import org.umn.ngantriin.domain.model.QueueMath
import org.umn.ngantriin.domain.repository.AuthRepository
import org.umn.ngantriin.domain.repository.NotificationRepository
import org.umn.ngantriin.domain.repository.QueueRepository
import org.umn.ngantriin.domain.repository.SessionState

/**
 * Section 37, local half.
 *
 * Push is the real delivery channel: a Supabase Edge Function watches the
 * `queues` table and sends through FCM, which reaches the phone whether or not
 * the app is running. This scheduler covers the case where push is not wired
 * up yet — no google-services.json, or demo mode — by deriving the same
 * milestones from the same queue state while the app is in memory.
 *
 * Both paths write through [NotificationRepository] with the id
 * `"<queueId>:<TYPE>"`, which is the client-side mirror of the
 * `unique (queue_id, type)` constraint. That is what stops a notification
 * firing again every time the position happens to tick.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QueueNotificationScheduler(
    private val context: Context,
    private val authRepository: AuthRepository,
    private val queueRepository: QueueRepository,
    private val notificationRepository: NotificationRepository,
    private val notificationDao: NotificationDao,
    private val notificationHelper: NotificationHelper
) {

    fun start(scope: CoroutineScope) {
        if (notificationHelper.isPushConfigured) {
            Log.i(TAG, "FCM configured; leaving queue notifications to push.")
            return
        }

        scope.launch {
            authRepository.sessionState
                .flatMapLatest { state ->
                    when (state) {
                        is SessionState.SignedIn ->
                            queueRepository.observeActiveQueue(state.user.id)
                        else -> flowOf(null)
                    }
                }
                .distinctUntilChanged()
                .collect { queue -> queue?.let { notifyMilestone(it) } }
        }
    }

    private suspend fun notifyMilestone(queue: ActiveQueue) {
        val userId = authRepository.currentUserOrNull()?.id ?: return
        val milestone = QueueMath.notificationMilestone(queue.status, queue.peopleAhead) ?: return
        emit(userId, queue, milestone)

        // Reaching CALLED implies the earlier milestones happened, even if the
        // app was closed when they did. Backfilling keeps the in-app list
        // readable as a history rather than a single orphaned entry.
        if (milestone == NotificationType.CALLED) {
            emit(userId, queue, NotificationType.RETURN_NOW, silent = true)
            emit(userId, queue, NotificationType.ALMOST_THERE, silent = true)
        }
    }

    private suspend fun emit(
        userId: String,
        queue: ActiveQueue,
        type: NotificationType,
        silent: Boolean = false
    ) {
        val id = QueueNotificationCopy.idFor(queue.entry.id, type)
        if (notificationDao.countById(id) > 0) return

        val notification = QueueNotificationCopy.build(context, userId, queue, type)
        notificationRepository.record(notification)
        if (!silent) notificationHelper.show(notification)
    }

    private companion object {
        const val TAG = "Ngantriin"
    }
}
