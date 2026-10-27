package org.umn.ngantriin.ui.history

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.umn.ngantriin.R
import org.umn.ngantriin.di.containerViewModel
import org.umn.ngantriin.domain.model.QueueEntry
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.domain.model.Review
import org.umn.ngantriin.ui.components.EmptyState
import org.umn.ngantriin.ui.components.FullScreenLoader
import org.umn.ngantriin.ui.components.InfoRow
import org.umn.ngantriin.ui.components.PrimaryButton
import org.umn.ngantriin.ui.components.QueueStatusPill
import org.umn.ngantriin.ui.components.RatingStars
import org.umn.ngantriin.ui.components.RestaurantImage
import org.umn.ngantriin.ui.components.SecondaryButton
import org.umn.ngantriin.ui.components.dateTimeText
import org.umn.ngantriin.ui.theme.LocalStatusColors
import org.umn.ngantriin.ui.theme.Spacing
import java.time.Instant

/** Cerita lengkap satu kunjungan. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(
    queueId: String,
    onBack: () -> Unit,
    onRate: (String) -> Unit,
    onViewRestaurant: (String) -> Unit,
    onOpenLiveQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = containerViewModel(key = "history-$queueId") { container ->
        HistoryDetailViewModel(
            queueId = queueId,
            queueRepository = container.queueRepository,
            reviewRepository = container.reviewRepository,
            restaurantRepository = container.restaurantRepository
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.history_detail_title),
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        val item = state.item
        when {
            state.isLoading -> FullScreenLoader(Modifier.padding(padding))

            item == null -> EmptyState(
                icon = Icons.Outlined.ReceiptLong,
                title = stringResource(R.string.history_not_found),
                message = "",
                actionLabel = stringResource(R.string.action_go_back),
                onAction = onBack,
                modifier = Modifier.fillMaxSize().padding(padding)
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screenGutter)
            ) {
                val entry = item.entry
                Spacer(Modifier.height(Spacing.sm))

                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.lg),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RestaurantImage(
                            imageUrl = item.restaurantImageUrl,
                            contentDescription = stringResource(R.string.cd_restaurant_photo, item.restaurantName),
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                        Spacer(Modifier.width(Spacing.lg))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = item.restaurantName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                text = dateTimeText(entry.joinedAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(Spacing.sm))
                            QueueStatusPill(entry.status, compact = true)
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.lg))

                InfoRow(
                    icon = Icons.Filled.ConfirmationNumber,
                    label = stringResource(R.string.confirm_queue_number_label),
                    value = entry.queueNumber
                )
                InfoRow(
                    icon = Icons.Filled.Groups,
                    label = stringResource(R.string.restaurant_party_size),
                    value = stringResource(R.string.history_party_size, entry.partySize)
                )
                InfoRow(
                    icon = Icons.Filled.Schedule,
                    label = stringResource(R.string.staff_waited_label),
                    // Labelnya sudah bilang "Menunggu", nilainya cukup waktunya.
                    value = entry.waitedMinutes
                        ?.let { stringResource(R.string.staff_waited_value, it) }
                        ?: stringResource(R.string.history_wait_not_recorded_short)
                )

                Spacer(Modifier.height(Spacing.xl))
                Text(
                    text = stringResource(R.string.history_timeline),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.md))
                Timeline(entry)

                if (entry.status == QueueStatus.COMPLETED) {
                    Spacer(Modifier.height(Spacing.xl))
                    ReviewSection(state.review)
                }

                Spacer(Modifier.height(Spacing.xl))

                if (state.isLive) {
                    PrimaryButton(
                        text = stringResource(R.string.history_open_live),
                        onClick = onOpenLiveQueue
                    )
                    Spacer(Modifier.height(Spacing.md))
                } else if (state.canReview) {
                    PrimaryButton(
                        text = if (state.review == null) {
                            stringResource(R.string.history_rate_visit)
                        } else {
                            stringResource(R.string.history_edit_review)
                        },
                        onClick = { onRate(entry.id) },
                        icon = Icons.Filled.RateReview
                    )
                    Spacer(Modifier.height(Spacing.md))
                }
                SecondaryButton(
                    text = stringResource(R.string.history_view_restaurant),
                    onClick = { onViewRestaurant(entry.restaurantId) },
                    icon = Icons.Filled.Storefront
                )
                Spacer(Modifier.height(Spacing.xxxl))
            }
        }
    }
}

private data class TimelineStep(val label: String, val at: Instant, val color: Color)

@Composable
private fun Timeline(entry: QueueEntry) {
    val colors = LocalStatusColors.current
    val steps = buildList {
        add(TimelineStep(stringResource(R.string.timeline_joined), entry.joinedAt, colors.waiting))
        entry.calledAt?.let { add(TimelineStep(stringResource(R.string.timeline_called), it, colors.called)) }
        entry.checkedInAt?.let {
            add(TimelineStep(stringResource(R.string.timeline_checked_in), it, colors.checkedIn))
        }
        entry.tableReadyAt?.let {
            add(TimelineStep(stringResource(R.string.timeline_table_ready), it, colors.tableReady))
        }
        entry.completedAt?.let {
            add(TimelineStep(stringResource(R.string.timeline_completed), it, colors.completed))
        }
        entry.cancelledAt?.let {
            add(TimelineStep(stringResource(R.string.timeline_cancelled), it, colors.cancelled))
        }
        entry.expiredAt?.let {
            add(TimelineStep(stringResource(R.string.timeline_expired), it, colors.expired))
        }
    }.sortedBy { it.at }

    Column {
        steps.forEachIndexed { index, step ->
            Row {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .background(step.color, CircleShape)
                    )
                    if (index < steps.lastIndex) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(36.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.padding(bottom = Spacing.sm)) {
                    Text(
                        text = step.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = dateTimeText(step.at),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewSection(review: Review?) {
    Text(
        text = stringResource(R.string.history_your_review),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(Spacing.md))
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            if (review == null) {
                Text(
                    text = stringResource(R.string.history_no_review),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                RatingStars(rating = review.rating.toDouble(), starSize = 20.dp)
                if (review.comment.isNotBlank()) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = review.comment,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = dateTimeText(review.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
