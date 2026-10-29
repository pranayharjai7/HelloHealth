package com.hellohealth.ui.charts

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.hellohealth.ui.charts.interaction.ChartProjection
import com.hellohealth.ui.charts.interaction.InteractiveChart
import com.hellohealth.ui.charts.interaction.TooltipRow
import com.hellohealth.ui.charts.interaction.TooltipSpec
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One (wall-clock time, value) point for a time-of-day chart. */
data class TimePoint(val epochMs: Long, val value: Float)

/**
 * Fraction (0..1) of the local day that [epochMs] falls at — midnight → 0.0, noon → 0.5. Pure/testable.
 */
fun timeOfDayFraction(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): Float {
    val instant = Instant.ofEpochMilli(epochMs)
    val date = instant.atZone(zone).toLocalDate()
    val startOfDay = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val fraction = (epochMs - startOfDay).toDouble() / (24.0 * 60.0 * 60.0 * 1000.0)
    return fraction.coerceIn(0.0, 1.0).toFloat()
}

/**
 * A line chart on a WALL-CLOCK x-axis (00/06/12/18/24h) with a value y-axis + gridlines, and
 * scrub-and-pin interaction (drag to read "14:20 • 82 bpm"). Gaps wider than ~2h render as gaps
 * (no fake interpolation). Wraps the shared [ChartScaffold] (axes/grid) inside [InteractiveChart]
 * (crosshair/tooltip); a collapsible value table sits beneath.
 *
 * @param unit value unit shown on the y-axis + tooltip (e.g. "bpm").
 */
@Composable
fun TimeOfDayChart(
    points: List<TimePoint>,
    lineColor: Color,
    unit: String,
    gridColor: Color,
    labelColor: Color,
    zone: ZoneId = ZoneId.systemDefault(),
    modifier: Modifier = Modifier,
    maxGapFraction: Float = 1f / 12f,
) {
    if (points.size < 2) return
    val sorted = points.sortedBy { it.epochMs }
    val yAxis = niceAxis(sorted.minOf { it.value }, sorted.maxOf { it.value }, unit)
    val xAxis = hourAxis()
    val fmt = DateTimeFormatter.ofPattern("HH:mm")
    val density = androidx.compose.ui.platform.LocalDensity.current

    androidx.compose.foundation.layout.Column(modifier) {
        InteractiveChart(
            heightDp = 180.dp,
            accent = lineColor,
            project = { size ->
                val plot = ChartGutters.plotRect(size, hasX = true, hasY = true, density = density)
                ChartProjection(
                    size = size,
                    count = sorted.size,
                    anchorOf = { i ->
                        val x = plot.left + timeOfDayFraction(sorted[i].epochMs, zone) * plot.width
                        val y = plot.bottom - ((sorted[i].value - yAxis.min) / yAxis.span) * plot.height
                        Offset(x, y)
                    },
                )
            },
            tooltipFor = { i ->
                val t = Instant.ofEpochMilli(sorted[i].epochMs).atZone(zone).toLocalTime().format(fmt)
                TooltipSpec(title = t, rows = listOf(TooltipRow("", "${sorted[i].value.toInt()} $unit", lineColor)))
            },
        ) { m ->
            ChartScaffold(
                heightDp = 180.dp,
                xAxis = xAxis,
                yAxis = yAxis,
                gridColor = gridColor,
                labelColor = labelColor,
                modifier = m,
            ) { mapping ->
                drawTimeSegments(sorted, zone, maxGapFraction, lineColor) { frac, v ->
                    Offset(
                        mapping.plot.left + frac * mapping.plot.width,
                        mapping.mapY(v),
                    )
                }
            }
        }
    }
}

/** Draws the gap-broken line + gradient fill, mapping (dayFraction, value) → pixel via [toPixel]. */
private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTimeSegments(
    sorted: List<TimePoint>,
    zone: ZoneId,
    maxGapFraction: Float,
    lineColor: Color,
    toPixel: (frac: Float, v: Float) -> Offset,
) {
    val segments = mutableListOf<MutableList<TimePoint>>()
    var current = mutableListOf(sorted.first())
    for (i in 1 until sorted.size) {
        val gap = timeOfDayFraction(sorted[i].epochMs, zone) - timeOfDayFraction(sorted[i - 1].epochMs, zone)
        if (gap > maxGapFraction) { segments.add(current); current = mutableListOf(sorted[i]) }
        else current.add(sorted[i])
    }
    segments.add(current)

    val baselineY = size.height
    for (seg in segments) {
        if (seg.size < 2) continue
        val line = Path(); val fill = Path()
        seg.forEachIndexed { i, p ->
            val o = toPixel(timeOfDayFraction(p.epochMs, zone), p.value)
            if (i == 0) { line.moveTo(o.x, o.y); fill.moveTo(o.x, baselineY); fill.lineTo(o.x, o.y) }
            else { line.lineTo(o.x, o.y); fill.lineTo(o.x, o.y) }
        }
        val lastO = toPixel(timeOfDayFraction(seg.last().epochMs, zone), seg.last().value)
        fill.lineTo(lastO.x, baselineY); fill.close()
        drawPath(fill, brush = Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.30f), lineColor.copy(alpha = 0.02f))))
        drawPath(line, color = lineColor, style = Stroke(width = 4f, cap = StrokeCap.Round))
    }
}
