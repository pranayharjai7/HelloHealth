package com.hellohealth.ui.charts.interaction

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
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

/** How strongly a pinch translates finger-span change into zoom, applied to the ABSOLUTE span ratio
 *  (current span ÷ span when the pinch started). ~1.4 feels natural; the old incremental model needed
 *  a much larger gain that still failed because it ratcheted per frame. */
private const val ZOOM_GAIN = 1.4f


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
    maxZoom: Float = 10f,
    chartBody: @Composable (Modifier, ChartViewport) -> Unit,
) {
    var selected by remember { mutableStateOf(-1) }
    var pointerX by remember { mutableStateOf(Float.NaN) }
    var viewport by remember { mutableStateOf(ChartViewport()) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    val projection = remember(canvasSize, viewport) {
        if (canvasSize == Size.Zero) null else project(canvasSize, viewport)
    }
    // The gesture coroutine is long-lived and MUST NOT restart mid-gesture. It is keyed only on
    // canvasSize/zoomable (stable during a touch); it reads the live viewport + projection through these
    // updated refs so it always sees the latest without the pointerInput being torn down and relaunched
    // on every viewport change (which was cancelling pinches/pans after a single frame).
    val viewportRef = androidx.compose.runtime.rememberUpdatedState(viewport)
    val projectionRef = androidx.compose.runtime.rememberUpdatedState(projection)
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val minHit = with(density) { 24.dp.toPx() }

    Box(modifier.fillMaxWidth().height(heightDp)) {
        chartBody(Modifier.matchParentSize().onSizeChanged { canvasSize = it.toSize() }, viewport)

        Box(
            Modifier
                .matchParentSize()
                // One unified gesture handler so the three modes never fight each other:
                //   • one finger, moved right away            → PAN the viewport left/right
                //   • one finger, held still past long-press   → SCRUB (pin + tooltip), drag moves the pin
                //   • two fingers                              → ZOOM only (pinch; no lateral pan)
                //   • double-tap                               → reset zoom (separate detector below)
                .pointerInput(canvasSize, zoomable) {
                    val plotW = ChartGutters.plotRect(size.toSize(), hasX = true, hasY = true, density = this).width
                    val longPressMs = android.view.ViewConfiguration.getLongPressTimeout().toLong()
                    val touchSlop = viewConfiguration.touchSlop
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downTime = android.os.SystemClock.uptimeMillis()
                            var mode = 0 // 0 = undecided, 1 = pan, 2 = scrub, 3 = zoom
                            var lastX = down.position.x
                            // Absolute pinch model: latch the span + scale when the 2nd finger lands, then
                            // recompute scale from the CURRENT span each frame. Immune to the per-frame
                            // ratchet that made the old incremental model discard zoom on every wobble.
                            var startSpan = Float.NaN
                            var scaleAtStart = 1f

                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) {
                                    // Released without ever deciding a mode → treat as a tap: pin the
                                    // nearest point (or clear if the tap missed). Keeps tap-to-inspect
                                    // working, esp. for non-zoomable charts (scatter/weekly).
                                    if (mode == 0) {
                                        val proj = projectionRef.value
                                        if (proj != null) {
                                            val h = hitTest(proj, down.position, minHit)
                                            selected = h
                                            if (h >= 0) { pointerX = down.position.x; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                                        }
                                    }
                                    break
                                }

                                if (zoomable && pressed.size >= 2) {
                                    mode = 3
                                    val span = (pressed[0].position - pressed[1].position).getDistance()
                                    if (startSpan.isNaN()) {
                                        // Latch on 2nd-finger-down (and re-latch after dropping to 1 finger).
                                        startSpan = span
                                        scaleAtStart = viewportRef.value.scale
                                    } else if (span > 1f && startSpan > 1f) {
                                        val ratio = 1f + (span / startSpan - 1f) * ZOOM_GAIN
                                        val cur = viewportRef.value
                                        viewport = cur.copy(scale = (scaleAtStart * ratio).coerceIn(1f, maxZoom)).clamped()
                                    }
                                    pressed.forEach { it.consume() }
                                    continue
                                }
                                // Back to (or still) one finger: drop the pinch latch so a re-pinch re-latches,
                                // and resync lastX so the pan after a pinch doesn't jump from a stale value.
                                if (!startSpan.isNaN()) { startSpan = Float.NaN; lastX = pressed.first().position.x }

                                val cur = pressed.first()
                                val dx = cur.position.x - lastX
                                if (mode == 0) {
                                    val moved = kotlin.math.abs(cur.position.x - down.position.x) > touchSlop ||
                                        kotlin.math.abs(cur.position.y - down.position.y) > touchSlop
                                    val held = android.os.SystemClock.uptimeMillis() - downTime >= longPressMs
                                    if (moved) {
                                        // Moved before the long-press fired → pan.
                                        mode = if (zoomable) 1 else 2
                                        if (mode == 2) {
                                            projectionRef.value?.let { p ->
                                                val h = hitTest(p, cur.position, minHit)
                                                if (h >= 0) { selected = h; pointerX = cur.position.x }
                                            }
                                        }
                                    } else if (held) {
                                        // Held still long enough → scrub; pin the starting point.
                                        mode = 2
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        projectionRef.value?.let { p ->
                                            val h = hitTest(p, cur.position, minHit)
                                            if (h >= 0) { selected = h; pointerX = cur.position.x }
                                        }
                                    }
                                }

                                when (mode) {
                                    1 -> { // pan — content tracks the finger 1:1
                                        if (plotW > 0f && dx != 0f) {
                                            val c = viewportRef.value
                                            viewport = c.copy(panFraction = c.panFraction - dx / plotW * c.windowFraction).clamped()
                                        }
                                        cur.consume()
                                    }
                                    2 -> { // scrub
                                        projectionRef.value?.let { p ->
                                            val h = hitTest(p, cur.position, minHit)
                                            pointerX = cur.position.x
                                            if (h >= 0 && h != selected) { selected = h; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                                        }
                                        cur.consume()
                                    }
                                }
                                lastX = cur.position.x
                            }
                        }
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

