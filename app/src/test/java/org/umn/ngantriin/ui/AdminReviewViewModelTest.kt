package org.umn.ngantriin.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.umn.ngantriin.core.AppError
import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.domain.model.ApplicantRole
import org.umn.ngantriin.domain.model.ApplicationDocument
import org.umn.ngantriin.domain.model.ProofType
import org.umn.ngantriin.domain.model.RestaurantApplication
import org.umn.ngantriin.domain.model.VerificationStatus
import org.umn.ngantriin.domain.repository.AdminRepository
import org.umn.ngantriin.ui.admin.AdminApplicationViewModel
import org.umn.ngantriin.ui.admin.AdminReviewViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class AdminReviewViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun application(id: String, status: VerificationStatus = VerificationStatus.PENDING_VERIFICATION) =
        RestaurantApplication(
            restaurantId = id, name = "Resto $id", address = "Jl. Uji", category = "Cafe", description = "",
            imageUrl = null, status = status, note = null, applicantName = "Owner",
            applicantEmail = "owner@example.com", contactPhone = "081234567890",
            applicantRole = ApplicantRole.OWNER, documentCount = 1, submittedAt = null
        )

    private fun document(id: String, path: String) =
        ApplicationDocument(id, ProofType.BUSINESS_LICENSE, path, round = 1, uploadedAt = null)

    private class FakeAdmin(
        var applications: Map<VerificationStatus, List<RestaurantApplication>> = emptyMap(),
        var documents: List<ApplicationDocument> = emptyList(),
        var failWith: AppError? = null
    ) : AdminRepository {
        val reviews = mutableListOf<Triple<String, Boolean, String?>>()
        val listed = mutableListOf<VerificationStatus>()

        override suspend fun applications(status: VerificationStatus): Outcome<List<RestaurantApplication>> {
            listed += status
            return failWith?.let { Outcome.Failure(it) } ?: Outcome.Success(applications[status].orEmpty())
        }

        override suspend fun documents(restaurantId: String): Outcome<List<ApplicationDocument>> =
            failWith?.let { Outcome.Failure(it) } ?: Outcome.Success(documents)

        override suspend fun documentUrl(document: ApplicationDocument): Outcome<String> =
            Outcome.Success("https://signed.example/${document.path}")

        override suspend fun review(restaurantId: String, approve: Boolean, note: String?): Outcome<Unit> {
            reviews += Triple(restaurantId, approve, note)
            return failWith?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)
        }
    }

    @Test
    fun `the list opens on pending applications`() = runTest(dispatcher) {
        val admin = FakeAdmin(mapOf(VerificationStatus.PENDING_VERIFICATION to listOf(application("a"), application("b"))))
        val vm = AdminReviewViewModel(admin, FakeAuth())
        advanceUntilIdle()

        assertEquals(VerificationStatus.PENDING_VERIFICATION, vm.state.value.filter)
        assertEquals(listOf("a", "b"), vm.state.value.applications.map { it.restaurantId })
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `switching the tab asks for that state and drops the old list`() = runTest(dispatcher) {
        val admin = FakeAdmin(
            mapOf(
                VerificationStatus.PENDING_VERIFICATION to listOf(application("a")),
                VerificationStatus.REJECTED to listOf(application("r", VerificationStatus.REJECTED))
            )
        )
        val vm = AdminReviewViewModel(admin, FakeAuth())
        advanceUntilIdle()

        vm.setFilter(VerificationStatus.REJECTED)
        assertTrue(vm.state.value.applications.isEmpty())
        advanceUntilIdle()

        assertEquals(listOf("r"), vm.state.value.applications.map { it.restaurantId })
        assertEquals(listOf(VerificationStatus.PENDING_VERIFICATION, VerificationStatus.REJECTED), admin.listed)
    }

    @Test
    fun `a non-admin sees the server's refusal and no data`() = runTest(dispatcher) {
        val admin = FakeAdmin(
            mapOf(VerificationStatus.PENDING_VERIFICATION to listOf(application("a"))),
            failWith = AppError.NotAdmin
        )
        val vm = AdminReviewViewModel(admin, FakeAuth())
        advanceUntilIdle()

        assertEquals(AppError.NotAdmin, vm.state.value.error)
        assertTrue(vm.state.value.applications.isEmpty())
    }

    @Test
    fun `approving sends the decision and closes the screen`() = runTest(dispatcher) {
        val admin = FakeAdmin(
            mapOf(VerificationStatus.PENDING_VERIFICATION to listOf(application("a"))),
            documents = listOf(document("d1", "u/lic.pdf"))
        )
        val vm = AdminApplicationViewModel(admin, "a", VerificationStatus.PENDING_VERIFICATION)
        advanceUntilIdle()
        assertEquals("a", vm.state.value.application?.restaurantId)

        vm.approve()
        advanceUntilIdle()

        assertEquals(listOf(Triple("a", true, null)), admin.reviews)
        assertTrue(vm.state.value.decided)
    }

    @Test
    fun `rejecting carries the reason`() = runTest(dispatcher) {
        val admin = FakeAdmin(mapOf(VerificationStatus.PENDING_VERIFICATION to listOf(application("a"))))
        val vm = AdminApplicationViewModel(admin, "a", VerificationStatus.PENDING_VERIFICATION)
        advanceUntilIdle()

        vm.reject("Dokumen tidak terbaca")
        advanceUntilIdle()

        assertEquals(listOf(Triple("a", false, "Dokumen tidak terbaca")), admin.reviews)
        assertTrue(vm.state.value.decided)
    }

    @Test
    fun `a refused decision keeps the screen open and shows why`() = runTest(dispatcher) {
        val admin = FakeAdmin(mapOf(VerificationStatus.PENDING_VERIFICATION to listOf(application("a"))))
        val vm = AdminApplicationViewModel(admin, "a", VerificationStatus.PENDING_VERIFICATION)
        advanceUntilIdle()

        admin.failWith = AppError.NotPending
        vm.approve()
        advanceUntilIdle()

        assertFalse(vm.state.value.decided)
        assertEquals(AppError.NotPending, vm.state.value.error)
        assertFalse(vm.state.value.isWorking)
    }

    @Test
    fun `an application that is no longer in the list says so instead of showing a blank card`() = runTest(dispatcher) {
        val admin = FakeAdmin(mapOf(VerificationStatus.PENDING_VERIFICATION to emptyList()))
        val vm = AdminApplicationViewModel(admin, "gone", VerificationStatus.PENDING_VERIFICATION)
        advanceUntilIdle()

        assertNull(vm.state.value.application)
        assertEquals(AppError.NotPending, vm.state.value.error)
    }

    @Test
    fun `only pictures get a preview link up front, pdfs are signed when opened`() = runTest(dispatcher) {
        val admin = FakeAdmin(
            mapOf(VerificationStatus.PENDING_VERIFICATION to listOf(application("a"))),
            documents = listOf(document("pdf", "u/lic.pdf"), document("img", "u/id.jpg"))
        )
        val vm = AdminApplicationViewModel(admin, "a", VerificationStatus.PENDING_VERIFICATION)
        advanceUntilIdle()

        assertEquals(setOf("img"), vm.state.value.imageUrls.keys)

        var opened: String? = null
        vm.openDocument(admin.documents.first { it.id == "pdf" }) { opened = it }
        advanceUntilIdle()
        assertEquals("https://signed.example/u/lic.pdf", opened)
    }
}
