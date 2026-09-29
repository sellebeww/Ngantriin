package org.umn.ngantriin.ui.restaurant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.umn.ngantriin.core.AppError
import org.umn.ngantriin.core.Constants
import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.domain.model.ActiveQueue
import org.umn.ngantriin.domain.model.GeoPoint
import org.umn.ngantriin.domain.model.QueueStats
import org.umn.ngantriin.domain.model.Restaurant
import org.umn.ngantriin.domain.model.Review
import org.umn.ngantriin.domain.repository.AuthRepository
import org.umn.ngantriin.domain.repository.QueueRepository
import org.umn.ngantriin.domain.repository.RestaurantRepository
import org.umn.ngantriin.domain.repository.ReviewRepository
import org.umn.ngantriin.domain.repository.SessionState
import org.umn.ngantriin.location.LocationTracker

data class RestaurantDetailUiState(
    val isLoading: Boolean = true,
    val restaurant: Restaurant? = null,
    val stats: QueueStats? = null,
    val reviews: List<Review> = emptyList(),
    val distanceMeters: Double? = null,
    val activeQueue: ActiveQueue? = null,
    val partySize: Int = 1,
    val isJoining: Boolean = false
) {
    val waitingCount: Int get() = stats?.waitingCount ?: 0

    val estimatedWaitMinutes: Int
        get() = stats?.estimatedWaitMinutes
            ?: (waitingCount * (restaurant?.averageServiceMinutes ?: 3))

    /** True when the customer's live ticket is for this venue. */
    val hasQueueHere: Boolean
        get() = activeQueue != null && activeQueue.restaurant.id == restaurant?.id

    /** Section 28.3: one active queue, anywhere. */
    val hasQueueElsewhere: Boolean
        get() = activeQueue != null && activeQueue.restaurant.id != restaurant?.id

    val isOpen: Boolean get() = restaurant?.isAcceptingQueue() == true

    /** Section 44: seats are open right now, nothing to queue for yet. */
    val hasAvailableSeats: Boolean get() = restaurant?.hasAvailableSeats == true

    val isQueueFull: Boolean
        get() = restaurant != null && waitingCount >= restaurant.queueCapacity

    val canJoin: Boolean
        get() = restaurant != null && isOpen && !hasAvailableSeats && !isQueueFull &&
            activeQueue == null && !isJoining
}

sealed interface RestaurantDetailEvent {
    data class Joined(val queueId: String) : RestaurantDetailEvent
    data class Failed(val error: AppError) : RestaurantDetailEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class RestaurantDetailViewModel(
    private val restaurantId: String,
    private val restaurantRepository: RestaurantRepository,
    private val queueRepository: QueueRepository,
    reviewRepository: ReviewRepository,
    authRepository: AuthRepository,
    locationTracker: LocationTracker
) : ViewModel() {

    private val partySize = MutableStateFlow(1)
    private val joining = MutableStateFlow(false)
    private val loaded = MutableStateFlow(false)

    private val events = Channel<RestaurantDetailEvent>(Channel.BUFFERED)
    val eventFlow: Flow<RestaurantDetailEvent> = events.receiveAsFlow()

    private val activeQueue = authRepository.sessionState.flatMapLatest { state ->
        when (state) {
            is SessionState.SignedIn -> queueRepository.observeActiveQueue(state.user.id)
            else -> flowOf(null)
        }
    }

    init {
        viewModelScope.launch {
            restaurantRepository.refreshRestaurant(restaurantId)
            loaded.value = true
        }
    }

    val uiState: StateFlow<RestaurantDetailUiState> = combine(
        restaurantRepository.observeRestaurant(restaurantId),
        restaurantRepository.observeQueueStats(restaurantId),
        reviewRepository.observeReviews(restaurantId),
        activeQueue,
        combine(locationTracker.location, partySize, joining, loaded) { location, size, busy, done ->
            Environment(location, size, busy, done)
        }
    ) { restaurant, stats, reviews, queue, environment ->
        RestaurantDetailUiState(
            isLoading = restaurant == null && !environment.loaded,
            restaurant = restaurant,
            stats = stats,
            reviews = reviews,
            distanceMeters = if (restaurant != null && environment.location != null) {
                environment.location.distanceTo(restaurant.location)
            } else {
                null
            },
            activeQueue = queue,
            partySize = environment.partySize,
            isJoining = environment.joining
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        RestaurantDetailUiState()
    )

    private data class Environment(
        val location: GeoPoint?,
        val partySize: Int,
        val joining: Boolean,
        val loaded: Boolean
    )

    fun onPartySizeChange(size: Int) {
        partySize.value = size.coerceIn(1, Constants.MAX_PARTY_SIZE)
    }

    /**
     * Section 11. The ticket number comes back from the backend; the screen
     * only forwards the id it was given.
     */
    fun joinQueue() {
        if (joining.value) return
        joining.value = true

        viewModelScope.launch {
            val outcome = queueRepository.joinQueue(
                restaurantId = restaurantId,
                partySize = partySize.value,
                note = null
            )
            joining.value = false

            when (outcome) {
                is Outcome.Success -> events.send(RestaurantDetailEvent.Joined(outcome.data.id))
                is Outcome.Failure -> events.send(RestaurantDetailEvent.Failed(outcome.error))
            }
        }
    }

    fun retry() {
        viewModelScope.launch { restaurantRepository.refreshRestaurant(restaurantId) }
    }
}
