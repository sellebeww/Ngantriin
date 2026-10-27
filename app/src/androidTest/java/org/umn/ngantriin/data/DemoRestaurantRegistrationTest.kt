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
 * Mode demo harus mengikuti aturan yang sama dengan SQL (0016): mendaftar
 * memunculkan restoran di daftar dan menjadikan pendaftar stafnya, staf hanya
 * bisa mengakses restorannya sendiri, dan restoran yang dijeda hilang dari
 * katalog serta tidak menerima tiket.
 *
 * Pakai context milik APK tes supaya file DataStore-nya terpisah dari
 * preferensi asli aplikasi.
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
        // Context APK tes tidak punya application context, jadi dibungkus agar DataStore bisa menemukan file.
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

    /** Aksi reviewer di aplikasi, lalu kembali ke sesi pendaftar. */
    private suspend fun approveAsAdmin(venueId: String, thenSignInAs: String) {
        auth.signIn("admin@ngantriin.id", "")
        admin.review(venueId, approve = true, note = null)
        auth.signIn(thenSignInAs, "")
    }

    @After
    fun tearDown() = runBlocking {
        // setUp bisa saja gagal sebelum ini ada, jangan sampai errornya tertutup.
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
        // Tengah malam ke tengah malam dianggap "24 jam", jadi bisa antre kapan pun tes dijalankan.
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
            listOf(DemoSeed.FIRST_ID),
            restaurants.fetchManagedRestaurants(waroeng.id).map { it.id }
        )

        val kopiId = "00000002-0000-4000-8000-000000000000"
        expectFailure("NOT_RESTAURANT_STAFF") { queues.callNext(kopiId) }

        // Login "staff…" yang belum terdaftar tidak mengelola apa-apa sampai mendaftarkan restoran.
        val stranger = auth.signIn("staff.new@example.com", "")
        assertTrue(restaurants.fetchManagedRestaurants(stranger.id).isEmpty())
    }

    @Test
    fun registeringIsAPendingApplicationThatListsOnlyAfterApproval() = runBlocking {
        val owner = auth.signIn("owner@example.com", "")
        assertEquals(UserRole.CUSTOMER, owner.role)

        val venue = restaurants.registerRestaurant(registration("Warung Uji"))

        // Pending: belum tampil di daftar, pendaftar bisa melihatnya tapi belum bisa mengelola.
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
        // Akun naik jadi staf dan tetap staf setelah keluar lalu masuk lagi.
        assertEquals(UserRole.STAFF, auth.fetchProfile(owner.id).role)
        auth.signOut()
        assertEquals(UserRole.STAFF, auth.signIn("owner@example.com", "").role)

        // Akun lain bisa melihat restoran tapi tidak bisa mengelolanya.
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
        // Staf tetap bisa melihatnya, dan pencarian langsung (riwayat tiket) tetap jalan.
        assertEquals(listOf(venue.id), restaurants.fetchManagedRestaurants(owner.id).map { it.id })
        assertEquals(venue.id, restaurants.fetchRestaurant(venue.id).id)

        auth.signIn("diner@example.com", "")
        expectFailure("RESTAURANT_CLOSED") { queues.joinQueue(venue.id, 2, null) }

        // Setelah dilanjutkan, alurnya jalan penuh: tampil, antre, dipanggil pemilik, isolasi tetap terjaga.
        auth.signIn("owner3@example.com", "")
        restaurants.setRestaurantActive(venue.id, true)
        assertTrue(restaurants.fetchRestaurants().any { it.id == venue.id })

        val diner = auth.signIn("diner@example.com", "")
        val ticket = queues.joinQueue(venue.id, 2, null)
        assertEquals("K-001", ticket.queueNumber)

        // Bukan staf restoran ini: tidak bisa melihat antrean, hanya tiketnya sendiri.
        assertEquals(listOf(ticket.id), queues.fetchRestaurantQueue(venue.id).map { it.id })
        expectFailure("NOT_RESTAURANT_STAFF") { queues.callNext(venue.id) }
        expectFailure("NOT_RESTAURANT_STAFF") { queues.updateStatus(ticket.id, QueueStatus.CALLED) }

        auth.signIn("owner3@example.com", "")
        val called = queues.callNext(venue.id)
        assertEquals(QueueStatus.CALLED, called.status)
        assertEquals(diner.id, called.userId)

        // Staf restoran lain tidak boleh menyentuhnya.
        auth.signIn("staff@ngantriin.id", "")
        expectFailure("NOT_RESTAURANT_STAFF") { queues.callNext(venue.id) }
    }

    private companion object {
        /** "%PDF-1": header sekadar cukup supaya terbaca sebagai berkas bukti. */
        val PDF_BYTES = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D, 0x31)
    }
}
