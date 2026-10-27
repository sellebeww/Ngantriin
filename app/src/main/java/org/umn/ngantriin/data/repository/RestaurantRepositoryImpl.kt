package org.umn.ngantriin.data.repository

import org.umn.ngantriin.domain.model.ProofDocument
import org.umn.ngantriin.domain.model.ApplicantRole
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
import org.umn.ngantriin.domain.model.RestaurantRegistration
import org.umn.ngantriin.domain.model.RestaurantSort
import org.umn.ngantriin.domain.repository.RestaurantRepository
import java.time.Duration
import java.time.Instant

/**
 * Cache dulu. Room yang ditampilkan UI, network hanya menulis ke Room.
 * Request yang gagal membiarkan baris sebelumnya apa adanya, jadi saat
 * offline yang tampil data lama, bukan layar kosong.
 */
class RestaurantRepositoryImpl(
    private val remote: RestaurantRemoteDataSource,
    private val dao: RestaurantDao
) : RestaurantRepository {

    private val refreshLock = Mutex()
    @Volatile private var lastCatalogueRefresh: Instant? = null
    private val probedIds = mutableSetOf<String>()

    override fun observeRestaurants(
        origin: GeoPoint?,
        filters: RestaurantFilters
    ): Flow<List<RestaurantListing>> = channelFlow {
        launch {
            combine(dao.observeAll(), dao.observeAllStats()) { restaurants, stats ->
                val statsById = stats.associateBy { it.restaurantId }
                // Restoran yang dijeda boleh di-cache untuk pemegang tiket dan staf,
                // hanya tidak pernah ditampilkan di daftar.
                restaurants.filter { it.isActive }.map { entity ->
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
            // Counter tetap live tanpa pengguna perlu tarik untuk refresh.
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

    override suspend fun registerRestaurant(
        registration: RestaurantRegistration
    ): Outcome<Restaurant> = runCatchingOutcome {
        remote.registerRestaurant(registration).also {
            // Hanya restoran yang sudah ditinjau yang masuk katalog. Pengajuan baru
            // masih pending dan tidak boleh muncul di pencarian perangkat ini.
            if (it.isApproved && it.isActive) dao.upsertRestaurants(listOf(it.toEntity()))
        }
    }

    override suspend fun resubmitRestaurant(
        restaurantId: String,
        contactPhone: String,
        applicantRole: ApplicantRole,
        documents: List<ProofDocument>
    ): Outcome<Restaurant> = runCatchingOutcome {
        remote.resubmitRestaurant(restaurantId, contactPhone, applicantRole, documents)
    }

    override suspend fun setRestaurantActive(
        restaurantId: String,
        active: Boolean
    ): Outcome<Restaurant> = runCatchingOutcome {
        remote.setRestaurantActive(restaurantId, active).also {
            dao.upsertRestaurants(listOf(it.toEntity()))
        }
    }

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
        runCatching {
            val stats = remote.fetchAllQueueStats()
            dao.upsertStats(stats.map { it.toEntity() })

            // Counter untuk restoran yang belum pernah di-cache berarti restoran itu
            // mendaftar setelah fetch katalog terakhir. Tiap id yang belum dikenal dicoba
            // sekali saja, supaya restoran yang dijeda (statistik tetap publik, barisnya
            // tersembunyi) tidak membuat setiap tick counter memicu fetch katalog.
            val known = dao.observeAll().first().map { it.id }.toSet()
            val unseen = stats.map { it.restaurantId }.filter { it !in known && it !in probedIds }
            if (unseen.isNotEmpty()) {
                probedIds += unseen
                refreshCatalogue()
            }
        }.onFailure { Log.i(TAG, "Stats refresh failed, serving cache", it) }
    }

    private suspend fun refreshStats(restaurantId: String) {
        runCatching {
            remote.fetchQueueStats(restaurantId)?.let { dao.upsertStats(it.toEntity()) }
        }.onFailure { Log.i(TAG, "Stats refresh failed for $restaurantId", it) }
    }

    /** Filter digabung dengan AND, filter yang tidak diisi dilewati. */
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

            // Restoran yang belum punya jarak tetap dipertahankan: menyembunyikannya
            // karena GPS belum dapat akan terlihat seperti daftar yang rusak.
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
            // Jarak yang tidak diketahui diurutkan paling akhir, bukan paling awal.
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
