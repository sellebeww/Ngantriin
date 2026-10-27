package org.umn.ngantriin.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.umn.ngantriin.core.AppError
import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.domain.model.User
import org.umn.ngantriin.domain.repository.AuthRepository
import org.umn.ngantriin.domain.repository.SessionState
import org.umn.ngantriin.ui.auth.LoginViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `invalid email is rejected without calling the repository`() = runTest(dispatcher) {
        val auth = FakeAuth()
        val vm = LoginViewModel(auth, isDemoMode = false)

        vm.onEmailChange("not-an-email")
        vm.onPasswordChange("secret")
        vm.signIn()
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.emailError)
        assertFalse(vm.uiState.value.isSubmitting)
        assertEquals(0, auth.signInCalls)
    }

    @Test
    fun `blank password is rejected without calling the repository`() = runTest(dispatcher) {
        val auth = FakeAuth()
        val vm = LoginViewModel(auth, isDemoMode = false)

        vm.onEmailChange("you@example.com")
        vm.signIn()
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.passwordError)
        assertEquals(0, auth.signInCalls)
    }

    @Test
    fun `successful sign in clears the submitting flag and leaves navigation to the session`() =
        runTest(dispatcher) {
            val auth = FakeAuth()
            val vm = LoginViewModel(auth, isDemoMode = false)

            vm.onEmailChange("you@example.com")
            vm.onPasswordChange("secret")
            vm.signIn()
            assertTrue(vm.uiState.value.isSubmitting)
            advanceUntilIdle()

            assertFalse(vm.uiState.value.isSubmitting)
            assertNull(vm.uiState.value.error)
            assertEquals(1, auth.signInCalls)
        }

    @Test
    fun `failed sign in surfaces the typed error and allows a retry`() = runTest(dispatcher) {
        val auth = FakeAuth(result = Outcome.Failure(AppError.InvalidCredentials))
        val vm = LoginViewModel(auth, isDemoMode = false)

        vm.onEmailChange("you@example.com")
        vm.onPasswordChange("wrong")
        vm.signIn()
        advanceUntilIdle()

        assertEquals(AppError.InvalidCredentials, vm.uiState.value.error)
        assertFalse(vm.uiState.value.isSubmitting)
        assertTrue(vm.uiState.value.canSubmit)

        // Mengedit field menghapus error yang basi.
        vm.onPasswordChange("right")
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `password reset needs a valid email and reports which address was used`() =
        runTest(dispatcher) {
            val auth = FakeAuth()
            val vm = LoginViewModel(auth, isDemoMode = false)

            vm.sendPasswordReset()
            advanceUntilIdle()
            assertNotNull(vm.uiState.value.emailError)
            assertEquals(0, auth.resetCalls)

            vm.onEmailChange(" you@example.com ")
            vm.sendPasswordReset()
            advanceUntilIdle()
            assertEquals("you@example.com", vm.uiState.value.resetEmailSent)
            assertEquals(1, auth.resetCalls)
        }
}
