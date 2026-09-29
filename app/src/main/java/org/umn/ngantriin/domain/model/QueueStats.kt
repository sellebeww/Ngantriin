package org.umn.ngantriin.domain.model

import java.time.Instant

/** Per-restaurant counters kept by the `queues_stats_sync` trigger. */
data class QueueStats(
    val restaurantId: String,
    val currentServingNumber: String?,
    val currentServingSequence: Int,
    val waitingCount: Int,
    val estimatedWaitMinutes: Int,
    val updatedAt: Instant
) {
    companion object {
        fun empty(restaurantId: String) = QueueStats(
            restaurantId = restaurantId,
            currentServingNumber = null,
            currentServingSequence = 0,
            waitingCount = 0,
            estimatedWaitMinutes = 0,
            updatedAt = Instant.EPOCH
        )
    }
}

/**
 * Where one ticket stands right now. Computed by the backend (the
 * `queue_position` SQL function), because a customer cannot read other
 * customers' rows under RLS and so cannot count the line themselves.
 */
data class QueuePosition(
    val peopleAhead: Int,
    val position: Int,
    val estimatedWaitMinutes: Int,
    val currentServingNumber: String?,
    val waitingCount: Int
)
