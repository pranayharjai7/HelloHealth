package com.hellohealth.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.domain.repository.ActivityRepository
import com.hellohealth.domain.repository.EmotionsRepository
import com.hellohealth.domain.repository.NutritionRepository
import com.hellohealth.domain.repository.VitalsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Lightweight VM for the dashboard's Insights entry card. It does NOT recompute the full weekly
 * cross-dimension series (that's the Insights screen's job) — it just counts how many of the four
 * dimensions have data TODAY, so the card can say "3 of 4 tracked today · see weekly trends" and
 * invite a tap. Cheap today-only reads; never throws (each guarded, missing → not counted).
 */
@HiltViewModel
class InsightsPreviewViewModel @Inject constructor(
    private val nutritionRepository: NutritionRepository,
    private val emotionsRepository: EmotionsRepository,
    private val vitalsRepository: VitalsRepository,
    private val activityRepository: ActivityRepository,
) : ViewModel() {

    data class InsightsPreviewUiState(
        val dimensionsTracked: Int = 0,
        val totalDimensions: Int = 4,
        val isLoading: Boolean = true,
    )

    private val _uiState = MutableStateFlow(InsightsPreviewUiState())
    val uiState: StateFlow<InsightsPreviewUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val today = LocalDate.now(ZoneId.systemDefault()).toString()
            var tracked = 0

            // Nutrition — any calories logged today.
            runCatching { nutritionRepository.observeDaySummary(today).first() }
                .getOrNull()?.let { if (it.caloriesConsumed > 0 || it.waterMl > 0) tracked++ }

            // Mood — any log today.
            runCatching { emotionsRepository.observeToday().first() }
                .getOrDefault(emptyList()).let { if (it.isNotEmpty()) tracked++ }

            // Vitals — a latest rollup exists.
            runCatching { vitalsRepository.observeLatestVitals().first() }
                .getOrNull()?.let { tracked++ }

            // Activity — any calories-out today.
            runCatching { activityRepository.observeTodayCaloriesOut().first() }
                .getOrDefault(0.0).let { if (it > 0) tracked++ }

            _uiState.value = InsightsPreviewUiState(dimensionsTracked = tracked, isLoading = false)
        }
    }
}
