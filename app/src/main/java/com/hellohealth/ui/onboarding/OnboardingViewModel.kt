package com.hellohealth.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.domain.model.ActivityLevel
import com.hellohealth.domain.model.Gender
import com.hellohealth.domain.model.GoalType
import com.hellohealth.domain.model.UnitPreference
import com.hellohealth.domain.repository.AuthRepository
import com.hellohealth.domain.repository.GoalsRepository
import com.hellohealth.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The onboarding wizard's steps, in order. [BASICS] is built in P0.5 Step 6; [BODY], [ACTIVITY],
 * and [CONFIRM] are filled in Steps 7-9. [ordinal] doubles as the progress index.
 */
enum class OnboardingStep {
    BASICS,
    BODY,
    ACTIVITY,
    CONFIRM;

    companion object {
        val count get() = entries.size
    }
}

/**
 * Single source of truth for the whole wizard. All collected fields live here (nullable until the
 * user provides them) so later steps only add mutators — no state reshaping. Held in the ViewModel,
 * which outlives configuration changes, so in-progress entries survive rotation (the app's
 * established convention; no SavedStateHandle is used for form state anywhere in the codebase).
 */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.BASICS,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    // Basics
    val displayName: String = "",
    val gender: Gender? = null,
    val birthDateEpochDay: Long? = null,
    val unitPreference: UnitPreference = UnitPreference.METRIC,
    // Body (Step 7)
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    // Activity & goal (Step 8)
    val activityLevel: ActivityLevel? = null,
    val goalType: GoalType? = null,
    val targetWeightKg: Double? = null,
    val targetRateKgPerWeek: Double? = null
) {
    /** Basics is complete once the user has named themselves and picked a birth date. */
    val isBasicsValid: Boolean
        get() = displayName.isNotBlank() && birthDateEpochDay != null
}

/**
 * Drives the first-run onboarding wizard. Prefills [OnboardingUiState.displayName] from the signed-in
 * user's name (Google accounts carry one; email/password sign-ups do not). Persistence of the
 * collected profile + seeded goals lands in Step 9 — [profileRepository]/[goalsRepository] are
 * injected now so that step adds only a `finish()` method, not new plumbing.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val goalsRepository: GoalsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    /**
     * True once the user edits the name field. Guards the async [prefillFromAuth] from clobbering
     * user input if the auth fetch resolves after they've started typing (both run on the main
     * thread; the fetch suspends, leaving a window for input).
     */
    private var nameTouched = false

    init {
        prefillFromAuth()
    }

    private fun prefillFromAuth() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val name = runCatching { authRepository.getCurrentUser()?.name }.getOrNull()
            _uiState.value = _uiState.value.copy(
                // Only apply the auth name if the user hasn't touched the field in the meantime.
                displayName = if (nameTouched) {
                    _uiState.value.displayName
                } else {
                    name?.takeIf { it.isNotBlank() } ?: _uiState.value.displayName
                },
                isLoading = false,
                error = null
            )
        }
    }

    // --- Basics mutators (Step 6) ---

    fun updateDisplayName(name: String) {
        nameTouched = true
        _uiState.value = _uiState.value.copy(displayName = name)
    }

    fun updateGender(gender: Gender) {
        _uiState.value = _uiState.value.copy(gender = gender)
    }

    fun updateBirthDate(epochDay: Long?) {
        _uiState.value = _uiState.value.copy(birthDateEpochDay = epochDay)
    }

    fun updateUnitPreference(unit: UnitPreference) {
        _uiState.value = _uiState.value.copy(unitPreference = unit)
    }

    // --- Step navigation ---

    fun nextStep() {
        val current = _uiState.value.step
        val next = OnboardingStep.entries.getOrNull(current.ordinal + 1) ?: return
        _uiState.value = _uiState.value.copy(step = next, error = null)
    }

    fun previousStep() {
        val current = _uiState.value.step
        val prev = OnboardingStep.entries.getOrNull(current.ordinal - 1) ?: return
        _uiState.value = _uiState.value.copy(step = prev, error = null)
    }
}
