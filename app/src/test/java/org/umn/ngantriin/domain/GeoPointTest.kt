package org.umn.ngantriin.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import org.umn.ngantriin.domain.model.GeoPoint

/**
 * [GeoPoint.distanceTo] masih dipakai untuk urutan terdekat di Beranda/Cari dan
 * tampilan jarak di Detail Restoran (lihat wiring LocationTracker di AppContainer),
 * walau verifikasi check-in tidak lagi memakainya: check-in sekarang diverifikasi
 * lewat foto (lihat CheckInViewModel / 0006_checkin_photo.sql).
 */
class GeoPointTest {

    private val waroeng = GeoPoint(-6.238800, 106.626500)

    @Test
    fun `distance to itself is zero`() {
        assertEquals(0.0, waroeng.distanceTo(waroeng), 0.01)
    }

    @Test
    fun `distance is symmetric`() {
        val other = GeoPoint(-6.243100, 106.630900)
        assertEquals(waroeng.distanceTo(other), other.distanceTo(waroeng), 0.001)
    }

    @Test
    fun `one degree of latitude is about 111 km`() {
        val north = GeoPoint(waroeng.latitude + 1.0, waroeng.longitude)
        assertEquals(111_195.0, waroeng.distanceTo(north), 500.0)
    }

    @Test
    fun `short city distances are accurate to within a metre`() {
        // Sekitar 100 m di utara restoran.
        val nearby = GeoPoint(waroeng.latitude + 0.000899, waroeng.longitude)
        assertEquals(100.0, waroeng.distanceTo(nearby), 1.0)
    }

}
