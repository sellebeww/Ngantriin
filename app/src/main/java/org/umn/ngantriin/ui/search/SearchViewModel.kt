package org.umn.ngantriin.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.umn.ngantriin.core.ConnectivityObserver
import org.umn.ngantriin.domain.model.RestaurantFilters
import org.umn.ngantriin.domain.model.RestaurantListing
import org.umn.ngantriin.domain.model.RestaurantSort
import org.umn.ngantriin.domain.repository.RestaurantRepository
import org.umn.ngantriin.domain.repository.UserPreferencesRepository
import org.umn.ngantriin.location.LocationTracker

data class SearchUiState(
    val query: String = "",
    val filters: RestaurantFilters = RestaurantFilters(),
    val results: List<RestaurantListing> = emptyList(),
    val categories: List<String> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val favoritesOnly: Boolean = false,
    val isLoading: Boolean = true,
    val isOffline: Boolean = false,
    val hasLocation: Boolean = false
) {
    val isEmpty: Boolean get() = !isLoading && results.isEmpty()
    val activeFilterCount: Int get() = filters.activeFilterCount + if (favoritesOnly) 1 else 0
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(
    private val restaurantRepository: RestaurantRepository,
    private val locationTracker: LocationTracker,
    connectivityObserver: ConnectivityObserver,
    private val preferences: UserPreferencesRepository,
    initialQuery: String = ""
) : ViewModel() {

    /** Typed text, echoed straight back to the field so it never lags. */
    private val query = MutableStateFlow(initialQuery)
    val queryText: StateFlow<String> = query.asStateFlow()

    private val filters = MutableStateFlow(RestaurantFilters(query = initialQuery))
    private val firstLoadDone = MutableStateFlow(false)
    private val favoritesOnly = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            restaurantRepository.refreshRestaurants()
            firstLoadDone.value = true
        }
        // Debounced so a search does not re-run the filter pipeline on every
        // keystroke; the field itself stays immediate.
        viewModelScope.launch {
            query.debounce(220).distinctUntilChanged().collect { text ->
                filters.update { it.copy(query = text) }
            }
        }
    }

    private val results = combine(filters, locationTracker.location) { activeFilters, origin ->
        activeFilters to origin
    }.flatMapLatest { (activeFilters, origin) ->
        restaurantRepository.observeRestaurants(origin, activeFilters)
    }

    val uiState: StateFlow<SearchUiState> = combine(
        query,
        filters,
        results,
        combine(
            restaurantRepository.observeCategories(),
            preferences.favoriteRestaurantIds,
            favoritesOnly
        ) { categories, ids, only -> Triple(categories, ids, only) },
        combine(
            connectivityObserver.online,
            locationTracker.location.map { it != null },
            firstLoadDone
        ) { online, hasLocation, loaded -> Triple(online, hasLocation, loaded) }
    ) { text, activeFilters, listings, catalogue, environment ->
        val (categories, favoriteIds, onlyFavorites) = catalogue
        val (online, hasLocation, loaded) = environment
        SearchUiState(
            query = text,
            filters = activeFilters,
            results = if (onlyFavorites) listings.filter { it.id in favoriteIds } else listings,
            categories = categories,
            favoriteIds = favoriteIds,
            favoritesOnly = onlyFavorites,
            isLoading = listings.isEmpty() && !loaded,
            isOffline = !online,
            hasLocation = hasLocation
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun toggleFavorite(restaurantId: String) {
        viewModelScope.launch { preferences.toggleFavoriteRestaurant(restaurantId) }
    }

    fun onFavoritesOnlyChange(enabled: Boolean) {
        favoritesOnly.value = enabled
    }

    fun clearQuery() {
        query.value = ""
        filters.update { it.copy(query = "") }
    }

    fun onSortChange(sort: RestaurantSort) {
        filters.update { it.copy(sort = sort) }
        // Sorting by distance is meaningless without a fix, so ask for one.
        if (sort == RestaurantSort.NEAREST) locationTracker.refresh()
    }

    fun onFiltersChange(updated: RestaurantFilters) {
        filters.update { updated.copy(query = it.query) }
    }

    fun clearFilters() {
        favoritesOnly.value = false
        filters.update { RestaurantFilters(query = it.query, sort = it.sort) }
    }
}
