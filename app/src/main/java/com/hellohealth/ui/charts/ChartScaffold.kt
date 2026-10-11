package com.hellohealth.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Maps data values into the plot rect. Shared by [ChartScaffold] and the interaction overlay so the
 * drawn marks and the hit-testing use identical geometry.
 */
class ChartMapping(
    val plot: Rect,
    private val xAxis: Axis?,
    private val yAxis: Axis?,
) {
    /** Map a data-x (same units as [xAxis], or a 0..n index when xAxis is null) to a pixel x. */
    fun mapX(x: Float, indexMax: Float): Float {
        val a = xAxis
        return if (a != null) plot.left + ((x - a.min) / a.span) * plot.width
        else plot.left + (if (indexMax <= 0f) 0f else x / indexMax) * plot.width
    }

    /** Map a data-y to a pixel y (inverted — larger values higher). */
    fun mapY(v: Float): Float {
        val a = yAxis ?: return plot.bottom
        return plot.bottom - ((v - a.min) / a.span) * plot.height
    }
}

/** Fixed gutter sizes so a chart's interaction overlay can reproduce the EXACT same plot rect without
 *  re-measuring text (keeps crosshair anchors aligned with the drawn marks). */
object ChartGutters {
    val left = 34.dp      // y-tick labels
    val bottom = 16.dp    // x-tick labels
    val top = 6.dp

    /** The inner plot rect for a given canvas [size], given which axes are present. */
    fun plotRect(size: Size, hasX: Boolean, hasY: Boolean, density: androidx.compose.ui.unit.Density): Rect {
        with(density) {
            return Rect(
                left = if (hasY) left.toPx() else 0f,
                top = top.toPx(),
                right = size.width,
                bottom = size.height - if (hasX) bottom.toPx() else 0f,
            )
        }
    }
}

/**
 * The shared chart chrome: reserves a left gutter for y-tick labels and a bottom gutter for x-tick
 * labels, draws recessive 1px solid hairline gridlines + tick labels (in muted TEXT tokens, never the
 * series color), then hands the inner plot [Rect] + a [ChartMapping] to [plot] so the chart's existing
 * Canvas math draws inside it almost unchanged. Gutters are FIXED ([ChartGutters]) so an interaction
 * overlay can reproduce the same plot rect for aligned hit-testing.
 */
@Composable
fun ChartScaffold(
    heightDp: Dp,
    xAxis: Axis?,
    yAxis: Axis?,
    gridColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier,
    plot: DrawScope.(mapping: ChartMapping) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val tickStyle = TextStyle(color = labelColor, fontSize = 10.sp)

    Canvas(modifier = modifier.fillMaxWidth().height(heightDp)) {
        val plotRect = ChartGutters.plotRect(size, hasX = xAxis != null, hasY = yAxis != null, density = this)
        val mapping = ChartMapping(plotRect, xAxis, yAxis)

        // Horizontal gridlines + y-tick labels at each nice tick inside the band.
        yAxis?.ticks?.forEach { t ->
            if (t < yAxis.min - 0.001f || t > yAxis.max + 0.001f) return@forEach
            val y = mapping.mapY(t)
            drawLine(gridColor, Offset(plotRect.left, y), Offset(plotRect.right, y), strokeWidth = 1f)
            val layout = measurer.measure(yAxis.format(t), tickStyle)
            drawText(layout, topLeft = Offset(plotRect.left - layout.size.width - 4.dp.toPx(), y - layout.size.height / 2f))
        }

        // X-tick labels along the bottom gutter (no vertical grid — keeps it quiet).
        xAxis?.ticks?.forEach { t ->
            val x = mapping.mapX(t, 1f)
            val layout = measurer.measure(xAxis.format(t), tickStyle)
            val tx = (x - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(tx, size.height - layout.size.height))
        }

        // The chart's own marks, clipped to the plot rect.
        clipRect(plotRect.left, plotRect.top, plotRect.right, plotRect.bottom) {
            plot(mapping)
        }
    }
}
