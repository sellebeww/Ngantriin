package org.umn.ngantriin.domain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.umn.ngantriin.domain.model.QueueEntry
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.domain.model.ServerClock
import java.time.Duration
import java.time.Instant

class ServerClockTest {

    @After
    fun tearDown() = ServerClock.reset()

    @Test
    fun `a phone running 5 minutes fast still counts the server's window`() {
        val phoneNow = Instant.now()
        // Server says it is 5 minutes earlier than the phone believes.
        ServerClock.sync(serverNow = phoneNow.minus(Duration.ofMinutes(5)), receivedAt = phoneNow)

        val called = QueueEntry(
            id = "q", restaurantId = "r", userId = "u", queueNumber = "A-001", ticketSequence = 1,
            partySize = 1, status = QueueStatus.CALLED, joinedAt = phoneNow,
            // Deadline is 10 minutes after the server's call, i.e. 5 minutes from the phone's "now".
            checkInDeadline = phoneNow.plus(Duration.ofMinutes(5))
        )
        val left = called.checkInSecondsLeft()!!
        assertTrue("expected ~600s, got $left", left in 595..600)
        assertEquals(false, called.isCheckInOverdue())
    }
}
