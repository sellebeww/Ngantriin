package org.umn.ngantriin.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.umn.ngantriin.core.ConnectivityObserver
import org.umn.ngantriin.core.Constants
import org.umn.ngantriin.domain.model.ActiveQueue
import org.umn.ngantriin.domain.model.GeoPoint
import org.umn.ngantriin.domain.model.RestaurantFilters
import org.umn.ngantriin.domain.model.RestaurantListing
import org.umn.ngantriin.domain.model.RestaurantSort
import org.umn.ngantriin.domain.repository.AuthRepository
import org.umn.ngantriin.domain.repository.NotificationRepository
import org.umn.ngantriin.domain.repository.QueueRepository
import org.umn.ngantriin.domain.repository.RestaurantRepository
import org.umn.ngantriin.domain.repository.SessionState
import org.umn.ngantriin.location.LocationTracker
import java.time.LocalTime

data class HomeUiState(
    /** Hour of day (0-23); the screen picks the localized greeting from it. */
    val greetingHour: Int = 12,
    val userName: String = "",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val nearby: List<RestaurantListing> = emptyList(),
    val all: List<RestaurantListing> = emptyList(),
    val activeQueue: ActiveQueue? = null,
    val unreadNotifications: Int = 0,
    val isOffline: Boolean = false,
    val hasLocation: Boolean = false,
    val locationGranted: Boolean = false
) {
    val isEmpty: Boolean get() = !isLoading && all.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    authRepository: AuthRepository,
    private val restaurantRepository: RestaurantRepository,
    queueRepository: QueueRepository,
    notificationRepository: NotificationRepository,
    private val locationTracker: LocationTracker,
    connectivityObserver: ConnectivityObserver
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)

    /**
     * Flips once the first catalogue fetch has come back, successfully or
     * not. Until then an empty list means "still loading"; afterwards it
     * means "there is nothing here", and those need different screens.
     */
    private val firstLoadDone = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            restaurantRepository.refreshRestaurants()
            firstLoadDone.value = true
        }
    }

    private val session = authRepository.sessionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionState.Loading)

    private val listings = locationTracker.location.flatMapLatest { origin ->
        restaurantRepository.observeRestaurants(
            origin = origin,
            filters = RestaurantFilters(sort = RestaurantSort.NEAREST)
        )
    }

    private val activeQueue = session.flatMapLatest { state ->
        when (state) {
            is SessionState.SignedIn -> queueRepository.observeActiveQueue(state.user.id)
            else -> flowOf(null)
        }
    }

    private val unread = session.flatMapLatest { state ->
        when (state) {
            is SessionState.SignedIn -> notificationRepository.observeUnreadCount(state.user.id)
            else -> flowOf(0)
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        session,
        listings,
        activeQueue,
        unread,
        combine(
            locationTracker.location,
            locationTracker.permissionGranted,
            connectivityObserver.online,
            refreshing,
            firstLoadDone
        ) { location, granted, online, isRefreshing, loaded ->
            Environment(location, granted, online, isRefreshing, loaded)
        }
    ) { sessionState, restaurants, queue, unreadCount, environment ->
        HomeUiState(
            greetingHour = LocalTime.now().hour,
            userName = (sessionState as? SessionState.SignedIn)?.user?.displayName.orEmpty(),
            isLoading = restaurants.isEmpty() && !environment.firstLoadDone,
            isRefreshing = environment.isRefreshing,
            nearby = restaurants.nearby(environment.location),
            all = restaurants,
            activeQueue = queue,
            unreadNotifications = unreadCount,
            isOffline = !environment.online,
            hasLocation = environment.location != null,
            locationGranted = environment.granted
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private data class Environment(
        val location: GeoPoint?,
        val granted: Boolean,
        val online: Boolean,
        val isRefreshing: Boolean,
        val firstLoadDone: Boolean
    )

    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        locationTracker.refresh()
        viewModelScope.launch {
            restaurantRepository.refreshRestaurants()
            refreshing.update { false }
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        locationTracker.onPermissionResult(granted)
    }

    /**
     * "Near You" is the first thing on the screen, so it stays short and only
     * shows venues that really are nearby. Without a fix we fall back to the
     * top of the list rather than showing nothing.
     */
    private fun List<RestaurantListing>.nearby(origin: GeoPoint?): List<RestaurantListing> {
        if (origin == null) return take(NEARBY_LIMIT)
        return filter { (it.distanceMeters ?: Double.MAX_VALUE) <= Constants.NEARBY_RADIUS_METERS }
            .take(NEARBY_LIMIT)
            .ifEmpty { take(NEARBY_LIMIT) }
    }

    private companion object {
        const val NEARBY_LIMIT = 6
    }
}
