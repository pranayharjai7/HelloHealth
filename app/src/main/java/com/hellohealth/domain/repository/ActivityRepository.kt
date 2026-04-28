package com.hellohealth.domain.repository

import android.content.Context
import android.content.Intent
import com.hellohealth.domain.model.ActivityGoals
import com.hellohealth.domain.model.HealthSummary

interface ActivityRepository {
    suspend fun fetchSummary(goals: ActivityGoals): HealthSummary
    suspend fun fetchWeeklyStats(): com.hellohealth.domain.model.WeeklyStats
    suspend fun hasPermissions(): Boolean
    fun getRequiredPermissions(): Set<String>
    fun getAvailability(): Int
    fun getSettingsIntent(context: Context): Intent
}
