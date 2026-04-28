package com.hellohealth.domain.model

data class ActivityGoals(
    val steps: Int = 10000,
    val activeCalories: Int = 500,
    val activeMinutes: Int = 60
)
