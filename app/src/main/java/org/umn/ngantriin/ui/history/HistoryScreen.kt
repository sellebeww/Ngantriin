package org.umn.ngantriin.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.umn.ngantriin.R
import org.umn.ngantriin.di.containerViewModel
import org.umn.ngantriin.domain.model.QueueHistoryItem
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.ui.components.EmptyState
import org.umn.ngantriin.ui.components.OfflineBanner
import org.umn.ngantriin.ui.components.Perforation
import org.umn.ngantriin.ui.components.QueueStatusPill
import org.umn.ngantriin.ui.components.SkeletonBox
import org.umn.ngantriin.ui.components.dateTimeText
import org.umn.ngantriin.ui.search.ChoiceChip
import org.umn.ngantriin.ui.theme.Spacing

@Composable
fun HistoryScreen(
    onFindRestaurants: () -> Unit,
    onRate: (String) -> Unit,
    onOpenVisit: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = containerViewModel { container ->
        HistoryViewModel(
            authRepository = container.authRepository,
            queueRepository = container.queueRepository,
            reviewRepository = container.reviewRepository,
            connectivityObserver = container.connectivityObserver
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize()) {
        if (state.isOffline) {
            OfflineBanner(message = stringResource(R.string.history_offline))
        }

        IconButton(onClick = onBack, modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xs)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
        }
        Text(
            text = stringResource(R.string.history_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(
                start = Spacing.screenGutter,
                end = Spacing.screenGutter,
                bottom = Spacing.lg
            )
        )

        if (!state.isEmpty && !state.isLoading) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screenGutter),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                HistoryFilter.entries.forEach { option ->
                    ChoiceChip(
                        label = stringResource(option.labelRes()),
                        selected = option == state.filter,
                        onClick = { viewModel.onFilterChange(option) }
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Spacing.xxxl)
        ) {
            when {
                state.isLoading -> items(4) {
                    SkeletonBox(
                        Modifier
                            .padding(horizontal = Spacing.screenGutter, vertical = Spacing.sm)
                            .fillMaxWidth()
                            .height(80.dp)
                    )
                }

                state.isEmpty -> item {
                    EmptyState(
                        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                        title = stringResource(R.string.history_empty_title),
                        message = stringResource(R.string.history_empty_message),
                        actionLabel = stringResource(R.string.active_queue_find_restaurant),
                        onAction = onFindRestaurants
                    )
                }

                state.isFilterEmpty -> item {
                    Text(
                        text = stringResource(R.string.history_filter_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.screenGutter, vertical = Spacing.xxl)
                    )
                }

                else -> items(state.items, key = { it.entry.id }) { item ->
                    HistoryCard(
                        item = item,
                        onRate = { onRate(item.entry.id) },
                        onClick = { onOpenVisit(item.entry.id) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

/**
 * One past visit as a torn-off stub: the number it was printed with on the
 * left of a tear line, the visit on the right. The number is what tells two
 * visits to the same place apart, so it leads.
 */
@Composable
private fun HistoryCard(
    item: QueueHistoryItem,
    onRate: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val entry = item.entry
    // Only a completed visit can be reviewed, and only once.
    val canRate = entry.status == QueueStatus.COMPLETED && !item.hasReview
    val finished = entry.status == QueueStatus.COMPLETED
    val rule = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick)
            .drawBehind {
                drawLine(rule, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
            .padding(start = Spacing.screenGutter, end = Spacing.sm, top = Spacing.md, bottom = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = entry.queueNumber,
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = MaterialTheme.typography.displayMedium.fontFamily,
            // A visit that did not happen is printed fainter.
            color = if (finished) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            modifier = Modifier.widthIn(min = 72.dp)
        )
        Perforation(
            color = MaterialTheme.colorScheme.outline,
            vertical = true,
            modifier = Modifier
                .width(Spacing.xl)
                .fillMaxHeight()
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = item.restaurantName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = dateTimeText(entry.joinedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.xs))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                QueueStatusPill(status = entry.status, compact = true)
                Text(
                    text = entry.waitedMinutes
                        ?.let { stringResource(R.string.history_waited_minutes, it) }
                        ?: stringResource(R.string.history_wait_not_recorded),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        when {
            canRate -> TextButton(onClick = onRate) {
                Text(stringResource(R.string.history_rate_visit), style = MaterialTheme.typography.labelMedium)
            }

            item.hasReview -> Text(
                text = stringResource(R.string.history_reviewed),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md)
            )
        }
    }
}

private fun HistoryFilter.labelRes(): Int = when (this) {
    HistoryFilter.ALL -> R.string.history_filter_all
    HistoryFilter.COMPLETED -> R.string.history_filter_completed
    HistoryFilter.ENDED -> R.string.history_filter_ended
}
