package org.umn.ngantriin.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.umn.ngantriin.R
import org.umn.ngantriin.di.containerViewModel
import org.umn.ngantriin.ui.components.EmptyState
import org.umn.ngantriin.ui.components.OfflineBanner
import org.umn.ngantriin.ui.components.RestaurantCard
import org.umn.ngantriin.ui.components.RestaurantCardSkeleton
import org.umn.ngantriin.ui.theme.Spacing

@Composable
fun SearchScreen(
    onRestaurantClick: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = containerViewModel { container ->
        SearchViewModel(
            restaurantRepository = container.restaurantRepository,
            locationTracker = container.locationTracker,
            connectivityObserver = container.connectivityObserver,
            preferences = container.userPreferencesRepository
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val queryText by viewModel.queryText.collectAsStateWithLifecycle()
    var filterSheetVisible by remember { mutableStateOf(false) }

    if (filterSheetVisible) {
        FilterSheet(
            filters = state.filters,
            categories = state.categories,
            onApply = viewModel::onFiltersChange,
            onClear = viewModel::clearFilters,
            onDismiss = { filterSheetVisible = false }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (state.isOffline) {
            OfflineBanner(message = stringResource(R.string.search_offline))
        }

        IconButton(onClick = onBack, modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xs)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
        }
        Column(Modifier.padding(horizontal = Spacing.screenGutter)) {
            Text(stringResource(R.string.search_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                stringResource(R.string.search_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = Spacing.screenGutter,
                    end = Spacing.md,
                    top = Spacing.lg,
                    bottom = Spacing.sm
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = queryText,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text(stringResource(R.string.home_search_placeholder)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (queryText.isNotEmpty()) {
                        IconButton(onClick = viewModel::clearQuery) {
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
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = { filterSheetVisible = true },
                modifier = Modifier.padding(start = Spacing.sm)
                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium)
            ) {
                BadgedBox(
                    badge = {
                        if (state.activeFilterCount > 0) {
                            Badge { Text("${state.activeFilterCount}") }
                        }
                    }
                ) {
                    Icon(Icons.Filled.FilterList, contentDescription = stringResource(R.string.cd_filters))
                }
            }
        }

        SortRow(
            selected = state.filters.sort,
            onSelect = viewModel::onSortChange,
            favoritesOnly = state.favoritesOnly,
            onFavoritesOnlyChange = viewModel::onFavoritesOnlyChange
        )

        Spacer(Modifier.height(Spacing.md))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenGutter, vertical = Spacing.xs)
        ) {
            Text(
                text = when {
                    state.isLoading -> stringResource(R.string.search_searching)
                    else -> pluralStringResource(
                        R.plurals.search_results_count,
                        state.results.size,
                        state.results.size
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (!state.hasLocation && state.filters.sort.name == "NEAREST") {
                Text(
                    text = stringResource(R.string.search_location_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

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
                    RestaurantCardSkeleton(Modifier.padding(vertical = Spacing.sm))
                }

                state.isEmpty -> item {
                    EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = stringResource(R.string.search_empty_title),
                        message = if (state.activeFilterCount > 0) {
                            stringResource(
                                if (state.favoritesOnly && state.filters.activeFilterCount == 0) {
                                    R.string.search_empty_favorites
                                } else {
                                    R.string.search_empty_with_filters
                                }
                            )
                        } else {
                            stringResource(R.string.search_empty_no_filters)
                        },
                        actionLabel = stringResource(R.string.search_clear_filters)
                            .takeIf { state.activeFilterCount > 0 },
                        onAction = viewModel::clearFilters
                    )
                }

                else -> items(state.results, key = { it.id }) { listing ->
                    RestaurantCard(
                        listing = listing,
                        onClick = { onRestaurantClick(listing.id) },
                        modifier = Modifier.animateItem().padding(vertical = Spacing.sm),
                        isFavorite = listing.id in state.favoriteIds,
                        onToggleFavorite = { viewModel.toggleFavorite(listing.id) }
                    )
                }
            }
        }
    }
}
