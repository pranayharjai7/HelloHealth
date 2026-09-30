package com.hellohealth.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.domain.health.BodyEnergy
import com.hellohealth.domain.health.MacroTargets
import com.hellohealth.domain.model.nutrition.EnergyBalance
import com.hellohealth.domain.model.nutrition.NutritionDaySummary
import com.hellohealth.domain.repository.ActivityRepository
import com.hellohealth.domain.repository.NutritionRepository
import com.hellohealth.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * State for the dashboard's Nutrition card. Pre-formatted strings keep the card dumb; a sparse
 * profile (no computable budget) surfaces as [DASH] and hides the budget-relative UI rather than
 * rendering a misleading "0". [caloriesProgress] drives the calorie ring (0..1); the macro fields
 * carry both the consumed grams and their targets so the card can draw P/C/F progress bars.
 *
 * [hasData] is false only in the zero state (no signed-in user / empty day AND no budget) so the
 * card can show a gentle "start logging" hint instead of an all-zero ring.
 */
data class NutritionUiState(
    val caloriesConsumed: Int = 0,
    val calorieBudget: Int? = null,
    val caloriesProgress: Float = 0f,
    val caloriesConsumedLabel: String = DASH,
    val budgetLabel: String = DASH,
    val remainingLabel: String = DASH,
    val netLabel: String = DASH,
    val isDeficit: Boolean = true,
    val proteinG: Int = 0,
    val carbsG: Int = 0,
    val fatG: Int = 0,
    val proteinTargetG: Int? = null,
    val carbsTargetG: Int? = null,
    val fatTargetG: Int? = null,
    val waterLabel: String = DASH,
    val hasData: Boolean = false,
) {
    companion object {
        const val DASH = "—"
    }
}

/**
 * Own VM for the dashboard's Nutrition card — a sibling of [VitalsViewModel], deliberately NOT folded
 * into [DashboardViewModel]. Performs the flagship read-time energy-balance join: the nutrition day
 * summary (calories in + macros + water) combined with the activity snapshot's calories out (active +
 * BMR) and the profile-derived calorie budget + macro targets. `net = caloriesOut − caloriesIn` is
 * computed in [EnergyBalance] and never persisted.
 *
 * The profile is a one-shot suspend read (there is no profile Flow), wrapped in its own `flow` so a
 * later profile change still refreshes the budget on the next collection. With no signed-in user the
 * repo flows emit their empty/zero defaults, so the state collapses to the zero state without any
 * special-casing.
 */
@HiltViewModel
class NutritionViewModel @Inject constructor(
    nutritionRepository: NutritionRepository,
    activityRepository: ActivityRepository,
    profileRepository: ProfileRepository,
) : ViewModel() {

    private val today: String = LocalDate.now(ZoneId.systemDefault()).toString()

    // Budget + macro targets derived once from the profile. A sparse profile yields null budget
    // (BodyEnergy returns null), which the UI reads as "no target" rather than zero. Never throws.
    private val budgetFlow = flow {
        val profile = runCatching { profileRepository.getProfile() }.getOrNull()
        val budget = profile?.let { BodyEnergy.calorieBudget(it) }
        val macros = MacroTargets.split(budget, profile?.goalType)
        emit(BudgetTargets(budget, macros?.proteinG, macros?.carbsG, macros?.fatG))
    }

    val uiState: StateFlow<NutritionUiState> =
        combine(
            nutritionRepository.observeDaySummary(today),
            activityRepository.observeTodayCaloriesOut(),
            budgetFlow,
        ) { summary, caloriesOut, targets ->
            toUiState(summary, caloriesOut, targets)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NutritionUiState(),
        )

    private fun toUiState(
        summary: NutritionDaySummary,
        caloriesOut: Double,
        targets: BudgetTargets,
    ): NutritionUiState {
        val balance = EnergyBalance(
            caloriesIn = summary.caloriesConsumed,
            caloriesOut = caloriesOut,
            budget = targets.budget,
        )
        val consumed = summary.caloriesConsumed
        val budget = targets.budget
        val progress = if (budget != null && budget > 0) {
            (consumed / budget).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
        val net = balance.net
        // A day with no entries, no burn, and no budget is the true zero state.
        val hasData = consumed > 0.0 || caloriesOut > 0.0 || budget != null

        return NutritionUiState(
            caloriesConsumed = consumed.roundToInt(),
            calorieBudget = budget,
            caloriesProgress = progress,
            caloriesConsumedLabel = "${consumed.roundToInt()} kcal",
            budgetLabel = budget?.let { "$it kcal" } ?: NutritionUiState.DASH,
            remainingLabel = balance.remainingToBudget?.let { "${it.roundToInt()} kcal left" }
                ?: NutritionUiState.DASH,
            netLabel = formatNet(net),
            isDeficit = net >= 0,
            proteinG = summary.proteinG.roundToInt(),
            carbsG = summary.carbsG.roundToInt(),
            fatG = summary.fatG.roundToInt(),
            proteinTargetG = targets.proteinG,
            carbsTargetG = targets.carbsG,
            fatTargetG = targets.fatG,
            waterLabel = formatWater(summary.waterMl),
            hasData = hasData,
        )
    }

    /** Net energy balance: positive = deficit (burned more), negative = surplus. */
    private fun formatNet(net: Double): String {
        val magnitude = kotlin.math.abs(net).roundToInt()
        return if (net >= 0) "$magnitude kcal deficit" else "$magnitude kcal surplus"
    }

    private fun formatWater(ml: Double): String = when {
        ml <= 0.0 -> NutritionUiState.DASH
        ml >= 1000 -> String.format("%.1f L", ml / 1000)
        else -> "${ml.roundToInt()} ml"
    }

    private data class BudgetTargets(
        val budget: Int?,
        val proteinG: Int?,
        val carbsG: Int?,
        val fatG: Int?,
    )
}
