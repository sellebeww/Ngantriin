package org.umn.ngantriin.ui.queue

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.umn.ngantriin.R
import org.umn.ngantriin.domain.model.ActiveQueue
import org.umn.ngantriin.domain.model.QueueEntry
import org.umn.ngantriin.domain.model.QueueStats
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.domain.model.Restaurant
import org.umn.ngantriin.ui.theme.NgantriinTheme
import java.time.Instant
import java.time.LocalTime

/**
 * What a screen reader hears on the ticket. The ticket shows the same facts
 * twice on purpose (the line is a picture of "groups ahead"), so this pins
 * down that each fact is announced exactly once and the picture is silent.
 */
@RunWith(AndroidJUnit4::class)
class QueueTicketSemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    private val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources

    private val restaurant = Restaurant(
        id = "r1",
        name = "Test Diner",
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

    private val queue = ActiveQueue(
        entry = QueueEntry(
            id = "q1",
            restaurantId = restaurant.id,
            userId = "u1",
            queueNumber = "A-027",
            ticketSequence = 27,
            partySize = 2,
            status = QueueStatus.WAITING,
            joinedAt = Instant.now()
        ),
        restaurant = restaurant,
        stats = QueueStats.empty(restaurant.id).copy(currentServingNumber = "A-021"),
        peopleAhead = 5,
        estimatedWaitMinutes = 15
    )

    @Test
    fun eachFigureIsAnnouncedOnce() {
        compose.setContent { NgantriinTheme { QueueTicket(queue) } }

        compose.onAllNodesWithContentDescription(resources.getString(R.string.cd_queue_number, "A-027"))
            .assertCountEquals(1)
        compose.onAllNodesWithContentDescription(resources.getQuantityString(R.plurals.cd_people_ahead, 5, 5))
            .assertCountEquals(1)
        compose.onAllNodesWithContentDescription(resources.getQuantityString(R.plurals.cd_estimated_wait, 15, 15))
            .assertCountEquals(1)
        compose.onAllNodesWithContentDescription("A-021", substring = true).assertCountEquals(1)
        // The printed digits are covered by the descriptions above, not read again.
        compose.onAllNodesWithText("A-027").assertCountEquals(0)
    }
}
