package org.umn.ngantriin.domain.repository

import org.umn.ngantriin.domain.model.ProofDocument
import org.umn.ngantriin.domain.model.ApplicantRole
import kotlinx.coroutines.flow.Flow
import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.domain.model.GeoPoint
import org.umn.ngantriin.domain.model.QueueStats
import org.umn.ngantriin.domain.model.Restaurant
import org.umn.ngantriin.domain.model.RestaurantFilters
import org.umn.ngantriin.domain.model.RestaurantListing
import org.umn.ngantriin.domain.model.RestaurantRegistration

interface RestaurantRepository {

    /**
     * Cache-first stream of restaurants matching [filters], with distances
     * resolved against [origin] when a location fix exists.
     *
     * The flow emits whatever is cached immediately and again
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

    /** Live queue counters for one venue. */
    fun observeQueueStats(restaurantId: String): Flow<QueueStats?>

    suspend fun refreshRestaurant(restaurantId: String): Outcome<Restaurant>

    /** The distinct categories present in the catalogue, for the filter sheet. */
    fun observeCategories(): Flow<List<String>>

    /** The venues this staff account is allowed to manage, paused ones included. */
    suspend fun managedRestaurants(userId: String): Outcome<List<Restaurant>>

    /**
     * Puts a new venue on the app. The caller becomes its staff and the venue
     * is listed to customers immediately; it is returned as stored.
     */
    suspend fun registerRestaurant(registration: RestaurantRegistration): Outcome<Restaurant>

    /** Lists ([active]) or pauses a venue the caller manages. */
    suspend fun setRestaurantActive(restaurantId: String, active: Boolean): Outcome<Restaurant>

    suspend fun resubmitRestaurant(
        restaurantId: String,
        contactPhone: String,
        applicantRole: ApplicantRole,
        documents: List<ProofDocument>
    ): Outcome<Restaurant>
}
