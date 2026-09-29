package org.umn.ngantriin.ui.staff

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Campaign
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.umn.ngantriin.R
import org.umn.ngantriin.core.AppError
import org.umn.ngantriin.di.LocalAppContainer
import org.umn.ngantriin.di.containerViewModel
import org.umn.ngantriin.ui.components.ConfirmDialog
import org.umn.ngantriin.ui.components.EmptyState
import org.umn.ngantriin.ui.components.FullScreenLoader
import org.umn.ngantriin.ui.components.PrimaryButton
import org.umn.ngantriin.ui.components.SecondaryButton
import org.umn.ngantriin.ui.components.waitTimeText
import org.umn.ngantriin.ui.search.ChoiceChip
import org.umn.ngantriin.ui.theme.LocalStatusColors
import org.umn.ngantriin.ui.theme.QueueNumberStyle
import org.umn.ngantriin.ui.theme.Spacing

/** Screen 20 (section 24). */
@Composable
fun StaffDashboardScreen(
    onOpenQueue: (String) -> Unit,
    onMessage: (AppError) -> Unit,
    modifier: Modifier = Modifier
) {
    val container = LocalAppContainer.current
    val viewModel = containerViewModel { c ->
        StaffQueueViewModel(
            authRepository = c.authRepository,
            restaurantRepository = c.restaurantRepository,
            queueRepository = c.queueRepository
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var confirmSignOut by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            if (event is StaffEvent.Failed) onMessage(event.error)
        }
    }

    if (confirmSignOut) {
        ConfirmDialog(
            title = stringResource(R.string.staff_sign_out_title),
            message = stringResource(R.string.staff_sign_out_message),
            confirmLabel = stringResource(R.string.profile_sign_out),
            destructive = true,
            onConfirm = {
                confirmSignOut = false
                viewModel.signOut()
            },
            onDismiss = { confirmSignOut = false }
        )
    }

    if (state.isLoading) {
        FullScreenLoader(modifier.fillMaxSize())
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenGutter)
    ) {
        Spacer(Modifier.height(Spacing.lg))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.staff_dashboard_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = container.authRepository.currentUserOrNull()?.email.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { confirmSignOut = true }) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.profile_sign_out))
            }
        }

        Spacer(Modifier.height(Spacing.lg))

        if (state.managedRestaurants.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Inbox,
                title = stringResource(R.string.staff_no_restaurants_title),
                message = stringResource(R.string.staff_no_restaurants_message)
            )
            return@Column
        }

        if (state.managedRestaurants.size > 1) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                state.managedRestaurants.forEach { restaurant ->
                    ChoiceChip(
                        label = restaurant.name,
                        selected = restaurant.id == state.selectedRestaurant?.id,
                        onClick = { viewModel.selectRestaurant(restaurant.id) }
                    )
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }

        val restaurant = state.selectedRestaurant
        if (restaurant != null) {
            Text(
                text = restaurant.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(Spacing.lg))
        }

        NowServingCard(state)

        Spacer(Modifier.height(Spacing.lg))

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StaffStat(
                value = "${state.waiting.size}",
                label = stringResource(R.string.staff_waiting),
                modifier = Modifier.weight(1f)
            )
            StaffStat(
                value = "${state.called.size}",
                label = stringResource(R.string.staff_called),
                modifier = Modifier.weight(1f)
            )
            StaffStat(
                value = "${state.seated.size}",
                label = stringResource(R.string.staff_seated),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(Spacing.lg))

        Text(
            text = state.stats?.let {
                stringResource(R.string.staff_estimated_wait_new_group, waitTimeText(it.estimatedWaitMinutes))
            } ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(Spacing.xl))

        PrimaryButton(
            text = state.next?.let { stringResource(R.string.staff_call_next_button, it.queueNumber) }
                ?: stringResource(R.string.staff_nobody_waiting),
            onClick = viewModel::callNext,
            enabled = state.canCallNext,
            loading = state.isWorking,
            icon = Icons.Filled.Campaign
        )

        Spacer(Modifier.height(Spacing.md))

        SecondaryButton(
            text = stringResource(R.string.staff_manage_queue, state.entries.size),
            onClick = { state.selectedRestaurant?.id?.let(onOpenQueue) },
            enabled = state.selectedRestaurant != null
        )

        Spacer(Modifier.height(Spacing.xxxl))
    }
}

@Composable
private fun NowServingCard(state: StaffQueueUiState) {
    val statusColors = LocalStatusColors.current

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(Spacing.xl),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.staff_now_serving),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.xs))
                val serving = state.nowServing?.queueNumber
                Text(
                    text = serving ?: "—",
                    // The placeholder must not borrow the "being served" green;
                    // an empty board is not a ticket.
                    style = if (serving != null) {
                        QueueNumberStyle.copy(
                            fontSize = MaterialTheme.typography.displayMedium.fontSize
                        )
                    } else {
                        MaterialTheme.typography.displaySmall
                    },
                    color = if (serving != null) {
                        statusColors.called
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Box(
                Modifier
                    .size(1.dp, 64.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = stringResource(R.string.staff_next_up),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = state.next?.queueNumber ?: "—",
                    style = MaterialTheme.typography.displaySmall,
                    color = if (state.next != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun StaffStat(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(vertical = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
