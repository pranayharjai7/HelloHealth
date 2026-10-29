package com.hellohealth.ui.charts.interaction

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure geometry a chart publishes so the shared overlay can hit-test and anchor a tooltip. Computed
 * from the Canvas size; carries NO Compose state. [anchorOf] maps a data index to its on-canvas point
 * (line/time/scatter: the vertex; bar: top-center). [hitRadiusPx] null ⇒ nearest-X scrub (line/time);
 * non-null ⇒ nearest-mark-within-radius (bar/scatter).
 */
data class ChartProjection(
    val size: Size,
    val count: Int,
    val anchorOf: (Int) -> Offset,
    val hitRadiusPx: Float? = null,
)

/** The tooltip content for a selected index: a title (e.g. the X label) + one row per series. */
data class TooltipSpec(val title: String, val rows: List<TooltipRow>)
data class TooltipRow(val label: String, val value: String, val swatch: Color? = null)

enum class CrosshairStyle { Vertical, MarkLift }

/**
 * Wraps a chart's Canvas [chartBody] and adds the shared, scrub-and-pin interaction layer the dataviz
 * method requires: drag scrubs a crosshair that snaps to the nearest data point; releasing leaves the
 * selection PINNED; tapping empty space clears it. One `pointerInput` + one hit-test + one clamped
 * tooltip serve every chart — charts contribute only pure [project] geometry and [tooltipFor]
 * formatting (reusing the mapX/mapY they already compute), never their own pointer code.
 */
@Composable
fun InteractiveChart(
    heightDp: Dp,
    accent: Color,
    project: (Size) -> ChartProjection,
    tooltipFor: (Int) -> TooltipSpec,
    modifier: Modifier = Modifier,
    crosshair: CrosshairStyle = CrosshairStyle.Vertical,
    chartBody: @Composable (Modifier) -> Unit,
) {
    var selected by remember { mutableStateOf(-1) }
    var projection by remember { mutableStateOf<ChartProjection?>(null) }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val minHit = with(density) { 24.dp.toPx() }

    Box(modifier.fillMaxWidth().height(heightDp)) {
        chartBody(Modifier.matchParentSize().onSizeChanged { projection = project(it.toSize()) })

        Box(
            Modifier
                .matchParentSize()
                .pointerInput(projection) {
                    val proj = projection ?: return@pointerInput
                    // One unified handler distinguishes tap (clear) from drag (scrub) by movement,
                    // avoiding the two-sibling-detector down-event conflict.
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitPointerEvent()
                            val first = down.changes.firstOrNull() ?: continue
                            if (!first.pressed) continue
                            val hit = hitTest(proj, first.position, minHit)
                            if (hit >= 0) { selected = hit; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                            else selected = -1 // tap on empty space clears the pin
                        }
                    }
                }
                .pointerInput(projection) {
                    val proj = projection ?: return@pointerInput
                    detectHorizontalDrag { pos ->
                        val i = hitTest(proj, pos, minHit)
                        if (i >= 0 && i != selected) { selected = i; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    }
                }
        ) {
            val proj = projection
            if (proj != null && selected in 0 until proj.count) {
                CrosshairLayer(proj, selected, accent, crosshair)
                TooltipBubble(proj.anchorOf(selected), tooltipFor(selected), proj.size)
            }
        }
    }
}

/** Scrub detector: reports the pointer position on down + every horizontal move. */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectHorizontalDrag(
    onPos: (Offset) -> Unit,
) {
    awaitPointerEventScope {
        while (true) {
            val down = awaitPointerEvent()
            val change = down.changes.firstOrNull { it.pressed } ?: continue
            onPos(change.position)
            change.consume()
        }
    }
}

/** Nearest data index for a touch position. Line/time ⇒ nearest X; bar/scatter ⇒ nearest within radius. */
internal fun hitTest(p: ChartProjection, pos: Offset, minHitPx: Float): Int {
    if (p.count == 0) return -1
    return if (p.hitRadiusPx == null) {
        (0 until p.count).minByOrNull { abs(p.anchorOf(it).x - pos.x) } ?: -1
    } else {
        var best = -1; var bestD = Float.MAX_VALUE
        for (i in 0 until p.count) {
            val d = (p.anchorOf(i) - pos).getDistance()
            if (d < bestD) { bestD = d; best = i }
        }
        if (bestD <= maxOf(p.hitRadiusPx, minHitPx / 2f)) best else -1
    }
}

@Composable
private fun CrosshairLayer(p: ChartProjection, index: Int, accent: Color, style: CrosshairStyle) {
    val surface = MaterialTheme.colorScheme.surface
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val a = p.anchorOf(index)
        when (style) {
            CrosshairStyle.Vertical -> {
                drawLine(accent.copy(alpha = 0.45f), Offset(a.x, 0f), Offset(a.x, size.height), strokeWidth = 2f)
                drawCircle(surface, radius = 9f, center = a)         // surface ring
                drawCircle(accent, radius = 6f, center = a)
            }
            CrosshairStyle.MarkLift -> {
                drawCircle(surface, radius = 11f, center = a)
                drawCircle(accent, radius = 8f, center = a)
            }
        }
    }
}

@Composable
private fun TooltipBubble(anchor: Offset, spec: TooltipSpec, size: Size) {
    var bubble by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .onSizeChanged { bubble = it }
            .offset {
                val margin = with(density) { 8.dp.toPx() }
                var x = anchor.x + margin
                if (x + bubble.width > size.width) x = anchor.x - bubble.width - margin
                x = x.coerceIn(0f, (size.width - bubble.width).coerceAtLeast(0f))
                var y = anchor.y - bubble.height - margin
                if (y < 0f) y = anchor.y + margin
                y = y.coerceIn(0f, (size.height - bubble.height).coerceAtLeast(0f))
                IntOffset(x.roundToInt(), y.roundToInt())
            },
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(spec.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface)
            spec.rows.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    r.swatch?.let {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(it)); Spacer(Modifier.width(6.dp))
                    }
                    if (r.label.isNotBlank()) {
                        Text(r.label, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(r.value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

