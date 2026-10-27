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
     * Stream restoran sesuai [filters], cache dulu, dengan jarak dihitung
     * terhadap [origin] kalau lokasi tersedia.
     *
     * Flow langsung mengirim isi cache, lalu lagi setiap refresh atau perubahan
     * statistik realtime masuk, jadi layar tidak pernah menampilkan daftar kosong
     * saat network lambat.
     */
    fun observeRestaurants(
        origin: GeoPoint?,
        filters: RestaurantFilters
    ): Flow<List<RestaurantListing>>

    /** Mengambil katalog restoran dan counter antrean dari backend. */
    suspend fun refreshRestaurants(): Outcome<Unit>

    fun observeRestaurant(restaurantId: String): Flow<Restaurant?>

    /** Counter antrean live untuk satu restoran. */
    fun observeQueueStats(restaurantId: String): Flow<QueueStats?>

    suspend fun refreshRestaurant(restaurantId: String): Outcome<Restaurant>

    /** Kategori berbeda yang ada di katalog, untuk sheet filter. */
    fun observeCategories(): Flow<List<String>>

    /** Restoran yang boleh dikelola akun staf ini, termasuk yang dijeda. */
    suspend fun managedRestaurants(userId: String): Outcome<List<Restaurant>>

    /**
     * Memasukkan restoran baru ke aplikasi. Pemanggil menjadi stafnya dan
     * restoran langsung tampil ke pelanggan, dikembalikan seperti yang tersimpan.
     */
    suspend fun registerRestaurant(registration: RestaurantRegistration): Outcome<Restaurant>

    /** Menampilkan ([active]) atau menjeda restoran yang dikelola pemanggil. */
    suspend fun setRestaurantActive(restaurantId: String, active: Boolean): Outcome<Restaurant>

    suspend fun resubmitRestaurant(
        restaurantId: String,
        contactPhone: String,
        applicantRole: ApplicantRole,
        documents: List<ProofDocument>
    ): Outcome<Restaurant>
}
