package com.hellohealth.domain.model

import java.time.Instant
import java.time.LocalDate

data class DailyHealthSnapshot(
    val date: LocalDate,
    val summary: HealthSummary,
    val snapshotTimezone: String,
    val lastSyncedAt: Instant,
    val syncStatus: SnapshotSyncStatus = SnapshotSyncStatus.COMPLETE,
    val dataSource: SnapshotDataSource = SnapshotDataSource.HEALTH_CONNECT
) {
    fun hasAnyData(): Boolean {
        return summary.steps > 0 ||
            summary.activeCalories > 0.0 ||
            summary.activeTimeMinutes > 0 ||
            summary.distanceKm > 0.0 ||
            summary.totalCalories > 0.0 ||
            summary.sleepDurationMinutes > 0 ||
            summary.weight != null ||
            summary.height != null ||
            summary.bodyFat != null ||
            summary.heartRateAvg != null ||
            summary.oxygenSaturation != null ||
            summary.bloodPressureSystolic != null ||
            summary.bloodPressureDiastolic != null ||
            summary.bloodGlucose != null ||
            summary.vo2max != null ||
            summary.exerciseSessions.isNotEmpty()
    }

    fun completionLevel(): Int {
        if (!hasAnyData()) return 1

        val stepProgress = summary.steps.toDouble() / summary.stepsGoal.coerceAtLeast(1)
        val calorieProgress = summary.activeCalories / summary.caloriesGoal.coerceAtLeast(1.0)
        val activeMinutesProgress = summary.activeTimeMinutes.toDouble() / summary.activeTimeGoal.coerceAtLeast(1)
        val averageProgress = listOf(stepProgress, calorieProgress, activeMinutesProgress).average()

        return when {
            averageProgress >= 1.0 -> 4
            averageProgress >= 0.66 -> 3
            averageProgress >= 0.33 -> 2
            else -> 1
        }
    }
}

enum class SnapshotSyncStatus {
    PARTIAL,
    COMPLETE
}

enum class SnapshotDataSource {
    HEALTH_CONNECT,
    MANUAL,
    MIXED
}
