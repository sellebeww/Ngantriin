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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.umn.ngantriin.core.AppError
import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.ui.auth.ResetPasswordViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class ResetPasswordViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `short password is rejected without calling the repository`() = runTest(dispatcher) {
        val auth = FakeAuth()
        val vm = ResetPasswordViewModel(auth)

        vm.onPasswordChange("short")
        vm.onConfirmPasswordChange("short")
        vm.submit()
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.passwordError)
        assertEquals(0, auth.updatePasswordCalls)
    }

    @Test
    fun `mismatched confirmation is rejected without calling the repository`() = runTest(dispatcher) {
        val auth = FakeAuth()
        val vm = ResetPasswordViewModel(auth)

        vm.onPasswordChange("long-enough-1")
        vm.onConfirmPasswordChange("long-enough-2")
        vm.submit()
        advanceUntilIdle()

        assertNull(vm.uiState.value.passwordError)
        assertNotNull(vm.uiState.value.confirmPasswordError)
        assertEquals(0, auth.updatePasswordCalls)
    }

    @Test
    fun `matching passwords are sent to the repository`() = runTest(dispatcher) {
        val auth = FakeAuth()
        val vm = ResetPasswordViewModel(auth)

        vm.onPasswordChange("long-enough-1")
        vm.onConfirmPasswordChange("long-enough-1")
        vm.submit()
        advanceUntilIdle()

        assertEquals(1, auth.updatePasswordCalls)
        assertEquals("long-enough-1", auth.lastNewPassword)
        assertFalse(vm.uiState.value.isSubmitting)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `a failed update surfaces the error and stops submitting`() = runTest(dispatcher) {
        val auth = FakeAuth(updatePasswordResult = Outcome.Failure(AppError.Network))
        val vm = ResetPasswordViewModel(auth)

        vm.onPasswordChange("long-enough-1")
        vm.onConfirmPasswordChange("long-enough-1")
        vm.submit()
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test
    fun `cancel abandons the recovery`() = runTest(dispatcher) {
        val auth = FakeAuth()
        val vm = ResetPasswordViewModel(auth)

        vm.cancel()
        advanceUntilIdle()

        assertEquals(1, auth.abandonCalls)
    }
}
