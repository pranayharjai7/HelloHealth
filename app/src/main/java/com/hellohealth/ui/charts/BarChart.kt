package com.hellohealth.ui.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** One bar: a [value] with an optional axis [label] shown beneath selected bars (e.g. hour ticks). */
data class BarDatum(val value: Float, val label: String? = null)

/**
 * A simple, dataviz-compliant bar chart (single series, one y-axis). Rounded-top bars anchored to the
 * baseline, a 2px surface gap between bars, a reveal animation, and a direct value label on the tallest
 * bar — identity is never color-alone (the caller supplies a title + the hue comes from [ChartPalette]
 * via [barColor]). Sparse [BarDatum.label]s render as x-axis ticks. Empty / all-zero inputs draw a flat
 * baseline rather than crashing.
 *
 * Follows the `drawTrend` conventions in [TrendCharts] (index x, baseline anchoring). Built for the
 * Phase-1 "steps / calories through the day" hourly graphs; reused anywhere a per-bucket magnitude is
 * shown.
 */
@Composable
fun BarChart(
    bars: List<BarDatum>,
    barColor: Color,
    modifier: Modifier = Modifier,
) {
    val progress: Float by animateFloatAsState(1f, tween(650), label = "barReveal")
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(top = 8.dp),
        ) {
            if (bars.isEmpty()) return@Canvas
            val maxValue = bars.maxOf { it.value }.coerceAtLeast(0.0001f)
            val n = bars.size
            val gapPx = 2.dp.toPx()
            val slot = size.width / n
            val barWidth = (slot - gapPx).coerceAtLeast(1f)
            val chartHeight = size.height
            val maxIndex = bars.indexOfFirst { it.value == bars.maxOf { b -> b.value } }

            bars.forEachIndexed { i, bar ->
                val h = (bar.value / maxValue) * chartHeight * progress
                if (h <= 0f) return@forEachIndexed
                val left = i * slot + gapPx / 2f
                val top = chartHeight - h
                drawRoundRect(
                    color = if (i == maxIndex) barColor else barColor.copy(alpha = 0.72f),
                    topLeft = Offset(left, top),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                )
            }
        }
    }
}
