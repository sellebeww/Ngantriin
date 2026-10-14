package org.umn.ngantriin.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.umn.ngantriin.R
import org.umn.ngantriin.core.Formatters
import org.umn.ngantriin.domain.model.RestaurantListing
import org.umn.ngantriin.ui.theme.LocalStatusColors
import org.umn.ngantriin.ui.theme.Spacing

/** A scannable restaurant row with queue information kept below the photo. */
@Composable
fun RestaurantCard(
    listing: RestaurantListing,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null
) {
    val restaurant = listing.restaurant
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                RestaurantImage(
                    imageUrl = restaurant.imageUrl,
                    contentDescription = stringResource(R.string.cd_restaurant_photo, restaurant.name),
                    modifier = Modifier.size(88.dp).clip(MaterialTheme.shapes.medium)
                )
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = restaurant.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (onToggleFavorite != null) {
                            FavoriteButton(
                                isFavorite = isFavorite,
                                onToggle = onToggleFavorite,
                                overPhoto = false
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = restaurant.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        RatingLabel(restaurant.rating)
                        if (listing.distanceMeters != null) {
                            Text(
                                text = "· " + distanceText(listing.distanceMeters),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        OpenClosedBadge(restaurant.isAcceptingQueue())
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))
            QueueSummaryRow(listing, compact = true)
        }
    }
}

/** The compact variant used by the "Near You" carousel. */
@Composable
fun RestaurantCardCompact(
    listing: RestaurantListing,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null
) {
    val restaurant = listing.restaurant

    Card(
        onClick = onClick,
        modifier = modifier.width(252.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            Box {
                RestaurantImage(
                    imageUrl = restaurant.imageUrl,
                    contentDescription = stringResource(R.string.cd_restaurant_photo, restaurant.name),
                    modifier = Modifier.fillMaxWidth().height(148.dp)
                )
                OpenClosedBadge(
                    open = restaurant.isAcceptingQueue(),
                    modifier = Modifier.align(Alignment.TopStart).padding(Spacing.md)
                )
                if (onToggleFavorite != null) {
                    FavoriteButton(
                        isFavorite = isFavorite,
                        onToggle = onToggleFavorite,
                        modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.sm)
                    )
                }
            }
            Column(Modifier.padding(Spacing.lg)) {
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
                        text = listing.distanceMeters?.let { distanceText(it) } ?: restaurant.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
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
    // Seats open beats any queue stat — there's nothing to wait
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
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (highlight) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .background(container, MaterialTheme.shapes.small)
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
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
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.dining_bowl),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(Spacing.md),
                contentScale = ContentScale.Fit
            )
        }
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** The loading shape of [RestaurantCard]. */
@Composable
fun RestaurantCardSkeleton(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                SkeletonBox(Modifier.size(88.dp), shape = MaterialTheme.shapes.medium)
                Column(Modifier.weight(1f).padding(top = Spacing.sm)) {
                    SkeletonBox(Modifier.fillMaxWidth(0.85f).height(18.dp))
                    Spacer(Modifier.height(Spacing.sm))
                    SkeletonBox(Modifier.fillMaxWidth(0.55f).height(12.dp))
                    Spacer(Modifier.height(Spacing.md))
                    SkeletonBox(Modifier.fillMaxWidth(0.7f).height(14.dp))
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SkeletonBox(Modifier.weight(1f).height(34.dp))
                SkeletonBox(Modifier.weight(1f).height(34.dp))
            }
        }
    }
}
