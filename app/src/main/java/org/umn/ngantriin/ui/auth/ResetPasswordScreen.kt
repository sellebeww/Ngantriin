package org.umn.ngantriin.ui.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.umn.ngantriin.R
import org.umn.ngantriin.core.Constants
import org.umn.ngantriin.di.containerViewModel
import org.umn.ngantriin.ui.components.NgantriinPasswordField
import org.umn.ngantriin.ui.components.PrimaryButton
import org.umn.ngantriin.ui.components.message
import org.umn.ngantriin.ui.theme.Spacing

@Composable
fun ResetPasswordScreen(modifier: Modifier = Modifier) {
    val viewModel = containerViewModel { container -> ResetPasswordViewModel(container.authRepository) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AuthScaffold(
        title = stringResource(R.string.reset_password_title),
        subtitle = stringResource(R.string.reset_password_subtitle),
        modifier = modifier
    ) {
        NgantriinPasswordField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = stringResource(R.string.reset_password_new_label),
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
            onImeAction = { if (state.canSubmit) viewModel.submit() },
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

        PrimaryButton(
            text = stringResource(R.string.reset_password_button),
            onClick = viewModel::submit,
            enabled = state.canSubmit,
            loading = state.isSubmitting
        )

        Spacer(Modifier.height(Spacing.sm))

        TextButton(onClick = viewModel::cancel, enabled = !state.isSubmitting) {
            Text(stringResource(R.string.reset_password_cancel), style = MaterialTheme.typography.labelLarge)
        }
    }
}
