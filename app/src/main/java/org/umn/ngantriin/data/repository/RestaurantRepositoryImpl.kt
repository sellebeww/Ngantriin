package org.umn.ngantriin.data.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.umn.ngantriin.core.Constants
import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.core.runCatchingOutcome
import org.umn.ngantriin.data.local.dao.RestaurantDao
import org.umn.ngantriin.data.mapper.toDomain
import org.umn.ngantriin.data.mapper.toEntity
import org.umn.ngantriin.data.remote.datasource.RestaurantRemoteDataSource
import org.umn.ngantriin.domain.model.GeoPoint
import org.umn.ngantriin.domain.model.QueueStats
import org.umn.ngantriin.domain.model.Restaurant
import org.umn.ngantriin.domain.model.RestaurantFilters
import org.umn.ngantriin.domain.model.RestaurantListing
import org.umn.ngantriin.domain.model.RestaurantSort
import org.umn.ngantriin.domain.repository.RestaurantRepository
import java.time.Duration
import java.time.Instant

/**
 * Cache-first (section 21). Room is what the UI renders; the network only ever
 * writes into Room. A failed request leaves the previous rows exactly where
 * they were, so going offline degrades to stale data rather than an empty
 * screen.
 */
class RestaurantRepositoryImpl(
    private val remote: RestaurantRemoteDataSource,
    private val dao: RestaurantDao
) : RestaurantRepository {

    private val refreshLock = Mutex()
    @Volatile private var lastCatalogueRefresh: Instant? = null

    override fun observeRestaurants(
        origin: GeoPoint?,
        filters: RestaurantFilters
    ): Flow<List<RestaurantListing>> = channelFlow {
        launch {
            combine(dao.observeAll(), dao.observeAllStats()) { restaurants, stats ->
                val statsById = stats.associateBy { it.restaurantId }
                restaurants.map { entity ->
                    val restaurant = entity.toDomain()
                    RestaurantListing(
                        restaurant = restaurant,
                        stats = statsById[entity.id]?.toDomain(),
                        distanceMeters = origin?.distanceTo(restaurant.location)
                    )
                }
            }
                .map { listings -> listings.filtered(filters).sorted(filters.sort) }
                .distinctUntilChanged()
                .collect { send(it) }
        }

        launch {
            refreshCatalogueIfStale()
            // Section 36: counters stay live without the user pulling to refresh.
            remote.observeAllQueueStatsChanges()
                .onStart { emit(Unit) }
                .collect { refreshStatsOnly() }
        }
    }

    override suspend fun refreshRestaurants(): Outcome<Unit> = runCatchingOutcome {
        refreshCatalogue()
    }

    override fun observeRestaurant(restaurantId: String): Flow<Restaurant?> =
        dao.observeById(restaurantId).map { it?.toDomain() }.distinctUntilChanged()

    override fun observeQueueStats(restaurantId: String): Flow<QueueStats?> = channelFlow {
        launch {
            dao.observeStats(restaurantId)
                .map { it?.toDomain() }
                .distinctUntilChanged()
                .collect { send(it) }
        }
        launch {
            remote.observeQueueStatsChanges(restaurantId)
                .onStart { emit(Unit) }
                .collect { refreshStats(restaurantId) }
        }
    }

    override suspend fun refreshRestaurant(restaurantId: String): Outcome<Restaurant> =
        runCatchingOutcome {
            val restaurant = remote.fetchRestaurant(restaurantId)
            dao.upsertRestaurants(listOf(restaurant.toEntity()))
            refreshStats(restaurantId)
            restaurant
        }

    override fun observeCategories(): Flow<List<String>> = dao.observeCategories()

    override suspend fun managedRestaurants(userId: String): Outcome<List<Restaurant>> =
        runCatchingOutcome { remote.fetchManagedRestaurants(userId) }

    // -------------------------------------------------------------------------

    private suspend fun refreshCatalogue() = refreshLock.withLock {
        val restaurants = remote.fetchRestaurants()
        val stats = remote.fetchAllQueueStats()
        val cachedAt = Instant.now()
        dao.replaceCatalogue(
            restaurants = restaurants.map { it.toEntity(cachedAt) },
            stats = stats.map { it.toEntity() }
        )
        lastCatalogueRefresh = cachedAt
    }

    private suspend fun refreshCatalogueIfStale() {
        val last = lastCatalogueRefresh
        val stale = last == null ||
            Duration.between(last, Instant.now()).toMinutes() >= Constants.CACHE_STALE_AFTER_MINUTES
        if (!stale) return
        runCatching { refreshCatalogue() }
            .onFailure { Log.i(TAG, "Catalogue refresh failed, serving cache", it) }
    }

    private suspend fun refreshStatsOnly() {
        runCatching { dao.upsertStats(remote.fetchAllQueueStats().map { it.toEntity() }) }
            .onFailure { Log.i(TAG, "Stats refresh failed, serving cache", it) }
    }

    private suspend fun refreshStats(restaurantId: String) {
        runCatching {
            remote.fetchQueueStats(restaurantId)?.let { dao.upsertStats(it.toEntity()) }
        }.onFailure { Log.i(TAG, "Stats refresh failed for $restaurantId", it) }
    }

    /** Section 9. Filters are AND-combined; an unset filter is simply skipped. */
    private fun List<RestaurantListing>.filtered(
        filters: RestaurantFilters
    ): List<RestaurantListing> {
        val query = filters.query.trim()
        return filter { listing ->
            val r = listing.restaurant
            val matchesQuery = query.isBlank() ||
                r.name.contains(query, ignoreCase = true) ||
                r.category.contains(query, ignoreCase = true) ||
                r.description.contains(query, ignoreCase = true) ||
                r.address.contains(query, ignoreCase = true)

            val matchesCategory = filters.category == null ||
                r.category.equals(filters.category, ignoreCase = true)

            // A restaurant with no distance yet is kept: hiding venues because
            // the GPS fix has not landed would look like a broken list.
            val matchesDistance = filters.maxDistanceMeters == null ||
                listing.distanceMeters == null ||
                listing.distanceMeters <= filters.maxDistanceMeters

            val matchesQueueLength = filters.maxQueueLength == null ||
                listing.waitingCount <= filters.maxQueueLength

            val matchesWait = filters.maxWaitMinutes == null ||
                listing.estimatedWaitMinutes <= filters.maxWaitMinutes

            val matchesRating = filters.minRating == null || r.rating >= filters.minRating
            val matchesOpen = !filters.openNowOnly || r.isAcceptingQueue()

            matchesQuery && matchesCategory && matchesDistance &&
                matchesQueueLength && matchesWait && matchesRating && matchesOpen
        }
    }

    private fun List<RestaurantListing>.sorted(sort: RestaurantSort): List<RestaurantListing> =
        when (sort) {
            // Unknown distances sort last rather than first.
            RestaurantSort.NEAREST -> sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
            RestaurantSort.SHORTEST_WAIT -> sortedWith(
                compareBy<RestaurantListing> { it.estimatedWaitMinutes }
                    .thenBy { it.distanceMeters ?: Double.MAX_VALUE }
            )
            RestaurantSort.HIGHEST_RATED -> sortedByDescending { it.restaurant.rating }
            RestaurantSort.MOST_POPULAR -> sortedWith(
                compareByDescending<RestaurantListing> { it.restaurant.ratingCount }
                    .thenByDescending { it.restaurant.rating }
            )
        }

    private companion object {
        const val TAG = "Ngantriin"
    }
}
