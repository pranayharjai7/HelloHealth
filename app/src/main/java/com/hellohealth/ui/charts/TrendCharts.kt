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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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

    val linePath = Path()
    val fillPath = Path()
    points.forEachIndexed { index, (px, py) ->
        val x = mapX(px)
        val y = mapY(py)
        if (index == 0) {
            linePath.moveTo(x, y); fillPath.moveTo(x, chartHeight); fillPath.lineTo(x, y)
        } else {
            linePath.lineTo(x, y); fillPath.lineTo(x, y)
        }
    }
    fillPath.lineTo(chartWidth, chartHeight)
    fillPath.close()

    drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.42f), lineColor.copy(alpha = 0.03f))),
    )
    drawPath(path = linePath, color = lineColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
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
        drawTrend(points, lineColor, strokeWidth = 6f, dotRadius = 8f)
    }
}

/** Compact sparkline (thin line, no fill emphasis dot) for the tile grid. */
@Composable
fun Sparkline(points: List<Pair<Float, Float>>, lineColor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(44.dp)) {
        drawTrend(points, lineColor, strokeWidth = 4f, dotRadius = 0f)
    }
}
