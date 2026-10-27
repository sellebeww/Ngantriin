package org.umn.ngantriin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.umn.ngantriin.domain.model.NotificationType
import org.umn.ngantriin.domain.model.QueueEntry
import org.umn.ngantriin.domain.model.QueueMath
import org.umn.ngantriin.domain.model.QueueStatus
import java.time.Instant

/** Estimasi waktu tunggu, dengan contoh hitungan. */
class QueueMathTest {

    /**
     * Contoh hitungan: A-020 sedang didudukkan, A-021 sampai A-026 masih
     * mengantre, dan pelanggan memegang A-027.
     */
    private val sampleQueue = buildList {
        add(entry(20, QueueStatus.TABLE_READY))
        (21..26).forEach { add(entry(it, QueueStatus.WAITING)) }
        add(entry(27, QueueStatus.WAITING))
        add(entry(28, QueueStatus.WAITING))
    }

    private fun entry(
        sequence: Int,
        status: QueueStatus,
        restaurantId: String = "r1",
        called: Boolean = status == QueueStatus.CALLED || status == QueueStatus.TABLE_READY
    ) = QueueEntry(
        id = "q$sequence",
        restaurantId = restaurantId,
        userId = "u$sequence",
        queueNumber = "A-%03d".format(sequence),
        ticketSequence = sequence,
        partySize = 2,
        status = status,
        joinedAt = Instant.EPOCH.plusSeconds(sequence.toLong()),
        calledAt = if (called) Instant.EPOCH.plusSeconds(1_000L + sequence) else null
    )

    @Test
    fun `people ahead matches the worked example`() {
        assertEquals(6, QueueMath.peopleAhead(sampleQueue, ticketSequence = 27))
    }

    @Test
    fun `a group being seated no longer counts as ahead`() {
        // A-020 TABLE_READY: mejanya sudah terpakai tapi bukan lagi dalam antrean.
        assertEquals(0, QueueMath.peopleAhead(sampleQueue, ticketSequence = 21))
    }

    @Test
    fun `a checked-in group still waiting for a table counts as ahead`() {
        val queue = listOf(
            entry(1, QueueStatus.CHECKED_IN, called = true),
            entry(2, QueueStatus.CHECKED_IN, called = false),
            entry(3, QueueStatus.WAITING)
        )
        assertEquals(2, QueueMath.peopleAhead(queue, ticketSequence = 3))
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
        // Restoran yang salah konfigurasi dengan 0 menit tetap menghasilkan estimasi yang nyata.
        assertEquals(4, QueueMath.estimatedWaitMinutes(peopleAhead = 4, averageServiceMinutes = 0))
    }

    @Test
    fun `venue estimate equals what the next ticket would be told`() {
        val queue = listOf(
            entry(1, QueueStatus.CALLED),
            entry(2, QueueStatus.CHECKED_IN, called = true),
            entry(3, QueueStatus.TABLE_READY),
            entry(4, QueueStatus.WAITING),
            entry(5, QueueStatus.COMPLETED)
        )
        val forNewcomer = QueueMath.estimatedWaitMinutes(
            QueueMath.peopleAhead(queue, ticketSequence = 6), averageServiceMinutes = 3
        )
        assertEquals(9, forNewcomer)
        assertEquals(forNewcomer, QueueMath.estimatedWaitForNewGroup(queue, averageServiceMinutes = 3))
    }

    @Test
    fun `waiting count excludes everyone past the front of the line`() {
        assertEquals(8, QueueMath.waitingCount(sampleQueue))
    }

    @Test
    fun `an early check-in is still waiting to be called, a called one is not`() {
        val queue = listOf(
            entry(1, QueueStatus.CHECKED_IN, called = true),
            entry(2, QueueStatus.CHECKED_IN, called = false),
            entry(3, QueueStatus.WAITING),
            entry(4, QueueStatus.EXPIRED)
        )
        assertEquals(2, QueueMath.waitingCount(queue))
    }

    @Test
    fun `next to call is the lowest ticket not yet called, early arrivals included`() {
        val queue = listOf(
            entry(1, QueueStatus.CALLED),
            entry(2, QueueStatus.CHECKED_IN, called = false),
            entry(3, QueueStatus.WAITING)
        )
        assertEquals("A-002", QueueMath.nextToCall(queue)?.queueNumber)
        assertNull(QueueMath.nextToCall(listOf(entry(1, QueueStatus.CALLED))))
    }

    @Test
    fun `an early check-in is not what the board says is being served`() {
        val queue = listOf(
            entry(1, QueueStatus.COMPLETED),
            entry(5, QueueStatus.CHECKED_IN, called = false)
        )
        assertEquals("A-001", QueueMath.currentServing(queue)?.queueNumber)
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
    fun `notification milestones fire at the defined thresholds`() {
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
        assertEquals(
            NotificationType.TABLE_READY,
            QueueMath.notificationMilestone(QueueStatus.TABLE_READY, peopleAhead = 0)
        )
    }

    @Test
    fun `checked in early does not nag the customer to head back`() {
        // Mereka sudah di sana, kabar berikutnya yang didengar adalah soal meja.
        assertEquals(
            NotificationType.CHECKED_IN,
            QueueMath.notificationMilestone(QueueStatus.CHECKED_IN, peopleAhead = 1)
        )
    }

    @Test
    fun `each ending has its own notification`() {
        assertEquals(NotificationType.COMPLETED, QueueMath.notificationMilestone(QueueStatus.COMPLETED, 0))
        assertEquals(NotificationType.CANCELLED, QueueMath.notificationMilestone(QueueStatus.CANCELLED, 0))
        assertEquals(NotificationType.EXPIRED, QueueMath.notificationMilestone(QueueStatus.EXPIRED, 0))
    }

    @Test
    fun `check-in window counts down and then is overdue`() {
        val now = Instant.parse("2026-10-08T12:00:00Z")
        val called = entry(1, QueueStatus.CALLED).copy(checkInDeadline = now.plusSeconds(600))

        assertEquals(600L, called.checkInSecondsLeft(now))
        assertFalse(called.isCheckInOverdue(now))
        assertEquals(0L, called.checkInSecondsLeft(now.plusSeconds(601)))
        assertTrue(called.isCheckInOverdue(now.plusSeconds(601)))

        // Tidak ada jendela di luar CALLED.
        assertNull(entry(2, QueueStatus.WAITING).checkInSecondsLeft(now))
    }
}
