package org.umn.ngantriin.domain.repository

import kotlinx.coroutines.flow.Flow
import org.umn.ngantriin.domain.model.GeoPoint

/** Small, device-local settings. Nothing here is user data worth syncing. */
interface UserPreferencesRepository {

    val onboardingCompleted: Flow<Boolean>
    suspend fun setOnboardingCompleted(completed: Boolean)

    /** Section 38: whether the location rationale has already been shown. */
    val locationRationaleShown: Flow<Boolean>
    suspend fun setLocationRationaleShown(shown: Boolean)

    val notificationsEnabled: Flow<Boolean>
    suspend fun setNotificationsEnabled(enabled: Boolean)

    /** Last good fix, so the first frame after launch can already show distances. */
    val lastKnownLocation: Flow<GeoPoint?>
    suspend fun setLastKnownLocation(point: GeoPoint)
}
