package org.umn.ngantriin.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.umn.ngantriin.domain.model.ProofDocument
import org.umn.ngantriin.domain.model.ProofType

class ProofDocumentTest {

    private fun pdf(vararg bytes: Byte) = ProofDocument(ProofType.BUSINESS_LICENSE, bytes, isPdf = true)
    private fun jpeg(vararg bytes: Byte) = ProofDocument(ProofType.OWNERSHIP_PROOF, bytes, isPdf = false)

    private val pdfHeader = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D, 0x31)
    private val jpegHeader = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())

    @Test
    fun `a real pdf and a real jpeg pass`() {
        assertTrue(pdf(*pdfHeader).isValid)
        assertTrue(jpeg(*jpegHeader).isValid)
    }

    @Test
    fun `a renamed file is refused`() {
        // Header MZ executable Windows, diklaim sebagai PDF dan foto.
        val exe = byteArrayOf(0x4D, 0x5A, 0x90.toByte(), 0x00)
        assertFalse(pdf(*exe).isValid)
        assertFalse(jpeg(*exe).isValid)
    }

    @Test
    fun `a pdf is not accepted as a photo and the other way round`() {
        assertFalse(jpeg(*pdfHeader).isValid)
        assertFalse(pdf(*jpegHeader).isValid)
    }

    @Test
    fun `an empty or truncated file is refused`() {
        assertFalse(pdf().isValid)
        assertFalse(pdf(0x25, 0x50).isValid)
    }

    @Test
    fun `a file over the limit is refused`() {
        val big = ByteArray(ProofDocument.MAX_BYTES + 1).also { pdfHeader.copyInto(it) }
        assertFalse(ProofDocument(ProofType.BUSINESS_LICENSE, big, isPdf = true).isValid)

        val atLimit = ByteArray(ProofDocument.MAX_BYTES).also { pdfHeader.copyInto(it) }
        assertTrue(ProofDocument(ProofType.BUSINESS_LICENSE, atLimit, isPdf = true).isValid)
    }
}
