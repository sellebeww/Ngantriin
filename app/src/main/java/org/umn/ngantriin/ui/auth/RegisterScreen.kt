package org.umn.ngantriin.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.umn.ngantriin.R
import org.umn.ngantriin.core.Constants
import org.umn.ngantriin.di.containerViewModel
import org.umn.ngantriin.ui.components.NgantriinPasswordField
import org.umn.ngantriin.ui.components.NgantriinTextField
import org.umn.ngantriin.ui.components.PrimaryButton
import org.umn.ngantriin.ui.components.message
import org.umn.ngantriin.ui.theme.Spacing

/** Section 7. */
@Composable
fun RegisterScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = containerViewModel { container -> RegisterViewModel(container.authRepository) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AuthScaffold(
        title = stringResource(R.string.register_title),
        subtitle = stringResource(R.string.register_subtitle),
        onBack = onBack,
        modifier = modifier
    ) {
        NgantriinTextField(
            value = state.name,
            onValueChange = viewModel::onNameChange,
            label = stringResource(R.string.register_full_name_label),
            leadingIcon = Icons.Filled.PersonOutline,
            errorText = state.nameError?.let { stringResource(it) },
            enabled = !state.isSubmitting
        )

        Spacer(Modifier.height(Spacing.lg))

        NgantriinTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = stringResource(R.string.auth_email_label),
            placeholder = stringResource(R.string.auth_email_placeholder),
            leadingIcon = Icons.Filled.MailOutline,
            errorText = state.emailError?.let { stringResource(it) },
            keyboardType = KeyboardType.Email,
            enabled = !state.isSubmitting
        )

        Spacer(Modifier.height(Spacing.lg))

        NgantriinPasswordField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = stringResource(R.string.auth_password_label),
            errorText = state.passwordError?.let {
                if (it == R.string.validation_password_too_short) {
                    stringResource(it, Constants.MIN_PASSWORD_LENGTH)
                } else {
                    stringResource(it)
                }
            },
            enabled = !state.isSubmitting
        )

        Spacer(Modifier.height(Spacing.lg))

        NgantriinPasswordField(
            value = state.confirmPassword,
            onValueChange = viewModel::onConfirmPasswordChange,
            label = stringResource(R.string.register_confirm_password_label),
            errorText = state.confirmPasswordError?.let { stringResource(it) },
            imeAction = ImeAction.Done,
            enabled = !state.isSubmitting
        )

        Spacer(Modifier.height(Spacing.xl))

        state.error?.let { error ->
            Text(
                text = error.message(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(Spacing.md))
        }

        if (state.confirmationRequired) {
            Text(
                text = stringResource(R.string.register_confirmation_required),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(Spacing.md))
        }

        PrimaryButton(
            text = stringResource(R.string.register_button),
            onClick = viewModel::register,
            enabled = state.canSubmit,
            loading = state.isSubmitting
        )

        Spacer(Modifier.height(Spacing.lg))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.register_already_have_account),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.register_log_in), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
