package org.umn.ngantriin.domain.repository

import android.content.Intent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.domain.model.User

/** Yang diketahui aplikasi tentang orang yang sedang login. */
sealed interface SessionState {
    /** Sesi sedang dipulihkan dari disk: tampilkan splash, bukan login. */
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val user: User) : SessionState
}

interface AuthRepository {

    /** Sumber tunggal untuk gating navigasi. */
    val sessionState: Flow<SessionState>

    /** Akses snapshot untuk pemanggil yang tidak bisa suspend, misalnya WorkManager. */
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
     * True sejak tautan reset password dibuka sampai password baru dipilih.
     * Saat itu sesinya sudah login, jadi navigasi butuh ini supaya orangnya
     * tetap di layar reset, bukan pindah ke beranda.
     */
    val recoveryPending: StateFlow<Boolean>

    /** Mengoper intent masuk ke handler tautan reset, yang lain diabaikan. */
    fun handleRecoveryLink(intent: Intent)

    suspend fun updatePassword(newPassword: String): Outcome<Unit>

    /** Meninggalkan layar reset: logout dan mengosongkan [recoveryPending]. */
    suspend fun abandonRecovery(): Outcome<Unit>

    suspend fun refreshProfile(): Outcome<User>

    suspend fun updateProfile(name: String, phone: String?): Outcome<User>

    /** Menyimpan token push perangkat ke profil. */
    suspend fun syncPushToken(token: String): Outcome<Unit>

    /** Memberi tahu backend bahasa untuk menulis notifikasi push. */
    suspend fun syncLocale(locale: String): Outcome<Unit>
}
