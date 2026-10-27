package org.umn.ngantriin.domain.repository

import kotlinx.coroutines.flow.Flow
import org.umn.ngantriin.domain.model.GeoPoint

/** Small, device-local settings. Nothing here is user data worth syncing. */
interface UserPreferencesRepository {

    val onboardingCompleted: Flow<Boolean>
    suspend fun setOnboardingCompleted(completed: Boolean)

    /** Whether the location rationale has already been shown. */
    val locationRationaleShown: Flow<Boolean>
    suspend fun setLocationRationaleShown(shown: Boolean)

    val notificationsEnabled: Flow<Boolean>
    suspend fun setNotificationsEnabled(enabled: Boolean)

    /** Last good fix, so the first frame after launch can already show distances. */
    val lastKnownLocation: Flow<GeoPoint?>
    suspend fun setLastKnownLocation(point: GeoPoint)

    /** Restaurants the customer starred. Device-local, like the other settings. */
    val favoriteRestaurantIds: Flow<Set<String>>
    suspend fun toggleFavoriteRestaurant(restaurantId: String)

    /**
     * Email of an account that signed up as restaurant staff and has not yet
     * filled in the restaurant application. Lets the app open the application
     * after the first sign-in, even if email confirmation came in between.
     */
    val staffApplicantEmail: Flow<String?>
    suspend fun setStaffApplicantEmail(email: String?)
}
