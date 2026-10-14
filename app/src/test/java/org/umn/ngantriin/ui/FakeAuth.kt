package org.umn.ngantriin.ui

import android.content.Intent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.domain.model.User
import org.umn.ngantriin.domain.repository.AuthRepository
import org.umn.ngantriin.domain.repository.SessionState

/** Records calls and returns canned results; shared by the auth ViewModel tests. */
internal class FakeAuth(
    private val result: Outcome<User> = Outcome.Success(User("u1", "You", "you@example.com")),
    var updatePasswordResult: Outcome<Unit> = Outcome.Success(Unit)
) : AuthRepository {
    var signInCalls = 0
    var resetCalls = 0
    var updatePasswordCalls = 0
    var lastNewPassword: String? = null
    var abandonCalls = 0

    override val sessionState: Flow<SessionState> = MutableStateFlow(SessionState.SignedOut)
    override val recoveryPending: StateFlow<Boolean> = MutableStateFlow(false)
    override fun currentUserOrNull(): User? = null

    override suspend fun signIn(email: String, password: String): Outcome<User> {
        signInCalls++
        return result
    }

    override suspend fun signUp(name: String, email: String, password: String) = result
    override suspend fun signOut(): Outcome<Unit> = Outcome.Success(Unit)
    override suspend fun sendPasswordReset(email: String): Outcome<Unit> {
        resetCalls++
        return Outcome.Success(Unit)
    }

    override fun handleRecoveryLink(intent: Intent) = Unit

    override suspend fun updatePassword(newPassword: String): Outcome<Unit> {
        updatePasswordCalls++
        lastNewPassword = newPassword
        return updatePasswordResult
    }

    override suspend fun abandonRecovery(): Outcome<Unit> {
        abandonCalls++
        return Outcome.Success(Unit)
    }

    override suspend fun refreshProfile() = result
    override suspend fun updateProfile(name: String, phone: String?) = result
    override suspend fun syncPushToken(token: String): Outcome<Unit> = Outcome.Success(Unit)
    override suspend fun syncLocale(locale: String): Outcome<Unit> = Outcome.Success(Unit)
}
