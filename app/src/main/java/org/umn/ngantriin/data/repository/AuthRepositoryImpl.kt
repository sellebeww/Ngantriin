package org.umn.ngantriin.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
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

    override val sessionState: Flow<SessionState> = remote.session
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
}
