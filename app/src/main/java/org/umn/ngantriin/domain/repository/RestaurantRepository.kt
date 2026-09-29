package org.umn.ngantriin.domain.repository

import kotlinx.coroutines.flow.Flow
import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.domain.model.GeoPoint
import org.umn.ngantriin.domain.model.QueueStats
import org.umn.ngantriin.domain.model.Restaurant
import org.umn.ngantriin.domain.model.RestaurantFilters
import org.umn.ngantriin.domain.model.RestaurantListing

interface RestaurantRepository {

    /**
     * Cache-first stream of restaurants matching [filters], with distances
     * resolved against [origin] when a location fix exists.
     *
     * The flow emits whatever is cached immediately (section 21) and again
     * whenever a refresh or a realtime stats change lands, so the screen never
     * shows a blank list while the network is slow.
     */
    fun observeRestaurants(
        origin: GeoPoint?,
        filters: RestaurantFilters
    ): Flow<List<RestaurantListing>>

    /** Pulls the restaurant catalogue and queue counters from the backend. */
    suspend fun refreshRestaurants(): Outcome<Unit>

    fun observeRestaurant(restaurantId: String): Flow<Restaurant?>

    /** Section 36: live queue counters for one venue. */
    fun observeQueueStats(restaurantId: String): Flow<QueueStats?>

    suspend fun refreshRestaurant(restaurantId: String): Outcome<Restaurant>

    /** The distinct categories present in the catalogue, for the filter sheet. */
    fun observeCategories(): Flow<List<String>>

    /** Section 24: the venues this staff account is allowed to manage. */
    suspend fun managedRestaurants(userId: String): Outcome<List<Restaurant>>
}
