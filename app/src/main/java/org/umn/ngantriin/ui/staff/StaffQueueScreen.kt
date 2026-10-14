package org.umn.ngantriin.ui.staff

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.umn.ngantriin.R
import org.umn.ngantriin.core.AppError
import org.umn.ngantriin.di.containerViewModel
import org.umn.ngantriin.domain.model.QueueEntry
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.ui.components.ConfirmDialog
import org.umn.ngantriin.ui.components.EmptyState
import org.umn.ngantriin.ui.components.QueueStatusPill
import org.umn.ngantriin.ui.components.SectionHeader
import org.umn.ngantriin.ui.components.relativeText
import org.umn.ngantriin.ui.queue.formatCountdown
import org.umn.ngantriin.ui.queue.rememberSecondsLeft
import org.umn.ngantriin.ui.theme.LocalStatusColors
import org.umn.ngantriin.ui.theme.Spacing

/** Screen 21. */
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

    var search by remember { mutableStateOf("") }
    // Removing someone from the line cannot be undone (CANCELLED is terminal),
    // so a stray tap on "Remove" or "No-show" asks first.
    var pendingCancel by remember { mutableStateOf<QueueEntry?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            if (event is StaffEvent.Failed) onMessage(event.error)
        }
    }

    pendingCancel?.let { entry ->
        ConfirmDialog(
            title = stringResource(R.string.staff_cancel_title, entry.queueNumber),
            message = stringResource(R.string.staff_cancel_message),
            confirmLabel = stringResource(R.string.staff_cancel_confirm),
            dismissLabel = stringResource(R.string.staff_cancel_keep),
            destructive = true,
            onConfirm = {
                pendingCancel = null
                viewModel.updateStatus(entry.id, QueueStatus.CANCELLED)
            },
            onDismiss = { pendingCancel = null }
        )
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

        val needle = search.trim()
        fun List<QueueEntry>.matching() =
            if (needle.isEmpty()) this else filter { it.queueNumber.contains(needle, ignoreCase = true) }

        val calledTitle = stringResource(R.string.staff_section_called)
        val calledSubtitle = stringResource(R.string.staff_section_called_subtitle)
        val arrivedTitle = stringResource(R.string.staff_section_arrived)
        val arrivedSubtitle = stringResource(R.string.staff_section_arrived_subtitle)
        val tableReadyTitle = stringResource(R.string.staff_section_table_ready)
        val tableReadySubtitle = stringResource(R.string.staff_section_table_ready_subtitle)
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
            if (state.entries.size > SEARCH_THRESHOLD || needle.isNotEmpty()) {
                item(key = "search") {
                    Spacer(Modifier.height(Spacing.sm))
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text(stringResource(R.string.staff_search_placeholder)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (search.isNotEmpty()) {
                                IconButton(onClick = { search = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_clear_search))
                                }
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            val sections = listOf(
                Triple(tableReadyTitle, tableReadySubtitle, state.tableReady.matching()),
                Triple(arrivedTitle, arrivedSubtitle, state.arrived.matching()),
                Triple(calledTitle, calledSubtitle, state.called.matching()),
                Triple(waitingTitle, waitingSubtitle, state.waiting.matching())
            )
            if (needle.isNotEmpty() && sections.all { it.third.isEmpty() }) {
                item(key = "no-match") {
                    Text(
                        text = stringResource(R.string.staff_search_no_match, needle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.xl)
                    )
                }
            }

            // Ordered by urgency: who is standing at the host right now first.
            sections.forEach { (title, subtitle, entries) ->
                queueSection(
                    title = title,
                    subtitle = subtitle,
                    sectionTitleCount = sectionTitleCount,
                    entries = entries,
                    isWorking = state.isWorking,
                    onOpenCustomer = onOpenCustomer,
                    onUpdateStatus = { id, status ->
                        if (status == QueueStatus.CANCELLED) {
                            pendingCancel = state.entries.firstOrNull { it.id == id }
                        } else {
                            viewModel.updateStatus(id, status)
                        }
                    },
                    onCountdownFinished = viewModel::onCheckInWindowClosed
                )
            }
        }
    }
}

/** Below this many live tickets the whole line fits on screen; no search needed. */
private const val SEARCH_THRESHOLD = 6

private fun androidx.compose.foundation.lazy.LazyListScope.queueSection(
    title: String,
    subtitle: String,
    sectionTitleCount: String,
    entries: List<QueueEntry>,
    isWorking: Boolean,
    onOpenCustomer: (String) -> Unit,
    onUpdateStatus: (String, QueueStatus) -> Unit,
    onCountdownFinished: () -> Unit
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
            onCountdownFinished = onCountdownFinished,
            modifier = Modifier.animateItem().padding(vertical = Spacing.xs)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StaffQueueRow(
    entry: QueueEntry,
    isWorking: Boolean,
    onClick: () -> Unit,
    onUpdateStatus: (QueueStatus) -> Unit,
    onCountdownFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColors = LocalStatusColors.current
    val seconds = rememberSecondsLeft(
        deadline = entry.checkInDeadline.takeIf { entry.status == QueueStatus.CALLED },
        onFinished = onCountdownFinished
    )

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
                    if (seconds != null) {
                        Text(
                            text = if (seconds > 0L) {
                                stringResource(R.string.staff_countdown, formatCountdown(seconds))
                            } else {
                                stringResource(R.string.staff_countdown_over)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (seconds <= 120L) statusColors.cancelled else statusColors.called
                        )
                    }
                    if (entry.checkedInEarly) {
                        Text(
                            text = stringResource(R.string.staff_badge_early),
                            style = MaterialTheme.typography.labelMedium,
                            color = statusColors.checkedIn
                        )
                    }
                }
                QueueStatusPill(status = entry.status, compact = true)
            }

            // Only the transitions the state machine actually allows are
            // offered, so staff cannot get the queue into an illegal state.
            val actions = entry.staffActions()
            if (actions.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                // A TextButton insets its label by 12dp; pull the row back so the
                // first action lines up with the ticket number above it.
                FlowRow(
                    modifier = Modifier.offset(x = (-12).dp),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
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

/** The legal next moves for staff, straight out of the state machine. */
private fun QueueEntry.staffActions(): List<Pair<Int, QueueStatus>> = when (status) {
    QueueStatus.WAITING, QueueStatus.ALMOST_THERE -> listOf(
        R.string.staff_action_call to QueueStatus.CALLED,
        R.string.staff_action_check_in to QueueStatus.CHECKED_IN,
        R.string.staff_action_remove to QueueStatus.CANCELLED
    )
    QueueStatus.CALLED -> listOf(
        R.string.staff_action_check_in to QueueStatus.CHECKED_IN,
        R.string.staff_action_no_show to QueueStatus.CANCELLED
    )
    QueueStatus.CHECKED_IN -> listOf(
        R.string.staff_action_table_ready to QueueStatus.TABLE_READY,
        R.string.staff_action_remove to QueueStatus.CANCELLED
    )
    QueueStatus.TABLE_READY -> listOf(
        R.string.staff_action_complete to QueueStatus.COMPLETED,
        R.string.staff_action_no_show to QueueStatus.CANCELLED
    )
    QueueStatus.COMPLETED, QueueStatus.CANCELLED, QueueStatus.EXPIRED -> emptyList()
}
