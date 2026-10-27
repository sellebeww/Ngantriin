package org.umn.ngantriin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.umn.ngantriin.data.mapper.toDomain
import org.umn.ngantriin.data.remote.dto.RestaurantDto
import org.umn.ngantriin.domain.model.VerificationStatus

class VerificationStatusTest {

    @Test
    fun `wire values map to their status`() {
        assertEquals(VerificationStatus.APPROVED, VerificationStatus.fromWire("APPROVED"))
        assertEquals(VerificationStatus.REJECTED, VerificationStatus.fromWire("rejected"))
        assertEquals(VerificationStatus.PENDING_VERIFICATION, VerificationStatus.fromWire("PENDING_VERIFICATION"))
    }

    @Test
    fun `an unreadable status never grants access`() {
        assertEquals(VerificationStatus.PENDING_VERIFICATION, VerificationStatus.fromWire(null))
        assertEquals(VerificationStatus.PENDING_VERIFICATION, VerificationStatus.fromWire("SOMETHING_NEW"))
    }

    @Test
    fun `only an approved restaurant is manageable`() {
        val base = RestaurantDto(id = "r", name = "R", latitude = 0.0, longitude = 0.0)
        assertTrue(base.toDomain().isApproved)
        assertFalse(base.copy(verificationStatus = "PENDING_VERIFICATION").toDomain().isApproved)
        val rejected = base.copy(verificationStatus = "REJECTED", verificationNote = "Dokumen buram").toDomain()
        assertFalse(rejected.isApproved)
        assertEquals("Dokumen buram", rejected.verificationNote)
    }
}
