package org.umn.ngantriin.domain.model

import java.time.Instant

/** Counter per restoran yang dijaga trigger `queues_stats_sync`. */
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
 * Posisi satu tiket saat ini. Dihitung backend (fungsi SQL
 * `queue_position`), karena pelanggan tidak bisa membaca baris pelanggan lain
 * di bawah RLS sehingga tidak bisa menghitung antrean sendiri.
 */
data class QueuePosition(
    val peopleAhead: Int,
    val position: Int,
    val estimatedWaitMinutes: Int,
    val currentServingNumber: String?,
    val waitingCount: Int
)
