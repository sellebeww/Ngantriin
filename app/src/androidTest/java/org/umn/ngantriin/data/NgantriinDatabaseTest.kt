package org.umn.ngantriin.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.umn.ngantriin.data.local.NgantriinDatabase
import org.umn.ngantriin.data.local.entity.QueueEntryEntity
import org.umn.ngantriin.data.local.entity.RestaurantEntity
import org.umn.ngantriin.data.local.entity.ReviewEntity
import org.umn.ngantriin.domain.model.QueueStatus
import java.time.Instant
import java.time.LocalTime

/**
 * The DAO queries that the UI depends on but a unit test cannot reach: the
 * active-ticket filter, the history join, and the catalogue replace.
 */
@RunWith(AndroidJUnit4::class)
class NgantriinDatabaseTest {

    private lateinit var database: NgantriinDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            NgantriinDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun activeQueueIgnoresFinishedTickets() = runBlocking {
        database.queueDao().upsert(
            listOf(
                queue("q1", status = QueueStatus.COMPLETED, sequence = 1),
                queue("q2", status = QueueStatus.CANCELLED, sequence = 2),
                queue("q3", status = QueueStatus.WAITING, sequence = 3)
            )
        )

        val active = database.queueDao().observeActive(USER).first()
        assertEquals("q3", active?.id)
    }

    @Test
    fun activeQueueIsNullWhenEverythingHasEnded() = runBlocking {
        database.queueDao().upsert(listOf(queue("q1", QueueStatus.COMPLETED, 1)))
        assertNull(database.queueDao().observeActive(USER).first())
    }

    @Test
    fun historyJoinsTheRestaurantAndFlagsReviewedVisits() = runBlocking {
        database.restaurantDao().upsertRestaurants(listOf(restaurant()))
        database.queueDao().upsert(
            listOf(
                queue("q1", QueueStatus.COMPLETED, 1),
                queue("q2", QueueStatus.CANCELLED, 2)
            )
        )
        database.reviewDao().upsert(
            ReviewEntity(
                id = "rev1",
                restaurantId = RESTAURANT,
                userId = USER,
                queueId = "q1",
                rating = 5,
                comment = "Great",
                createdAt = Instant.now(),
                authorName = null
            )
        )

        val history = database.queueDao().observeHistory(USER).first()

        assertEquals(2, history.size)
        assertTrue(history.all { it.restaurantName == "Taco Libre" })
        assertTrue(history.first { it.queue.id == "q1" }.hasReview)
        assertTrue(!history.first { it.queue.id == "q2" }.hasReview)
    }

    @Test
    fun historySurvivesAMissingRestaurantRow() = runBlocking {
        // The venue was never cached; the ticket must still be listed.
        database.queueDao().upsert(listOf(queue("q1", QueueStatus.COMPLETED, 1)))
        val history = database.queueDao().observeHistory(USER).first()
        assertEquals(1, history.size)
        assertNull(history.first().restaurantName)
    }

    @Test
    fun replacingTheCatalogueDropsVenuesTheBackendNoLongerReturns() = runBlocking {
        val dao = database.restaurantDao()
        dao.upsertRestaurants(listOf(restaurant(), restaurant(id = "gone", name = "Closed Down")))
        assertEquals(2, dao.observeAll().first().size)

        dao.replaceCatalogue(listOf(restaurant()), emptyList())

        val remaining = dao.observeAll().first()
        assertEquals(1, remaining.size)
        assertEquals(RESTAURANT, remaining.single().id)
    }

    @Test
    fun localTimeSurvivesTheRoundTripThroughSqlite() = runBlocking {
        database.restaurantDao().upsertRestaurants(listOf(restaurant()))
        val stored = database.restaurantDao().findById(RESTAURANT)
        assertEquals(LocalTime.of(11, 0), stored?.openingTime)
        assertEquals(LocalTime.of(22, 0), stored?.closingTime)
    }

    private fun queue(
        id: String,
        status: QueueStatus,
        sequence: Int
    ) = QueueEntryEntity(
        id = id,
        restaurantId = RESTAURANT,
        userId = USER,
        queueNumber = "T-%03d".format(sequence),
        ticketSequence = sequence,
        partySize = 2,
        status = status.wireValue,
        note = null,
        joinedAt = Instant.EPOCH.plusSeconds(sequence.toLong()),
        calledAt = null,
        checkedInAt = null,
        completedAt = null,
        cancelledAt = null
    )

    private fun restaurant(
        id: String = RESTAURANT,
        name: String = "Taco Libre"
    ) = RestaurantEntity(
        id = id,
        name = name,
        description = "",
        category = "Mexican",
        address = "",
        latitude = -6.2567,
        longitude = 106.6189,
        imageUrl = null,
        rating = 4.2,
        ratingCount = 41,
        openingTime = LocalTime.of(11, 0),
        closingTime = LocalTime.of(22, 0),
        isOpen = true,
        averageServiceMinutes = 3,
        queuePrefix = "T",
        queueCapacity = 35,
        checkInRadiusMeters = 150,
        cachedAt = Instant.EPOCH
    )

    private companion object {
        const val USER = "user-1"
        const val RESTAURANT = "restaurant-1"
    }
}
