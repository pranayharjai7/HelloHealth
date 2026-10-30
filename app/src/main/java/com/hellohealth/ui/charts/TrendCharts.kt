package com.hellohealth.ui.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Shared Canvas path math: maps (index,value) points into the draw area with a 12% y-pad. */
private fun DrawScope.drawTrend(
    points: List<Pair<Float, Float>>,
    lineColor: Color,
    strokeWidth: Float,
    dotRadius: Float,
) {
    if (points.isEmpty()) return
    val chartWidth = size.width
    val chartHeight = size.height
    val safeMaxX = (points.maxOf { it.first }).coerceAtLeast(1f)
    val rawMinY = points.minOf { it.second }
    val rawMaxY = points.maxOf { it.second }
    val yPadding = if (abs(rawMaxY - rawMinY) < 0.01f) 1f else (rawMaxY - rawMinY) * 0.12f
    val minY = rawMinY - yPadding
    val maxY = rawMaxY + yPadding

    fun mapX(x: Float): Float = (x / safeMaxX) * chartWidth
    fun mapY(value: Float): Float = chartHeight - (((value - minY) / (maxY - minY)) * chartHeight)

    val pixels = points.map { (px, py) -> Offset(mapX(px), mapY(py)) }
    // Smooth, thread-like line through the points (clamped so it never overshoots the data).
    val linePath = smoothPath(pixels)
    // Fill re-uses the same smoothed top edge, then closes down to the baseline.
    val fillPath = smoothPath(pixels).apply {
        lineTo(chartWidth, chartHeight)
        lineTo(pixels.first().x, chartHeight)
        close()
    }

    drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.16f), lineColor.copy(alpha = 0.0f))),
    )
    drawPath(path = linePath, color = lineColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
    if (dotRadius > 0f) {
        points.lastOrNull()?.let { (px, py) -> drawCircle(color = lineColor, radius = dotRadius, center = Offset(mapX(px), mapY(py))) }
    }
}

/** Full trend line (gradient fill + emphasised last point) with the reveal animation. */
@Composable
fun TrendLine(points: List<Pair<Float, Float>>, lineColor: Color, modifier: Modifier = Modifier) {
    val progress: Float by animateFloatAsState(1f, tween(850), label = "trendReveal")
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .graphicsLayer(alpha = progress, translationY = (1f - progress) * 36f),
    ) {
        drawTrend(points, lineColor, strokeWidth = 5f, dotRadius = 7f)
    }
}

/** Compact sparkline (thin line, no fill emphasis dot) for the tile grid. */
@Composable
fun Sparkline(points: List<Pair<Float, Float>>, lineColor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(44.dp)) {
        drawTrend(points, lineColor, strokeWidth = 3.5f, dotRadius = 0f)
    }
}

/**
 * The full, readable, interactive trend chart used by the Vitals & Body trends screens — a value
 * y-axis (clean ticks + unit), sparse date x-ticks (from [TrendMetric.pointLabels]), hairline
 * gridlines, scrub-and-pin crosshair + tooltip ("Oct 6 · 74.1 kg"), and a Show-values table. Wraps the
 * shared [ChartScaffold] inside [InteractiveChart]; the per-point draw reuses the same gradient-fill
 * style as [TrendLine]. Falls back to point index when no date labels are supplied.
 */
@Composable
fun InteractiveTrendLine(
    metric: TrendMetric,
    gridColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier,
) {
    val pts = metric.points
    if (pts.size < 2) return
    val yAxis = niceAxis(pts.minOf { it.second }, pts.maxOf { it.second }, metric.unit)
    val maxIndex = pts.maxOf { it.first }.coerceAtLeast(1f)
    val labels = metric.pointLabels
    // Sparse x-ticks: first, middle, last date label (if available).
    val xAxis = if (labels.size == pts.size && labels.isNotEmpty()) {
        val idxs = listOf(0, pts.size / 2, pts.size - 1).distinct()
        Axis(
            min = 0f, max = 1f,
            ticks = idxs.map { pts[it].first / maxIndex },
            format = { frac -> labels[(frac * maxIndex).toInt().coerceIn(0, labels.lastIndex)] },
        )
    } else null
    val density = androidx.compose.ui.platform.LocalDensity.current

    androidx.compose.foundation.layout.Column(modifier) {
        com.hellohealth.ui.charts.interaction.InteractiveChart(
            heightDp = 180.dp,
            accent = metric.color,
            project = { size, vp ->
                val plot = ChartGutters.plotRect(size, hasX = xAxis != null, hasY = true, density = density)
                com.hellohealth.ui.charts.interaction.ChartProjection(
                    size = size,
                    count = pts.size,
                    anchorOf = { i ->
                        val x = plot.left + vp.toWindow(pts[i].first / maxIndex) * plot.width
                        val y = plot.bottom - ((pts[i].second - yAxis.min) / yAxis.span) * plot.height
                        Offset(x, y)
                    },
                )
            },
            tooltipFor = { i ->
                val title = labels.getOrNull(i) ?: "Point ${i + 1}"
                com.hellohealth.ui.charts.interaction.TooltipSpec(
                    title = title,
                    rows = listOf(com.hellohealth.ui.charts.interaction.TooltipRow("", formatValue(pts[i].second, metric.unit), metric.color)),
                )
            },
        ) { m, vp ->
            ChartScaffold(heightDp = 180.dp, xAxis = xAxis, yAxis = yAxis, gridColor = gridColor, labelColor = labelColor, modifier = m, viewport = vp) { mapping ->
                drawTrendInRect(pts, metric.color, mapping, maxIndex, yAxis)
            }
        }
    }
}

private fun formatValue(v: Float, unit: String): String {
    val s = if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(v)
    return if (unit.isBlank()) s else "$s $unit"
}

/** Draws the gradient-fill trend line into the scaffold's plot rect using its mapping. */
private fun DrawScope.drawTrendInRect(
    points: List<Pair<Float, Float>>,
    lineColor: Color,
    mapping: ChartMapping,
    maxIndex: Float,
    yAxis: Axis,
) {
    val plot = mapping.plot
    fun px(i: Int) = Offset(
        mapping.visibleX(points[i].first / maxIndex),
        plot.bottom - ((points[i].second - yAxis.min) / yAxis.span) * plot.height,
    )
    val pixels = points.indices.map { px(it) }
    val line = smoothPath(pixels)
    val fill = smoothPath(pixels).apply {
        lineTo(pixels.last().x, plot.bottom)
        lineTo(pixels.first().x, plot.bottom)
        close()
    }
    drawPath(fill, brush = Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.16f), lineColor.copy(alpha = 0.0f))))
    drawPath(line, color = lineColor, style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    points.lastOrNull()?.let { drawCircle(lineColor, radius = 7f, center = px(points.lastIndex)) }
}
