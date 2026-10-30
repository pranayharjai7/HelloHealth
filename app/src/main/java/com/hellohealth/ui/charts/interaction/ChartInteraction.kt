package com.hellohealth.ui.charts.interaction

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import com.hellohealth.ui.charts.ChartGutters
import com.hellohealth.ui.charts.ChartViewport
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
    project: (Size, ChartViewport) -> ChartProjection,
    tooltipFor: (Int) -> TooltipSpec,
    modifier: Modifier = Modifier,
    crosshair: CrosshairStyle = CrosshairStyle.Vertical,
    // Optional override: given the live pointer x and the nearest index, a chart may return a special
    // tooltip (e.g. "No reading" when the pointer is inside a data gap). Null → fall back to tooltipFor.
    tooltipAt: ((pointerX: Float, nearestIndex: Int) -> TooltipSpec?)? = null,
    // When true (default) a two-finger pinch zooms and pans the x-axis; double-tap resets. Charts with
    // too few points to benefit (e.g. scatter) pass false to stay fixed.
    zoomable: Boolean = true,
    // Max horizontal zoom factor. Clamped per-chart so a sparse series can't zoom into nonsense.
    maxZoom: Float = 6f,
    chartBody: @Composable (Modifier, ChartViewport) -> Unit,
) {
    var selected by remember { mutableStateOf(-1) }
    var pointerX by remember { mutableStateOf(Float.NaN) }
    var viewport by remember { mutableStateOf(ChartViewport()) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    val projection = remember(canvasSize, viewport) {
        if (canvasSize == Size.Zero) null else project(canvasSize, viewport)
    }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val minHit = with(density) { 24.dp.toPx() }

    Box(modifier.fillMaxWidth().height(heightDp)) {
        chartBody(Modifier.matchParentSize().onSizeChanged { canvasSize = it.toSize() }, viewport)

        Box(
            Modifier
                .matchParentSize()
                // Two-finger pinch/pan navigates the x-axis; kept on its own detector so it never fights
                // the one-finger scrub below or the parent vertical scroll (two-finger is unambiguous).
                .then(
                    if (zoomable) Modifier.pointerInput(canvasSize) {
                        val plotW = ChartGutters.plotRect(size.toSize(), hasX = true, hasY = true, density = this).width
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.size < 2 || plotW <= 0f) continue // single finger → leave to scrub
                                val p0 = pressed[0]; val p1 = pressed[1]
                                val prevC = (p0.previousPosition + p1.previousPosition) / 2f
                                val curC = (p0.position + p1.position) / 2f
                                val prevSpan = (p0.previousPosition - p1.previousPosition).getDistance()
                                val curSpan = (p0.position - p1.position).getDistance()
                                val zoom = if (prevSpan > 1f) curSpan / prevSpan else 1f
                                val panPx = curC.x - prevC.x
                                val cur = viewport
                                val newScale = (cur.scale * zoom).coerceIn(1f, maxZoom)
                                // Dragging right moves the window left (pan in fraction-of-full-range).
                                val panDelta = -panPx / plotW * cur.windowFraction
                                viewport = cur.copy(scale = newScale, panFraction = cur.panFraction + panDelta).clamped()
                                pressed.forEach { it.consume() }
                            }
                        }
                    } else Modifier
                )
                .pointerInput(projection) {
                    val proj = projection ?: return@pointerInput
                    // One unified handler distinguishes tap (clear) from drag (scrub) by movement,
                    // avoiding the two-sibling-detector down-event conflict.
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitPointerEvent()
                            val first = down.changes.firstOrNull() ?: continue
                            if (!first.pressed) continue
                            if (down.changes.size > 1) continue // let the transform detector own multitouch
                            val hit = hitTest(proj, first.position, minHit)
                            if (hit >= 0) { selected = hit; pointerX = first.position.x; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                            else selected = -1 // tap on empty space clears the pin
                        }
                    }
                }
                .pointerInput(projection) {
                    val proj = projection ?: return@pointerInput
                    detectHorizontalDrag { pos ->
                        val i = hitTest(proj, pos, minHit)
                        pointerX = pos.x
                        if (i >= 0 && i != selected) { selected = i; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    }
                }
                .then(
                    if (zoomable) Modifier.pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { viewport = ChartViewport() })
                    } else Modifier
                )
        ) {
            val proj = projection
            if (proj != null && selected in 0 until proj.count) {
                val gapSpec = tooltipAt?.invoke(pointerX, selected)
                if (gapSpec != null) {
                    // Pointer is in a gap: anchor the bubble at the pointer x on the baseline, no crosshair dot.
                    TooltipBubble(Offset(pointerX, proj.size.height / 2f), gapSpec, proj.size)
                } else {
                    CrosshairLayer(proj, selected, accent, crosshair)
                    TooltipBubble(proj.anchorOf(selected), tooltipFor(selected), proj.size)
                }
            }
            // A quiet "zoomed · double-tap to reset" hint + a scrollbar strip showing the window position.
            if (zoomable && !viewport.isIdentity && proj != null) {
                ViewportHint(viewport, accent, proj.size)
            }
        }
    }
}

/** A thin bottom scrollbar strip indicating the visible window within the full range when zoomed. */
@Composable
private fun ViewportHint(viewport: ChartViewport, accent: Color, size: Size) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val trackY = this.size.height - 2f
        val x0 = viewport.panFraction * this.size.width
        val x1 = (viewport.panFraction + viewport.windowFraction) * this.size.width
        drawLine(accent.copy(alpha = 0.15f), Offset(0f, trackY), Offset(this.size.width, trackY), strokeWidth = 3f)
        drawLine(accent.copy(alpha = 0.8f), Offset(x0, trackY), Offset(x1, trackY), strokeWidth = 3f)
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

