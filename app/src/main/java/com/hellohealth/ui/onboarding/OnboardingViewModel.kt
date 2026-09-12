package com.hellohealth.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.domain.model.ActivityLevel
import com.hellohealth.domain.model.Gender
import com.hellohealth.domain.model.GoalType
import com.hellohealth.domain.model.UnitPreference
import com.hellohealth.domain.repository.ActivityRepository
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
    /** Whole years derived from [birthDateEpochDay] at pick time; drives the 13-120 age gate. */
    val ageYears: Int? = null,
    val unitPreference: UnitPreference = UnitPreference.METRIC,
    // Body (Step 7)
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val isPrefillingBody: Boolean = false,
    // Activity & goal (Step 8)
    val activityLevel: ActivityLevel? = null,
    val goalType: GoalType? = null,
    val targetWeightKg: Double? = null,
    val targetRateKgPerWeek: Double? = null
) {
    /** Basics is complete once the user has named themselves and picked a birth date. */
    val isBasicsValid: Boolean
        get() = displayName.isNotBlank() && birthDateEpochDay != null

    /**
     * Body is complete once both height and weight are set and within sane human bounds. The bounds
     * reject fat-finger entries (a 3 cm height, a 5 kg adult) while staying permissive enough for
     * real extremes; they mirror the metric ranges checked in [OnboardingViewModel].
     */
    val isBodyValid: Boolean
        get() {
            val h = heightCm ?: return false
            val w = weightKg ?: return false
            return h in OnboardingViewModel.MIN_HEIGHT_CM..OnboardingViewModel.MAX_HEIGHT_CM &&
                w in OnboardingViewModel.MIN_WEIGHT_KG..OnboardingViewModel.MAX_WEIGHT_KG
        }

    /** Age must be a plausible human range; also drives whether the Body/Activity steps make sense. */
    val isAgeValid: Boolean
        get() = ageYears?.let { it in OnboardingViewModel.MIN_AGE..OnboardingViewModel.MAX_AGE } ?: false

    /**
     * Activity & goal is complete once an activity level and goal direction are chosen and the age is
     * plausible. Target weight/rate are optional, but when supplied they must be within sane bounds
     * (an out-of-range rate would corrupt the derived calorie budget).
     */
    val isActivityValid: Boolean
        get() {
            if (activityLevel == null || goalType == null || !isAgeValid) return false
            val rate = targetRateKgPerWeek
            if (rate != null && rate !in OnboardingViewModel.MIN_RATE_KG_PER_WEEK..OnboardingViewModel.MAX_RATE_KG_PER_WEEK) {
                return false
            }
            val target = targetWeightKg
            if (target != null && target !in OnboardingViewModel.MIN_WEIGHT_KG..OnboardingViewModel.MAX_WEIGHT_KG) {
                return false
            }
            return true
        }
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
    private val goalsRepository: GoalsRepository,
    private val activityRepository: ActivityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    /**
     * True once the user edits the name field. Guards the async [prefillFromAuth] from clobbering
     * user input if the auth fetch resolves after they've started typing (both run on the main
     * thread; the fetch suspends, leaving a window for input).
     */
    private var nameTouched = false

    /**
     * True once the user edits either body field. Guards the async Health Connect [prefillBody] from
     * overwriting a value the user typed while the read was in flight (same race as [nameTouched]).
     * Prefill also only fills fields that are still null, so a manual entry is never clobbered even
     * within a single field.
     */
    private var heightTouched = false
    private var weightTouched = false

    /** Ensures the one-shot Health Connect prefill runs at most once, on first entry to the Body step. */
    private var bodyPrefillAttempted = false

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
        // Derive age once here so the 13-120 gate stays a pure getter on the state.
        val age = epochDay?.let { day ->
            val birth = java.time.LocalDate.ofEpochDay(day)
            val today = java.time.LocalDate.now()
            if (birth.isAfter(today)) null else java.time.Period.between(birth, today).years
        }
        _uiState.value = _uiState.value.copy(birthDateEpochDay = epochDay, ageYears = age)
    }

    fun updateUnitPreference(unit: UnitPreference) {
        _uiState.value = _uiState.value.copy(unitPreference = unit)
    }

    // --- Body mutators (Step 7) ---

    /**
     * Kicks off a one-shot Health Connect prefill of height/weight when the user first reaches the
     * Body step. No-op on repeat entries (so re-visiting the step doesn't re-clobber edits) and only
     * fills fields the user hasn't touched and that are still null. Never crashes: the repository
     * returns empty metrics when Health Connect is unavailable or unconnected.
     */
    fun prefillBodyFromHealthConnect() {
        if (bodyPrefillAttempted) return
        bodyPrefillAttempted = true
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPrefillingBody = true)
            val metrics = runCatching { activityRepository.fetchLatestBodyMetrics() }.getOrNull()
            _uiState.value = _uiState.value.copy(
                heightCm = if (!heightTouched && _uiState.value.heightCm == null) {
                    metrics?.heightCm ?: _uiState.value.heightCm
                } else {
                    _uiState.value.heightCm
                },
                weightKg = if (!weightTouched && _uiState.value.weightKg == null) {
                    metrics?.weightKg ?: _uiState.value.weightKg
                } else {
                    _uiState.value.weightKg
                },
                isPrefillingBody = false
            )
        }
    }

    /** Stores height in metric (cm). The UI converts imperial ft/in before calling this. */
    fun updateHeightCm(heightCm: Double?) {
        heightTouched = true
        _uiState.value = _uiState.value.copy(heightCm = heightCm)
    }

    /** Stores weight in metric (kg). The UI converts imperial lb before calling this. */
    fun updateWeightKg(weightKg: Double?) {
        weightTouched = true
        _uiState.value = _uiState.value.copy(weightKg = weightKg)
    }

    // --- Activity & goal mutators (Step 8) ---

    fun updateActivityLevel(level: ActivityLevel) {
        _uiState.value = _uiState.value.copy(activityLevel = level)
    }

    /**
     * Sets the goal direction. Switching to MAINTAIN clears any target weight/rate (they only make
     * sense for a directional goal), so a leftover out-of-range rate can't linger and corrupt the
     * budget or block the gate.
     */
    fun updateGoalType(goalType: GoalType) {
        _uiState.value = if (goalType == GoalType.MAINTAIN) {
            _uiState.value.copy(goalType = goalType, targetWeightKg = null, targetRateKgPerWeek = null)
        } else {
            _uiState.value.copy(goalType = goalType)
        }
    }

    /** Stores target weight in metric (kg); null clears it. The UI converts imperial lb first. */
    fun updateTargetWeightKg(weightKg: Double?) {
        _uiState.value = _uiState.value.copy(targetWeightKg = weightKg)
    }

    /** Stores the weekly weight-change target (kg/week, always metric); null clears it. */
    fun updateTargetRateKgPerWeek(rate: Double?) {
        _uiState.value = _uiState.value.copy(targetRateKgPerWeek = rate)
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

    companion object {
        // Metric validation bounds for the Body step. Permissive enough for real human extremes,
        // tight enough to reject fat-finger entries (a 3 cm height, a 5 kg adult).
        const val MIN_HEIGHT_CM = 50.0
        const val MAX_HEIGHT_CM = 272.0
        const val MIN_WEIGHT_KG = 20.0
        const val MAX_WEIGHT_KG = 400.0

        // Age gate (years) and weekly weight-change bounds (kg/week). A rate outside this band would
        // push the calorie budget into unsafe territory, so the Activity step blocks it.
        const val MIN_AGE = 13
        const val MAX_AGE = 120
        const val MIN_RATE_KG_PER_WEEK = 0.1
        const val MAX_RATE_KG_PER_WEEK = 1.0
    }
}
