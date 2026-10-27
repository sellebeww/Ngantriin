package org.umn.ngantriin.domain.repository

import kotlinx.coroutines.flow.Flow
import org.umn.ngantriin.domain.model.GeoPoint

/** Pengaturan kecil milik perangkat. Tidak ada yang perlu disinkronkan. */
interface UserPreferencesRepository {

    val onboardingCompleted: Flow<Boolean>
    suspend fun setOnboardingCompleted(completed: Boolean)

    /** Apakah penjelasan izin lokasi sudah pernah ditampilkan. */
    val locationRationaleShown: Flow<Boolean>
    suspend fun setLocationRationaleShown(shown: Boolean)

    val notificationsEnabled: Flow<Boolean>
    suspend fun setNotificationsEnabled(enabled: Boolean)

    /** Lokasi terakhir yang baik, supaya frame pertama setelah launch sudah bisa menampilkan jarak. */
    val lastKnownLocation: Flow<GeoPoint?>
    suspend fun setLastKnownLocation(point: GeoPoint)

    /** Restoran yang dibintangi pelanggan. Milik perangkat, seperti pengaturan lain. */
    val favoriteRestaurantIds: Flow<Set<String>>
    suspend fun toggleFavoriteRestaurant(restaurantId: String)

    /**
     * Email akun yang mendaftar sebagai staf restoran dan belum mengisi
     * pengajuan restoran. Dengan ini aplikasi bisa membuka pengajuan setelah
     * login pertama, walau konfirmasi email terjadi di antaranya.
     */
    val staffApplicantEmail: Flow<String?>
    suspend fun setStaffApplicantEmail(email: String?)
}
