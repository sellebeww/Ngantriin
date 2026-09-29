package org.umn.ngantriin.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.umn.ngantriin.R
import org.umn.ngantriin.core.Formatters
import org.umn.ngantriin.domain.model.RestaurantListing
import org.umn.ngantriin.ui.theme.LocalStatusColors
import org.umn.ngantriin.ui.theme.Spacing

/**
 * Section 8. The card answers the two questions that decide where someone
 * eats — how long is the line, and how far is it — before the name has
 * finished being read.
 */
@Composable
fun RestaurantCard(
    listing: RestaurantListing,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val restaurant = listing.restaurant
    val open = restaurant.isAcceptingQueue()

    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column {
            Box {
                RestaurantImage(
                    imageUrl = restaurant.imageUrl,
                    contentDescription = stringResource(R.string.cd_restaurant_photo, restaurant.name),
                    modifier = Modifier.fillMaxWidth().height(148.dp)
                )
                OpenClosedBadge(
                    open = open,
                    modifier = Modifier.align(Alignment.TopStart).padding(Spacing.md)
                )
                if (listing.distanceMeters != null) {
                    DistanceBadge(
                        distance = listing.distanceMeters,
                        modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.md)
                    )
                }
            }

            Column(Modifier.padding(Spacing.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = restaurant.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    RatingLabel(restaurant.rating)
                }

                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = restaurant.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(Spacing.md))
                QueueSummaryRow(listing)
            }
        }
    }
}

/** The compact variant used by the "Near You" carousel. */
@Composable
fun RestaurantCardCompact(
    listing: RestaurantListing,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val restaurant = listing.restaurant

    Card(
        modifier = modifier.width(244.dp).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column {
            Box {
                RestaurantImage(
                    imageUrl = restaurant.imageUrl,
                    contentDescription = stringResource(R.string.cd_restaurant_photo, restaurant.name),
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                )
                OpenClosedBadge(
                    open = restaurant.isAcceptingQueue(),
                    modifier = Modifier.align(Alignment.TopStart).padding(Spacing.sm)
                )
            }
            Column(Modifier.padding(Spacing.md)) {
                Text(
                    text = restaurant.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(Spacing.xxs))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    RatingLabel(restaurant.rating)
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = distanceText(listing.distanceMeters),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                QueueSummaryRow(listing, compact = true)
            }
        }
    }
}

@Composable
fun QueueSummaryRow(
    listing: RestaurantListing,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    // Section 44: seats open beats any queue stat — there's nothing to wait
    // for, so showing "0 waiting / no wait" here would just be noise.
    if (listing.restaurant.hasAvailableSeats) {
        QueueFact(
            icon = Icons.Filled.EventSeat,
            value = stringResource(R.string.seats_available_badge),
            label = stringResource(
                R.string.seats_available_count,
                listing.restaurant.availableSeats
            ),
            compact = compact,
            highlight = true,
            modifier = modifier.fillMaxWidth()
        )
        return
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        QueueFact(
            icon = Icons.Filled.Groups,
            value = groupsText(listing.waitingCount),
            label = stringResource(R.string.restaurant_card_waiting),
            compact = compact,
            modifier = Modifier.weight(1f)
        )
        QueueFact(
            icon = Icons.Filled.Schedule,
            value = waitTimeShortText(listing.estimatedWaitMinutes),
            label = stringResource(R.string.restaurant_card_est_wait),
            compact = compact,
            highlight = true,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QueueFact(
    icon: ImageVector,
    value: String,
    label: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    val container = if (highlight) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (highlight) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .background(container, MaterialTheme.shapes.small)
            .padding(horizontal = Spacing.sm, vertical = if (compact) Spacing.xs else Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(15.dp))
        Column {
            Text(value, style = MaterialTheme.typography.labelMedium, color = content)
            if (!compact) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.75f)
                )
            }
        }
    }
}

@Composable
fun RatingLabel(rating: Double, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = Formatters.rating(rating),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun OpenClosedBadge(open: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalStatusColors.current
    val container = if (open) colors.calledContainer else colors.cancelledContainer
    val content = if (open) colors.called else colors.cancelled

    Text(
        text = if (open) stringResource(R.string.restaurant_open) else stringResource(R.string.restaurant_closed),
        style = MaterialTheme.typography.labelSmall,
        color = content,
        modifier = modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
    )
}

@Composable
private fun DistanceBadge(distance: Double, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        Icon(
            Icons.Filled.NearMe,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = distanceText(distance),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}

/**
 * Remote photo with a branded placeholder. A missing image never leaves a
 * blank rectangle, which matters most in the loading and offline cases.
 */
@Composable
fun RestaurantImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (imageUrl.isNullOrBlank()) {
            ImageFallback(Modifier.fillMaxSize())
        } else {
            AsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        // Keeps white badge text legible over a bright photo.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.22f),
                        0.45f to Color.Transparent
                    )
                )
        )
    }
}

@Composable
private fun ImageFallback(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Outlined.Restaurant,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(32.dp)
        )
    }
}

/** Section 29: the loading shape of [RestaurantCard]. */
@Composable
fun RestaurantCardSkeleton(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column {
            SkeletonBox(
                modifier = Modifier.fillMaxWidth().aspectRatio(2.3f),
                shape = RoundedCornerShape(0.dp)
            )
            Column(Modifier.padding(Spacing.lg)) {
                SkeletonLine(width = 170.dp, height = 18.dp)
                Spacer(Modifier.height(Spacing.sm))
                SkeletonLine(width = 110.dp, height = 12.dp)
                Spacer(Modifier.height(Spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SkeletonBox(Modifier.weight(1f).height(40.dp))
                    SkeletonBox(Modifier.weight(1f).height(40.dp))
                }
            }
        }
    }
}
