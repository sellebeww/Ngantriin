package org.umn.ngantriin.work

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.umn.ngantriin.data.local.dao.PendingActionDao
import org.umn.ngantriin.data.local.entity.PendingActionEntity
import java.time.Instant

/**
 * Section 21. A write that could not reach the backend, parked until
 * connectivity returns.
 *
 * Only actions that stay correct when they arrive late are queued. Joining a
 * queue is not one of them — a ticket issued twenty minutes after the tap is
 * worse than no ticket, so a failed join is reported to the user instead.
 * Leaving a queue *is* queued: the transition guard in the database rejects a
 * replay against a ticket that has since been served, so a stale replay is a
 * harmless no-op rather than a wrong cancellation.
 */
@Serializable
sealed interface PendingAction {

    @Serializable
    @SerialName("leave_queue")
    data class LeaveQueue(val queueId: String) : PendingAction

    @Serializable
    @SerialName("submit_review")
    data class SubmitReview(
        val queueId: String,
        val restaurantId: String,
        val rating: Int,
        val comment: String
    ) : PendingAction

    @Serializable
    @SerialName("mark_notification_read")
    data class MarkNotificationRead(val notificationId: String) : PendingAction
}

/** Persists and replays [PendingAction]s. */
class PendingActionQueue(private val dao: PendingActionDao) {

    private val json = Json { classDiscriminator = "action" }

    suspend fun enqueue(action: PendingAction) {
        dao.insert(
            PendingActionEntity(
                type = action::class.simpleName.orEmpty(),
                payload = json.encodeToString(PendingAction.serializer(), action),
                createdAt = Instant.now()
            )
        )
    }

    suspend fun pendingCount(): Int = dao.count()

    /**
     * Replays everything in order. [handler] returns true when the action is
     * settled — either it succeeded, or it failed in a way that replaying
     * again will not fix.
     */
    suspend fun drain(handler: suspend (PendingAction) -> Boolean) {
        dao.dropExhausted(MAX_ATTEMPTS)
        dao.all().forEach { entity ->
            val action = runCatching {
                json.decodeFromString(PendingAction.serializer(), entity.payload)
            }.getOrNull()

            if (action == null) {
                // Unparseable rows can never succeed; drop rather than retry.
                dao.delete(entity)
                return@forEach
            }

            if (handler(action)) dao.delete(entity) else dao.recordAttempt(entity.id)
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}
