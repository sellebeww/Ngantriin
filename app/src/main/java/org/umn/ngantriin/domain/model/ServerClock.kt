package org.umn.ngantriin.domain.model

import java.time.Duration
import java.time.Instant

/**
 * The phone's clock can be minutes off, but `check_in_deadline` is set by the
 * server. Every position fetch carries the server's `now()`; the difference
 * from the local clock is kept here so countdowns match what the server will
 * enforce. Offset error is at most one network round trip.
 */
object ServerClock {
    @Volatile
    private var offset: Duration = Duration.ZERO

    fun now(): Instant = Instant.now().plus(offset)

    fun sync(serverNow: Instant, receivedAt: Instant = Instant.now()) {
        offset = Duration.between(receivedAt, serverNow)
    }

    fun reset() {
        offset = Duration.ZERO
    }
}
