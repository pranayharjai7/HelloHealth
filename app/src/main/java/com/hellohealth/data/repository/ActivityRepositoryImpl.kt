package com.hellohealth.data.repository

import android.content.Context
import android.content.Intent
import com.hellohealth.data.health.HealthConnectManager
import com.hellohealth.domain.model.HealthSummary
import com.hellohealth.domain.repository.ActivityRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityRepositoryImpl @Inject constructor(
    private val healthConnectManager: HealthConnectManager
) : ActivityRepository {
    override suspend fun fetchSummary(): HealthSummary {
        return if (healthConnectManager.isAvailable && healthConnectManager.hasAllPermissions()) {
            healthConnectManager.fetchHealthSummary()
        } else {
            HealthSummary()
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
