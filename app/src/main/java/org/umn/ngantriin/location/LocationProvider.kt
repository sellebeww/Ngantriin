package org.umn.ngantriin.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.umn.ngantriin.core.AppError
import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.domain.model.GeoPoint
import kotlin.coroutines.resume

/**
 * Section 16. The only place in the app that touches Play Services location,
 * so permission handling and the Android Location type stop at this boundary.
 */
class LocationProvider(context: Context) {

    private val appContext = context.applicationContext
    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(appContext)

    fun hasPermission(): Boolean = FINE_AND_COARSE.any { permission ->
        ContextCompat.checkSelfPermission(appContext, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** Precise permission specifically, which check-in prefers. */
    fun hasPrecisePermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled(): Boolean {
        val manager = appContext.getSystemService(LocationManager::class.java) ?: return false
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * A fresh fix, falling back to the last known one.
     *
     * `getCurrentLocation` can sit there for a long time indoors, so it is
     * bounded: a slightly stale fix beats a spinner that never resolves.
     */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(
        highAccuracy: Boolean = false
    ): Outcome<GeoPoint> {
        if (!hasPermission()) return Outcome.Failure(AppError.LocationPermissionDenied)
        if (!isLocationEnabled()) return Outcome.Failure(AppError.LocationUnavailable)

        val priority = if (highAccuracy && hasPrecisePermission()) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val fresh = withTimeoutOrNull(FIX_TIMEOUT_MILLIS) {
            awaitLocation(priority)
        }
        if (fresh != null) return Outcome.Success(fresh)

        val last = runCatching { awaitLastLocation() }.getOrNull()
        return last?.let { Outcome.Success(it) } ?: Outcome.Failure(AppError.LocationUnavailable)
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitLocation(priority: Int): GeoPoint? =
        suspendCancellableCoroutine { continuation ->
            val request = CurrentLocationRequest.Builder()
                .setPriority(priority)
                .setMaxUpdateAgeMillis(MAX_FIX_AGE_MILLIS)
                .build()

            client.getCurrentLocation(request, null)
                .addOnSuccessListener { location ->
                    continuation.resumeIfActive(location?.let { GeoPoint(it.latitude, it.longitude) })
                }
                .addOnFailureListener { continuation.resumeIfActive(null) }
                .addOnCanceledListener { continuation.resumeIfActive(null) }
        }

    @SuppressLint("MissingPermission")
    private suspend fun awaitLastLocation(): GeoPoint? =
        suspendCancellableCoroutine { continuation ->
            client.lastLocation
                .addOnSuccessListener { location ->
                    continuation.resumeIfActive(location?.let { GeoPoint(it.latitude, it.longitude) })
                }
                .addOnFailureListener { continuation.resumeIfActive(null) }
                .addOnCanceledListener { continuation.resumeIfActive(null) }
        }

    private fun CancellableContinuation<GeoPoint?>.resumeIfActive(value: GeoPoint?) {
        if (isActive) resume(value)
    }

    companion object {
        val FINE_AND_COARSE = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        private const val FIX_TIMEOUT_MILLIS = 8_000L
        private const val MAX_FIX_AGE_MILLIS = 30_000L
    }
}
