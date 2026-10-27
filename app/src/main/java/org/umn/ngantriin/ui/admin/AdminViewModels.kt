package org.umn.ngantriin.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.umn.ngantriin.core.AppError
import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.domain.model.ApplicationDocument
import org.umn.ngantriin.domain.model.RestaurantApplication
import org.umn.ngantriin.domain.model.VerificationStatus
import org.umn.ngantriin.domain.repository.AdminRepository
import org.umn.ngantriin.domain.repository.AuthRepository

data class AdminReviewUiState(
    val filter: VerificationStatus = VerificationStatus.PENDING_VERIFICATION,
    val applications: List<RestaurantApplication> = emptyList(),
    val isLoading: Boolean = true,
    val error: AppError? = null
)

/** The list of applications in one state. The server decides who may see it. */
class AdminReviewViewModel(
    private val adminRepository: AdminRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AdminReviewUiState())
    val state: StateFlow<AdminReviewUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun setFilter(filter: VerificationStatus) {
        if (filter == _state.value.filter) return
        // Show nothing rather than the previous list under the new tab.
        _state.update { it.copy(filter = filter, applications = emptyList(), isLoading = true, error = null) }
        refresh()
    }

    fun refresh() {
        val filter = _state.value.filter
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val outcome = adminRepository.applications(filter)
            // A slow answer for a tab the reviewer already left must not overwrite the current one.
            if (_state.value.filter != filter) return@launch
            when (outcome) {
                is Outcome.Success -> _state.update {
                    it.copy(applications = outcome.data, isLoading = false)
                }
                is Outcome.Failure -> _state.update {
                    it.copy(applications = emptyList(), isLoading = false, error = outcome.error)
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }
}

data class AdminApplicationUiState(
    val application: RestaurantApplication? = null,
    val documents: List<ApplicationDocument> = emptyList(),
    /** Short-lived links for the documents that are shown as pictures. */
    val imageUrls: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val error: AppError? = null,
    /** The decision went through; the screen closes. */
    val decided: Boolean = false
)

/** One application: who applied, the proof files, and the decision. */
class AdminApplicationViewModel(
    private val adminRepository: AdminRepository,
    private val restaurantId: String,
    private val status: VerificationStatus
) : ViewModel() {

    private val _state = MutableStateFlow(AdminApplicationUiState())
    val state: StateFlow<AdminApplicationUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val application = when (val outcome = adminRepository.applications(status)) {
                is Outcome.Success -> outcome.data.firstOrNull { it.restaurantId == restaurantId }
                is Outcome.Failure -> return@launch fail(outcome.error)
            }
            val documents = when (val outcome = adminRepository.documents(restaurantId)) {
                is Outcome.Success -> outcome.data
                is Outcome.Failure -> return@launch fail(outcome.error)
            }
            val urls = documents.filterNot { it.isPdf }.mapNotNull { doc ->
                (adminRepository.documentUrl(doc) as? Outcome.Success)?.let { doc.id to it.data }
            }.toMap()
            _state.update {
                it.copy(
                    application = application,
                    documents = documents,
                    imageUrls = urls,
                    isLoading = false,
                    // The list moved on while this screen was open: say so, do not show a blank card.
                    error = if (application == null) AppError.NotPending else null
                )
            }
        }
    }

    /** A fresh link every time: the old one lapses after a few minutes. */
    fun openDocument(document: ApplicationDocument, open: (String) -> Unit) {
        viewModelScope.launch {
            when (val outcome = adminRepository.documentUrl(document)) {
                is Outcome.Success -> open(outcome.data)
                is Outcome.Failure -> _state.update { it.copy(error = outcome.error) }
            }
        }
    }

    fun approve() = decide(approve = true, note = null)

    fun reject(note: String) = decide(approve = false, note = note)

    private fun decide(approve: Boolean, note: String?) {
        if (_state.value.isWorking) return
        _state.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            when (val outcome = adminRepository.review(restaurantId, approve, note)) {
                is Outcome.Success -> _state.update { it.copy(isWorking = false, decided = true) }
                is Outcome.Failure -> _state.update { it.copy(isWorking = false, error = outcome.error) }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun fail(error: AppError) =
        _state.update { it.copy(isLoading = false, error = error) }
}
