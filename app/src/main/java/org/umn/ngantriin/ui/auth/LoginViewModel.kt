package org.umn.ngantriin.ui.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.umn.ngantriin.R
import org.umn.ngantriin.core.AppError
import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.core.Validators
import org.umn.ngantriin.domain.repository.AuthRepository

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    @param:StringRes val emailError: Int? = null,
    @param:StringRes val passwordError: Int? = null,
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
    /** Set by [LoginViewModel.sendPasswordReset]; the screen fills in the email. */
    val resetEmailSent: String? = null
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() && password.isNotBlank() && !isSubmitting
}

class LoginViewModel(
    private val authRepository: AuthRepository,
    val isDemoMode: Boolean
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, emailError = null, error = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, passwordError = null, error = null) }
    }

    /**
     * Navigation is not triggered from here: the session flow is the single
     * source of truth for where the app should be (section 35), so a
     * successful sign-in simply lets that flow move the user.
     */
    fun signIn() {
        val state = _uiState.value
        val emailError = Validators.emailError(state.email)
        // Only shape is checked on sign-in. A length rule here would reject
        // valid older passwords; the server decides whether it is right.
        val passwordError = if (state.password.isBlank()) {
            R.string.validation_password_required
        } else {
            null
        }

        if (emailError != null || passwordError != null) {
            _uiState.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, error = null, resetEmailSent = null) }
        viewModelScope.launch {
            when (val outcome = authRepository.signIn(state.email, state.password)) {
                is Outcome.Success -> _uiState.update { it.copy(isSubmitting = false) }
                is Outcome.Failure ->
                    _uiState.update { it.copy(isSubmitting = false, error = outcome.error) }
            }
        }
    }

    fun sendPasswordReset() {
        val email = _uiState.value.email
        val emailError = Validators.emailError(email)
        if (emailError != null) {
            _uiState.update { it.copy(emailError = emailError) }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            when (val outcome = authRepository.sendPasswordReset(email)) {
                is Outcome.Success -> _uiState.update {
                    it.copy(isSubmitting = false, resetEmailSent = email.trim())
                }
                is Outcome.Failure ->
                    _uiState.update { it.copy(isSubmitting = false, error = outcome.error) }
            }
        }
    }

    fun dismissMessages() {
        _uiState.update { it.copy(error = null, resetEmailSent = null) }
    }
}
