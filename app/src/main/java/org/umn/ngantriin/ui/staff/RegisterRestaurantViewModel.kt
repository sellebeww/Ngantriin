package org.umn.ngantriin.ui.staff

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.umn.ngantriin.R
import org.umn.ngantriin.core.AppError
import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.domain.model.ApplicantRole
import org.umn.ngantriin.domain.model.ProofDocument
import org.umn.ngantriin.domain.model.RestaurantRegistration
import org.umn.ngantriin.domain.repository.AuthRepository
import org.umn.ngantriin.domain.repository.RestaurantRepository
import org.umn.ngantriin.domain.repository.UserPreferencesRepository
import org.umn.ngantriin.location.LocationProvider
import java.time.LocalTime

data class RegisterRestaurantUiState(
    val name: String = "",
    val address: String = "",
    val category: String = "",
    val description: String = "",
    val photo: ByteArray? = null,
    val latitude: String = "",
    val longitude: String = "",
    val open24Hours: Boolean = false,
    val openingTime: String = "10:00",
    val closingTime: String = "22:00",
    val serviceMinutes: String = "3",
    val capacity: String = "50",
    val contactPhone: String = "",
    val applicantRole: ApplicantRole = ApplicantRole.OWNER,
    val license: ProofDocument? = null,
    val licenseName: String? = null,
    val ownershipProof: ProofDocument? = null,
    val ownershipProofName: String? = null,
    val errors: Map<Field, Int> = emptyMap(),
    val isLocating: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
    val registered: Boolean = false
) {
    enum class Field { NAME, ADDRESS, CATEGORY, LOCATION, HOURS, SERVICE, CAPACITY, PHONE, LICENSE }
}

/**
 * Kalau [resubmitId] diisi, ini memperbaiki pengajuan REJECTED: hanya kontak
 * dan bukti yang diminta lagi, restorannya sendiri tidak berubah.
 */
class RegisterRestaurantViewModel(
    private val authRepository: AuthRepository,
    private val restaurantRepository: RestaurantRepository,
    private val locationProvider: LocationProvider,
    private val preferences: UserPreferencesRepository,
    val resubmitId: String? = null
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterRestaurantUiState())
    val state: StateFlow<RegisterRestaurantUiState> = _state.asStateFlow()

    fun onName(value: String) = edit { copy(name = value.take(80)) }
    fun onAddress(value: String) = edit { copy(address = value) }
    fun onCategory(value: String) = edit { copy(category = value) }
    fun onDescription(value: String) = edit { copy(description = value.take(300)) }
    fun onPhoto(bytes: ByteArray?) = edit { copy(photo = bytes) }
    fun onLatitude(value: String) = edit { copy(latitude = value) }
    fun onLongitude(value: String) = edit { copy(longitude = value) }
    fun onOpen24Hours(value: Boolean) = edit { copy(open24Hours = value) }
    fun onOpening(value: String) = edit { copy(openingTime = value) }
    fun onClosing(value: String) = edit { copy(closingTime = value) }
    fun onServiceMinutes(value: String) = edit { copy(serviceMinutes = value.filter(Char::isDigit).take(2)) }
    fun onPhone(value: String) = edit { copy(contactPhone = value.filter { it.isDigit() || it in "+ -" }.take(20)) }
    fun onRole(value: ApplicantRole) = edit { copy(applicantRole = value) }
    fun onLicense(doc: ProofDocument?, name: String?) = edit { copy(license = doc, licenseName = name) }
    fun onOwnershipProof(doc: ProofDocument?, name: String?) =
        edit { copy(ownershipProof = doc, ownershipProofName = name) }

    fun onCapacity(value: String) = edit { copy(capacity = value.filter(Char::isDigit).take(3)) }

    /** Mengedit field menghapus keluhannya sendiri dan error server yang basi. */
    private fun edit(change: RegisterRestaurantUiState.() -> RegisterRestaurantUiState) =
        _state.update { it.change().copy(errors = emptyMap(), error = null) }

    fun hasLocationPermission(): Boolean = locationProvider.hasPermission()

    fun useCurrentLocation() {
        if (_state.value.isLocating) return
        _state.update { it.copy(isLocating = true, error = null) }
        viewModelScope.launch {
            when (val outcome = locationProvider.currentLocation(highAccuracy = true)) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        isLocating = false,
                        latitude = "%.6f".format(java.util.Locale.US, outcome.data.latitude),
                        longitude = "%.6f".format(java.util.Locale.US, outcome.data.longitude),
                        errors = it.errors - RegisterRestaurantUiState.Field.LOCATION
                    )
                }
                is Outcome.Failure -> _state.update { it.copy(isLocating = false, error = outcome.error) }
            }
        }
    }

    fun submit() {
        val current = _state.value
        if (current.isSubmitting) return
        if (resubmitId != null) {
            resubmit(current, resubmitId)
            return
        }
        val parsed = validate(current)
        if (parsed.errors.isNotEmpty()) {
            _state.update { it.copy(errors = parsed.errors) }
            return
        }

        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val registration = RestaurantRegistration(
                name = current.name.trim(),
                address = current.address.trim(),
                category = current.category.trim(),
                description = current.description.trim(),
                photo = current.photo,
                latitude = parsed.latitude,
                longitude = parsed.longitude,
                openingTime = parsed.opening,
                closingTime = parsed.closing,
                averageServiceMinutes = parsed.service,
                queueCapacity = parsed.capacity,
                contactPhone = parsed.phone,
                applicantRole = current.applicantRole,
                documents = proofsOf(current)
            )
            when (val outcome = restaurantRepository.registerRestaurant(registration)) {
                is Outcome.Success -> {
                    preferences.setStaffApplicantEmail(null)
                    // Akun sudah jadi staf. Menarik profil yang memindahkan aplikasi
                    // ke dashboard restoran, yang menampilkan pengajuan sebagai pending
                    // sampai ditinjau.
                    authRepository.refreshProfile()
                    _state.update { it.copy(isSubmitting = false, registered = true) }
                }
                is Outcome.Failure -> _state.update {
                    it.copy(isSubmitting = false, error = outcome.error)
                }
            }
        }
    }

    private fun proofsOf(s: RegisterRestaurantUiState): List<ProofDocument> =
        listOfNotNull(s.license, s.ownershipProof)

    private fun resubmit(current: RegisterRestaurantUiState, restaurantId: String) {
        val errors = contactErrors(current)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            when (val outcome = restaurantRepository.resubmitRestaurant(
                restaurantId = restaurantId,
                contactPhone = digitsOf(current.contactPhone),
                applicantRole = current.applicantRole,
                documents = proofsOf(current)
            )) {
                is Outcome.Success -> _state.update { it.copy(isSubmitting = false, registered = true) }
                is Outcome.Failure -> _state.update { it.copy(isSubmitting = false, error = outcome.error) }
            }
        }
    }

    /** "+62 812-3456-7890" -> "+6281234567890", seperti yang dinormalisasi server. */
    private fun digitsOf(raw: String): String {
        val plus = if (raw.trim().startsWith("+")) "+" else ""
        return plus + raw.filter(Char::isDigit)
    }

    private fun contactErrors(s: RegisterRestaurantUiState): Map<RegisterRestaurantUiState.Field, Int> {
        val errors = linkedMapOf<RegisterRestaurantUiState.Field, Int>()
        if (digitsOf(s.contactPhone).count(Char::isDigit) !in 8..15) {
            errors[RegisterRestaurantUiState.Field.PHONE] = R.string.validation_contact_phone
        }
        if (s.license == null) {
            errors[RegisterRestaurantUiState.Field.LICENSE] = R.string.validation_proof_required
        } else if (!s.license.isValid || s.ownershipProof?.isValid == false) {
            // File yang sebenarnya bukan PDF/JPEG tidak pernah keluar dari HP.
            errors[RegisterRestaurantUiState.Field.LICENSE] = R.string.register_restaurant_proof_unreadable
        }
        return errors
    }

    private class Parsed(
        val errors: Map<RegisterRestaurantUiState.Field, Int>,
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val opening: LocalTime = LocalTime.MIDNIGHT,
        val closing: LocalTime = LocalTime.MIDNIGHT,
        val service: Int = 0,
        val capacity: Int = 0,
        val phone: String = ""
    )

    private fun validate(s: RegisterRestaurantUiState): Parsed {
        val errors = linkedMapOf<RegisterRestaurantUiState.Field, Int>()
        fun fail(field: RegisterRestaurantUiState.Field, @StringRes message: Int) {
            errors[field] = message
        }

        if (s.name.trim().length < 2) fail(RegisterRestaurantUiState.Field.NAME, R.string.validation_restaurant_name)
        if (s.address.trim().length < 5) fail(RegisterRestaurantUiState.Field.ADDRESS, R.string.validation_restaurant_address)
        if (s.category.isBlank()) fail(RegisterRestaurantUiState.Field.CATEGORY, R.string.validation_restaurant_category)

        val lat = s.latitude.trim().toDoubleOrNull()
        val lng = s.longitude.trim().toDoubleOrNull()
        if (lat == null || lng == null || lat !in -90.0..90.0 || lng !in -180.0..180.0) {
            fail(RegisterRestaurantUiState.Field.LOCATION, R.string.validation_restaurant_location)
        }

        val opening = if (s.open24Hours) LocalTime.MIDNIGHT else parseClock(s.openingTime)
        val closing = if (s.open24Hours) LocalTime.MIDNIGHT else parseClock(s.closingTime)
        if (opening == null || closing == null) fail(RegisterRestaurantUiState.Field.HOURS, R.string.validation_restaurant_hours)

        val service = s.serviceMinutes.toIntOrNull()
        if (service == null || service !in 1..60) fail(RegisterRestaurantUiState.Field.SERVICE, R.string.validation_restaurant_service)
        val capacity = s.capacity.toIntOrNull()
        if (capacity == null || capacity !in 1..500) fail(RegisterRestaurantUiState.Field.CAPACITY, R.string.validation_restaurant_capacity)

        errors += contactErrors(s)

        if (errors.isNotEmpty()) return Parsed(errors)
        return Parsed(errors, lat!!, lng!!, opening!!, closing!!, service!!, capacity!!, digitsOf(s.contactPhone))
    }

    private fun parseClock(value: String): LocalTime? {
        val match = CLOCK.matchEntire(value.trim()) ?: return null
        return LocalTime.of(match.groupValues[1].toInt(), match.groupValues[2].toInt())
    }

    private companion object {
        val CLOCK = Regex("""([01]?\d|2[0-3]):([0-5]\d)""")
    }
}
