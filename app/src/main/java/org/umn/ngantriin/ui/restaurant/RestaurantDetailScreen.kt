package org.umn.ngantriin.ui.restaurant

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.umn.ngantriin.R
import org.umn.ngantriin.core.AppError
import org.umn.ngantriin.core.Constants
import org.umn.ngantriin.core.Formatters
import org.umn.ngantriin.di.containerViewModel
import org.umn.ngantriin.domain.model.Restaurant
import org.umn.ngantriin.domain.model.Review
import org.umn.ngantriin.ui.components.FullScreenLoader
import org.umn.ngantriin.ui.components.InfoRow
import org.umn.ngantriin.ui.components.OpenClosedBadge
import org.umn.ngantriin.ui.components.PrimaryButton
import org.umn.ngantriin.ui.components.RatingLabel
import org.umn.ngantriin.ui.components.RatingStars
import org.umn.ngantriin.ui.components.RestaurantImage
import org.umn.ngantriin.ui.components.SecondaryButton
import org.umn.ngantriin.ui.components.SectionHeader
import org.umn.ngantriin.ui.components.distanceText
import org.umn.ngantriin.ui.components.message
import org.umn.ngantriin.ui.components.relativeText
import org.umn.ngantriin.ui.components.waitTimeText
import org.umn.ngantriin.ui.theme.LocalStatusColors
import org.umn.ngantriin.ui.theme.Spacing

/** Sections 10 and 11. */
@Composable
fun RestaurantDetailScreen(
    restaurantId: String,
    onBack: () -> Unit,
    onJoined: (String) -> Unit,
    onViewActiveQueue: () -> Unit,
    onError: (AppError) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = containerViewModel(key = "restaurant-$restaurantId") { container ->
        RestaurantDetailViewModel(
            restaurantId = restaurantId,
            restaurantRepository = container.restaurantRepository,
            queueRepository = container.queueRepository,
            reviewRepository = container.reviewRepository,
            authRepository = container.authRepository,
            locationTracker = container.locationTracker
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is RestaurantDetailEvent.Joined -> onJoined(event.queueId)
                is RestaurantDetailEvent.Failed -> onError(event.error)
            }
        }
    }

    val restaurant = state.restaurant
    if (restaurant == null) {
        if (state.isLoading) FullScreenLoader(modifier.fillMaxSize()) else NotFound(onBack)
        return
    }

    val context = LocalContext.current

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            item { DetailHeader(restaurant, state.distanceMeters, onBack) }

            item {
                Column(Modifier.padding(horizontal = Spacing.screenGutter)) {
                    Spacer(Modifier.height(Spacing.xl))
                    QueueStatusCard(state)
                    Spacer(Modifier.height(Spacing.xl))

                    SectionHeader(title = stringResource(R.string.restaurant_about))
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = restaurant.description.ifBlank {
                            stringResource(
                                R.string.restaurant_description_fallback,
                                restaurant.category,
                                restaurant.address
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(Spacing.lg))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    InfoRow(
                        icon = Icons.Filled.Restaurant,
                        label = stringResource(R.string.restaurant_cuisine),
                        value = restaurant.category
                    )
                    InfoRow(
                        icon = Icons.Filled.Schedule,
                        label = stringResource(R.string.restaurant_opening_hours),
                        value = restaurant.operatingHours
                    )
                    InfoRow(
                        icon = Icons.Filled.AccessTime,
                        label = stringResource(R.string.restaurant_avg_service_time),
                        value = stringResource(
                            R.string.restaurant_avg_service_value,
                            restaurant.averageServiceMinutes
                        )
                    )
                    InfoRow(
                        icon = Icons.Filled.Place,
                        label = stringResource(R.string.restaurant_address),
                        value = restaurant.address,
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            val uri = Uri.parse(
                                "https://www.google.com/maps/search/?api=1&query=" +
                                    "${restaurant.latitude},${restaurant.longitude}"
                            )
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        }
                    )
                    if (state.distanceMeters != null) {
                        InfoRow(
                            icon = Icons.Filled.NearMe,
                            label = stringResource(R.string.restaurant_distance),
                            value = distanceText(state.distanceMeters)
                        )
                    }

                    Spacer(Modifier.height(Spacing.xl))
                    SectionHeader(
                        title = stringResource(R.string.restaurant_reviews),
                        subtitle = if (restaurant.ratingCount > 0) {
                            stringResource(
                                R.string.restaurant_reviews_subtitle,
                                Formatters.rating(restaurant.rating),
                                restaurant.ratingCount
                            )
                        } else {
                            stringResource(R.string.restaurant_no_reviews)
                        }
                    )
                    Spacer(Modifier.height(Spacing.sm))
                }
            }

            items(state.reviews.take(5), key = { it.id }) { review ->
                ReviewRow(
                    review = review,
                    modifier = Modifier.padding(
                        horizontal = Spacing.screenGutter,
                        vertical = Spacing.sm
                    )
                )
            }
        }

        JoinQueueBar(
            state = state,
            onJoin = viewModel::joinQueue,
            onViewActiveQueue = onViewActiveQueue,
            onPartySizeChange = viewModel::onPartySizeChange,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun DetailHeader(
    restaurant: Restaurant,
    distanceMeters: Double?,
    onBack: () -> Unit
) {
    Box {
        RestaurantImage(
            imageUrl = restaurant.imageUrl,
            contentDescription = stringResource(R.string.cd_restaurant_photo, restaurant.name),
            modifier = Modifier.fillMaxWidth().height(250.dp)
        )

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(Spacing.sm)
                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(50))
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                tint = Color.White
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(top = 200.dp),
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.padding(Spacing.screenGutter)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = restaurant.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    OpenClosedBadge(open = restaurant.isAcceptingQueue())
                }
                Spacer(Modifier.height(Spacing.sm))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    RatingLabel(restaurant.rating)
                    Text(
                        text = restaurant.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (distanceMeters != null) {
                        Text(
                            text = distanceText(distanceMeters),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** Section 10's queue status card. */
@Composable
private fun QueueStatusCard(state: RestaurantDetailUiState) {
    val restaurant = state.restaurant ?: return
    val statusColors = LocalStatusColors.current
    val fill = if (restaurant.queueCapacity > 0) {
        (state.waitingCount.toFloat() / restaurant.queueCapacity).coerceIn(0f, 1f)
    } else {
        0f
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(Spacing.xl)) {
            Text(
                text = stringResource(R.string.restaurant_current_queue),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.md))

            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Groups,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            text = "${state.waitingCount}",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = stringResource(R.string.restaurant_groups_waiting),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = waitTimeText(state.estimatedWaitMinutes),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.restaurant_estimated_wait),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            LinearProgressIndicator(
                progress = { fill },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = when {
                    fill > 0.85f -> statusColors.cancelled
                    fill > 0.5f -> statusColors.almostThere
                    else -> statusColors.called
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                gapSize = 0.dp,
                drawStopIndicator = {}
            )

            Spacer(Modifier.height(Spacing.sm))

            Row(Modifier.fillMaxWidth()) {
                Text(
                    text = state.stats?.currentServingNumber?.let {
                        stringResource(R.string.restaurant_now_serving, it)
                    } ?: stringResource(R.string.restaurant_nobody_served),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.restaurant_capacity, restaurant.queueCapacity),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** The sticky call to action, plus the party-size stepper. */
@Composable
private fun JoinQueueBar(
    state: RestaurantDetailUiState,
    onJoin: () -> Unit,
    onViewActiveQueue: () -> Unit,
    onPartySizeChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp
    ) {
        Column(Modifier.padding(Spacing.screenGutter)) {
            when {
                state.hasQueueHere -> {
                    Text(
                        text = stringResource(
                            R.string.restaurant_already_in_queue_as,
                            state.activeQueue?.queueNumber.orEmpty()
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.md))
                    PrimaryButton(
                        text = stringResource(R.string.action_view_my_queue),
                        onClick = onViewActiveQueue
                    )
                }

                state.hasQueueElsewhere -> {
                    Text(
                        text = AppError.AlreadyInQueue.message() +
                            stringResource(R.string.restaurant_leave_before_joining),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.md))
                    SecondaryButton(
                        text = stringResource(R.string.action_view_my_queue),
                        onClick = onViewActiveQueue
                    )
                }

                // Section 44: seats are open right now — a walk-in, not a
                // queue. No button here on purpose; there's nothing to tap.
                state.hasAvailableSeats && state.isOpen -> {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EventSeat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.seats_available_title,
                                    state.restaurant?.availableSeats ?: 0,
                                    state.restaurant?.availableSeats ?: 0
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.seats_available_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                !state.isOpen -> {
                    Text(
                        text = AppError.RestaurantClosed.message(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.md))
                    // Disabled on purpose, but labelled with the reason: a
                    // greyed-out "Join Queue" reads as a broken button.
                    PrimaryButton(
                        text = stringResource(R.string.restaurant_closed_button),
                        onClick = {},
                        enabled = false
                    )
                }

                state.isQueueFull -> {
                    Text(
                        text = AppError.QueueFull.message(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(Spacing.md))
                    PrimaryButton(
                        text = stringResource(R.string.restaurant_queue_full_button),
                        onClick = {},
                        enabled = false
                    )
                }

                else -> {
                    PartySizeStepper(
                        partySize = state.partySize,
                        onChange = onPartySizeChange
                    )
                    Spacer(Modifier.height(Spacing.md))
                    PrimaryButton(
                        text = stringResource(R.string.restaurant_join_queue),
                        onClick = onJoin,
                        loading = state.isJoining,
                        enabled = state.canJoin
                    )
                }
            }
        }
    }
}

@Composable
private fun PartySizeStepper(partySize: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.restaurant_party_size),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = { onChange(partySize - 1) },
            enabled = partySize > 1
        ) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.cd_fewer_people))
        }
        Text(
            text = "$partySize",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.Center
        )
        IconButton(
            onClick = { onChange(partySize + 1) },
            enabled = partySize < Constants.MAX_PARTY_SIZE
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_more_people))
        }
    }
}

@Composable
private fun ReviewRow(review: Review, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RatingStars(rating = review.rating.toDouble())
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = relativeText(review.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (review.comment.isNotBlank()) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = review.comment,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun NotFound(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.restaurant_not_found),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(Spacing.lg))
        SecondaryButton(text = stringResource(R.string.action_go_back), onClick = onBack)
    }
}
