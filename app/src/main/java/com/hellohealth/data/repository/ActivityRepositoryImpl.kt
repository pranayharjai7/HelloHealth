package com.hellohealth.data.repository

import android.content.Context
import android.content.Intent
import com.hellohealth.data.health.HealthConnectManager
import com.hellohealth.domain.model.ActivityGoals
import com.hellohealth.domain.model.HealthSummary
import com.hellohealth.domain.repository.ActivityRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityRepositoryImpl @Inject constructor(
    private val healthConnectManager: HealthConnectManager
) : ActivityRepository {
    override suspend fun fetchSummary(goals: ActivityGoals): HealthSummary {
        return if (healthConnectManager.isAvailable && healthConnectManager.hasAllPermissions()) {
            healthConnectManager.fetchHealthSummary(goals)
        } else {
            HealthSummary(
                stepsGoal = goals.steps.toLong(),
                caloriesGoal = goals.activeCalories.toDouble(),
                activeTimeGoal = goals.activeMinutes.toLong()
            )
        }
    }

    override suspend fun fetchWeeklyStats(): com.hellohealth.domain.model.WeeklyStats {
        return if (healthConnectManager.isAvailable && healthConnectManager.hasAllPermissions()) {
            healthConnectManager.fetchWeeklyStats()
        } else {
            com.hellohealth.domain.model.WeeklyStats()
        }
    }

    override suspend fun hasPermissions(): Boolean {
        return healthConnectManager.hasAllPermissions()
    }

    override fun getRequiredPermissions(): Set<String> {
        return healthConnectManager.permissions
    }

    override fun getAvailability(): Int {
        return healthConnectManager.getAvailability()
    }

    override fun getSettingsIntent(context: Context): Intent {
        return healthConnectManager.getHealthConnectSettingsIntent()
    }
}
