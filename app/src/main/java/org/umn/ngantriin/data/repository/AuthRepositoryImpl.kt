package org.umn.ngantriin.data.repository

import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.core.runCatchingOutcome
import org.umn.ngantriin.data.local.NgantriinDatabase
import org.umn.ngantriin.data.remote.datasource.AuthRemoteDataSource
import org.umn.ngantriin.data.remote.datasource.RemoteSession
import org.umn.ngantriin.domain.model.User
import org.umn.ngantriin.domain.repository.AuthRepository
import org.umn.ngantriin.domain.repository.SessionState

class AuthRepositoryImpl(
    private val remote: AuthRemoteDataSource,
    private val database: NgantriinDatabase,
    scope: CoroutineScope
) : AuthRepository {

    private val cachedUser = MutableStateFlow<User?>(null)
    private val _recoveryPending = MutableStateFlow(false)
    override val recoveryPending: StateFlow<Boolean> = _recoveryPending.asStateFlow()

    private val restoredSession: Flow<SessionState> = remote.session
        .map { session ->
            when (session) {
                RemoteSession.Initializing -> SessionState.Loading

                RemoteSession.SignedOut -> {
                    cachedUser.value = null
                    SessionState.SignedOut
                }

                is RemoteSession.SignedIn -> {
                    val known = cachedUser.value?.takeIf { it.id == session.userId }
                    val user = known
                        ?: runCatching { remote.fetchProfile(session.userId) }.getOrNull()
                        // Offline with a valid session: route into the app on a
                        // placeholder rather than stranding the user on the
                        // splash screen waiting for a profile fetch.
                        ?: User(id = session.userId, name = "", email = "")
                    cachedUser.value = user
                    SessionState.SignedIn(user)
                }
            }
        }

    // A refreshed profile has to reach navigation too: registering a restaurant
    // changes the role without touching the auth session, and the role decides
    // which half of the app you are in.
    override val sessionState: Flow<SessionState> = combine(restoredSession, cachedUser) { state, user ->
        if (state is SessionState.SignedIn && user != null && user.id == state.user.id) {
            SessionState.SignedIn(user)
        } else {
            state
        }
    }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, SessionState.Loading)

    override fun currentUserOrNull(): User? = cachedUser.value

    override suspend fun signIn(email: String, password: String): Outcome<User> =
        runCatchingOutcome { remote.signIn(email, password).also { cachedUser.value = it } }

    override suspend fun signUp(name: String, email: String, password: String): Outcome<User> =
        runCatchingOutcome {
            remote.signUp(name, email, password).also { user ->
                if (user.id.isNotBlank()) cachedUser.value = user
            }
        }

    override suspend fun signOut(): Outcome<Unit> = runCatchingOutcome {
        // Best effort, and before the session goes away: without it this
        // phone keeps receiving the previous account's queue pushes. The
        // 0013 trigger covers the case where the call never gets through.
        remote.currentUserId()?.let { id -> runCatching { remote.clearPushToken(id) } }
        remote.signOut()
        cachedUser.value = null
        // Explicitly signing out is the one time it is right to drop the
        // cache: leaving another account's tickets on the device would be
        // both confusing and a small privacy leak.
        database.queueDao().clear()
        database.notificationDao().clear()
        database.reviewDao().clear()
        database.pendingActionDao().clear()
    }

    override suspend fun sendPasswordReset(email: String): Outcome<Unit> =
        runCatchingOutcome { remote.sendPasswordReset(email) }

    override fun handleRecoveryLink(intent: Intent) {
        remote.handleRecoveryLink(intent) { _recoveryPending.value = true }
    }

    override suspend fun updatePassword(newPassword: String): Outcome<Unit> =
        runCatchingOutcome {
            remote.updatePassword(newPassword)
            _recoveryPending.value = false
        }

    override suspend fun abandonRecovery(): Outcome<Unit> {
        _recoveryPending.value = false
        return signOut()
    }

    override suspend fun refreshProfile(): Outcome<User> = runCatchingOutcome {
        val id = remote.currentUserId() ?: error("NOT_AUTHENTICATED")
        remote.fetchProfile(id).also { cachedUser.value = it }
    }

    override suspend fun updateProfile(name: String, phone: String?): Outcome<User> =
        runCatchingOutcome {
            val id = remote.currentUserId() ?: error("NOT_AUTHENTICATED")
            remote.updateProfile(id, name, phone).also { cachedUser.value = it }
        }

    override suspend fun syncPushToken(token: String): Outcome<Unit> = runCatchingOutcome {
        val id = remote.currentUserId() ?: return@runCatchingOutcome
        remote.updatePushToken(id, token)
    }

    override suspend fun syncLocale(locale: String): Outcome<Unit> = runCatchingOutcome {
        val id = remote.currentUserId() ?: return@runCatchingOutcome
        remote.updateLocale(id, locale)
    }
}
