package org.umn.ngantriin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.umn.ngantriin.domain.model.NotificationType
import org.umn.ngantriin.domain.model.QueueEntry
import org.umn.ngantriin.domain.model.QueueMath
import org.umn.ngantriin.domain.model.QueueStatus
import java.time.Instant

/** Section 23 and 40, using the worked example from the spec. */
class QueueMathTest {

    /**
     * The section 40 sample: A-020 is at the table, A-021 to A-026 are still
     * in line, and the customer holds A-027.
     */
    private val sampleQueue = buildList {
        add(entry(20, QueueStatus.CHECKED_IN))
        (21..26).forEach { add(entry(it, QueueStatus.WAITING)) }
        add(entry(27, QueueStatus.WAITING))
        add(entry(28, QueueStatus.WAITING))
    }

    private fun entry(
        sequence: Int,
        status: QueueStatus,
        restaurantId: String = "r1"
    ) = QueueEntry(
        id = "q$sequence",
        restaurantId = restaurantId,
        userId = "u$sequence",
        queueNumber = "A-%03d".format(sequence),
        ticketSequence = sequence,
        partySize = 2,
        status = status,
        joinedAt = Instant.EPOCH.plusSeconds(sequence.toLong())
    )

    @Test
    fun `people ahead matches the worked example`() {
        assertEquals(6, QueueMath.peopleAhead(sampleQueue, ticketSequence = 27))
    }

    @Test
    fun `a seated group no longer counts as ahead`() {
        // A-020 is CHECKED_IN, so its table is taken but it is not in line.
        assertEquals(0, QueueMath.peopleAhead(sampleQueue, ticketSequence = 21))
    }

    @Test
    fun `a called group still counts as ahead`() {
        val queue = listOf(
            entry(1, QueueStatus.CALLED),
            entry(2, QueueStatus.WAITING)
        )
        assertEquals(1, QueueMath.peopleAhead(queue, ticketSequence = 2))
    }

    @Test
    fun `cancelled and completed groups drop out of the count`() {
        val queue = listOf(
            entry(1, QueueStatus.COMPLETED),
            entry(2, QueueStatus.CANCELLED),
            entry(3, QueueStatus.WAITING),
            entry(4, QueueStatus.WAITING)
        )
        assertEquals(1, QueueMath.peopleAhead(queue, ticketSequence = 4))
    }

    @Test
    fun `estimated wait is service time times people ahead`() {
        assertEquals(18, QueueMath.estimatedWaitMinutes(peopleAhead = 6, averageServiceMinutes = 3))
        assertEquals(12, QueueMath.estimatedWaitMinutes(peopleAhead = 4, averageServiceMinutes = 3))
        assertEquals(0, QueueMath.estimatedWaitMinutes(peopleAhead = 0, averageServiceMinutes = 3))
    }

    @Test
    fun `estimated wait never goes negative or multiplies by zero`() {
        assertEquals(0, QueueMath.estimatedWaitMinutes(peopleAhead = -3, averageServiceMinutes = 3))
        // A misconfigured restaurant with 0 minutes still yields a real estimate.
        assertEquals(4, QueueMath.estimatedWaitMinutes(peopleAhead = 4, averageServiceMinutes = 0))
    }

    @Test
    fun `waiting count excludes everyone past the front of the line`() {
        assertEquals(8, QueueMath.waitingCount(sampleQueue))
    }

    @Test
    fun `now serving prefers a called ticket over a completed one`() {
        val queue = listOf(
            entry(1, QueueStatus.COMPLETED),
            entry(2, QueueStatus.CALLED),
            entry(3, QueueStatus.WAITING)
        )
        assertEquals("A-002", QueueMath.currentServing(queue)?.queueNumber)
    }

    @Test
    fun `now serving falls back to the last completed ticket`() {
        val queue = listOf(
            entry(1, QueueStatus.COMPLETED),
            entry(2, QueueStatus.COMPLETED),
            entry(3, QueueStatus.WAITING)
        )
        assertEquals("A-002", QueueMath.currentServing(queue)?.queueNumber)
    }

    @Test
    fun `notification milestones fire at the section 37 thresholds`() {
        assertNull(QueueMath.notificationMilestone(QueueStatus.WAITING, peopleAhead = 6))
        assertNull(QueueMath.notificationMilestone(QueueStatus.WAITING, peopleAhead = 4))
        assertEquals(
            NotificationType.ALMOST_THERE,
            QueueMath.notificationMilestone(QueueStatus.WAITING, peopleAhead = 3)
        )
        assertEquals(
            NotificationType.RETURN_NOW,
            QueueMath.notificationMilestone(QueueStatus.ALMOST_THERE, peopleAhead = 1)
        )
        assertEquals(
            NotificationType.CALLED,
            QueueMath.notificationMilestone(QueueStatus.CALLED, peopleAhead = 0)
        )
    }

    @Test
    fun `terminal tickets produce no further milestones`() {
        assertNull(QueueMath.notificationMilestone(QueueStatus.CANCELLED, peopleAhead = 0))
    }
}
