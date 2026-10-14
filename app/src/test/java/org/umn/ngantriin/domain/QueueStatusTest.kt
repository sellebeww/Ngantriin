package org.umn.ngantriin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.umn.ngantriin.domain.model.QueueStatus

/**
 * The transition table, checked exhaustively. This is the rule
 * the SQL trigger enforces too (`is_valid_queue_transition`), so if these
 * expectations ever change, both have to move together.
 */
class QueueStatusTest {

    private val legalTransitions = setOf(
        QueueStatus.WAITING to QueueStatus.ALMOST_THERE,
        QueueStatus.WAITING to QueueStatus.CALLED,
        QueueStatus.WAITING to QueueStatus.CHECKED_IN,
        QueueStatus.WAITING to QueueStatus.CANCELLED,
        QueueStatus.ALMOST_THERE to QueueStatus.CALLED,
        QueueStatus.ALMOST_THERE to QueueStatus.CHECKED_IN,
        QueueStatus.ALMOST_THERE to QueueStatus.CANCELLED,
        QueueStatus.CALLED to QueueStatus.CHECKED_IN,
        QueueStatus.CALLED to QueueStatus.CANCELLED,
        QueueStatus.CALLED to QueueStatus.EXPIRED,
        QueueStatus.CHECKED_IN to QueueStatus.TABLE_READY,
        QueueStatus.CHECKED_IN to QueueStatus.CANCELLED,
        QueueStatus.TABLE_READY to QueueStatus.COMPLETED,
        QueueStatus.TABLE_READY to QueueStatus.CANCELLED
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
            assertFalse(QueueStatus.EXPIRED.canTransitionTo(to))
        }
    }

    @Test
    fun `a cancelled queue can never return to waiting`() {
        // This is the one rule a customer
        // would notice immediately if it broke.
        assertFalse(QueueStatus.CANCELLED.canTransitionTo(QueueStatus.WAITING))
    }

    @Test
    fun `active states are the ones that hold a place in line`() {
        assertTrue(QueueStatus.WAITING.isActive)
        assertTrue(QueueStatus.ALMOST_THERE.isActive)
        assertTrue(QueueStatus.CALLED.isActive)
        assertTrue(QueueStatus.CHECKED_IN.isActive)
        assertTrue(QueueStatus.TABLE_READY.isActive)
        assertFalse(QueueStatus.COMPLETED.isActive)
        assertFalse(QueueStatus.CANCELLED.isActive)
        assertFalse(QueueStatus.EXPIRED.isActive)
    }

    @Test
    fun `the journey must pass check-in and table ready to complete`() {
        // The concept's order: no skipping from called straight to done.
        assertFalse(QueueStatus.CALLED.canTransitionTo(QueueStatus.COMPLETED))
        assertFalse(QueueStatus.CALLED.canTransitionTo(QueueStatus.TABLE_READY))
        assertFalse(QueueStatus.CHECKED_IN.canTransitionTo(QueueStatus.COMPLETED))
    }

    @Test
    fun `only a called ticket can expire`() {
        QueueStatus.entries.forEach { from ->
            assertEquals("$from -> EXPIRED", from == QueueStatus.CALLED, from.canTransitionTo(QueueStatus.EXPIRED))
        }
    }

    @Test
    fun `check-in is open early and while called, not after`() {
        assertTrue(QueueStatus.WAITING.allowsCheckIn)
        assertTrue(QueueStatus.ALMOST_THERE.allowsCheckIn)
        assertTrue(QueueStatus.CALLED.allowsCheckIn)
        assertFalse(QueueStatus.CHECKED_IN.allowsCheckIn)
        assertFalse(QueueStatus.TABLE_READY.allowsCheckIn)
        assertFalse(QueueStatus.EXPIRED.allowsCheckIn)
    }

    @Test
    fun `wire decoding is case insensitive and defaults to waiting`() {
        assertEquals(QueueStatus.CHECKED_IN, QueueStatus.fromWire("checked_in"))
        assertEquals(QueueStatus.CALLED, QueueStatus.fromWire(" CALLED "))
        assertEquals(QueueStatus.WAITING, QueueStatus.fromWire(null))
        assertEquals(QueueStatus.WAITING, QueueStatus.fromWire("SOMETHING_NEW"))
    }
}
