package org.umn.ngantriin.ui.home

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.umn.ngantriin.R
import org.umn.ngantriin.di.containerViewModel
import org.umn.ngantriin.ui.components.ActiveQueueBanner
import org.umn.ngantriin.ui.components.EmptyState
import org.umn.ngantriin.ui.components.LocationRationaleDialog
import org.umn.ngantriin.ui.components.OfflineBanner
import org.umn.ngantriin.ui.components.RestaurantCard
import org.umn.ngantriin.ui.components.RestaurantCardCompact
import org.umn.ngantriin.ui.components.RestaurantCardSkeleton
import org.umn.ngantriin.ui.components.SectionHeader
import org.umn.ngantriin.ui.components.SkeletonBox
import org.umn.ngantriin.ui.components.greetingText
import org.umn.ngantriin.ui.components.rememberLocationPermissionState
import org.umn.ngantriin.ui.theme.Spacing

/** Section 8. */
@Composable
fun HomeScreen(
    onRestaurantClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onActiveQueueClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = containerViewModel { container ->
        HomeViewModel(
            authRepository = container.authRepository,
            restaurantRepository = container.restaurantRepository,
            queueRepository = container.queueRepository,
            notificationRepository = container.notificationRepository,
            locationTracker = container.locationTracker,
            connectivityObserver = container.connectivityObserver
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
                        horizontal = Spacing.screenGutter,
                        vertical = Spacing.lg
                    )
                )
            }

            state.activeQueue?.let { queue ->
                item {
                    ActiveQueueBanner(
                        queue = queue,
                        onClick = onActiveQueueClick,
                        modifier = Modifier.padding(
                            horizontal = Spacing.screenGutter,
                            vertical = Spacing.sm
                        )
                    )
                    Spacer(Modifier.height(Spacing.sm))
                }
            }

            if (state.isLoading) {
                item { NearYouSkeleton() }
                items(3) { index ->
                    RestaurantCardSkeleton(
                        modifier = Modifier.padding(
                            horizontal = Spacing.screenGutter,
                            vertical = Spacing.sm
                        )
                    )
                    if (index == 2) Spacer(Modifier.height(Spacing.lg))
                }
            } else if (state.isEmpty) {
                item {
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
            } else {
                item {
                    SectionHeader(
                        title = stringResource(R.string.home_near_you),
                        subtitle = if (state.hasLocation) {
                            stringResource(R.string.home_near_you_subtitle_located)
                        } else {
                            stringResource(R.string.home_near_you_subtitle_default)
                        },
                        modifier = Modifier.padding(horizontal = Spacing.screenGutter)
                    )
                    Spacer(Modifier.height(Spacing.md))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Spacing.screenGutter),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        items(state.nearby, key = { it.id }) { listing ->
                            RestaurantCardCompact(
                                listing = listing,
                                onClick = { onRestaurantClick(listing.id) }
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.xxl))
                    SectionHeader(
                        title = stringResource(R.string.home_all_restaurants),
                        subtitle = stringResource(R.string.home_all_restaurants_subtitle),
                        actionLabel = stringResource(R.string.home_see_all),
                        onAction = onSearchClick,
                        modifier = Modifier.padding(horizontal = Spacing.screenGutter)
                    )
                    Spacer(Modifier.height(Spacing.md))
                }

                items(state.all, key = { it.id }) { listing ->
                    RestaurantCard(
                        listing = listing,
                        onClick = { onRestaurantClick(listing.id) },
                        modifier = Modifier.padding(
                            horizontal = Spacing.screenGutter,
                            vertical = Spacing.sm
                        )
                    )
                }
            }
        }
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
            .padding(
                start = Spacing.screenGutter,
                end = Spacing.sm,
                top = Spacing.lg
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = if (userName.isBlank()) greeting else "$greeting, $userName",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(Spacing.xs))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(enabled = !locationGranted, onClick = onEnableLocation)
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
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(onClick = onNotificationsClick) {
            BadgedBox(
                badge = {
                    if (unreadCount > 0) {
                        Badge {
                        Text(if (unreadCount > 9) stringResource(R.string.badge_overflow) else "$unreadCount")
                    }
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = stringResource(R.string.cd_notifications)
                )
            }
        }
    }
}

@Composable
private fun HomeSearchBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.lg),
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

@Composable
private fun NearYouSkeleton() {
    Column(Modifier.padding(horizontal = Spacing.screenGutter)) {
        SkeletonBox(Modifier.width(140.dp).height(22.dp))
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            repeat(2) {
                Box(Modifier.width(244.dp)) {
                    SkeletonBox(
                        Modifier
                            .fillMaxWidth()
                            .height(212.dp)
                            .background(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.shapes.large
                            )
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.xxl))
        SkeletonBox(Modifier.width(170.dp).height(22.dp))
        Spacer(Modifier.height(Spacing.md))
    }
}
