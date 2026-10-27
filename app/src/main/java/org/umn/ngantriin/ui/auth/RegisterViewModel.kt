package org.umn.ngantriin.ui.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.umn.ngantriin.core.AppError
import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.core.Validators
import org.umn.ngantriin.domain.repository.AuthRepository
import org.umn.ngantriin.domain.repository.UserPreferencesRepository

enum class AccountType { USER, STAFF }

data class RegisterUiState(
    val accountType: AccountType = AccountType.USER,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    @param:StringRes val nameError: Int? = null,
    @param:StringRes val emailError: Int? = null,
    @param:StringRes val passwordError: Int? = null,
    @param:StringRes val confirmPasswordError: Int? = null,
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
    /** Diisi kalau proyek mewajibkan konfirmasi email sebelum login pertama. */
    val confirmationRequired: Boolean = false
) {
    val canSubmit: Boolean
        get() = name.isNotBlank() && email.isNotBlank() &&
            password.isNotBlank() && confirmPassword.isNotBlank() && !isSubmitting
}

class RegisterViewModel(
    private val authRepository: AuthRepository,
    private val preferences: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onAccountTypeChange(value: AccountType) =
        _uiState.update { it.copy(accountType = value, error = null) }

    fun onNameChange(value: String) =
        _uiState.update { it.copy(name = value, nameError = null, error = null) }

    fun onEmailChange(value: String) =
        _uiState.update { it.copy(email = value, emailError = null, error = null) }

    fun onPasswordChange(value: String) =
        _uiState.update {
            // Sekaligus menghapus confirmPasswordError: pesan itu mencerminkan apakah
            // field ini dan confirmPassword sama, jadi bisa basi di sini juga, bukan
            // hanya saat confirmPassword sendiri berubah.
            it.copy(
                password = value,
                passwordError = null,
                confirmPasswordError = null,
                error = null
            )
        }

    fun onConfirmPasswordChange(value: String) =
        _uiState.update { it.copy(confirmPassword = value, confirmPasswordError = null, error = null) }

    fun register() {
        val state = _uiState.value
        val nameError = Validators.nameError(state.name)
        val emailError = Validators.emailError(state.email)
        val passwordError = Validators.passwordError(state.password)
        val confirmError = Validators.confirmPasswordError(state.password, state.confirmPassword)

        if (nameError != null || emailError != null ||
            passwordError != null || confirmError != null
        ) {
            _uiState.update {
                it.copy(
                    nameError = nameError,
                    emailError = emailError,
                    passwordError = passwordError,
                    confirmPasswordError = confirmError
                )
            }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            when (val outcome = authRepository.signUp(state.name, state.email, state.password)) {
                is Outcome.Success -> {
                    // Akunnya sendiri selalu pengguna biasa (server yang memastikan).
                    // Daftar sebagai staf hanya mengingat bahwa pengajuan restoran
                    // adalah langkah berikutnya.
                    if (state.accountType == AccountType.STAFF) {
                        preferences.setStaffApplicantEmail(state.email)
                    }
                    _uiState.update {
                        // Id kosong berarti Supabase membuat akun tapi belum
                        // menerbitkan sesi: proyek mewajibkan pengguna
                        // konfirmasi email dulu.
                        it.copy(
                            isSubmitting = false,
                            confirmationRequired = outcome.data.id.isBlank()
                        )
                    }
                }
                is Outcome.Failure ->
                    _uiState.update { it.copy(isSubmitting = false, error = outcome.error) }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}
