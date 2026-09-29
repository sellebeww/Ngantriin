package org.umn.ngantriin.data.remote.datasource

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.umn.ngantriin.data.mapper.toDomain
import org.umn.ngantriin.data.remote.dto.FcmTokenUpdateDto
import org.umn.ngantriin.data.remote.dto.UserDto
import org.umn.ngantriin.data.remote.dto.UserProfileUpdateDto
import org.umn.ngantriin.domain.model.User
import org.umn.ngantriin.domain.model.UserRole

class SupabaseAuthRemoteDataSource(
    private val client: SupabaseClient
) : AuthRemoteDataSource {

    override val session: Flow<RemoteSession> = client.auth.sessionStatus.map { status ->
        when (status) {
            is SessionStatus.Authenticated ->
                status.session.user?.id
                    ?.let { RemoteSession.SignedIn(it) }
                    ?: RemoteSession.SignedOut
            is SessionStatus.NotAuthenticated -> RemoteSession.SignedOut
            // Initializing and RefreshFailure both mean "not decided yet"; a
            // refresh failure resolves itself into NotAuthenticated shortly.
            else -> RemoteSession.Initializing
        }
    }

    override fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    override suspend fun signIn(email: String, password: String): User {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        val id = requireNotNull(currentUserId()) { "NOT_AUTHENTICATED" }
        return fetchProfile(id)
    }

    override suspend fun signUp(name: String, email: String, password: String): User {
        client.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
            // Read by the handle_new_user trigger, so the profile row exists
            // before the client ever asks for it.
            data = buildJsonObject {
                put("name", name.trim())
                put("role", UserRole.CUSTOMER.wireValue)
            }
        }

        // With email confirmation enabled there is no session yet; hand back a
        // provisional profile so the UI can tell the user to confirm.
        val id = currentUserId()
            ?: return User(id = "", name = name.trim(), email = email.trim())
        return fetchProfile(id)
    }

    override suspend fun signOut() {
        client.auth.signOut()
    }

    override suspend fun sendPasswordReset(email: String) {
        client.auth.resetPasswordForEmail(email.trim())
    }

    override suspend fun fetchProfile(userId: String): User =
        client.from("users")
            .select { filter { eq("id", userId) } }
            .decodeSingle<UserDto>()
            .toDomain()

    override suspend fun updateProfile(userId: String, name: String, phone: String?): User =
        client.from("users")
            .update(UserProfileUpdateDto(name = name.trim(), phone = phone?.trim())) {
                filter { eq("id", userId) }
                select()
            }
            .decodeSingle<UserDto>()
            .toDomain()

    override suspend fun updatePushToken(userId: String, token: String) {
        client.from("users").update(FcmTokenUpdateDto(token)) {
            filter { eq("id", userId) }
        }
    }
}
