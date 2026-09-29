package org.umn.ngantriin.notification

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.umn.ngantriin.domain.model.ActiveQueue
import org.umn.ngantriin.domain.model.NotificationType
import org.umn.ngantriin.domain.model.QueueEntry
import org.umn.ngantriin.domain.model.QueueStats
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.domain.model.Restaurant
import java.time.Instant
import java.time.LocalTime

/**
 * Section 28.13: a lock screen is a public surface, so no notification may
 * name the restaurant a customer is queueing at. Needs a real Context (for
 * string resources), hence an instrumented test rather than a plain unit test
 * — mirrors the existing Room tests in this module.
 */
@RunWith(AndroidJUnit4::class)
class QueueNotificationCopyTest {

    private val restaurantName = "Secret Sauce Diner"

    private val restaurant = Restaurant(
        id = "r1",
        name = restaurantName,
        description = "",
        category = "Test",
        address = "",
        latitude = 0.0,
        longitude = 0.0,
        imageUrl = null,
        rating = 4.5,
        ratingCount = 1,
        openingTime = LocalTime.of(9, 0),
        closingTime = LocalTime.of(21, 0),
        isOpen = true,
        averageServiceMinutes = 3,
        queuePrefix = "A",
        queueCapacity = 50,
        checkInRadiusMeters = 150
    )

    private fun activeQueue(status: QueueStatus) = ActiveQueue(
        entry = QueueEntry(
            id = "q1",
            restaurantId = restaurant.id,
            userId = "u1",
            queueNumber = "A-005",
            ticketSequence = 5,
            partySize = 2,
            status = status,
            joinedAt = Instant.now()
        ),
        restaurant = restaurant,
        stats = QueueStats.empty(restaurant.id),
        peopleAhead = 1,
        estimatedWaitMinutes = 3
    )

    @Test
    fun noBuiltNotificationNamesTheRestaurant() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        for (type in NotificationType.entries) {
            val notification = QueueNotificationCopy.build(
                context = context,
                userId = "u1",
                queue = activeQueue(QueueStatus.WAITING),
                type = type
            )

            assertFalse(
                "title for $type leaked the restaurant name: ${notification.title}",
                notification.title.contains(restaurantName)
            )
            assertFalse(
                "body for $type leaked the restaurant name: ${notification.body}",
                notification.body.contains(restaurantName)
            )
        }
    }
}
