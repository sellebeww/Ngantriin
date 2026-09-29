package org.umn.ngantriin.domain.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A plain lat/lng pair, so the domain layer never imports Android's Location. */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double
) {
    companion object {
        private const val EARTH_RADIUS_METERS = 6_371_000.0

        /** Gading Serpong, used only as a map centre when GPS is unavailable. */
        val GADING_SERPONG = GeoPoint(-6.2412, 106.6256)

        /**
         * Great-circle distance in metres (haversine). Accurate to well under
         * a metre at city scale, which is all a check-in radius needs.
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
