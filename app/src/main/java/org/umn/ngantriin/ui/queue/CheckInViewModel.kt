package org.umn.ngantriin.ui.queue

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
import kotlinx.coroutines.launch
import org.umn.ngantriin.core.AppError
import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.domain.model.ActiveQueue
import org.umn.ngantriin.domain.repository.AuthRepository
import org.umn.ngantriin.domain.repository.QueueRepository
import org.umn.ngantriin.domain.repository.SessionState

/** Section 17. */
sealed interface CheckInStep {
    data object Idle : CheckInStep
    data object Uploading : CheckInStep
    data object Success : CheckInStep
    data class Failed(val error: AppError) : CheckInStep
}

data class CheckInUiState(
    val queue: ActiveQueue? = null,
    val step: CheckInStep = CheckInStep.Idle,
    val isLoading: Boolean = true
) {
    val isBusy: Boolean get() = step is CheckInStep.Uploading
    val canCheckIn: Boolean get() = queue?.canCheckIn == true && !isBusy
}

@OptIn(ExperimentalCoroutinesApi::class)
class CheckInViewModel(
    private val queueId: String,
    authRepository: AuthRepository,
    private val queueRepository: QueueRepository
) : ViewModel() {

    private val step = MutableStateFlow<CheckInStep>(CheckInStep.Idle)
    private val settled = MutableStateFlow(false)

    private val activeQueue = authRepository.sessionState.flatMapLatest { state ->
        when (state) {
            is SessionState.SignedIn -> queueRepository.observeActiveQueue(state.user.id)
            else -> flowOf(null)
        }
    }

    init {
        viewModelScope.launch {
            queueRepository.getQueue(queueId)
            settled.value = true
        }
    }

    val uiState: StateFlow<CheckInUiState> = combine(
        activeQueue,
        step,
        settled
    ) { queue, currentStep, hasSettled ->
        CheckInUiState(
            // Only the ticket this screen was opened for.
            queue = queue?.takeIf { it.entry.id == queueId },
            step = currentStep,
            isLoading = queue == null && !hasSettled
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CheckInUiState())

    /**
     * Section 17's verification: the customer photographs themselves at the
     * restaurant. The backend rejects the transition to CHECKED_IN if the
     * upload's path never reaches it, so a client cannot skip this step.
     */
    fun checkIn(photo: ByteArray) {
        val queue = uiState.value.queue ?: return
        if (uiState.value.isBusy) return

        viewModelScope.launch {
            step.value = CheckInStep.Uploading
            step.value = when (
                val result = queueRepository.checkIn(queueId, queue.restaurant.id, photo)
            ) {
                is Outcome.Success -> CheckInStep.Success
                is Outcome.Failure -> CheckInStep.Failed(result.error)
            }
        }
    }

    fun reset() {
        step.value = CheckInStep.Idle
    }
}
