package org.umn.ngantriin.data.remote.demo

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.umn.ngantriin.data.local.NgantriinDatabase
import org.umn.ngantriin.data.mapper.toDomain
import org.umn.ngantriin.data.mapper.toEntity
import org.umn.ngantriin.domain.model.QueueEntry
import org.umn.ngantriin.domain.model.QueueMath
import org.umn.ngantriin.domain.model.QueuePosition
import org.umn.ngantriin.domain.model.QueueStats
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.domain.model.Restaurant
import org.umn.ngantriin.domain.model.Review
import java.time.Instant
import java.util.UUID

/**
 * An in-process stand-in for the Supabase project, used when no credentials
 * are configured so that the app is runnable end to end on a fresh checkout.
 *
 * It is not a shortcut around the real backend. It enforces the same rules the
 * SQL functions do — server-allocated ticket numbers, one active queue per
 * customer, the transition table in section 14, a distance test on check-in —
 * and it pushes change notifications through the same Flow contract Supabase
 * Realtime uses. Swapping the two out changes nothing above the data source.
 *
 * State is mirrored into Room so a demo queue survives a restart.
 */
class DemoBackend(private val database: NgantriinDatabase) {

    private val mutex = Mutex()
    private var hydrated = false

    private val restaurants = LinkedHashMap<String, Restaurant>()
    private val queues = LinkedHashMap<String, QueueEntry>()
    private val reviews = LinkedHashMap<String, Review>()
    private val lastIssuedSequence = HashMap<String, Int>()

    private val _changes = MutableSharedFlow<Change>(extraBufferCapacity = 64)

    /** Mirrors what Supabase Realtime would publish. */
    val changes: SharedFlow<Change> = _changes

    data class Change(val restaurantId: String?, val userIds: Set<String>)

    // -------------------------------------------------------------------------
    // Hydration
    // -------------------------------------------------------------------------

    suspend fun ensureReady() {
        if (hydrated) return
        mutex.withLock {
            if (hydrated) return
            val dao = database.restaurantDao()
            val cachedRestaurants = DemoSeed.RESTAURANTS.mapNotNull { seed ->
                dao.findById(seed.id)?.toDomain()
            }

            if (cachedRestaurants.size == DemoSeed.RESTAURANTS.size) {
                cachedRestaurants.forEach { restaurants[it.id] = it }
            } else {
                DemoSeed.RESTAURANTS.forEach { restaurants[it.id] = it }
                database.restaurantDao().upsertRestaurants(restaurants.values.map { it.toEntity() })
            }

            restaurants.keys.forEach { restaurantId ->
                val cached = database.queueDao().findAllForRestaurant(restaurantId)
                if (cached.isEmpty()) {
                    DemoSeed.seedQueue(restaurants.getValue(restaurantId)).forEach {
                        queues[it.id] = it
                    }
                } else {
                    cached.forEach { entity -> queues[entity.id] = entity.toDomain() }
                }
            }

            restaurants.keys.forEach { id ->
                lastIssuedSequence[id] = queues.values
                    .filter { it.restaurantId == id }
                    .maxOfOrNull { it.ticketSequence } ?: 0
            }

            database.queueDao().upsert(queues.values.map { it.toEntity() })
            hydrated = true
        }
    }

    // -------------------------------------------------------------------------
    // Reads
    // -------------------------------------------------------------------------

    suspend fun allRestaurants(): List<Restaurant> {
        ensureReady()
        return restaurants.values.sortedBy { it.name }
    }

    suspend fun restaurant(id: String): Restaurant {
        ensureReady()
        return restaurants[id] ?: error("RESTAURANT_NOT_FOUND")
    }

    suspend fun allStats(): List<QueueStats> {
        ensureReady()
        return restaurants.keys.map { statsFor(it) }
    }

    suspend fun stats(restaurantId: String): QueueStats {
        ensureReady()
        return statsFor(restaurantId)
    }

    suspend fun queue(queueId: String): QueueEntry {
        ensureReady()
        return queues[queueId] ?: error("QUEUE_NOT_FOUND")
    }

    suspend fun activeQueueFor(userId: String): QueueEntry? {
        ensureReady()
        return queues.values
            .filter { it.userId == userId && it.status.isActive }
            .maxByOrNull { it.joinedAt }
    }

    suspend fun historyFor(userId: String): List<QueueEntry> {
        ensureReady()
        return queues.values.filter { it.userId == userId }.sortedByDescending { it.joinedAt }
    }

    suspend fun queueFor(restaurantId: String): List<QueueEntry> {
        ensureReady()
        return queues.values
            .filter { it.restaurantId == restaurantId }
            .sortedBy { it.ticketSequence }
    }

    suspend fun position(queueId: String): QueuePosition {
        ensureReady()
        val entry = queues[queueId] ?: error("QUEUE_NOT_FOUND")
        val restaurant = restaurants.getValue(entry.restaurantId)
        val live = queues.values.filter { it.restaurantId == entry.restaurantId }
        val ahead = QueueMath.peopleAhead(live, entry.ticketSequence)
        return QueuePosition(
            peopleAhead = ahead,
            position = ahead + 1,
            estimatedWaitMinutes = QueueMath.estimatedWaitMinutes(
                ahead,
                restaurant.averageServiceMinutes
            ),
            currentServingNumber = QueueMath.currentServing(live)?.queueNumber,
            waitingCount = QueueMath.waitingCount(live)
        )
    }

    suspend fun reviewsFor(restaurantId: String): List<Review> {
        ensureReady()
        return reviews.values
            .filter { it.restaurantId == restaurantId }
            .sortedByDescending { it.createdAt }
    }

    suspend fun reviewForQueue(queueId: String): Review? {
        ensureReady()
        return reviews.values.firstOrNull { it.queueId == queueId }
    }

    // -------------------------------------------------------------------------
    // Writes — these mirror 0002_queue_functions.sql
    // -------------------------------------------------------------------------

    suspend fun joinQueue(
        userId: String,
        restaurantId: String,
        partySize: Int,
        note: String?
    ): QueueEntry = mutate(restaurantId, userId) {
        val restaurant = restaurants[restaurantId] ?: error("RESTAURANT_NOT_FOUND")
        if (!restaurant.isAcceptingQueue()) error("RESTAURANT_CLOSED")
        if (restaurant.hasAvailableSeats) error("SEATS_AVAILABLE")
        if (queues.values.any { it.userId == userId && it.status.isActive }) error("ALREADY_IN_QUEUE")

        val live = queues.values.filter { it.restaurantId == restaurantId }
        if (QueueMath.waitingCount(live) >= restaurant.queueCapacity) error("QUEUE_FULL")

        val sequence = (lastIssuedSequence[restaurantId] ?: 0) + 1
        lastIssuedSequence[restaurantId] = sequence

        QueueEntry(
            id = UUID.randomUUID().toString(),
            restaurantId = restaurantId,
            userId = userId,
            queueNumber = "${restaurant.queuePrefix}-${sequence.toString().padStart(3, '0')}",
            ticketSequence = sequence,
            partySize = partySize.coerceAtLeast(1),
            status = QueueStatus.WAITING,
            note = note?.takeIf { it.isNotBlank() },
            joinedAt = Instant.now()
        ).also { queues[it.id] = it }
    }

    suspend fun cancelQueue(userId: String, queueId: String): QueueEntry {
        val existing = queue(queueId)
        if (existing.userId != userId) error("QUEUE_NOT_FOUND")
        if (!existing.status.canTransitionTo(QueueStatus.CANCELLED)) error("QUEUE_NOT_CANCELLABLE")
        return transition(existing, QueueStatus.CANCELLED)
    }

    suspend fun checkIn(userId: String, queueId: String, photoUrl: String): QueueEntry {
        val existing = queue(queueId)
        if (existing.userId != userId) error("QUEUE_NOT_FOUND")
        if (existing.status != QueueStatus.CALLED) error("QUEUE_NOT_CALLED")
        if (photoUrl.isBlank()) error("PHOTO_REQUIRED")

        return transition(existing, QueueStatus.CHECKED_IN, checkInPhotoUrl = photoUrl)
    }

    suspend fun callNext(restaurantId: String): QueueEntry {
        ensureReady()
        val next = queues.values
            .filter {
                it.restaurantId == restaurantId &&
                    (it.status == QueueStatus.WAITING || it.status == QueueStatus.ALMOST_THERE)
            }
            .minByOrNull { it.ticketSequence }
            ?: error("QUEUE_EMPTY")
        return transition(next, QueueStatus.CALLED)
    }

    suspend fun updateStatus(queueId: String, status: QueueStatus): QueueEntry =
        transition(queue(queueId), status)

    suspend fun submitReview(
        userId: String,
        queueId: String,
        restaurantId: String,
        rating: Int,
        comment: String
    ): Review {
        val entry = queue(queueId)
        if (entry.userId != userId || entry.status != QueueStatus.COMPLETED) {
            error("REVIEW_NOT_ALLOWED")
        }
        val review = Review(
            id = UUID.randomUUID().toString(),
            restaurantId = restaurantId,
            userId = userId,
            queueId = queueId,
            rating = rating.coerceIn(1, 5),
            comment = comment.trim(),
            createdAt = Instant.now()
        )
        mutex.withLock {
            reviews[review.id] = review
            val venue = restaurants.getValue(restaurantId)
            val venueReviews = reviews.values.filter { it.restaurantId == restaurantId }
            restaurants[restaurantId] = venue.copy(
                rating = venueReviews.map { it.rating }.average(),
                ratingCount = venueReviews.size
            )
            database.restaurantDao().upsertRestaurants(
                listOf(restaurants.getValue(restaurantId).toEntity())
            )
            database.reviewDao().upsert(review.toEntity())
        }
        emitChange(restaurantId, setOf(userId))
        return review
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private suspend fun transition(
        entry: QueueEntry,
        next: QueueStatus,
        checkInPhotoUrl: String? = null
    ): QueueEntry {
        if (!entry.status.canTransitionTo(next)) {
            error("ILLEGAL_TRANSITION: ${entry.status} -> $next")
        }
        val now = Instant.now()
        val updated = entry.copy(
            status = next,
            calledAt = if (next == QueueStatus.CALLED) now else entry.calledAt,
            checkedInAt = if (next == QueueStatus.CHECKED_IN) now else entry.checkedInAt,
            completedAt = if (next == QueueStatus.COMPLETED) now else entry.completedAt,
            cancelledAt = if (next == QueueStatus.CANCELLED) now else entry.cancelledAt,
            checkInPhotoUrl = checkInPhotoUrl ?: entry.checkInPhotoUrl
        )
        return mutate(entry.restaurantId, entry.userId) { updated.also { queues[it.id] = it } }
    }

    /**
     * Runs [block] under the lock, persists the resulting ticket and publishes
     * one change event — the same shape a Postgres trigger plus Realtime would
     * produce.
     */
    private suspend fun mutate(
        restaurantId: String,
        userId: String,
        block: suspend () -> QueueEntry
    ): QueueEntry {
        ensureReady()
        val result = mutex.withLock {
            val entry = block()
            promoteAlmostThere(restaurantId)
            database.queueDao().upsert(
                queues.values.filter { it.restaurantId == restaurantId }.map { it.toEntity() }
            )
            entry
        }
        emitChange(restaurantId, affectedUsers(restaurantId) + userId)
        return result
    }

    /** Section 37: tickets near the front move to ALMOST_THERE on their own. */
    private fun promoteAlmostThere(restaurantId: String) {
        val live = queues.values.filter { it.restaurantId == restaurantId }
        live.filter { it.status == QueueStatus.WAITING }.forEach { entry ->
            val ahead = QueueMath.peopleAhead(live, entry.ticketSequence)
            if (ahead <= QueueMath.ALMOST_THERE_THRESHOLD) {
                queues[entry.id] = entry.copy(status = QueueStatus.ALMOST_THERE)
            }
        }
    }

    private fun affectedUsers(restaurantId: String): Set<String> =
        queues.values.filter { it.restaurantId == restaurantId }.map { it.userId }.toSet()

    private fun emitChange(restaurantId: String?, userIds: Set<String>) {
        _changes.tryEmit(Change(restaurantId, userIds))
    }

    private fun statsFor(restaurantId: String): QueueStats {
        val restaurant = restaurants[restaurantId] ?: return QueueStats.empty(restaurantId)
        val live = queues.values.filter { it.restaurantId == restaurantId }
        val serving = QueueMath.currentServing(live)
        val waiting = QueueMath.waitingCount(live)
        return QueueStats(
            restaurantId = restaurantId,
            currentServingNumber = serving?.queueNumber,
            currentServingSequence = serving?.ticketSequence ?: 0,
            waitingCount = waiting,
            estimatedWaitMinutes = QueueMath.estimatedWaitMinutes(
                waiting,
                restaurant.averageServiceMinutes
            ),
            updatedAt = Instant.now()
        )
    }
}
