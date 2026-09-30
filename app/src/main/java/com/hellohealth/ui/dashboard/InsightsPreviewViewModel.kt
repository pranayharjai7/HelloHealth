package com.hellohealth.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.core.date.SelectedDateHolder
import com.hellohealth.domain.repository.ActivityRepository
import com.hellohealth.domain.repository.EmotionsRepository
import com.hellohealth.domain.repository.NutritionRepository
import com.hellohealth.domain.repository.VitalsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Lightweight VM for the dashboard's Insights entry card. It does NOT recompute the full weekly
 * cross-dimension series (that's the Insights screen's job) — it just counts how many of the four
 * dimensions have data for the SELECTED day, so the card can say "N of 4 tracked" and invite a tap.
 *
 * Date-aware: reads are keyed on [SelectedDateHolder.selectedDate] via `flatMapLatest`, so the count
 * reflects the day being browsed. All per-day reads; never throws (missing dimension → not counted).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InsightsPreviewViewModel @Inject constructor(
    nutritionRepository: NutritionRepository,
    emotionsRepository: EmotionsRepository,
    vitalsRepository: VitalsRepository,
    activityRepository: ActivityRepository,
    selectedDateHolder: SelectedDateHolder,
) : ViewModel() {

    data class InsightsPreviewUiState(
        val dimensionsTracked: Int = 0,
        val totalDimensions: Int = 4,
        val isLoading: Boolean = true,
    )

    val uiState: StateFlow<InsightsPreviewUiState> =
        selectedDateHolder.selectedDate.flatMapLatest { date ->
            val iso = date.toString()
            combine(
                nutritionRepository.observeDaySummary(iso),
                emotionsRepository.observeWindow(date.toEpochDay(), date.toEpochDay()),
                vitalsRepository.observeVitalsForDay(iso),
                activityRepository.observeCaloriesOutForDay(iso),
            ) { nutrition, moods, vitals, caloriesOut ->
                var tracked = 0
                if (nutrition.caloriesConsumed > 0 || nutrition.waterMl > 0) tracked++
                if (moods.isNotEmpty()) tracked++
                if (vitals != null) tracked++
                if ((caloriesOut ?: 0.0) > 0) tracked++
                InsightsPreviewUiState(dimensionsTracked = tracked, isLoading = false)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = InsightsPreviewUiState(),
        )
}
