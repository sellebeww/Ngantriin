package org.umn.ngantriin.ui.staff

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.umn.ngantriin.core.AppError
import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.domain.model.QueueEntry
import org.umn.ngantriin.domain.model.QueueMath
import org.umn.ngantriin.domain.model.QueueStats
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.domain.model.Restaurant
import org.umn.ngantriin.domain.repository.AuthRepository
import org.umn.ngantriin.domain.repository.QueueRepository
import org.umn.ngantriin.domain.repository.RestaurantRepository

data class StaffQueueUiState(
    val isLoading: Boolean = true,
    val managedRestaurants: List<Restaurant> = emptyList(),
    val selectedRestaurant: Restaurant? = null,
    val entries: List<QueueEntry> = emptyList(),
    val stats: QueueStats? = null,
    val isWorking: Boolean = false,
    val error: AppError? = null
) {
    /** Not called yet, in ticket order — early arrivals included. */
    val waiting: List<QueueEntry>
        get() = entries.filter { it.isWaitingToBeCalled }.sortedBy { it.ticketSequence }

    /** Their turn; the check-in window is running. */
    val called: List<QueueEntry>
        get() = entries.filter { it.status == QueueStatus.CALLED }

    /** Called and checked in: prepare their table. */
    val arrived: List<QueueEntry>
        get() = entries.filter { it.status == QueueStatus.CHECKED_IN && it.hasBeenCalled }

    /** Table set, being seated: complete once they sit down. */
    val tableReady: List<QueueEntry>
        get() = entries.filter { it.status == QueueStatus.TABLE_READY }

    /** Everyone physically at the restaurant right now. */
    val atRestaurant: Int get() = entries.count { it.status.isAtRestaurant }

    val nowServing: QueueEntry? get() = QueueMath.currentServing(entries)

    /** What call_next would pick. */
    val next: QueueEntry? get() = QueueMath.nextToCall(entries)

    val canCallNext: Boolean get() = next != null && !isWorking
}

sealed interface StaffEvent {
    data class Called(val entry: QueueEntry) : StaffEvent
    data class Failed(val error: AppError) : StaffEvent
}

/** Section 24. */
@OptIn(ExperimentalCoroutinesApi::class)
class StaffQueueViewModel(
    private val authRepository: AuthRepository,
    private val restaurantRepository: RestaurantRepository,
    private val queueRepository: QueueRepository,
    initialRestaurantId: String? = null
) : ViewModel() {

    private val managed = MutableStateFlow<List<Restaurant>>(emptyList())
    private val selectedId = MutableStateFlow(initialRestaurantId)
    private val working = MutableStateFlow(false)
    private val error = MutableStateFlow<AppError?>(null)
    private val settled = MutableStateFlow(false)

    private val events = Channel<StaffEvent>(Channel.BUFFERED)
    val eventFlow: Flow<StaffEvent> = events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val userId = authRepository.currentUserOrNull()?.id
            if (userId == null) {
                error.value = AppError.SessionExpired
            } else {
                when (val outcome = restaurantRepository.managedRestaurants(userId)) {
                    is Outcome.Success -> {
                        managed.value = outcome.data
                        if (selectedId.value == null) selectedId.value = outcome.data.firstOrNull()?.id
                    }
                    is Outcome.Failure -> error.value = outcome.error
                }
            }
            settled.value = true
        }

        // Section 16: keep the board honest while it's open. The backend's
        // own sweep may be minutes away (or absent without pg_cron).
        viewModelScope.launch {
            while (isActive) {
                delay(SWEEP_INTERVAL_MILLIS)
                selectedId.value?.let { queueRepository.expireOverdue(it) }
            }
        }
    }

    private val entries = selectedId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else queueRepository.observeRestaurantQueue(id)
    }

    private val stats = selectedId.flatMapLatest { id ->
        if (id == null) flowOf(null) else restaurantRepository.observeQueueStats(id)
    }

    val uiState: StateFlow<StaffQueueUiState> = combine(
        managed,
        selectedId,
        entries,
        stats,
        combine(working, error, settled) { isWorking, currentError, hasSettled ->
            Triple(isWorking, currentError, hasSettled)
        }
    ) { restaurants, id, queueEntries, queueStats, flags ->
        val (isWorking, currentError, hasSettled) = flags
        StaffQueueUiState(
            isLoading = !hasSettled,
            managedRestaurants = restaurants,
            selectedRestaurant = restaurants.firstOrNull { it.id == id },
            entries = queueEntries,
            stats = queueStats,
            isWorking = isWorking,
            error = currentError
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StaffQueueUiState())

    fun selectRestaurant(restaurantId: String) {
        selectedId.value = restaurantId
    }

    /** Section 24: WAITING/ALMOST_THERE -> CALLED for the longest-waiting ticket. */
    fun callNext() {
        val restaurantId = selectedId.value ?: return
        if (working.value) return
        working.value = true

        viewModelScope.launch {
            when (val outcome = queueRepository.callNext(restaurantId)) {
                is Outcome.Success -> events.send(StaffEvent.Called(outcome.data))
                is Outcome.Failure -> events.send(StaffEvent.Failed(outcome.error))
            }
            working.value = false
        }
    }

    fun updateStatus(queueId: String, status: QueueStatus) {
        if (working.value) return
        working.value = true

        viewModelScope.launch {
            val outcome = queueRepository.updateStatus(queueId, status)
            if (outcome is Outcome.Failure) events.send(StaffEvent.Failed(outcome.error))
            working.value = false
        }
    }

    /** A CALLED row's countdown reached zero on screen. */
    fun onCheckInWindowClosed() {
        val restaurantId = selectedId.value ?: return
        viewModelScope.launch { queueRepository.expireOverdue(restaurantId) }
    }

    fun dismissError() {
        error.value = null
    }

    /**
     * Resolves a ticket's stored check-in photo reference into something a
     * viewer can actually load (a signed Storage URL for a real backend, a
     * local Uri passthrough in demo mode — see [QueueRepository]).
     */
    suspend fun resolveCheckInPhotoUrl(photoUrl: String): String? =
        when (val outcome = queueRepository.resolveCheckInPhotoUrl(photoUrl)) {
            is Outcome.Success -> outcome.data
            is Outcome.Failure -> null
        }

    /**
     * Signing out is all this screen has to do: the session flow in
     * NgantriinApp is what moves the app back to login (section 35).
     */
    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    private companion object {
        const val SWEEP_INTERVAL_MILLIS = 30_000L
    }
}
