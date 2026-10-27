package org.umn.ngantriin.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.umn.ngantriin.R
import org.umn.ngantriin.core.AppError
import org.umn.ngantriin.di.containerViewModel
import org.umn.ngantriin.domain.model.RestaurantApplication
import org.umn.ngantriin.domain.model.VerificationStatus
import org.umn.ngantriin.ui.components.ConfirmDialog
import org.umn.ngantriin.ui.components.EmptyState
import org.umn.ngantriin.ui.components.FullScreenLoader
import org.umn.ngantriin.ui.components.dateTimeText
import org.umn.ngantriin.ui.search.ChoiceChip
import org.umn.ngantriin.ui.theme.Spacing

/** Beranda reviewer: pengajuan per status. Tidak ada hal lain yang bisa dibuka dari akun admin. */
@Composable
fun AdminReviewScreen(
    onOpenApplication: (restaurantId: String, status: VerificationStatus) -> Unit,
    onMessage: (AppError) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = containerViewModel { c ->
        AdminReviewViewModel(adminRepository = c.adminRepository, authRepository = c.authRepository)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmSignOut by remember { mutableStateOf(false) }

    // Kembali dari keputusan: daftarnya sudah berubah.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    LaunchedEffect(state.error) { state.error?.let(onMessage) }

    if (confirmSignOut) {
        ConfirmDialog(
            title = stringResource(R.string.staff_sign_out_title),
            message = stringResource(R.string.profile_sign_out_message),
            confirmLabel = stringResource(R.string.profile_sign_out),
            dismissLabel = stringResource(R.string.action_cancel),
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
            .padding(horizontal = Spacing.screenGutter)
    ) {
        Spacer(Modifier.height(Spacing.lg))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.admin_review_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { confirmSignOut = true }) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.profile_sign_out))
            }
        }

        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FILTERS.forEach { (status, label) ->
                ChoiceChip(
                    label = stringResource(label),
                    selected = state.filter == status,
                    onClick = { viewModel.setFilter(status) }
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))

        when {
            state.isLoading -> FullScreenLoader()
            state.applications.isEmpty() -> EmptyState(
                icon = Icons.Outlined.Inbox,
                title = stringResource(R.string.admin_empty_title),
                message = stringResource(R.string.admin_empty_message)
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                items(state.applications, key = { it.restaurantId }) { application ->
                    ApplicationRow(
                        application = application,
                        onClick = { onOpenApplication(application.restaurantId, state.filter) }
                    )
                }
            }
        }
    }
}

private val FILTERS = listOf(
    VerificationStatus.PENDING_VERIFICATION to R.string.admin_filter_pending,
    VerificationStatus.REJECTED to R.string.admin_filter_rejected,
    VerificationStatus.APPROVED to R.string.admin_filter_approved
)

@Composable
private fun ApplicationRow(application: RestaurantApplication, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Text(
                text = application.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = application.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = application.applicantName.ifBlank { application.applicantEmail },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                application.submittedAt?.let {
                    Text(
                        text = stringResource(R.string.admin_submitted, dateTimeText(it)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = stringResource(R.string.admin_documents_count, application.documentCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
