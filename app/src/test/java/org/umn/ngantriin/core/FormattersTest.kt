package org.umn.ngantriin.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class FormattersTest {

    @Test
    fun `distance switches units at a kilometre`() {
        assertEquals("850 m", Formatters.distance(850.0))
        assertEquals("1.4 km", Formatters.distance(1_420.0))
        assertEquals("—", Formatters.distance(null))
    }

    @Test
    fun `wait time reads naturally at every scale`() {
        assertEquals("No wait", Formatters.waitTime(0))
        assertEquals("~18 min", Formatters.waitTime(18))
        assertEquals("~1 hr", Formatters.waitTime(60))
        assertEquals("~1 hr 5 min", Formatters.waitTime(65))
    }

    @Test
    fun `group counts are singular when there is one`() {
        assertEquals("1 group", Formatters.groups(1))
        assertEquals("12 groups", Formatters.groups(12))
        assertEquals("0 groups", Formatters.groups(0))
    }

    @Test
    fun `relative time degrades from minutes to a date`() {
        val now = Instant.parse("2026-09-21T12:00:00Z")
        assertEquals("just now", Formatters.relative(now, now))
        assertEquals("12 min ago", Formatters.relative(now.minus(Duration.ofMinutes(12)), now))
        assertEquals("3 hr ago", Formatters.relative(now.minus(Duration.ofHours(3)), now))
        assertEquals("2 d ago", Formatters.relative(now.minus(Duration.ofDays(2)), now))
    }

    @Test
    fun `greeting follows the clock`() {
        assertEquals("Good morning", Formatters.greeting(8))
        assertEquals("Good afternoon", Formatters.greeting(13))
        assertEquals("Good evening", Formatters.greeting(18))
        assertEquals("Good night", Formatters.greeting(23))
    }
}
