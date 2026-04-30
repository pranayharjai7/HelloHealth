package com.hellohealth.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.domain.model.ActivityGoals
import com.hellohealth.domain.model.FoodPreferences
import com.hellohealth.domain.model.WeeklyInsights
import com.hellohealth.domain.model.WeeklyStats
import com.hellohealth.domain.repository.ActivityRepository
import com.hellohealth.domain.repository.GoalsRepository
import com.hellohealth.domain.repository.UserRepository
import com.hellohealth.domain.usecase.BuildWeeklyInsightsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InsightsUiState(
    val weeklyStats: WeeklyStats = WeeklyStats(),
    val goals: ActivityGoals = ActivityGoals(),
    val foodPreferences: FoodPreferences = FoodPreferences(),
    val weeklyInsights: WeeklyInsights = WeeklyInsights(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val repository: ActivityRepository,
    private val goalsRepository: GoalsRepository,
    private val userRepository: UserRepository,
    private val buildWeeklyInsights: BuildWeeklyInsightsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    init {
        loadWeeklyStats()
        observeGoals()
    }

    private fun observeGoals() {
        viewModelScope.launch {
            goalsRepository.getActivityGoals().collectLatest { goals ->
                _uiState.update { state ->
                    if (state.weeklyStats.dailyStats.isEmpty()) {
                        state.copy(goals = goals)
                    } else {
                        val insights = buildWeeklyInsights(state.weeklyStats, goals, state.foodPreferences)
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
                val insights = buildWeeklyInsights(stats, goals, preferences)
                _uiState.value = _uiState.value.copy(
                    weeklyStats = stats,
                    goals = goals,
                    foodPreferences = preferences,
                    weeklyInsights = insights,
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
}
