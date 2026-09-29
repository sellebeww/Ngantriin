package org.umn.ngantriin.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.umn.ngantriin.R
import org.umn.ngantriin.di.LocalAppContainer
import org.umn.ngantriin.di.containerViewModel
import org.umn.ngantriin.domain.model.UserRole
import org.umn.ngantriin.ui.components.ConfirmDialog
import org.umn.ngantriin.ui.components.NgantriinTextField
import org.umn.ngantriin.ui.components.PrimaryButton
import org.umn.ngantriin.ui.components.SecondaryButton
import org.umn.ngantriin.ui.components.message
import org.umn.ngantriin.ui.theme.Spacing

/** Screen 17. */
@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenStaffDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = LocalAppContainer.current
    val viewModel = containerViewModel { c ->
        ProfileViewModel(
            authRepository = c.authRepository,
            queueRepository = c.queueRepository,
            notificationRepository = c.notificationRepository,
            preferences = c.userPreferencesRepository
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmSignOut by remember { mutableStateOf(false) }

    if (confirmSignOut) {
        ConfirmDialog(
            title = stringResource(R.string.profile_sign_out_title),
            message = stringResource(R.string.profile_sign_out_message),
            confirmLabel = stringResource(R.string.profile_sign_out),
            destructive = true,
            onConfirm = {
                confirmSignOut = false
                viewModel.signOut()
            },
            onDismiss = { confirmSignOut = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenGutter)
    ) {
        Spacer(Modifier.height(Spacing.lg))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.profile_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
            }
        }

        Spacer(Modifier.height(Spacing.lg))

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(Spacing.xl)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(50)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.user?.initials ?: "N",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(Modifier.width(Spacing.lg))

                    Column(Modifier.weight(1f)) {
                        Text(
                            text = state.user?.displayName ?: stringResource(R.string.profile_guest),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = state.user?.email.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!state.isEditing) {
                        IconButton(onClick = viewModel::startEditing) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.cd_edit_profile))
                        }
                    }
                }

                if (state.isEditing) {
                    Spacer(Modifier.height(Spacing.lg))
                    NgantriinTextField(
                        value = state.editedName,
                        onValueChange = viewModel::onNameChange,
                        label = stringResource(R.string.register_full_name_label),
                        enabled = !state.isSaving
                    )
                    state.error?.let { error ->
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            text = error.message(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.height(Spacing.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        SecondaryButton(
                            text = stringResource(R.string.action_cancel),
                            onClick = viewModel::cancelEditing,
                            modifier = Modifier.weight(1f)
                        )
                        PrimaryButton(
                            text = stringResource(R.string.action_save),
                            onClick = viewModel::saveProfile,
                            loading = state.isSaving,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.lg))

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StatTile(
                value = "${state.completedVisits}",
                label = stringResource(R.string.profile_stat_visits),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                value = "${state.totalQueues}",
                label = stringResource(R.string.profile_stat_queues),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                value = "${state.minutesSaved}",
                label = stringResource(R.string.profile_stat_minutes_saved),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(Spacing.xl))

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                ProfileRow(
                    icon = Icons.Filled.Notifications,
                    label = stringResource(R.string.profile_notifications),
                    trailing = if (state.unreadNotifications > 0) {
                        stringResource(R.string.profile_notifications_new, state.unreadNotifications)
                    } else {
                        null
                    },
                    onClick = onOpenNotifications
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ProfileRow(
                    icon = Icons.Filled.Settings,
                    label = stringResource(R.string.settings_title),
                    onClick = onOpenSettings
                )

                if (state.user?.role == UserRole.STAFF) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ProfileRow(
                        icon = Icons.Filled.Settings,
                        label = stringResource(R.string.profile_restaurant_dashboard),
                        onClick = onOpenStaffDashboard
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.xl))

        SecondaryButton(
            text = stringResource(R.string.profile_sign_out),
            onClick = { confirmSignOut = true },
            icon = Icons.AutoMirrored.Filled.Logout
        )

        Spacer(Modifier.height(Spacing.lg))

        Text(
            text = if (container.isDemoMode) {
                stringResource(R.string.profile_footer_demo)
            } else {
                stringResource(R.string.profile_footer)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(Spacing.xxxl))
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(vertical = Spacing.lg, horizontal = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ProfileRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    trailing: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(Spacing.sm))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline
        )
    }
}
