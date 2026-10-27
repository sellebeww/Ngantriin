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

data class ResetPasswordUiState(
    val password: String = "",
    val confirmPassword: String = "",
    @param:StringRes val passwordError: Int? = null,
    @param:StringRes val confirmPasswordError: Int? = null,
    val isSubmitting: Boolean = false,
    val error: AppError? = null
) {
    val canSubmit: Boolean
        get() = password.isNotBlank() && confirmPassword.isNotBlank() && !isSubmitting
}

/**
 * Memilih password baru setelah membuka tautan reset. Kalau berhasil,
 * repository menghapus flag recovery dan navigasi pindah sendiri, sama
 * seperti setelah login.
 */
class ResetPasswordViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResetPasswordUiState())
    val uiState: StateFlow<ResetPasswordUiState> = _uiState.asStateFlow()

    fun onPasswordChange(value: String) =
        _uiState.update {
            it.copy(password = value, passwordError = null, confirmPasswordError = null, error = null)
        }

    fun onConfirmPasswordChange(value: String) =
        _uiState.update { it.copy(confirmPassword = value, confirmPasswordError = null, error = null) }

    fun submit() {
        val state = _uiState.value
        val passwordError = Validators.passwordError(state.password)
        val confirmError = Validators.confirmPasswordError(state.password, state.confirmPassword)
        if (passwordError != null || confirmError != null) {
            _uiState.update {
                it.copy(passwordError = passwordError, confirmPasswordError = confirmError)
            }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            when (val outcome = authRepository.updatePassword(state.password)) {
                is Outcome.Success -> _uiState.update { it.copy(isSubmitting = false) }
                is Outcome.Failure ->
                    _uiState.update { it.copy(isSubmitting = false, error = outcome.error) }
            }
        }
    }

    fun cancel() {
        viewModelScope.launch { authRepository.abandonRecovery() }
    }
}
