package org.umn.ngantriin.domain.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Pasangan lat/lng biasa, supaya lapisan domain tidak pernah meng-import Location Android. */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double
) {
    companion object {
        private const val EARTH_RADIUS_METERS = 6_371_000.0

        /**
         * Jarak great-circle dalam meter (haversine). Akurasinya jauh di bawah
         * satu meter untuk skala kota, cukup untuk radius check-in.
         */
        fun distanceMeters(from: GeoPoint, to: GeoPoint): Double {
            val dLat = Math.toRadians(to.latitude - from.latitude)
            val dLon = Math.toRadians(to.longitude - from.longitude)
            val lat1 = Math.toRadians(from.latitude)
            val lat2 = Math.toRadians(to.latitude)
            val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
            return 2 * EARTH_RADIUS_METERS * asin(sqrt(a.coerceIn(0.0, 1.0)))
        }
    }

    fun distanceTo(other: GeoPoint): Double = distanceMeters(this, other)
}
