package com.hellohealth.domain.model

import java.time.LocalDate

data class DailyStat(
    val date: LocalDate,
    val steps: Long,
    val calories: Double,
    val activeMinutes: Long,
    val sleepMinutes: Long,
    val avgHeartRate: Int
)

data class WeeklyStats(
    val dailyStats: List<DailyStat> = emptyList()
)
