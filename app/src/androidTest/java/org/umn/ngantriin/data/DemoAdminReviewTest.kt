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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.umn.ngantriin.data.local.NgantriinDatabase
import org.umn.ngantriin.data.local.prefs.UserPreferencesDataStore
import org.umn.ngantriin.data.remote.demo.DemoAdminRemoteDataSource
import org.umn.ngantriin.data.remote.demo.DemoAuthRemoteDataSource
import org.umn.ngantriin.data.remote.demo.DemoBackend
import org.umn.ngantriin.data.remote.demo.DemoQueueRemoteDataSource
import org.umn.ngantriin.data.remote.demo.DemoRestaurantRemoteDataSource
import org.umn.ngantriin.domain.model.ApplicantRole
import org.umn.ngantriin.domain.model.ProofDocument
import org.umn.ngantriin.domain.model.ProofType
import org.umn.ngantriin.domain.model.RestaurantRegistration
import org.umn.ngantriin.domain.model.UserRole
import org.umn.ngantriin.domain.model.VerificationStatus
import java.io.File
import java.time.LocalTime

/**
 * Mode demo harus mengikuti aturan review yang sama dengan SQL (0019, 0021):
 * pengajuan berstatus pending dan belum tampil, hanya admin yang memutuskan,
 * penolakan wajib punya alasan yang bisa dibaca pemilik, pengajuan yang
 * ditolak bisa diperbaiki dan dikirim ulang, dan orang lain tidak boleh
 * memanggil fungsi milik reviewer.
 */
@RunWith(AndroidJUnit4::class)
class DemoAdminReviewTest {

    private lateinit var database: NgantriinDatabase
    private lateinit var auth: DemoAuthRemoteDataSource
    private lateinit var restaurants: DemoRestaurantRemoteDataSource
    private lateinit var queues: DemoQueueRemoteDataSource
    private lateinit var admin: DemoAdminRemoteDataSource
    private val run = System.nanoTime()

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        database = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, NgantriinDatabase::class.java).build()
        val testContext = object : ContextWrapper(instrumentation.context) {
            override fun getApplicationContext(): Context = this
        }
        val preferences = UserPreferencesDataStore(testContext)
        val backend = DemoBackend(database, preferences)
        auth = DemoAuthRemoteDataSource(preferences)
        val files = instrumentation.targetContext.cacheDir
        restaurants = DemoRestaurantRemoteDataSource(
            backend, auth, preferences, File(files, "test-photos"), File(files, "test-proofs")
        )
        queues = DemoQueueRemoteDataSource(backend, auth)
        admin = DemoAdminRemoteDataSource(backend, auth)
    }

    @After
    fun tearDown() = runBlocking {
        if (::auth.isInitialized) auth.signOut()
        if (::database.isInitialized) database.close()
    }

    private fun registration(name: String, documents: List<ProofDocument> = listOf(license())) =
        RestaurantRegistration(
            name = name,
            address = "Jl. Pengujian No. 1, Tangerang",
            category = "Coffee",
            description = "",
            photo = null,
            latitude = -6.2,
            longitude = 106.6,
            openingTime = LocalTime.MIDNIGHT,
            closingTime = LocalTime.MIDNIGHT,
            averageServiceMinutes = 3,
            queueCapacity = 20,
            contactPhone = "081234567890",
            applicantRole = ApplicantRole.OWNER,
            documents = documents
        )

    private fun license() = ProofDocument(ProofType.BUSINESS_LICENSE, PDF_BYTES, isPdf = true)

    private suspend fun expectFailure(token: String, block: suspend () -> Unit) {
        try {
            block()
            fail("expected $token")
        } catch (e: IllegalStateException) {
            assertTrue("got ${e.message}", e.message.orEmpty().contains(token))
        }
    }

    private suspend fun asAdmin() = auth.signIn("admin@ngantriin.id", "")

    /**
     * State demo disimpan di file DataStore yang masih ada setelah tes selesai, dan
     * satu pengajuan pending per akun memang aturan aslinya. Alamat baru tiap run
     * mencegah tes yang diulang (atau yang gagal di tengah) tersandung sisa data lama.
     */
    private fun mail(tag: String) = "rev-$tag-$run@example.com"

    @Test
    fun anApplicationIsPendingAndTheAdminSeesItWithItsDocuments() = runBlocking {
        auth.signIn(mail("owner"), "")
        val venue = restaurants.registerRestaurant(registration("Warung Tinjau"))
        assertEquals(VerificationStatus.PENDING_VERIFICATION, venue.verificationStatus)
        assertFalse(venue.isActive)

        asAdmin()
        val pending = admin.fetchApplications(VerificationStatus.PENDING_VERIFICATION)
        val application = pending.single { it.restaurantId == venue.id }
        assertEquals(mail("owner"), application.applicantEmail)
        assertEquals("081234567890", application.contactPhone)
        assertEquals(ApplicantRole.OWNER, application.applicantRole)
        assertEquals(1, application.documentCount)

        val documents = admin.fetchDocuments(venue.id)
        assertEquals(listOf(ProofType.BUSINESS_LICENSE), documents.map { it.type })
        assertTrue(documents.single().isPdf)
        assertTrue(admin.documentUrl(documents.single()).isNotBlank())
    }

    @Test
    fun approvalListsTheVenueAndStartsStaffAccess() = runBlocking {
        val owner = auth.signIn(mail("owner2"), "")
        val venue = restaurants.registerRestaurant(registration("Kedai Setuju"))
        assertFalse(restaurants.fetchRestaurants().any { it.id == venue.id })

        asAdmin()
        admin.review(venue.id, approve = true, note = null)

        auth.signIn(mail("owner2"), "")
        assertTrue(restaurants.fetchRestaurants().any { it.id == venue.id })
        assertEquals(VerificationStatus.APPROVED, restaurants.fetchManagedRestaurants(owner.id).single().verificationStatus)
        restaurants.setRestaurantActive(venue.id, false) // hak staf sudah aktif

        // Sudah diputuskan ya sudah.
        asAdmin()
        expectFailure("NOT_PENDING") { admin.review(venue.id, approve = false, note = "terlambat") }
        assertTrue(admin.fetchApplications(VerificationStatus.PENDING_VERIFICATION).none { it.restaurantId == venue.id })
    }

    @Test
    fun rejectionNeedsAReasonTheOwnerReadsAndCanBeFixedAndResubmitted() = runBlocking {
        val owner = auth.signIn(mail("owner3"), "")
        val venue = restaurants.registerRestaurant(registration("Warung Tolak"))

        asAdmin()
        expectFailure("REJECTION_NOTE_REQUIRED") { admin.review(venue.id, approve = false, note = "  ") }
        admin.review(venue.id, approve = false, note = "Dokumen tidak terbaca")

        auth.signIn(mail("owner3"), "")
        val rejected = restaurants.fetchManagedRestaurants(owner.id).single()
        assertEquals(VerificationStatus.REJECTED, rejected.verificationStatus)
        assertEquals("Dokumen tidak terbaca", rejected.verificationNote)
        assertFalse(restaurants.fetchRestaurants().any { it.id == venue.id })

        // File yang salah tidak membuka lagi pengajuannya, yang benar membuka.
        expectFailure("PROOF_REQUIRED") {
            restaurants.resubmitRestaurant(
                venue.id, "081234567890", ApplicantRole.MANAGER,
                listOf(ProofDocument(ProofType.BUSINESS_LICENSE, byteArrayOf(0x4D, 0x5A), isPdf = true))
            )
        }
        val reopened = restaurants.resubmitRestaurant(venue.id, "081234567890", ApplicantRole.MANAGER, listOf(license()))
        assertEquals(VerificationStatus.PENDING_VERIFICATION, reopened.verificationStatus)
        assertNull(reopened.verificationNote)

        asAdmin()
        val documents = admin.fetchDocuments(venue.id)
        assertEquals(listOf(2, 1), documents.map { it.round })
        admin.review(venue.id, approve = true, note = null)

        auth.signIn(mail("owner3"), "")
        assertTrue(restaurants.fetchRestaurants().any { it.id == venue.id })
    }

    @Test
    fun onlyTheOwnerOfARejectedApplicationCanResubmitIt() = runBlocking {
        auth.signIn(mail("owner4"), "")
        val venue = restaurants.registerRestaurant(registration("Warung Orang Lain"))
        // Masih pending, jadi belum ada yang bisa diajukan ulang.
        expectFailure("NOT_RESUBMITTABLE") {
            restaurants.resubmitRestaurant(venue.id, "081234567890", ApplicantRole.OWNER, listOf(license()))
        }

        asAdmin()
        admin.review(venue.id, approve = false, note = "Alamat tidak cocok")

        auth.signIn(mail("intruder"), "")
        expectFailure("NOT_RESUBMITTABLE") {
            restaurants.resubmitRestaurant(venue.id, "081234567890", ApplicantRole.OWNER, listOf(license()))
        }
    }

    @Test
    fun nobodyButAnAdminReachesTheReviewerFunctions() = runBlocking {
        auth.signIn(mail("owner5"), "")
        val venue = restaurants.registerRestaurant(registration("Warung Rahasia"))

        val outsiders = listOf(mail("owner5"), "staff@ngantriin.id", "someone@example.com")
        for (email in outsiders) {
            val user = auth.signIn(email, "")
            assertNotEquals(UserRole.ADMIN, user.role)
            expectFailure("NOT_ADMIN") { admin.fetchApplications(VerificationStatus.PENDING_VERIFICATION) }
            expectFailure("NOT_ADMIN") { admin.fetchDocuments(venue.id) }
            expectFailure("NOT_ADMIN") { admin.review(venue.id, approve = true, note = null) }
        }
        // Percobaan orang luar tidak mengubah pengajuan sama sekali.
        assertEquals(
            VerificationStatus.PENDING_VERIFICATION,
            restaurants.fetchManagedRestaurants(auth.signIn(mail("owner5"), "").id)
                .single().verificationStatus
        )
    }

    @Test
    fun anAdminCannotRegisterARestaurant() = runBlocking {
        val adminUser = asAdmin()
        assertEquals(UserRole.ADMIN, adminUser.role)
        expectFailure("ADMIN_CANNOT_REGISTER") { restaurants.registerRestaurant(registration("Resto Admin")) }
        assertEquals(UserRole.ADMIN, auth.fetchProfile(adminUser.id).role)
    }

    @Test
    fun aRenamedFileIsNotAcceptedAsProof() = runBlocking {
        auth.signIn(mail("owner6"), "")
        val fake = ProofDocument(ProofType.BUSINESS_LICENSE, byteArrayOf(0x4D, 0x5A, 0x90.toByte()), isPdf = true)
        expectFailure("PROOF_REQUIRED") { restaurants.registerRestaurant(registration("Warung Palsu", listOf(fake))) }
    }

    @Test
    fun anUnreviewedVenueIsInvisibleToEveryoneButItsApplicant() = runBlocking {
        auth.signIn(mail("owner7"), "")
        val venue = restaurants.registerRestaurant(registration("Warung Tersembunyi"))
        assertEquals(venue.id, restaurants.fetchRestaurant(venue.id).id)

        auth.signIn("diner@example.com", "")
        expectFailure("RESTAURANT_NOT_FOUND") { restaurants.fetchRestaurant(venue.id) }
        expectFailure("RESTAURANT_CLOSED") { queues.joinQueue(venue.id, 2, null) }
    }

    @Test
    fun sampleModeKeepsItsOwnDatabaseFile() {
        assertNotEquals(NgantriinDatabase.fileName(demo = true), NgantriinDatabase.fileName(demo = false))
    }

    private companion object {
        val PDF_BYTES = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D, 0x31)
    }
}
