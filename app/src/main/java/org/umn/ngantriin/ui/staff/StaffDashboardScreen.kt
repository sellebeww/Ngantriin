package org.umn.ngantriin.ui.staff

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import org.umn.ngantriin.domain.model.QueueEntry
import org.umn.ngantriin.domain.model.VerificationStatus
import org.umn.ngantriin.domain.model.Restaurant
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Cancel
import org.umn.ngantriin.ui.components.fullBleed
import org.umn.ngantriin.ui.components.relativeText
import org.umn.ngantriin.ui.components.waitTimeText
import org.umn.ngantriin.ui.search.ChoiceChip
import org.umn.ngantriin.ui.theme.LocalStatusColors
import org.umn.ngantriin.ui.theme.QueueNumberStyle
import org.umn.ngantriin.ui.theme.Spacing
import org.umn.ngantriin.ui.theme.balanced

/** Screen 20. */
@Composable
fun StaffDashboardScreen(
    onOpenQueue: (String) -> Unit,
    onRegisterRestaurant: () -> Unit,
    onResubmit: (String) -> Unit,
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

    // Coming back from the registration form: pick up the new venue.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshManaged() }

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
            .animateContentSize()
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
            IconButton(onClick = onRegisterRestaurant) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.staff_register_restaurant))
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
            Spacer(Modifier.height(Spacing.lg))
            PrimaryButton(
                text = stringResource(R.string.staff_register_restaurant),
                onClick = onRegisterRestaurant
            )
            return@Column
        }

        if (state.managedRestaurants.size > 1) {
            Row(
                modifier = Modifier
                    .fullBleed(Spacing.screenGutter)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screenGutter),
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
        // Nothing here is manageable until the application is approved; the
        // server enforces it too (is_restaurant_staff), this just says why.
        if (restaurant != null && !restaurant.isApproved) {
            Text(
                text = restaurant.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(Spacing.md))
            VerificationCard(
                restaurant = restaurant,
                onRefresh = viewModel::refreshManaged,
                onResubmit = { onResubmit(restaurant.id) }
            )
            return@Column
        }

        if (restaurant != null) {
            Text(
                text = restaurant.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(Spacing.md))
            ListingToggle(
                listed = restaurant.isActive,
                enabled = !state.isWorking,
                onChange = viewModel::setListed
            )
            Spacer(Modifier.height(Spacing.lg))
        }

        NowServingCard(state)

        Spacer(Modifier.height(Spacing.lg))

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StaffStat(
                value = "${state.waiting.size}",
                label = stringResource(R.string.staff_waiting),
                accent = LocalStatusColors.current.waiting,
                modifier = Modifier.weight(1f)
            )
            StaffStat(
                value = "${state.called.size}",
                label = stringResource(R.string.staff_called),
                accent = LocalStatusColors.current.called,
                modifier = Modifier.weight(1f)
            )
            StaffStat(
                value = "${state.atRestaurant}",
                label = stringResource(R.string.staff_seated),
                accent = LocalStatusColors.current.checkedIn,
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

        if (state.waiting.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.xxl))
            Text(
                text = stringResource(R.string.staff_upcoming_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(Spacing.sm))
            state.waiting.take(UPCOMING_PREVIEW).forEachIndexed { index, entry ->
                UpcomingRow(index = index + 1, entry = entry)
                Spacer(Modifier.height(Spacing.sm))
            }
            val more = state.waiting.size - UPCOMING_PREVIEW
            if (more > 0) {
                Text(
                    text = stringResource(R.string.staff_upcoming_more, more),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(Spacing.xxxl))
    }
}

private const val UPCOMING_PREVIEW = 3

/** Whether customers can find this venue and take tickets there. */
@Composable
private fun ListingToggle(listed: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.staff_listing_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(
                        if (listed) R.string.staff_listing_on else R.string.staff_listing_off
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = listed, onCheckedChange = onChange, enabled = enabled)
        }
    }
}

/** One line of the "up next" preview: who is next and how long they've waited. */
@Composable
private fun UpcomingRow(index: Int, entry: QueueEntry) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$index",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.queueNumber,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(
                        R.string.staff_party_joined,
                        entry.partySize,
                        relativeText(entry.joinedAt)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (entry.checkedInEarly) {
                Text(
                    text = stringResource(R.string.staff_badge_early),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalStatusColors.current.checkedIn
                )
            }
        }
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
private fun StaffStat(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(vertical = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    (slideInVertically { it / 2 } + fadeIn()) togetherWith
                        (slideOutVertically { -it / 2 } + fadeOut())
                },
                label = "staffStat"
            ) { shown ->
                Text(
                    text = shown,
                    style = MaterialTheme.typography.headlineSmall,
                    color = accent
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.balanced(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}


/** Pending: wait. Rejected: here is why, and a way to fix it. */
@Composable
private fun VerificationCard(
    restaurant: Restaurant,
    onRefresh: () -> Unit,
    onResubmit: () -> Unit
) {
    val colors = LocalStatusColors.current
    val rejected = restaurant.verificationStatus == VerificationStatus.REJECTED
    val container = if (rejected) colors.cancelledContainer else colors.waitingContainer
    val content = if (rejected) colors.cancelled else colors.waiting

    Surface(
        shape = MaterialTheme.shapes.large,
        color = container,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(Spacing.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (rejected) Icons.Filled.Cancel else Icons.Filled.HourglassTop,
                    contentDescription = null,
                    tint = content
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = stringResource(
                        if (rejected) R.string.verification_rejected_title else R.string.verification_pending_title
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = content
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = if (rejected) {
                    stringResource(
                        R.string.verification_rejected_body,
                        restaurant.verificationNote?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.verification_no_reason)
                    )
                } else {
                    stringResource(R.string.verification_pending_body)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(Spacing.lg))
            if (rejected) {
                PrimaryButton(text = stringResource(R.string.verification_resubmit), onClick = onResubmit)
                Spacer(Modifier.height(Spacing.sm))
            }
            SecondaryButton(text = stringResource(R.string.verification_refresh), onClick = onRefresh)
        }
    }
}
