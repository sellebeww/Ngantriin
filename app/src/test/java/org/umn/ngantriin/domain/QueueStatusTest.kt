package org.umn.ngantriin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.umn.ngantriin.domain.model.QueueStatus

/**
 * The transition table in section 14, checked exhaustively. This is the rule
 * the SQL trigger enforces too (`is_valid_queue_transition`), so if these
 * expectations ever change, both have to move together.
 */
class QueueStatusTest {

    private val legalTransitions = setOf(
        QueueStatus.WAITING to QueueStatus.ALMOST_THERE,
        QueueStatus.WAITING to QueueStatus.CALLED,
        QueueStatus.WAITING to QueueStatus.CANCELLED,
        QueueStatus.ALMOST_THERE to QueueStatus.CALLED,
        QueueStatus.ALMOST_THERE to QueueStatus.CANCELLED,
        QueueStatus.CALLED to QueueStatus.CHECKED_IN,
        QueueStatus.CALLED to QueueStatus.COMPLETED,
        QueueStatus.CALLED to QueueStatus.CANCELLED,
        QueueStatus.CHECKED_IN to QueueStatus.COMPLETED
    )

    @Test
    fun `only the documented transitions are allowed`() {
        for (from in QueueStatus.entries) {
            for (to in QueueStatus.entries) {
                val expected = (from to to) in legalTransitions
                assertEquals(
                    "$from -> $to",
                    expected,
                    from.canTransitionTo(to)
                )
            }
        }
    }

    @Test
    fun `terminal states never transition again`() {
        for (to in QueueStatus.entries) {
            assertFalse(QueueStatus.COMPLETED.canTransitionTo(to))
            assertFalse(QueueStatus.CANCELLED.canTransitionTo(to))
        }
    }

    @Test
    fun `a cancelled queue can never return to waiting`() {
        // Section 18 states this explicitly; it is the one rule a customer
        // would notice immediately if it broke.
        assertFalse(QueueStatus.CANCELLED.canTransitionTo(QueueStatus.WAITING))
    }

    @Test
    fun `active states are the ones that hold a place in line`() {
        assertTrue(QueueStatus.WAITING.isActive)
        assertTrue(QueueStatus.ALMOST_THERE.isActive)
        assertTrue(QueueStatus.CALLED.isActive)
        assertTrue(QueueStatus.CHECKED_IN.isActive)
        assertFalse(QueueStatus.COMPLETED.isActive)
        assertFalse(QueueStatus.CANCELLED.isActive)
    }

    @Test
    fun `wire decoding is case insensitive and defaults to waiting`() {
        assertEquals(QueueStatus.CHECKED_IN, QueueStatus.fromWire("checked_in"))
        assertEquals(QueueStatus.CALLED, QueueStatus.fromWire(" CALLED "))
        assertEquals(QueueStatus.WAITING, QueueStatus.fromWire(null))
        assertEquals(QueueStatus.WAITING, QueueStatus.fromWire("SOMETHING_NEW"))
    }
}
