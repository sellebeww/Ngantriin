package org.umn.ngantriin.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import org.umn.ngantriin.ui.components.QueueStatusPill
import org.umn.ngantriin.ui.components.RestaurantImage
import org.umn.ngantriin.ui.components.SkeletonBox
import org.umn.ngantriin.ui.components.dateTimeText
import org.umn.ngantriin.ui.theme.Spacing

/** Section 19. */
@Composable
fun HistoryScreen(
    onFindRestaurants: () -> Unit,
    onRate: (String) -> Unit,
    onRestaurantClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = containerViewModel { container ->
        HistoryViewModel(
            authRepository = container.authRepository,
            queueRepository = container.queueRepository,
            connectivityObserver = container.connectivityObserver
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize()) {
        if (state.isOffline) {
            OfflineBanner(message = stringResource(R.string.history_offline))
        }

        Text(
            text = stringResource(R.string.history_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(
                horizontal = Spacing.screenGutter,
                vertical = Spacing.lg
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.screenGutter,
                end = Spacing.screenGutter,
                bottom = Spacing.xxxl
            )
        ) {
            when {
                state.isLoading -> items(4) {
                    SkeletonBox(
                        Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .padding(vertical = Spacing.sm)
                    )
                }

                state.isEmpty -> item {
                    EmptyState(
                        icon = Icons.Outlined.ReceiptLong,
                        title = stringResource(R.string.history_empty_title),
                        message = stringResource(R.string.history_empty_message),
                        actionLabel = stringResource(R.string.active_queue_find_restaurant),
                        onAction = onFindRestaurants
                    )
                }

                else -> items(state.items, key = { it.entry.id }) { item ->
                    HistoryCard(
                        item = item,
                        onRate = { onRate(item.entry.id) },
                        onClick = { onRestaurantClick(item.entry.restaurantId) },
                        modifier = Modifier.padding(vertical = Spacing.sm)
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
    item: QueueHistoryItem,
    onRate: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val entry = item.entry
    // Section 20: only a completed visit can be reviewed, and only once.
    val canRate = entry.status == QueueStatus.COMPLETED && !item.hasReview

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.clickable(onClick = onClick).padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RestaurantImage(
                    imageUrl = item.restaurantImageUrl,
                    contentDescription = stringResource(
                        R.string.cd_restaurant_photo,
                        item.restaurantName
                    ),
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                )

                Spacer(Modifier.width(Spacing.lg))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.restaurantName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        text = stringResource(
                            R.string.history_number_and_date,
                            entry.queueNumber,
                            dateTimeText(entry.joinedAt)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                QueueStatusPill(status = entry.status, compact = true)
            }

            Spacer(Modifier.height(Spacing.md))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = entry.waitedMinutes
                        ?.let { stringResource(R.string.history_waited_minutes, it) }
                        ?: stringResource(R.string.history_wait_not_recorded),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                when {
                    canRate -> TextButton(onClick = onRate) {
                        Text(stringResource(R.string.history_rate_visit), style = MaterialTheme.typography.labelMedium)
                    }

                    item.hasReview -> Text(
                        text = stringResource(R.string.history_reviewed),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}
