package org.umn.ngantriin.data.repository

import android.util.Log
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.umn.ngantriin.core.AppError
import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.core.runCatchingOutcome
import org.umn.ngantriin.data.local.dao.QueueDao
import org.umn.ngantriin.data.local.dao.RestaurantDao
import org.umn.ngantriin.data.mapper.toDomain
import org.umn.ngantriin.data.mapper.toEntity
import org.umn.ngantriin.data.remote.datasource.QueueRemoteDataSource
import org.umn.ngantriin.data.remote.datasource.RestaurantRemoteDataSource
import org.umn.ngantriin.domain.model.ActiveQueue
import org.umn.ngantriin.domain.model.QueueEntry
import org.umn.ngantriin.domain.model.QueueHistoryItem
import org.umn.ngantriin.domain.model.QueueMath
import org.umn.ngantriin.domain.model.QueuePosition
import org.umn.ngantriin.domain.model.QueueStats
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.domain.model.Restaurant
import org.umn.ngantriin.domain.repository.QueueRepository
import org.umn.ngantriin.work.OfflineWriteQueue
import org.umn.ngantriin.work.PendingAction

/**
 * Sections 11-13. Room is the render source and Supabase Realtime is what
 * keeps it current, so the UI has data on the first frame and updates without
 * anyone pulling to refresh.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QueueRepositoryImpl(
    private val remote: QueueRemoteDataSource,
    private val restaurantRemote: RestaurantRemoteDataSource,
    private val queueDao: QueueDao,
    private val restaurantDao: RestaurantDao,
    private val offlineWriteQueue: OfflineWriteQueue
) : QueueRepository {

    /**
     * Server-computed positions, keyed by queue id. Held in memory rather than
     * Room because it is derived data with a short shelf life — if it is
     * missing we fall back to the counters, which are cached.
     */
    private val positions = MutableStateFlow<Map<String, QueuePosition>>(emptyMap())

    override fun observeActiveQueue(userId: String): Flow<ActiveQueue?> = channelFlow {
        val entryFlow = queueDao.observeActive(userId)
            .map { it?.toDomain() }
            .distinctUntilChanged()

        // 1. The ticket row itself, kept in step with the backend.
        launch {
            remote.observeUserQueueChanges(userId)
                .onStart { emit(Unit) }
                .collect { syncUserQueues(userId) }
        }

        // 2. The line in front of the ticket. Other customers' rows are not
        //    readable, so both the counters and the position come from the
        //    server, re-read whenever this venue's counters change.
        launch {
            entryFlow
                .flatMapLatest { entry ->
                    if (entry == null) {
                        emptyFlow()
                    } else {
                        restaurantRemote.observeQueueStatsChanges(entry.restaurantId)
                            .onStart { emit(Unit) }
                            .map { entry }
                    }
                }
                .collect { entry ->
                    syncRestaurant(entry.restaurantId)
                    syncPosition(entry.id)
                }
        }

        // 3. Render, purely from cache.
        launch {
            entryFlow
                .flatMapLatest { entry ->
                    if (entry == null) {
                        flowOf(null)
                    } else {
                        combine(
                            restaurantDao.observeById(entry.restaurantId),
                            restaurantDao.observeStats(entry.restaurantId),
                            queueDao.observeLiveForRestaurant(entry.restaurantId),
                            positions
                        ) { restaurantEntity, statsEntity, liveEntities, positionsById ->
                            val restaurant = restaurantEntity?.toDomain() ?: return@combine null
                            val stats = statsEntity?.toDomain() ?: QueueStats.empty(restaurant.id)
                            buildActiveQueue(
                                entry = entry,
                                restaurant = restaurant,
                                stats = stats,
                                liveEntries = liveEntities.map { it.toDomain() },
                                serverPosition = positionsById[entry.id]
                            )
                        }
                    }
                }
                .distinctUntilChanged()
                .collect { send(it) }
        }
    }

    override fun observeRestaurantQueue(restaurantId: String): Flow<List<QueueEntry>> =
        channelFlow {
            launch {
                queueDao.observeLiveForRestaurant(restaurantId)
                    .map { entities -> entities.map { it.toDomain() } }
                    .distinctUntilChanged()
                    .collect { send(it) }
            }
            launch {
                remote.observeRestaurantQueueChanges(restaurantId)
                    .onStart { emit(Unit) }
                    .collect { syncRestaurantQueue(restaurantId) }
            }
        }

    override fun observeHistory(userId: String): Flow<List<QueueHistoryItem>> =
        queueDao.observeHistory(userId)
            .map { rows -> rows.map { it.toDomain() } }
            .distinctUntilChanged()

    override suspend fun refreshHistory(userId: String): Outcome<Unit> = runCatchingOutcome {
        syncUserQueues(userId)
    }

    override suspend fun getQueue(queueId: String): Outcome<QueueEntry> = runCatchingOutcome {
        remote.fetchQueue(queueId).also { queueDao.upsert(it.toEntity()) }
    }

    override suspend fun joinQueue(
        restaurantId: String,
        partySize: Int,
        note: String?
    ): Outcome<QueueEntry> = runCatchingOutcome {
        // The number comes back from the backend; nothing is generated here.
        val entry = remote.joinQueue(restaurantId, partySize, note)
        queueDao.upsert(entry.toEntity())
        syncPosition(entry.id)
        syncRestaurant(restaurantId)
        entry
    }

    override suspend fun leaveQueue(queueId: String): Outcome<QueueEntry> {
        val outcome = runCatchingOutcome {
            val entry = remote.leaveQueue(queueId)
            queueDao.upsert(entry.toEntity())
            positions.update { it - queueId }
            syncRestaurant(entry.restaurantId)
            entry
        }

        // Offline: park it rather than cancelling locally. Showing a ticket as
        // cancelled and then having it reappear would break the one promise
        // section 18 makes — CANCELLED is final.
        if (outcome is Outcome.Failure && outcome.error is AppError.Network) {
            offlineWriteQueue.park(PendingAction.LeaveQueue(queueId))
            return Outcome.Failure(AppError.OfflineActionQueued)
        }
        return outcome
    }

    override suspend fun checkIn(
        queueId: String,
        restaurantId: String,
        photo: ByteArray
    ): Outcome<QueueEntry> = runCatchingOutcome {
        val entry = remote.checkIn(queueId, restaurantId, photo)
        queueDao.upsert(entry.toEntity())
        syncRestaurant(entry.restaurantId)
        entry
    }

    override suspend fun resolveCheckInPhotoUrl(photoUrl: String): Outcome<String> =
        runCatchingOutcome { remote.resolveCheckInPhotoUrl(photoUrl) }

    override suspend fun callNext(restaurantId: String): Outcome<QueueEntry> =
        runCatchingOutcome {
            val entry = remote.callNext(restaurantId)
            queueDao.upsert(entry.toEntity())
            syncRestaurantQueue(restaurantId)
            entry
        }

    override suspend fun updateStatus(
        queueId: String,
        status: QueueStatus
    ): Outcome<QueueEntry> = runCatchingOutcome {
        val entry = remote.updateStatus(queueId, status)
        queueDao.upsert(entry.toEntity())
        syncRestaurantQueue(entry.restaurantId)
        entry
    }

    // -------------------------------------------------------------------------

    /**
     * Combines a ticket with everything around it. [serverPosition] is
     * authoritative when present; otherwise we fall back to what is cached,
     * which keeps the screen sensible offline (section 21).
     */
    private fun buildActiveQueue(
        entry: QueueEntry,
        restaurant: Restaurant,
        stats: QueueStats,
        liveEntries: List<QueueEntry>,
        serverPosition: QueuePosition?
    ): ActiveQueue {
        val peopleAhead = serverPosition?.peopleAhead
            ?: fallbackPeopleAhead(entry, stats, liveEntries)

        val estimate = serverPosition?.estimatedWaitMinutes
            ?: QueueMath.estimatedWaitMinutes(peopleAhead, restaurant.averageServiceMinutes)

        return ActiveQueue(
            entry = entry,
            restaurant = restaurant,
            stats = serverPosition?.let {
                stats.copy(
                    currentServingNumber = it.currentServingNumber ?: stats.currentServingNumber,
                    waitingCount = it.waitingCount
                )
            } ?: stats,
            peopleAhead = peopleAhead,
            estimatedWaitMinutes = estimate
        )
    }

    /**
     * Two fallbacks, in order of trust:
     *  1. Count the cached tickets ahead — exact when we can see them, which
     *     is the case for staff and in demo mode.
     *  2. Otherwise derive from the counters: how far this ticket is past the
     *     one being served, never more than the number still waiting.
     */
    private fun fallbackPeopleAhead(
        entry: QueueEntry,
        stats: QueueStats,
        liveEntries: List<QueueEntry>
    ): Int {
        val visibleOthers = liveEntries.count { it.id != entry.id }
        if (visibleOthers > 0) return QueueMath.peopleAhead(liveEntries, entry.ticketSequence)

        val fromCounters = entry.ticketSequence - stats.currentServingSequence - 1
        return fromCounters.coerceIn(0, stats.waitingCount.coerceAtLeast(0))
    }

    private suspend fun syncUserQueues(userId: String) {
        runCatching {
            val history = remote.fetchHistory(userId)
            if (history.isNotEmpty()) queueDao.upsert(history.map { it.toEntity() })
            remote.fetchActiveQueue(userId)?.let { active ->
                queueDao.upsert(active.toEntity())
                syncPosition(active.id)
            }
        }.onFailure { Log.i(TAG, "Queue sync failed, serving cache", it) }
    }

    private suspend fun syncRestaurantQueue(restaurantId: String) {
        runCatching {
            val entries = remote.fetchRestaurantQueue(restaurantId)
            queueDao.replaceRestaurantQueue(restaurantId, entries.map { it.toEntity() })
        }.onFailure { Log.i(TAG, "Restaurant queue sync failed", it) }
        syncRestaurant(restaurantId)
    }

    /**
     * Pulls the counters, and the venue itself if it is not cached yet — a
     * ticket can outlive the catalogue that introduced it, and the active
     * queue screen cannot render without the restaurant row.
     */
    private suspend fun syncRestaurant(restaurantId: String) {
        runCatching {
            if (restaurantDao.findById(restaurantId) == null) {
                restaurantDao.upsertRestaurants(
                    listOf(restaurantRemote.fetchRestaurant(restaurantId).toEntity())
                )
            }
            restaurantRemote.fetchQueueStats(restaurantId)
                ?.let { restaurantDao.upsertStats(it.toEntity()) }
        }.onFailure { Log.i(TAG, "Stats sync failed", it) }
    }

    private suspend fun syncPosition(queueId: String) {
        runCatching { remote.fetchPosition(queueId) }
            .onSuccess { position -> positions.update { it + (queueId to position) } }
            .onFailure { Log.i(TAG, "Position sync failed", it) }
    }

    private companion object {
        const val TAG = "Ngantriin"
    }
}
