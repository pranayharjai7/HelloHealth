package com.hellohealth.domain.repository

import android.content.Context
import android.content.Intent
import com.hellohealth.domain.model.ActivityDetail
import com.hellohealth.domain.model.ActivityGoals
import com.hellohealth.domain.model.DailyHealthSnapshot
import com.hellohealth.domain.model.HealthSummary
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
    fun getRequiredPermissions(): Set<String>
    fun getAvailability(): Int
    fun getSettingsIntent(context: Context): Intent
}
