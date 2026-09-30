package com.hellohealth.domain.repository

import android.content.Context
import android.content.Intent
import com.hellohealth.domain.model.ActivityDetail
import com.hellohealth.domain.model.ActivityGoals
import com.hellohealth.domain.model.BodyMetrics
import com.hellohealth.domain.model.DailyHealthSnapshot
import com.hellohealth.domain.model.HealthSummary
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

interface ActivityRepository {
    suspend fun fetchSummary(
        goals: ActivityGoals,
        date: LocalDate = LocalDate.now(),
        forceRefresh: Boolean = false
    ): HealthSummary
    suspend fun getHistoryForMonth(month: YearMonth): List<DailyHealthSnapshot>
    suspend fun getExerciseSessionDetail(
        sessionId: String,
        startTimeHint: Instant? = null,
        endTimeHint: Instant? = null
    ): ActivityDetail?
    suspend fun fetchWeeklyStats(): com.hellohealth.domain.model.WeeklyStats
    suspend fun hasPermissions(): Boolean
    /**
     * Latest height + weight from Health Connect for onboarding pre-fill (metric). Returns empty
     * fields when Health Connect is unavailable, unconnected, or holds no such records — never throws.
     */
    suspend fun fetchLatestBodyMetrics(): BodyMetrics
    /**
     * Today's "calories out" (active + BMR) from the persisted daily snapshot, as a reactive Flow so
     * the Nutrition card can compute energy balance (`net = caloriesOut − caloriesIn`) at read time.
     * Emits 0.0 when there is no signed-in user or no snapshot for today; never throws.
     */
    fun observeTodayCaloriesOut(): Flow<Double>
    /**
     * "Calories out" (active + BMR) for a specific local day (ISO `yyyy-MM-dd`), as a reactive Flow,
     * so a date-aware card can compute energy balance for the selected day. Emits **null** when there
     * is no signed-in user OR no persisted snapshot for that day — the caller must dash (not zero) a
     * missing day rather than imply a real 0-kcal burn. Never throws.
     */
    fun observeCaloriesOutForDay(localDate: String): Flow<Double?>
    fun getRequiredPermissions(): Set<String>
    fun getAvailability(): Int
    fun getSettingsIntent(context: Context): Intent
}
