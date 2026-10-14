package org.umn.ngantriin.domain.repository

import android.content.Intent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
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

    /** Single source of truth for navigation gating. */
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

    /**
     * True between opening a password-reset link and choosing the new
     * password. The session is already signed in by then, so navigation needs
     * this to keep the person on the reset screen instead of the home screen.
     */
    val recoveryPending: StateFlow<Boolean>

    /** Feeds an incoming intent to the reset-link handler; others are ignored. */
    fun handleRecoveryLink(intent: Intent)

    suspend fun updatePassword(newPassword: String): Outcome<Unit>

    /** Walks away from the reset screen: signs out and clears [recoveryPending]. */
    suspend fun abandonRecovery(): Outcome<Unit>

    suspend fun refreshProfile(): Outcome<User>

    suspend fun updateProfile(name: String, phone: String?): Outcome<User>

    /** Stores the device push token against the profile. */
    suspend fun syncPushToken(token: String): Outcome<Unit>

    /** Tells the backend which language to write push notifications in. */
    suspend fun syncLocale(locale: String): Outcome<Unit>
}
