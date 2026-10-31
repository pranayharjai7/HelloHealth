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
 * Horizontal zoom + pan state for a chart. [scale] ≥ 1 (1 = whole range visible); [panFraction] is the
 * left edge of the visible window expressed as a fraction of the FULL data range, clamped so the window
 * [panFraction, panFraction + 1/scale] always stays within [0,1]. At scale 1 this is the identity, so a
 * chart that never zooms behaves exactly as before.
 */
data class ChartViewport(val scale: Float = 1f, val panFraction: Float = 0f) {
    /** Visible window width as a fraction of the full range. */
    val windowFraction: Float get() = 1f / scale.coerceAtLeast(1f)

    /** Clamp so the window never runs off either end of the data. */
    fun clamped(): ChartViewport {
        val s = scale.coerceAtLeast(1f)
        val maxPan = (1f - 1f / s).coerceAtLeast(0f)
        return ChartViewport(s, panFraction.coerceIn(0f, maxPan))
    }

    /** A full-range fraction f∈[0,1] → its fraction WITHIN the visible window (may be <0 or >1 = off-screen). */
    fun toWindow(f: Float): Float = (f - panFraction) / windowFraction

    val isIdentity: Boolean get() = scale <= 1.0001f && panFraction == 0f
}

/**
 * Maps data values into the plot rect. Shared by [ChartScaffold] and the interaction overlay so the
 * drawn marks and the hit-testing use identical geometry.
 */
class ChartMapping(
    val plot: Rect,
    private val xAxis: Axis?,
    private val yAxis: Axis?,
    val viewport: ChartViewport = ChartViewport(),
) {
    /** Map a FULL-range fraction (0..1) to a pixel x through the current viewport. */
    fun visibleX(fraction: Float): Float = plot.left + viewport.toWindow(fraction) * plot.width

    /** Map a data-x (same units as [xAxis], or a 0..n index when xAxis is null) to a pixel x. */
    fun mapX(x: Float, indexMax: Float): Float {
        val a = xAxis
        val fullFrac = if (a != null) ((x - a.min) / a.span)
        else (if (indexMax <= 0f) 0f else x / indexMax)
        return visibleX(fullFrac)
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
    viewport: ChartViewport = ChartViewport(),
    plot: DrawScope.(mapping: ChartMapping) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val tickStyle = TextStyle(color = labelColor, fontSize = 10.sp)

    Canvas(modifier = modifier.fillMaxWidth().height(heightDp)) {
        val plotRect = ChartGutters.plotRect(size, hasX = xAxis != null, hasY = yAxis != null, density = this)
        val mapping = ChartMapping(plotRect, xAxis, yAxis, viewport)

        // Horizontal gridlines + y-tick labels at each nice tick inside the band.
        yAxis?.ticks?.forEach { t ->
            if (t < yAxis.min - 0.001f || t > yAxis.max + 0.001f) return@forEach
            val y = mapping.mapY(t)
            drawLine(gridColor, Offset(plotRect.left, y), Offset(plotRect.right, y), strokeWidth = 1f)
            val layout = measurer.measure(yAxis.format(t), tickStyle)
            drawText(layout, topLeft = Offset(plotRect.left - layout.size.width - 4.dp.toPx(), y - layout.size.height / 2f))
        }

        // X-tick labels along the bottom gutter (no vertical grid — keeps it quiet). When zoomed, cull
        // ticks whose position falls outside the visible window so labels stay meaningful.
        xAxis?.ticks?.forEach { t ->
            val fullFrac = (t - xAxis.min) / xAxis.span
            val w = viewport.toWindow(fullFrac)
            if (w < -0.001f || w > 1.001f) return@forEach
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
