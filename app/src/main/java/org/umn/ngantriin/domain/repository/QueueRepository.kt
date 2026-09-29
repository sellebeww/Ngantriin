package org.umn.ngantriin.domain.repository

import kotlinx.coroutines.flow.Flow
import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.domain.model.ActiveQueue
import org.umn.ngantriin.domain.model.QueueEntry
import org.umn.ngantriin.domain.model.QueueHistoryItem
import org.umn.ngantriin.domain.model.QueueStatus

interface QueueRepository {

    /**
     * The customer's one live ticket, or null. Backed by a Supabase Realtime
     * subscription (section 13/36) — no polling, no pull-to-refresh.
     */
    fun observeActiveQueue(userId: String): Flow<ActiveQueue?>

    /** Section 24: every live ticket at a venue, ordered by ticket number. */
    fun observeRestaurantQueue(restaurantId: String): Flow<List<QueueEntry>>

    fun observeHistory(userId: String): Flow<List<QueueHistoryItem>>

    suspend fun refreshHistory(userId: String): Outcome<Unit>

    suspend fun getQueue(queueId: String): Outcome<QueueEntry>

    /**
     * Section 11. The backend allocates the number; this returns whatever it
     * assigned. Fails with [org.umn.ngantriin.core.AppError.AlreadyInQueue]
     * when the customer already holds a live ticket.
     */
    suspend fun joinQueue(
        restaurantId: String,
        partySize: Int,
        note: String?
    ): Outcome<QueueEntry>

    /** Section 18. WAITING/ALMOST_THERE/CALLED -> CANCELLED, never back. */
    suspend fun leaveQueue(queueId: String): Outcome<QueueEntry>

    /**
     * Section 17. The customer photographs themselves at the restaurant;
     * the backend rejects the transition to CHECKED_IN if [photo] didn't
     * upload to a non-blank path, so a client can't skip verification.
     */
    suspend fun checkIn(queueId: String, restaurantId: String, photo: ByteArray): Outcome<QueueEntry>

    /** Turns a stored check-in photo reference into a URL a viewer can load. */
    suspend fun resolveCheckInPhotoUrl(photoUrl: String): Outcome<String>

    /** Section 24: staff calls the longest-waiting ticket. */
    suspend fun callNext(restaurantId: String): Outcome<QueueEntry>

    /** Section 24: staff moves one ticket along the state machine. */
    suspend fun updateStatus(queueId: String, status: QueueStatus): Outcome<QueueEntry>
}
