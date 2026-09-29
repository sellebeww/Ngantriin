package org.umn.ngantriin.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MailOutline
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
import org.umn.ngantriin.di.containerViewModel
import org.umn.ngantriin.ui.components.NgantriinPasswordField
import org.umn.ngantriin.ui.components.NgantriinTextField
import org.umn.ngantriin.ui.components.PrimaryButton
import org.umn.ngantriin.ui.components.message
import org.umn.ngantriin.ui.theme.Spacing

/** Section 7. */
@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = containerViewModel { container ->
        LoginViewModel(container.authRepository, container.isDemoMode)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AuthScaffold(
        title = stringResource(R.string.login_title),
        subtitle = stringResource(R.string.login_subtitle),
        modifier = modifier
    ) {
        if (viewModel.isDemoMode) {
            DemoModeNotice()
            Spacer(Modifier.height(Spacing.xl))
        }

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
            errorText = state.passwordError?.let { stringResource(it) },
            imeAction = ImeAction.Done,
            enabled = !state.isSubmitting
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = viewModel::sendPasswordReset,
                enabled = !state.isSubmitting
            ) {
                Text(stringResource(R.string.login_forgot_password), style = MaterialTheme.typography.labelMedium)
            }
        }

        state.error?.let { error ->
            Text(
                text = error.message(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(Spacing.md))
        }

        state.resetEmailSent?.let { email ->
            Text(
                text = stringResource(R.string.login_reset_email_sent, email),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(Spacing.md))
        }

        Spacer(Modifier.height(Spacing.sm))

        PrimaryButton(
            text = stringResource(R.string.login_button),
            onClick = viewModel::signIn,
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
                text = stringResource(R.string.login_new_to_app),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onNavigateToRegister) {
                Text(stringResource(R.string.login_create_account), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
