package org.umn.ngantriin.data.remote.datasource

import android.content.Intent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.umn.ngantriin.data.mapper.toDomain
import org.umn.ngantriin.data.remote.dto.FcmTokenUpdateDto
import org.umn.ngantriin.data.remote.dto.LocaleUpdateDto
import org.umn.ngantriin.data.remote.dto.UserDto
import org.umn.ngantriin.data.remote.dto.UserProfileUpdateDto
import org.umn.ngantriin.domain.model.User

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
            // Initializing dan RefreshFailure sama-sama berarti "belum diputuskan",
            // kegagalan refresh akan segera jadi NotAuthenticated sendiri.
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
            // Dibaca trigger handle_new_user, jadi baris profil sudah ada
            // sebelum client memintanya. Role tidak dikirim: trigger selalu
            // membuat CUSTOMER (0012), akses staf diberikan admin lewat
            // restaurant_staff.
            data = buildJsonObject {
                put("name", name.trim())
            }
        }

        // Kalau konfirmasi email aktif, belum ada sesi. Kembalikan profil
        // sementara supaya UI bisa menyuruh pengguna konfirmasi.
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

    override fun handleRecoveryLink(intent: Intent, onRecovered: () -> Unit) {
        client.handleDeeplinks(intent) { onRecovered() }
    }

    override suspend fun updatePassword(newPassword: String) {
        client.auth.updateUser { password = newPassword }
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

    override suspend fun clearPushToken(userId: String) {
        // JSON client membuang null, jadi DTO tidak bisa membawa null eksplisit.
        client.from("users").update({ set<String?>("fcm_token", null) }) {
            filter { eq("id", userId) }
        }
    }

    override suspend fun updateLocale(userId: String, locale: String) {
        client.from("users").update(LocaleUpdateDto(locale)) {
            filter { eq("id", userId) }
        }
    }
}
