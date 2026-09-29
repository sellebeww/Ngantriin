package org.umn.ngantriin.location

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.domain.model.GeoPoint
import org.umn.ngantriin.domain.repository.UserPreferencesRepository

/**
 * One shared location fix for the whole app.
 *
 * Without this, Home, Search and the restaurant detail screen would each ask
 * for GPS separately and could show three different distances for the same
 * venue. The last good fix is persisted so the first frame after launch
 * already has distances (section 16/38).
 */
class LocationTracker(
    private val provider: LocationProvider,
    private val preferences: UserPreferencesRepository,
    private val scope: CoroutineScope
) {

    private val refreshLock = Mutex()

    private val _location = MutableStateFlow<GeoPoint?>(null)
    val location: StateFlow<GeoPoint?> = _location.asStateFlow()

    private val _permissionGranted = MutableStateFlow(provider.hasPermission())
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    init {
        scope.launch {
            preferences.lastKnownLocation.first()?.let { cached ->
                if (_location.value == null) _location.value = cached
            }
            if (provider.hasPermission()) refreshNow()
        }
    }

    /** Called after the permission dialog resolves. */
    fun onPermissionResult(granted: Boolean) {
        _permissionGranted.value = granted
        if (granted) scope.launch { refreshNow() }
    }

    fun refresh() {
        scope.launch { refreshNow() }
    }

    private suspend fun refreshNow() = refreshLock.withLock {
        val granted = provider.hasPermission()
        _permissionGranted.value = granted
        if (!granted) return@withLock

        val outcome = provider.currentLocation(highAccuracy = false)
        if (outcome is Outcome.Success) store(outcome.data)
    }

    private suspend fun store(point: GeoPoint) {
        _location.value = point
        preferences.setLastKnownLocation(point)
    }
}
