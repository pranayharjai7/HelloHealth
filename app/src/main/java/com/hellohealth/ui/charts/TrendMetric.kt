package com.hellohealth.ui.charts

import androidx.compose.ui.graphics.Color

/**
 * One metric's trend series, ready for the hybrid chart layout. [points] are (x,y) where x is the
 * chronological index (0-based) and y the value; empty/size-1 series render an empty/placeholder
 * state. [latestFormatted] is the pre-formatted latest value (e.g. "75.0 kg"); [lastRecorded] is the
 * relative label (e.g. "2 days ago") or null.
 */
data class TrendMetric(
    val title: String,
    val unit: String,
    val color: Color,
    val points: List<Pair<Float, Float>>,
    val latestFormatted: String?,
    val lastRecorded: String?,
)
