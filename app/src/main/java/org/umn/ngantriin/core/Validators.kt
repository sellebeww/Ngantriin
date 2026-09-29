package org.umn.ngantriin.core

import androidx.annotation.StringRes
import org.umn.ngantriin.R

/**
 * Form rules, kept out of the ViewModels so they can be unit tested. Returns
 * a `@StringRes` id rather than raw text — mirroring [AppError] — so the
 * screen resolves it through `stringResource()` and picks up whichever
 * language Settings -> Language selected (section 43).
 */
object Validators {

    private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    @StringRes
    fun emailError(value: String): Int? = when {
        value.isBlank() -> R.string.validation_email_required
        !EMAIL.matches(value.trim()) -> R.string.validation_email_invalid
        else -> null
    }

    @StringRes
    fun passwordError(value: String): Int? = when {
        value.isBlank() -> R.string.validation_password_required
        value.length < Constants.MIN_PASSWORD_LENGTH -> R.string.validation_password_too_short
        else -> null
    }

    @StringRes
    fun nameError(value: String): Int? = when {
        value.isBlank() -> R.string.validation_name_required
        value.trim().length < 2 -> R.string.validation_name_invalid
        else -> null
    }

    @StringRes
    fun confirmPasswordError(password: String, confirmation: String): Int? = when {
        confirmation.isBlank() -> R.string.validation_confirm_password_required
        password != confirmation -> R.string.validation_passwords_dont_match
        else -> null
    }
}
