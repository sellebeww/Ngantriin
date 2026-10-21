package org.umn.ngantriin.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.umn.ngantriin.R
import org.umn.ngantriin.di.containerViewModel
import org.umn.ngantriin.domain.model.RestaurantListing
import org.umn.ngantriin.ui.components.EmptyState
import org.umn.ngantriin.ui.components.LocationRationaleDialog
import org.umn.ngantriin.ui.components.OfflineBanner
import org.umn.ngantriin.ui.components.RestaurantCard
import org.umn.ngantriin.ui.components.RestaurantCardSkeleton
import org.umn.ngantriin.ui.components.SectionHeader
import org.umn.ngantriin.ui.components.greetingText
import org.umn.ngantriin.ui.components.rememberLocationPermissionState
import org.umn.ngantriin.ui.theme.Spacing

/**
 * Explore. No promo hero: the first thing under the search field is the
 * list itself, each row already showing how long its wait is, so "where can
 * I eat soon?" is answered by scrolling, not tapping.
 */
@Composable
fun HomeScreen(
    onRestaurantClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = containerViewModel { container ->
        HomeViewModel(
            authRepository = container.authRepository,
            restaurantRepository = container.restaurantRepository,
            queueRepository = container.queueRepository,
            notificationRepository = container.notificationRepository,
            locationTracker = container.locationTracker,
            connectivityObserver = container.connectivityObserver,
            preferences = container.userPreferencesRepository
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionState = rememberLocationPermissionState(viewModel::onLocationPermissionResult)

    LocationRationaleDialog(permissionState)

    Column(modifier = modifier.fillMaxSize()) {
        if (state.isOffline) {
            OfflineBanner(message = stringResource(R.string.home_offline))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Spacing.xxxl)
        ) {
            item {
                HomeHeader(
                    greeting = greetingText(state.greetingHour),
                    userName = state.userName,
                    hasLocation = state.hasLocation,
                    locationGranted = state.locationGranted,
                    unreadCount = state.unreadNotifications,
                    onNotificationsClick = onNotificationsClick,
                    onEnableLocation = permissionState::request
                )
            }

            item {
                HomeSearchBar(
                    onClick = onSearchClick,
                    modifier = Modifier.padding(
                        start = Spacing.screenGutter,
                        end = Spacing.screenGutter,
                        top = Spacing.md,
                        bottom = Spacing.xl
                    )
                )
            }

            when {
                state.isLoading -> items(5) { RestaurantCardSkeleton() }

                state.isEmpty -> item {
                    EmptyState(
                        icon = Icons.Outlined.Storefront,
                        title = stringResource(R.string.home_empty_title),
                        message = if (state.isOffline) {
                            stringResource(R.string.home_empty_offline)
                        } else {
                            stringResource(R.string.home_empty_no_results)
                        },
                        actionLabel = stringResource(R.string.home_refresh),
                        onAction = viewModel::refresh
                    )
                }

                else -> {
                    if (state.favorites.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = stringResource(R.string.home_favorites),
                                subtitle = stringResource(R.string.home_favorites_subtitle),
                                modifier = Modifier.padding(horizontal = Spacing.screenGutter)
                            )
                        }
                        restaurantRows(
                            listings = state.favorites,
                            keyPrefix = "fav-",
                            favoriteIds = state.favoriteIds,
                            onClick = onRestaurantClick,
                            onToggleFavorite = viewModel::toggleFavorite
                        )
                        item { Spacer(Modifier.height(Spacing.xxl)) }
                    }

                    item {
                        // Nearest first once we know where you are; otherwise
                        // the whole list, with location offered in the header.
                        SectionHeader(
                            title = if (state.hasLocation) {
                                stringResource(R.string.home_near_you)
                            } else {
                                stringResource(R.string.home_all_restaurants)
                            },
                            subtitle = if (state.hasLocation) {
                                stringResource(R.string.home_near_you_subtitle_located)
                            } else {
                                stringResource(R.string.home_all_restaurants_subtitle)
                            },
                            actionLabel = stringResource(R.string.home_see_all),
                            onAction = onSearchClick,
                            modifier = Modifier.padding(horizontal = Spacing.screenGutter)
                        )
                    }
                    restaurantRows(
                        listings = state.all,
                        keyPrefix = "",
                        favoriteIds = state.favoriteIds,
                        onClick = onRestaurantClick,
                        onToggleFavorite = viewModel::toggleFavorite
                    )
                }
            }
        }
    }
}

private fun LazyListScope.restaurantRows(
    listings: List<RestaurantListing>,
    keyPrefix: String,
    favoriteIds: Set<String>,
    onClick: (String) -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    items(listings, key = { keyPrefix + it.id }) { listing ->
        RestaurantCard(
            listing = listing,
            onClick = { onClick(listing.id) },
            modifier = Modifier.animateItem(),
            isFavorite = listing.id in favoriteIds,
            onToggleFavorite = { onToggleFavorite(listing.id) }
        )
    }
}

@Composable
private fun HomeHeader(
    greeting: String,
    userName: String,
    hasLocation: Boolean,
    locationGranted: Boolean,
    unreadCount: Int,
    onNotificationsClick: () -> Unit,
    onEnableLocation: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.screenGutter, end = Spacing.sm, top = Spacing.xl),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = if (userName.isBlank()) greeting else "$greeting,\n$userName",
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() }
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(enabled = !locationGranted, role = Role.Button, onClick = onEnableLocation)
                    .heightIn(min = Spacing.touchTarget)
            ) {
                Icon(
                    imageVector = Icons.Filled.MyLocation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = when {
                        hasLocation -> stringResource(R.string.home_location_known)
                        locationGranted -> stringResource(R.string.home_location_finding)
                        else -> stringResource(R.string.home_location_off)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    // Only the "turn it on" state is an action, so only it is accent.
                    color = if (locationGranted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
        }

        IconButton(onClick = onNotificationsClick) {
            BadgedBox(
                badge = {
                    if (unreadCount > 0) {
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text(if (unreadCount > 9) stringResource(R.string.badge_overflow) else "$unreadCount")
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = stringResource(R.string.cd_notifications)
                )
            }
        }
    }
}

@Composable
private fun HomeSearchBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 52.dp)
                .padding(horizontal = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = stringResource(R.string.home_search_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
