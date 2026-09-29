package org.umn.ngantriin.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.umn.ngantriin.domain.model.GeoPoint
import org.umn.ngantriin.domain.repository.UserPreferencesRepository
import java.io.IOException

private val Context.preferencesStore: DataStore<Preferences> by preferencesDataStore("ngantriin_prefs")

class UserPreferencesDataStore(context: Context) : UserPreferencesRepository {

    private val store = context.applicationContext.preferencesStore

    private object Keys {
        val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
        val locationRationaleShown = booleanPreferencesKey("location_rationale_shown")
        val notificationsEnabled = booleanPreferencesKey("notifications_enabled")
        val lastLatitude = doublePreferencesKey("last_latitude")
        val lastLongitude = doublePreferencesKey("last_longitude")

        // Demo-mode session, see DemoAuthRemoteDataSource.
        val demoUserId = stringPreferencesKey("demo_user_id")
        val demoUserName = stringPreferencesKey("demo_user_name")
        val demoUserEmail = stringPreferencesKey("demo_user_email")
        val demoUserRole = stringPreferencesKey("demo_user_role")
    }

    /** A corrupted or unreadable store must not stop the app from starting. */
    private val preferences: Flow<Preferences> = store.data.catch { cause ->
        if (cause is IOException) emit(emptyPreferences()) else throw cause
    }

    override val onboardingCompleted: Flow<Boolean> =
        preferences.map { it[Keys.onboardingCompleted] == true }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        store.edit { it[Keys.onboardingCompleted] = completed }
    }

    override val locationRationaleShown: Flow<Boolean> =
        preferences.map { it[Keys.locationRationaleShown] == true }

    override suspend fun setLocationRationaleShown(shown: Boolean) {
        store.edit { it[Keys.locationRationaleShown] = shown }
    }

    override val notificationsEnabled: Flow<Boolean> =
        preferences.map { it[Keys.notificationsEnabled] ?: true }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        store.edit { it[Keys.notificationsEnabled] = enabled }
    }

    override val lastKnownLocation: Flow<GeoPoint?> = preferences.map { prefs ->
        val lat = prefs[Keys.lastLatitude]
        val lng = prefs[Keys.lastLongitude]
        if (lat != null && lng != null) GeoPoint(lat, lng) else null
    }

    override suspend fun setLastKnownLocation(point: GeoPoint) {
        store.edit {
            it[Keys.lastLatitude] = point.latitude
            it[Keys.lastLongitude] = point.longitude
        }
    }

    // --- Demo session -------------------------------------------------------

    data class DemoProfile(
        val id: String,
        val name: String,
        val email: String,
        val role: String
    )

    val demoProfile: Flow<DemoProfile?> = preferences.map { prefs ->
        val id = prefs[Keys.demoUserId] ?: return@map null
        DemoProfile(
            id = id,
            name = prefs[Keys.demoUserName].orEmpty(),
            email = prefs[Keys.demoUserEmail].orEmpty(),
            role = prefs[Keys.demoUserRole] ?: "CUSTOMER"
        )
    }

    suspend fun setDemoProfile(profile: DemoProfile) {
        store.edit {
            it[Keys.demoUserId] = profile.id
            it[Keys.demoUserName] = profile.name
            it[Keys.demoUserEmail] = profile.email
            it[Keys.demoUserRole] = profile.role
        }
    }

    suspend fun clearDemoProfile() {
        store.edit {
            it.remove(Keys.demoUserId)
            it.remove(Keys.demoUserName)
            it.remove(Keys.demoUserEmail)
            it.remove(Keys.demoUserRole)
        }
    }
}
