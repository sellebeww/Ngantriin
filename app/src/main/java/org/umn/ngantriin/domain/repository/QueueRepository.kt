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
     * subscription — no polling, no pull-to-refresh.
     */
    fun observeActiveQueue(userId: String): Flow<ActiveQueue?>

    /** Every live ticket at a venue, ordered by ticket number. */
    fun observeRestaurantQueue(restaurantId: String): Flow<List<QueueEntry>>

    /**
     * The customer's most recent ticket, live or finished. Unlike
     * [observeActiveQueue] this keeps emitting after the ticket ends, which is
     * how the app notices "completed" or "expired" and reacts to it.
     */
    fun observeLatestTicket(userId: String): Flow<QueueEntry?>

    fun observeHistory(userId: String): Flow<List<QueueHistoryItem>>

    /** One past (or live) ticket with its venue, for the history detail page. */
    fun observeHistoryItem(queueId: String): Flow<QueueHistoryItem?>

    suspend fun refreshHistory(userId: String): Outcome<Unit>

    suspend fun getQueue(queueId: String): Outcome<QueueEntry>

    /**
     * The backend allocates the number; this returns whatever it
     * assigned. Fails with [org.umn.ngantriin.core.AppError.AlreadyInQueue]
     * when the customer already holds a live ticket.
     */
    suspend fun joinQueue(
        restaurantId: String,
        partySize: Int,
        note: String?
    ): Outcome<QueueEntry>

    /** Any live status -> CANCELLED, never back. */
    suspend fun leaveQueue(queueId: String): Outcome<QueueEntry>

    /**
     * True when this device cancelled [queueId] itself, so a CANCELLED ticket
     * can be told apart from one the restaurant removed.
     */
    fun wasLeftByCustomer(queueId: String): Boolean

    /**
     * The customer photographs themselves at the restaurant —
     * early (WAITING / ALMOST_THERE) or once CALLED, inside the check-in
     * window. The backend verifies the uploaded object exists before it
     * allows CHECKED_IN, so a client can't skip the photo.
     */
    suspend fun checkIn(queueId: String, restaurantId: String, photo: ByteArray): Outcome<QueueEntry>

    /** Turns a stored check-in photo reference into a URL a viewer can load. */
    suspend fun resolveCheckInPhotoUrl(photoUrl: String): Outcome<String>

    /** Staff calls the longest-waiting ticket. */
    suspend fun callNext(restaurantId: String): Outcome<QueueEntry>

    /** Staff moves one ticket along the state machine. */
    suspend fun updateStatus(queueId: String, status: QueueStatus): Outcome<QueueEntry>

    /**
     * Settle CALLED tickets whose check-in window has run out.
     * Called when a countdown on screen reaches zero, so the UI does not have
     * to wait for the backend's scheduled sweep.
     */
    suspend fun expireOverdue(restaurantId: String): Outcome<Unit>
}
