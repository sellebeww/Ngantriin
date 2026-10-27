package org.umn.ngantriin.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.umn.ngantriin.data.local.NgantriinDatabase
import org.umn.ngantriin.data.local.prefs.UserPreferencesDataStore
import org.umn.ngantriin.data.remote.demo.DemoAdminRemoteDataSource
import org.umn.ngantriin.domain.model.VerificationStatus
import org.umn.ngantriin.data.remote.demo.DemoAuthRemoteDataSource
import org.umn.ngantriin.data.remote.demo.DemoBackend
import org.umn.ngantriin.data.remote.demo.DemoQueueRemoteDataSource
import org.umn.ngantriin.data.remote.demo.DemoRestaurantRemoteDataSource
import org.umn.ngantriin.data.remote.demo.DemoSeed
import org.umn.ngantriin.domain.model.ApplicantRole
import org.umn.ngantriin.domain.model.ProofDocument
import org.umn.ngantriin.domain.model.ProofType
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.domain.model.RestaurantRegistration
import org.umn.ngantriin.domain.model.UserRole
import java.io.File
import java.time.LocalTime

/**
 * Sample mode has to follow the same rules the SQL does (0016): registering
 * lists a venue and makes the caller its staff, staff reach only their own
 * venues, and a paused venue disappears from the catalogue and takes no
 * tickets.
 *
 * Uses the test APK's own context so its DataStore file is separate from the
 * app's real preferences.
 */
@RunWith(AndroidJUnit4::class)
class DemoRestaurantRegistrationTest {

    private lateinit var database: NgantriinDatabase
    private lateinit var auth: DemoAuthRemoteDataSource
    private lateinit var restaurants: DemoRestaurantRemoteDataSource
    private lateinit var queues: DemoQueueRemoteDataSource
    private lateinit var admin: DemoAdminRemoteDataSource

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        database = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, NgantriinDatabase::class.java).build()
        // The test APK's context has no application context; wrap it so DataStore can find files.
        val testContext = object : ContextWrapper(instrumentation.context) {
            override fun getApplicationContext(): Context = this
        }
        val preferences = UserPreferencesDataStore(testContext)
        val backend = DemoBackend(database, preferences)
        auth = DemoAuthRemoteDataSource(preferences)
        restaurants = DemoRestaurantRemoteDataSource(
            backend, auth, preferences, File(instrumentation.targetContext.cacheDir, "test-photos")
        )
        queues = DemoQueueRemoteDataSource(backend, auth)
        admin = DemoAdminRemoteDataSource(backend, auth)
    }

    /** What the reviewer does in the app, then back to the applicant's own session. */
    private suspend fun approveAsAdmin(venueId: String, thenSignInAs: String) {
        auth.signIn("admin@ngantriin.id", "")
        admin.review(venueId, approve = true, note = null)
        auth.signIn(thenSignInAs, "")
    }

    @After
    fun tearDown() = runBlocking {
        // setUp may have failed before these exist; do not mask its error.
        if (::auth.isInitialized) auth.signOut()
        if (::database.isInitialized) database.close()
    }

    private fun registration(
        name: String,
        documents: List<ProofDocument> = listOf(ProofDocument(ProofType.BUSINESS_LICENSE, PDF_BYTES, isPdf = true))
    ) = RestaurantRegistration(
        name = name,
        address = "Jl. Pengujian No. 1, Tangerang",
        category = "Coffee",
        description = "",
        photo = byteArrayOf(1, 2, 3),
        latitude = -6.2,
        longitude = 106.6,
        // Midnight to midnight is "24 hours", so joining works whatever time the test runs.
        openingTime = LocalTime.MIDNIGHT,
        closingTime = LocalTime.MIDNIGHT,
        averageServiceMinutes = 3,
        queueCapacity = 20,
        contactPhone = "+62 812-3456-7890",
        applicantRole = ApplicantRole.OWNER,
        documents = documents
    )

    private suspend fun expectFailure(token: String, block: suspend () -> Unit) {
        try {
            block()
            fail("expected $token")
        } catch (e: IllegalStateException) {
            assertTrue("got ${e.message}", e.message.orEmpty().contains(token))
        }
    }

    @Test
    fun sampleStaffLoginsManageOnlyTheirOwnVenue() = runBlocking {
        val waroeng = auth.signIn("staff@ngantriin.id", "")
        assertEquals(UserRole.STAFF, waroeng.role)
        assertEquals(
            listOf(DemoSeed.WAROENG_ID),
            restaurants.fetchManagedRestaurants(waroeng.id).map { it.id }
        )

        val kopiId = "22222222-2222-4222-8222-222222222222"
        expectFailure("NOT_RESTAURANT_STAFF") { queues.callNext(kopiId) }

        // An unlisted "staff…" login runs nothing until it registers a venue.
        val stranger = auth.signIn("staff.new@example.com", "")
        assertTrue(restaurants.fetchManagedRestaurants(stranger.id).isEmpty())
    }

    @Test
    fun registeringIsAPendingApplicationThatListsOnlyAfterApproval() = runBlocking {
        val owner = auth.signIn("owner@example.com", "")
        assertEquals(UserRole.CUSTOMER, owner.role)

        val venue = restaurants.registerRestaurant(registration("Warung Uji"))

        // Pending: unlisted, and the applicant sees it but cannot run it yet.
        assertEquals(VerificationStatus.PENDING_VERIFICATION, venue.verificationStatus)
        assertFalse(venue.isActive)
        assertFalse(restaurants.fetchRestaurants().any { it.id == venue.id })
        assertFalse(restaurants.fetchAllQueueStats().any { it.restaurantId == venue.id })
        assertEquals(listOf(venue.id), restaurants.fetchManagedRestaurants(owner.id).map { it.id })
        expectFailure("NOT_RESTAURANT_STAFF") { queues.callNext(venue.id) }
        expectFailure("NOT_RESTAURANT_STAFF") { restaurants.setRestaurantActive(venue.id, true) }

        approveAsAdmin(venue.id, thenSignInAs = "owner@example.com")

        assertTrue(restaurants.fetchRestaurant(venue.id).isActive)
        assertEquals("W", venue.queuePrefix)
        assertNotNull(venue.imageUrl)
        assertTrue(restaurants.fetchRestaurants().any { it.id == venue.id })
        assertEquals(listOf(venue.id), restaurants.fetchManagedRestaurants(owner.id).map { it.id })
        // The account is promoted, and stays promoted across a sign-out and back in.
        assertEquals(UserRole.STAFF, auth.fetchProfile(owner.id).role)
        auth.signOut()
        assertEquals(UserRole.STAFF, auth.signIn("owner@example.com", "").role)

        // Another account sees the venue but cannot run it.
        val customer = auth.signIn("someone@example.com", "")
        assertTrue(restaurants.fetchManagedRestaurants(customer.id).isEmpty())
        expectFailure("NOT_RESTAURANT_STAFF") { queues.callNext(venue.id) }
        expectFailure("NOT_RESTAURANT_STAFF") { restaurants.setRestaurantActive(venue.id, false) }
    }

    @Test
    fun invalidRegistrationIsRejected() = runBlocking {
        auth.signIn("owner2@example.com", "")
        expectFailure("INVALID_RESTAURANT") {
            restaurants.registerRestaurant(registration("X"))
        }
    }

    @Test
    fun registrationWithoutABusinessDocumentIsRejected() = runBlocking {
        auth.signIn("owner4@example.com", "")
        expectFailure("PROOF_REQUIRED") {
            restaurants.registerRestaurant(registration("Warung Tanpa Bukti", documents = emptyList()))
        }
        expectFailure("PROOF_REQUIRED") {
            restaurants.registerRestaurant(
                registration(
                    "Warung Salah Dokumen",
                    documents = listOf(ProofDocument(ProofType.OWNERSHIP_PROOF, byteArrayOf(1), isPdf = false))
                )
            )
        }
    }

    @Test
    fun pausedVenueLeavesTheCatalogueAndTakesNoTickets() = runBlocking {
        val owner = auth.signIn("owner3@example.com", "")
        val venue = restaurants.registerRestaurant(registration("Kedai Jeda"))
        approveAsAdmin(venue.id, thenSignInAs = "owner3@example.com")

        val paused = restaurants.setRestaurantActive(venue.id, false)
        assertFalse(paused.isActive)
        assertFalse(restaurants.fetchRestaurants().any { it.id == venue.id })
        assertFalse(restaurants.fetchAllQueueStats().any { it.restaurantId == venue.id })
        // Staff still see it, and a direct lookup (ticket history) still works.
        assertEquals(listOf(venue.id), restaurants.fetchManagedRestaurants(owner.id).map { it.id })
        assertEquals(venue.id, restaurants.fetchRestaurant(venue.id).id)

        auth.signIn("diner@example.com", "")
        expectFailure("RESTAURANT_CLOSED") { queues.joinQueue(venue.id, 2, null) }

        // Resume it and the full path works: list, join, owner calls, isolation holds.
        auth.signIn("owner3@example.com", "")
        restaurants.setRestaurantActive(venue.id, true)
        assertTrue(restaurants.fetchRestaurants().any { it.id == venue.id })

        val diner = auth.signIn("diner@example.com", "")
        val ticket = queues.joinQueue(venue.id, 2, null)
        assertEquals("K-001", ticket.queueNumber)

        // Not staff of the venue: cannot read the line, only their own ticket.
        assertEquals(listOf(ticket.id), queues.fetchRestaurantQueue(venue.id).map { it.id })
        expectFailure("NOT_RESTAURANT_STAFF") { queues.callNext(venue.id) }
        expectFailure("NOT_RESTAURANT_STAFF") { queues.updateStatus(ticket.id, QueueStatus.CALLED) }

        auth.signIn("owner3@example.com", "")
        val called = queues.callNext(venue.id)
        assertEquals(QueueStatus.CALLED, called.status)
        assertEquals(diner.id, called.userId)

        // Another venue's staff cannot touch it.
        auth.signIn("staff@ngantriin.id", "")
        expectFailure("NOT_RESTAURANT_STAFF") { queues.callNext(venue.id) }
    }

    private companion object {
        /** "%PDF-1": enough of a header to be a readable proof file. */
        val PDF_BYTES = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D, 0x31)
    }
}
