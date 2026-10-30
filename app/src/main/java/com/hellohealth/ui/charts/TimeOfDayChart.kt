package com.hellohealth.ui.charts

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    // The series low/high (labelled inline on the plot) and a reference baseline (the first reading).
    val minV = sorted.minByOrNull { it.value }
    val maxV = sorted.maxByOrNull { it.value }
    val baselineValue = sorted.first().value
    // No-data spans (day-fraction) where the watch wasn't worn: the lead-in before the first reading,
    // the trailing stretch after the last, and any interior gap wider than maxGapFraction between two
    // readings. Used to shade those spans and to report "No reading" when the pointer lands in one.
    val gaps: List<Pair<Float, Float>> = remember(sorted, maxGapFraction, zone) {
        buildList {
            val firstFrac = timeOfDayFraction(sorted.first().epochMs, zone)
            val lastFrac = timeOfDayFraction(sorted.last().epochMs, zone)
            if (firstFrac > maxGapFraction) add(0f to firstFrac)
            for (i in 1 until sorted.size) {
                val a = timeOfDayFraction(sorted[i - 1].epochMs, zone)
                val b = timeOfDayFraction(sorted[i].epochMs, zone)
                if (b - a > maxGapFraction) add(a to b)
            }
            if (1f - lastFrac > maxGapFraction) add(lastFrac to 1f)
        }
    }

    androidx.compose.foundation.layout.Column(modifier) {
        // Holds the plot rect + active viewport computed in project() so the gap-tooltip can map
        // pointer-x → day-fraction through the same zoom/pan transform the marks use.
        var plotRect by remember { mutableStateOf<Rect?>(null) }
        var activeVp by remember { mutableStateOf(ChartViewport()) }
        InteractiveChart(
            heightDp = 180.dp,
            accent = lineColor,
            project = { size, vp ->
                val plot = ChartGutters.plotRect(size, hasX = true, hasY = true, density = density)
                plotRect = plot
                activeVp = vp
                ChartProjection(
                    size = size,
                    count = sorted.size,
                    anchorOf = { i ->
                        val x = plot.left + vp.toWindow(timeOfDayFraction(sorted[i].epochMs, zone)) * plot.width
                        val y = plot.bottom - ((sorted[i].value - yAxis.min) / yAxis.span) * plot.height
                        Offset(x, y)
                    },
                )
            },
            tooltipFor = { i ->
                val t = Instant.ofEpochMilli(sorted[i].epochMs).atZone(zone).toLocalTime().format(fmt)
                TooltipSpec(title = t, rows = listOf(TooltipRow("", "${sorted[i].value.toInt()} $unit", lineColor)))
            },
            tooltipAt = gapTooltip@{ pointerX, _ ->
                if (gaps.isEmpty()) return@gapTooltip null
                val plot = plotRect ?: return@gapTooltip null
                if (plot.width <= 0f) return@gapTooltip null
                // Invert the viewport: pointer pixel → window fraction → full-range day fraction.
                val windowFrac = (pointerX - plot.left) / plot.width
                val frac = (activeVp.panFraction + windowFrac * activeVp.windowFraction).coerceIn(0f, 1f)
                val inGap = gaps.any { (a, b) -> frac > a && frac < b }
                if (inGap) TooltipSpec(title = "No reading", rows = listOf(TooltipRow("", "device not worn", labelColor))) else null
            },
        ) { m, vp ->
            ChartScaffold(
                heightDp = 180.dp,
                xAxis = xAxis,
                yAxis = yAxis,
                gridColor = gridColor,
                labelColor = labelColor,
                modifier = m,
                viewport = vp,
            ) { mapping ->
                val toPixel = { frac: Float, v: Float ->
                    Offset(mapping.visibleX(frac), mapping.mapY(v))
                }
                // Shade interior gaps (device not worn) so a gap reads as known absence, not a blank chart.
                for ((a, b) in gaps) {
                    val x0 = mapping.visibleX(a)
                    val x1 = mapping.visibleX(b)
                    drawRect(
                        color = labelColor.copy(alpha = 0.06f),
                        topLeft = Offset(x0, mapping.plot.top),
                        size = Size(x1 - x0, mapping.plot.height),
                    )
                }
                // Faint dotted reference baseline at the first reading.
                val baseY = mapping.mapY(baselineValue)
                drawLine(
                    color = labelColor.copy(alpha = 0.35f),
                    start = Offset(mapping.plot.left, baseY),
                    end = Offset(mapping.plot.right, baseY),
                    strokeWidth = 1f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 8f)),
                )
                drawTimeSegments(sorted, zone, maxGapFraction, lineColor, toPixel)
                // Inline min/max value labels pinned at the low/high points.
                minV?.let { labelPoint(textMeasurer, toPixel(timeOfDayFraction(it.epochMs, zone), it.value), "${it.value.toInt()}", labelColor, lineColor, below = true) }
                maxV?.let { labelPoint(textMeasurer, toPixel(timeOfDayFraction(it.epochMs, zone), it.value), "${it.value.toInt()}", labelColor, lineColor, below = false) }
            }
        }
        // Caption only when there is actually a shaded no-data span — never on a fully-worn day.
        if (gaps.isNotEmpty()) {
            androidx.compose.material3.Text(
                "Shaded stretches are gaps — the watch wasn't worn.",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = labelColor.copy(alpha = 0.9f),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Draws the gap-broken ribbon line + faint fill, mapping (dayFraction, value) → pixel via [toPixel]. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTimeSegments(
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
        val pixels = seg.map { toPixel(timeOfDayFraction(it.epochMs, zone), it.value) }
        if (pixels.size < 2) {
            // A lone reading between two gaps: draw it as a soft dot rather than dropping it.
            pixels.firstOrNull()?.let { drawCircle(lineColor, radius = 4f, center = it) }
            continue
        }
        val line = smoothPath(pixels)
        val fill = smoothPath(pixels).apply {
            lineTo(pixels.last().x, baselineY)
            lineTo(pixels.first().x, baselineY)
            close()
        }
        // Faint fade so the ribbon line is the hero (not a heavy area block).
        drawPath(fill, brush = Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.14f), lineColor.copy(alpha = 0.0f))))
        drawPath(line, color = lineColor, style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * Pins a small value label (with a marker dot) at a data point — the Revolut-style inline min/max.
 * [below] is the PREFERRED side (low label below the point, high label above), but the label flips to
 * the other side when the preferred side would overflow the plot, and its x is clamped inside the plot
 * — so a max near the top never collides with the top axis tick, and a label never clips at the edges.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.labelPoint(
    measurer: androidx.compose.ui.text.TextMeasurer,
    at: Offset,
    text: String,
    labelColor: Color,
    markerColor: Color,
    below: Boolean,
) {
    drawCircle(markerColor, radius = 4f, center = at)
    val style = androidx.compose.ui.text.TextStyle(color = labelColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    val laid = measurer.measure(text, style)
    val gap = 10f
    val aboveY = at.y - laid.size.height - gap
    val belowY = at.y + gap
    // Preferred side, but flip if it would overflow the plot vertically.
    val ty = when {
        below && belowY + laid.size.height <= size.height -> belowY
        below -> aboveY
        !below && aboveY >= 0f -> aboveY
        else -> belowY
    }
    val tx = (at.x - laid.size.width / 2f).coerceIn(0f, size.width - laid.size.width)
    drawText(laid, topLeft = Offset(tx, ty.coerceIn(0f, size.height - laid.size.height)))
}
