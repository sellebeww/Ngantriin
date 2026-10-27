package org.umn.ngantriin.notification

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.umn.ngantriin.data.local.dao.NotificationDao
import org.umn.ngantriin.domain.model.ActiveQueue
import org.umn.ngantriin.domain.model.NotificationType
import org.umn.ngantriin.domain.model.QueueEntry
import org.umn.ngantriin.domain.model.QueueMath
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.domain.repository.AuthRepository
import org.umn.ngantriin.domain.repository.NotificationRepository
import org.umn.ngantriin.domain.repository.QueueRepository
import org.umn.ngantriin.domain.repository.RestaurantRepository
import org.umn.ngantriin.domain.repository.SessionState
import org.umn.ngantriin.domain.repository.UserPreferencesRepository
import java.time.Duration
import java.time.Instant

/**
 * Local half.
 *
 * Push is the primary delivery channel: a Supabase Edge Function watches the
 * `queues` table and sends through FCM, which reaches the phone whether or not
 * the app is running. This scheduler covers the case where push is not wired
 * up — no google-services.json, or demo mode — by deriving the same milestones
 * from the same queue state while the process is alive. [QueueTrackingService]
 * is what keeps the process alive once the app is backgrounded.
 *
 * Both paths write through [NotificationRepository] with the id
 * `"<queueId>:<TYPE>"`, the client-side mirror of the `unique (queue_id, type)`
 * constraint. That is what stops a notification firing again every time the
 * position happens to tick.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QueueNotificationScheduler(
    private val context: Context,
    private val authRepository: AuthRepository,
    private val queueRepository: QueueRepository,
    private val restaurantRepository: RestaurantRepository,
    private val notificationRepository: NotificationRepository,
    private val notificationDao: NotificationDao,
    private val notificationHelper: NotificationHelper,
    private val preferences: UserPreferencesRepository
) {

    fun start(scope: CoroutineScope) {
        if (notificationHelper.isPushConfigured) {
            Log.i(TAG, "FCM configured; leaving queue notifications to push.")
            return
        }

        val userIds = authRepository.sessionState
            .map { (it as? SessionState.SignedIn)?.user?.id }
            .distinctUntilChanged()

        // Live milestones: these need the line around the ticket.
        scope.launch {
            userIds
                .flatMapLatest { id -> id?.let { queueRepository.observeActiveQueue(it) } ?: flowOf(null) }
                .distinctUntilChanged()
                .collect { queue -> queue?.let { notifyMilestone(it) } }
        }

        // Endings: a finished ticket drops out of the active flow, so watch
        // the latest ticket instead.
        scope.launch {
            userIds
                .flatMapLatest { id -> id?.let { queueRepository.observeLatestTicket(it) } ?: flowOf(null) }
                .distinctUntilChanged()
                .collect { entry -> entry?.let { notifyEnding(it) } }
        }
    }

    private suspend fun notifyMilestone(queue: ActiveQueue) {
        val userId = authRepository.currentUserOrNull()?.id ?: return
        // A guessed position must not burn a once-only milestone: with no
        // counters yet it reads "0 ahead" and would fire "head back now" the
        // moment the ticket is created.
        if (!queue.isPositionConfirmed && queue.entry.isWaitingToBeCalled) return

        // Join confirmation. Recent tickets only, so restoring an old ticket
        // on a fresh install does not announce a join from hours ago.
        val justJoined = Duration.between(queue.entry.joinedAt, Instant.now()) <= JOIN_RECENT &&
            emit(QueueNotificationCopy.build(context, userId, queue, NotificationType.QUEUE_JOINED))

        val milestone = QueueMath.notificationMilestone(queue.status, queue.peopleAhead) ?: return
        // Joining a short line already said how many are ahead; record the
        // milestone so it can't fire later, but don't buzz twice at once.
        emit(QueueNotificationCopy.build(context, userId, queue, milestone), silent = justJoined)

        // Reaching CALLED implies the earlier milestones happened, even if the
        // app was closed when they did. Backfilling keeps the in-app list
        // readable as a history rather than a single orphaned entry.
        if (milestone == NotificationType.CALLED) {
            emit(QueueNotificationCopy.build(context, userId, queue, NotificationType.RETURN_NOW), silent = true)
            emit(QueueNotificationCopy.build(context, userId, queue, NotificationType.ALMOST_THERE), silent = true)
        }
    }

    private suspend fun notifyEnding(entry: QueueEntry) {
        val type = when (entry.status) {
            QueueStatus.COMPLETED -> NotificationType.COMPLETED
            QueueStatus.EXPIRED -> NotificationType.EXPIRED
            // Leaving on purpose needs no "your queue was cancelled" ping.
            QueueStatus.CANCELLED ->
                if (queueRepository.wasLeftByCustomer(entry.id)) return else NotificationType.CANCELLED
            else -> return
        }
        // Only news if it just happened — a fresh install syncing last week's
        // history must not light up the tray.
        val finishedAt = entry.finishedAt ?: return
        if (Duration.between(finishedAt, Instant.now()) > RECENT) return

        val userId = authRepository.currentUserOrNull()?.id ?: return
        if (entry.userId != userId) return
        val window = restaurantRepository.observeRestaurant(entry.restaurantId).first()
            ?.checkInWindowMinutes ?: QueueMath.DEFAULT_CHECK_IN_WINDOW_MINUTES
        emit(QueueNotificationCopy.build(context, userId, entry, window, type))
    }

    private suspend fun emit(
        notification: org.umn.ngantriin.domain.model.AppNotification,
        silent: Boolean = false
    ): Boolean {
        if (notificationDao.countById(notification.id) > 0) return false
        notificationRepository.record(notification)
        // Settings -> Queue notifications: the in-app list still records it,
        // only the system tray stays quiet.
        if (!silent && preferences.notificationsEnabled.first()) {
            notificationHelper.show(notification)
        }
        return true
    }

    private companion object {
        const val TAG = "Ngantriin"
        val RECENT: Duration = Duration.ofMinutes(30)
        val JOIN_RECENT: Duration = Duration.ofMinutes(5)
    }
}
