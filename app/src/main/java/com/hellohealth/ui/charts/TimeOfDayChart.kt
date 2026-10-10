package com.hellohealth.ui.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId

/** One (wall-clock time, value) point for a time-of-day chart. */
data class TimePoint(val epochMs: Long, val value: Float)

/**
 * Fraction (0..1) of the local day that [epochMs] falls at — midnight → 0.0, noon → 0.5, just before
 * next-midnight → ~1.0. Pure/testable; used to place points on the wall-clock x-axis.
 */
fun timeOfDayFraction(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): Float {
    val instant = Instant.ofEpochMilli(epochMs)
    val date = instant.atZone(zone).toLocalDate()
    val startOfDay = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val fraction = (epochMs - startOfDay).toDouble() / (24.0 * 60.0 * 60.0 * 1000.0)
    return fraction.coerceIn(0.0, 1.0).toFloat()
}

/**
 * A line chart whose x-axis is WALL-CLOCK TIME across the local day (not a dense index), so a reading
 * at 6am and one at 10pm sit where they belong. Reuses [TrendCharts]' gradient-fill + reveal style.
 * Gaps between non-adjacent points render as GAPS (no interpolation across missing hours — honesty).
 * One y-axis; the caller supplies a title + [lineColor] from [ChartPalette]. Empty / single-point →
 * nothing drawn (the card shows its empty state).
 *
 * Built for the Phase-1 "heart rate through the day" graph; reused by the Phase-2 hypnogram axis and
 * Phase-3/4 intraday curves.
 */
@Composable
fun TimeOfDayChart(
    points: List<TimePoint>,
    lineColor: Color,
    zone: ZoneId = ZoneId.systemDefault(),
    modifier: Modifier = Modifier,
    maxGapFraction: Float = 1f / 12f, // break the line across gaps wider than ~2h
) {
    val progress: Float by animateFloatAsState(1f, tween(850), label = "todReveal")
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .graphicsLayer(alpha = progress, translationY = (1f - progress) * 24f),
    ) {
        if (points.size < 2) return@Canvas
        val sorted = points.sortedBy { it.epochMs }
        val minY = sorted.minOf { it.value }
        val maxY = sorted.maxOf { it.value }
        val yPad = if (kotlin.math.abs(maxY - minY) < 0.01f) 1f else (maxY - minY) * 0.12f
        val lo = minY - yPad
        val hi = maxY + yPad

        fun mapX(epochMs: Long): Float = timeOfDayFraction(epochMs, zone) * size.width
        fun mapY(v: Float): Float = size.height - ((v - lo) / (hi - lo)) * size.height

        // Build segments, breaking where the time gap exceeds maxGapFraction (gaps stay gaps).
        val segments = mutableListOf<MutableList<TimePoint>>()
        var current = mutableListOf(sorted.first())
        for (i in 1 until sorted.size) {
            val gap = timeOfDayFraction(sorted[i].epochMs, zone) - timeOfDayFraction(sorted[i - 1].epochMs, zone)
            if (gap > maxGapFraction) {
                segments.add(current); current = mutableListOf(sorted[i])
            } else current.add(sorted[i])
        }
        segments.add(current)

        for (seg in segments) {
            if (seg.size < 2) continue
            val line = Path()
            val fill = Path()
            seg.forEachIndexed { i, p ->
                val x = mapX(p.epochMs); val y = mapY(p.value)
                if (i == 0) { line.moveTo(x, y); fill.moveTo(x, size.height); fill.lineTo(x, y) }
                else { line.lineTo(x, y); fill.lineTo(x, y) }
            }
            fill.lineTo(mapX(seg.last().epochMs), size.height); fill.close()
            drawPath(fill, brush = Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.35f), lineColor.copy(alpha = 0.02f))))
            drawPath(line, color = lineColor, style = Stroke(width = 5f, cap = StrokeCap.Round))
        }
        // Emphasise the latest reading.
        sorted.lastOrNull()?.let { drawCircle(lineColor, radius = 7f, center = Offset(mapX(it.epochMs), mapY(it.value))) }
    }
}
