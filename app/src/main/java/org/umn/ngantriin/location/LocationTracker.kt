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
 * Satu lokasi bersama untuk seluruh aplikasi.
 *
 * Tanpa ini, Beranda, Cari, dan detail restoran masing-masing meminta GPS
 * sendiri dan bisa menampilkan tiga jarak berbeda untuk restoran yang sama.
 * Lokasi terakhir yang baik disimpan, jadi frame pertama setelah launch
 * sudah punya jarak.
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

    /** Dipanggil setelah dialog izin selesai. */
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
