package com.hellohealth.ui.common

import kotlin.math.roundToInt

fun formatCalories(value: Double): String = "${value.roundToInt()} Cal"

fun formatProgress(current: Long, goal: Long): String = "$current/$goal"

fun formatProgress(current: Int, goal: Int): String = "$current/$goal"
