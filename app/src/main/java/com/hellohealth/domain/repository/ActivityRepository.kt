package com.hellohealth.domain.repository

import android.content.Context
import android.content.Intent
import com.hellohealth.domain.model.WorkoutSummary
import kotlinx.coroutines.flow.Flow

interface ActivityRepository {
    fun getAvailability(): Int
    fun isAvailable(): Boolean
    fun getSettingsIntent(context: Context): Intent
    fun getRequiredPermissions(): Set<String>
    suspend fun hasPermissions(): Boolean
    suspend fun fetchSummary(): WorkoutSummary
    fun sync(): Flow<Result<WorkoutSummary>>
}
