package com.hellohealth.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.core.date.SelectedDateHolder
import com.hellohealth.domain.model.ActivityGoals
import com.hellohealth.domain.model.CrossDimensionInsights
import com.hellohealth.domain.model.FoodPreferences
import com.hellohealth.domain.model.WeeklyInsights
import com.hellohealth.domain.model.WeeklyStats
import com.hellohealth.domain.repository.ActivityRepository
import com.hellohealth.domain.repository.EmotionsRepository
import com.hellohealth.domain.repository.GoalsRepository
import com.hellohealth.domain.repository.NutritionRepository
import com.hellohealth.domain.repository.UserRepository
import com.hellohealth.domain.repository.VitalsRepository
import com.hellohealth.domain.usecase.BuildCrossInsightsUseCase
import com.hellohealth.domain.usecase.BuildWeeklyInsightsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class InsightsUiState(
    val weeklyStats: WeeklyStats = WeeklyStats(),
    val goals: ActivityGoals = ActivityGoals(),
    val foodPreferences: FoodPreferences = FoodPreferences(),
    val weeklyInsights: WeeklyInsights = WeeklyInsights(),
    val crossInsights: CrossDimensionInsights = CrossDimensionInsights(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val repository: ActivityRepository,
    private val goalsRepository: GoalsRepository,
    private val userRepository: UserRepository,
    private val emotionsRepository: EmotionsRepository,
    private val vitalsRepository: VitalsRepository,
    private val nutritionRepository: NutritionRepository,
    private val buildWeeklyInsights: BuildWeeklyInsightsUseCase,
    private val buildCrossInsights: BuildCrossInsightsUseCase,
    private val selectedDateHolder: SelectedDateHolder
) : ViewModel() {

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    /** The window-end for the 7-day insights window: the dashboard's selected date (default today). */
    private val asOf: LocalDate get() = selectedDateHolder.selectedDate.value

    init {
        observeGoals()
        // Collecting the selected date emits its current value immediately → initial load.
        observeSelectedDate()
    }

    /** Re-anchor the insights window whenever the dashboard's selected date changes. */
    private fun observeSelectedDate() {
        viewModelScope.launch {
            selectedDateHolder.selectedDate.collectLatest { loadWeeklyStats() }
        }
    }

    private fun observeGoals() {
        viewModelScope.launch {
            goalsRepository.getActivityGoals().collectLatest { goals ->
                _uiState.update { state ->
                    if (state.weeklyStats.dailyStats.isEmpty()) {
                        state.copy(goals = goals)
                    } else {
                        val insights = buildWeeklyInsights(state.weeklyStats, goals, state.foodPreferences, asOf)
                        state.copy(
                            goals = goals,
                            weeklyInsights = insights
                        )
                    }
                }
            }
        }
    }

    fun loadWeeklyStats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val stats = repository.fetchWeeklyStats()
                val goals = goalsRepository.getCurrentActivityGoals()
                val preferences = userRepository.getCurrentFoodPreferences()
                val insights = buildWeeklyInsights(stats, goals, preferences, asOf)
                val cross = buildCrossDimension(stats)
                _uiState.value = _uiState.value.copy(
                    weeklyStats = stats,
                    goals = goals,
                    foodPreferences = preferences,
                    weeklyInsights = insights,
                    crossInsights = cross,
                    isLoading = false,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load insights"
                )
            }
        }
    }

    /**
     * Snapshot the other three dimensions for the last 7 days and fold them into the cross-dimension
     * series. Each repo read is `.first()` (one-shot, matching this loader's style) and wrapped so a
     * single dimension failing degrades that dimension to empty rather than failing the whole screen.
     * calories-out per day comes from the activity [WeeklyStats] (active calories) that just loaded.
     */
    private suspend fun buildCrossDimension(stats: WeeklyStats): CrossDimensionInsights {
        val zone = ZoneId.systemDefault()
        val today = asOf
        val windowDays = (0..6).map { today.minusDays((6 - it).toLong()) }

        // Emotions: the 7-day window (inclusive) by epoch-day.
        val emotions = runCatching {
            emotionsRepository.observeWindow(today.minusDays(6).toEpochDay(), today.toEpochDay()).first()
        }.getOrDefault(emptyList())

        // Vitals: pull >= 14 days so the readiness calculator has baseline history for the window.
        val rollups = runCatching { vitalsRepository.observeRecentRollups(21).first() }.getOrDefault(emptyList())

        // Nutrition: per-day only, so snapshot each of the 7 days.
        val nutritionByDay = windowDays.associate { day ->
            val iso = day.toString()
            iso to runCatching { nutritionRepository.observeDaySummary(iso).first() }.getOrNull()
        }.filterValues { it != null }.mapValues { it.value!! }

        // Calories out per day from the activity weekly stats (active calories).
        val caloriesOutByDay = stats.dailyStats
            .filter { it.calories > 0 }
            .associate { it.date.toString() to it.calories }

        return buildCrossInsights(
            today = today,
            zone = zone,
            emotions = emotions,
            rollups = rollups,
            nutritionByDay = nutritionByDay,
            caloriesOutByDay = caloriesOutByDay,
        )
    }
}
