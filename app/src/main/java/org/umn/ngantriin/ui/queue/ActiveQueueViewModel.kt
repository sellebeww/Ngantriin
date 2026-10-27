package org.umn.ngantriin.ui.queue

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
import org.umn.ngantriin.core.ConnectivityObserver
import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.domain.model.ActiveQueue
import org.umn.ngantriin.domain.repository.AuthRepository
import org.umn.ngantriin.domain.repository.QueueRepository
import org.umn.ngantriin.domain.repository.SessionState

data class ActiveQueueUiState(
    val isLoading: Boolean = true,
    val queue: ActiveQueue? = null,
    val isOffline: Boolean = false,
    val isLeaving: Boolean = false
)

sealed interface ActiveQueueEvent {
    data object Left : ActiveQueueEvent
    data class Failed(val error: AppError) : ActiveQueueEvent
}

/**
 * State di sini adalah proyeksi langsung dari flow realtime
 * repository. Tidak ada polling dan tidak ada refresh manual,
 * karena antrean bergerak adalah event yang sudah diberitahukan backend.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActiveQueueViewModel(
    authRepository: AuthRepository,
    private val queueRepository: QueueRepository,
    connectivityObserver: ConnectivityObserver
) : ViewModel() {

    private val leaving = MutableStateFlow(false)
    private val settled = MutableStateFlow(false)

    private val events = Channel<ActiveQueueEvent>(Channel.BUFFERED)
    val eventFlow: Flow<ActiveQueueEvent> = events.receiveAsFlow()

    private val activeQueue = authRepository.sessionState.flatMapLatest { state ->
        when (state) {
            is SessionState.SignedIn -> queueRepository.observeActiveQueue(state.user.id)
            else -> flowOf(null)
        }
    }

    init {
        // Satu emisi cukup untuk tahu apakah "tidak ada antrean" jawabannya
        // atau cache baru memanas.
        viewModelScope.launch {
            authRepository.currentUserOrNull()?.let { queueRepository.refreshHistory(it.id) }
            settled.value = true
        }
    }

    val uiState: StateFlow<ActiveQueueUiState> = combine(
        activeQueue,
        connectivityObserver.online,
        leaving,
        settled
    ) { queue, online, isLeaving, hasSettled ->
        ActiveQueueUiState(
            isLoading = queue == null && !hasSettled,
            queue = queue,
            isOffline = !online,
            isLeaving = isLeaving
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveQueueUiState())

    /**
     * Hitung mundur di layar sampai nol. Minta backend menyelesaikannya
     * sekarang daripada menunggu sapuan terjadwal, lalu tiket
     * berubah ke EXPIRED lewat jalur realtime biasa.
     */
    fun onCheckInWindowClosed() {
        val restaurantId = uiState.value.queue?.restaurant?.id ?: return
        viewModelScope.launch { queueRepository.expireOverdue(restaurantId) }
    }

    /** Hanya sampai di sini setelah dialog konfirmasi. */
    fun leaveQueue() {
        val queueId = uiState.value.queue?.entry?.id ?: return
        if (leaving.value) return
        leaving.value = true

        viewModelScope.launch {
            val outcome = queueRepository.leaveQueue(queueId)
            leaving.value = false
            when (outcome) {
                is Outcome.Success -> events.send(ActiveQueueEvent.Left)
                is Outcome.Failure -> events.send(ActiveQueueEvent.Failed(outcome.error))
            }
        }
    }
}
