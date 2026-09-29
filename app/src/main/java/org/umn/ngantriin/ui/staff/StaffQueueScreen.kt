package org.umn.ngantriin.ui.staff

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.umn.ngantriin.R
import org.umn.ngantriin.core.AppError
import org.umn.ngantriin.di.containerViewModel
import org.umn.ngantriin.domain.model.QueueEntry
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.ui.components.EmptyState
import org.umn.ngantriin.ui.components.QueueStatusPill
import org.umn.ngantriin.ui.components.SectionHeader
import org.umn.ngantriin.ui.components.relativeText
import org.umn.ngantriin.ui.theme.Spacing

/** Screen 21 (section 24). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffQueueScreen(
    restaurantId: String,
    onBack: () -> Unit,
    onOpenCustomer: (String) -> Unit,
    onMessage: (AppError) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = containerViewModel(key = "staff-queue-$restaurantId") { container ->
        StaffQueueViewModel(
            authRepository = container.authRepository,
            restaurantRepository = container.restaurantRepository,
            queueRepository = container.queueRepository,
            initialRestaurantId = restaurantId
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            if (event is StaffEvent.Failed) onMessage(event.error)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.selectedRestaurant?.name ?: stringResource(R.string.staff_queue_title_fallback),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::callNext, enabled = state.canCallNext) {
                        Text(stringResource(R.string.staff_call_next_action), style = MaterialTheme.typography.labelMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (state.entries.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Inbox,
                title = stringResource(R.string.staff_empty_title),
                message = stringResource(R.string.staff_empty_message),
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            return@Scaffold
        }

        val calledTitle = stringResource(R.string.staff_section_called)
        val calledSubtitle = stringResource(R.string.staff_section_called_subtitle)
        val seatedTitle = stringResource(R.string.staff_section_seated)
        val seatedSubtitle = stringResource(R.string.staff_section_seated_subtitle)
        val waitingTitle = stringResource(R.string.staff_section_waiting)
        val waitingSubtitle = stringResource(R.string.staff_section_waiting_subtitle)
        val sectionTitleCount = stringResource(R.string.staff_section_title_count)

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = Spacing.screenGutter,
                end = Spacing.screenGutter,
                bottom = Spacing.xxxl
            )
        ) {
            queueSection(
                title = calledTitle,
                subtitle = calledSubtitle,
                sectionTitleCount = sectionTitleCount,
                entries = state.called,
                isWorking = state.isWorking,
                onOpenCustomer = onOpenCustomer,
                onUpdateStatus = viewModel::updateStatus
            )
            queueSection(
                title = seatedTitle,
                subtitle = seatedSubtitle,
                sectionTitleCount = sectionTitleCount,
                entries = state.seated,
                isWorking = state.isWorking,
                onOpenCustomer = onOpenCustomer,
                onUpdateStatus = viewModel::updateStatus
            )
            queueSection(
                title = waitingTitle,
                subtitle = waitingSubtitle,
                sectionTitleCount = sectionTitleCount,
                entries = state.waiting,
                isWorking = state.isWorking,
                onOpenCustomer = onOpenCustomer,
                onUpdateStatus = viewModel::updateStatus
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.queueSection(
    title: String,
    subtitle: String,
    sectionTitleCount: String,
    entries: List<QueueEntry>,
    isWorking: Boolean,
    onOpenCustomer: (String) -> Unit,
    onUpdateStatus: (String, QueueStatus) -> Unit
) {
    if (entries.isEmpty()) return

    item(key = "header-$title") {
        Spacer(Modifier.height(Spacing.lg))
        SectionHeader(
            title = sectionTitleCount.format(title, entries.size),
            subtitle = subtitle
        )
        Spacer(Modifier.height(Spacing.sm))
    }

    items(entries, key = { it.id }) { entry ->
        StaffQueueRow(
            entry = entry,
            isWorking = isWorking,
            onClick = { onOpenCustomer(entry.id) },
            onUpdateStatus = { status -> onUpdateStatus(entry.id, status) },
            modifier = Modifier.padding(vertical = Spacing.xs)
        )
    }
}

@Composable
private fun StaffQueueRow(
    entry: QueueEntry,
    isWorking: Boolean,
    onClick: () -> Unit,
    onUpdateStatus: (QueueStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.clickable(onClick = onClick).padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                QueueStatusPill(status = entry.status, compact = true)
            }

            // Only the transitions the state machine actually allows are
            // offered, so staff cannot get the queue into an illegal state.
            val actions = entry.status.staffActions()
            if (actions.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    actions.forEach { (labelRes, status) ->
                        TextButton(
                            onClick = { onUpdateStatus(status) },
                            enabled = !isWorking
                        ) {
                            Text(stringResource(labelRes), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

/** The legal next moves for staff, straight out of section 14. */
private fun QueueStatus.staffActions(): List<Pair<Int, QueueStatus>> = when (this) {
    QueueStatus.WAITING, QueueStatus.ALMOST_THERE -> listOf(
        R.string.staff_action_call to QueueStatus.CALLED,
        R.string.staff_action_remove to QueueStatus.CANCELLED
    )
    QueueStatus.CALLED -> listOf(
        R.string.staff_action_seat to QueueStatus.CHECKED_IN,
        R.string.staff_action_complete to QueueStatus.COMPLETED,
        R.string.staff_action_no_show to QueueStatus.CANCELLED
    )
    QueueStatus.CHECKED_IN -> listOf(R.string.staff_action_complete to QueueStatus.COMPLETED)
    QueueStatus.COMPLETED, QueueStatus.CANCELLED -> emptyList()
}
