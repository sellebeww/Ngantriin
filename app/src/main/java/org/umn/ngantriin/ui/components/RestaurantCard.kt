package org.umn.ngantriin.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.umn.ngantriin.R
import org.umn.ngantriin.core.Formatters
import org.umn.ngantriin.domain.model.RestaurantListing
import org.umn.ngantriin.ui.theme.LocalStatusColors
import org.umn.ngantriin.ui.theme.Spacing

private val Thumb = 64.dp

/**
 * One restaurant as a list row, not a card: name and facts on the left, and
 * on the right the one thing people choose by — how long the wait is — set
 * in the ticket face, like a slip clipped to the row.
 */
@Composable
fun RestaurantCard(
    listing: RestaurantListing,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null
) {
    val restaurant = listing.restaurant
    val rule = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 96.dp)
            .drawBehind {
                // Hairline under the text only, so rows read as one list.
                val x = (Thumb + Spacing.screenGutter + Spacing.md).toPx()
                drawLine(rule, Offset(x, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
            .padding(start = Spacing.screenGutter, end = Spacing.sm, top = Spacing.md, bottom = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RestaurantImage(
            imageUrl = restaurant.imageUrl,
            contentDescription = null,
            modifier = Modifier
                .size(Thumb)
                .clip(MaterialTheme.shapes.medium)
                .outlined(MaterialTheme.shapes.medium)
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = restaurant.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Spacing.xxs))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                RatingLabel(restaurant.rating)
                Text(
                    text = listOfNotNull(
                        restaurant.category,
                        listing.distanceMeters?.let { distanceText(it) }
                    ).joinToString(" · ", prefix = "· "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!restaurant.isAcceptingQueue()) {
                Spacer(Modifier.height(Spacing.xs))
                OpenClosedBadge(open = false)
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        WaitSlip(listing)
        if (onToggleFavorite != null) {
            FavoriteButton(isFavorite = isFavorite, onToggle = onToggleFavorite, overPhoto = false)
        }
    }
}

/**
 * The wait, as the row's loudest figure: "~6 min" in the ticket face with the
 * groups under it, or "Seats open" when there is nothing to queue for.
 */
@Composable
private fun WaitSlip(listing: RestaurantListing) {
    val restaurant = listing.restaurant
    val status = LocalStatusColors.current
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 56.dp)) {
        when {
            restaurant.hasAvailableSeats -> {
                Text(
                    text = stringResource(R.string.seats_available_badge),
                    style = MaterialTheme.typography.labelLarge,
                    color = status.almostThere,
                    textAlign = TextAlign.End
                )
                Text(
                    text = stringResource(R.string.seats_available_count, restaurant.availableSeats),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            restaurant.isAcceptingQueue() -> {
                Text(
                    text = waitTimeShortText(listing.estimatedWaitMinutes),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = MaterialTheme.typography.displayMedium.fontFamily,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.End
                )
                Text(
                    text = groupsText(listing.waitingCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Closed: the badge on the left already says so.
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
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = Formatters.rating(rating),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Open is saffron (go), closed is muted ink: information, not an alarm. */
@Composable
fun OpenClosedBadge(open: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalStatusColors.current
    val container = if (open) colors.almostThereContainer else colors.expiredContainer
    val content = if (open) colors.almostThere else colors.expired

    Text(
        text = if (open) stringResource(R.string.restaurant_open) else stringResource(R.string.restaurant_closed),
        style = MaterialTheme.typography.labelSmall,
        color = content,
        modifier = modifier
            .background(container, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
    )
}

/** A neutral hairline so photos never bleed into the page edge. */
fun Modifier.outlined(shape: androidx.compose.ui.graphics.Shape): Modifier =
    border(1.dp, Color.Black.copy(alpha = 0.08f), shape)

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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .padding(horizontal = Spacing.screenGutter, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SkeletonBox(Modifier.size(Thumb), shape = MaterialTheme.shapes.medium)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            SkeletonBox(Modifier.fillMaxWidth(0.7f).height(20.dp))
            Spacer(Modifier.height(Spacing.sm))
            SkeletonBox(Modifier.fillMaxWidth(0.5f).height(12.dp))
        }
        Spacer(Modifier.width(Spacing.md))
        Column(horizontalAlignment = Alignment.End) {
            SkeletonBox(Modifier.width(56.dp).height(24.dp))
            Spacer(Modifier.height(Spacing.xs))
            SkeletonBox(Modifier.width(40.dp).height(12.dp), shape = RoundedCornerShape(4.dp))
        }
    }
}
