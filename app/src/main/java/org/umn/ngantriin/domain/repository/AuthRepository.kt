package org.umn.ngantriin.domain.repository

import kotlinx.coroutines.flow.Flow
import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.domain.model.User

/** What the app knows about the signed-in person right now. */
sealed interface SessionState {
    /** Session is being restored from disk — show the splash, not the login. */
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val user: User) : SessionState
}

interface AuthRepository {

    /** Single source of truth for navigation gating (section 35). */
    val sessionState: Flow<SessionState>

    /** Snapshot accessor for callers that cannot suspend, e.g. WorkManager. */
    fun currentUserOrNull(): User?

    suspend fun signIn(email: String, password: String): Outcome<User>

    suspend fun signUp(
        name: String,
        email: String,
        password: String
    ): Outcome<User>

    suspend fun signOut(): Outcome<Unit>

    suspend fun sendPasswordReset(email: String): Outcome<Unit>

    suspend fun refreshProfile(): Outcome<User>

    suspend fun updateProfile(name: String, phone: String?): Outcome<User>

    /** Section 15: stores the device push token against the profile. */
    suspend fun syncPushToken(token: String): Outcome<Unit>
}
