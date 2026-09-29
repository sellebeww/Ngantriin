package org.umn.ngantriin.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.umn.ngantriin.domain.model.Restaurant
import java.time.LocalTime

/** Section 28.1: whether a venue is taking queues right now. */
class RestaurantOpeningTest {

    @Test
    fun `open within hours`() {
        val restaurant = restaurant(LocalTime.of(10, 0), LocalTime.of(22, 0))
        assertTrue(restaurant.isAcceptingQueue(LocalTime.of(12, 0)))
        assertTrue(restaurant.isAcceptingQueue(LocalTime.of(10, 0)))
    }

    @Test
    fun `closed outside hours`() {
        val restaurant = restaurant(LocalTime.of(10, 0), LocalTime.of(22, 0))
        assertFalse(restaurant.isAcceptingQueue(LocalTime.of(9, 59)))
        assertFalse(restaurant.isAcceptingQueue(LocalTime.of(22, 0)))
        assertFalse(restaurant.isAcceptingQueue(LocalTime.of(3, 0)))
    }

    @Test
    fun `the manual switch overrides the schedule`() {
        // Staff closing the queue early must win over the opening hours.
        val restaurant = restaurant(LocalTime.of(10, 0), LocalTime.of(22, 0), isOpen = false)
        assertFalse(restaurant.isAcceptingQueue(LocalTime.of(12, 0)))
    }

    @Test
    fun `hours that run past midnight stay open on both sides of it`() {
        val restaurant = restaurant(LocalTime.of(18, 0), LocalTime.of(2, 0))
        assertTrue(restaurant.isAcceptingQueue(LocalTime.of(23, 30)))
        assertTrue(restaurant.isAcceptingQueue(LocalTime.of(1, 0)))
        assertFalse(restaurant.isAcceptingQueue(LocalTime.of(3, 0)))
        assertFalse(restaurant.isAcceptingQueue(LocalTime.of(17, 0)))
    }

    private fun restaurant(
        opening: LocalTime,
        closing: LocalTime,
        isOpen: Boolean = true
    ) = Restaurant(
        id = "r1",
        name = "Test",
        description = "",
        category = "",
        address = "",
        latitude = 0.0,
        longitude = 0.0,
        imageUrl = null,
        rating = 0.0,
        ratingCount = 0,
        openingTime = opening,
        closingTime = closing,
        isOpen = isOpen,
        averageServiceMinutes = 3,
        queuePrefix = "A",
        queueCapacity = 50,
        checkInRadiusMeters = 150
    )
}
