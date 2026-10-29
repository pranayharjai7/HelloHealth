package com.hellohealth.ui.charts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path

/**
 * A single cubic Bézier segment from `start` to `end` with two control points. Pure data so the
 * smoothing math is unit-testable without an Android [Path].
 */
data class CubicSegment(
    val c1x: Float, val c1y: Float,
    val c2x: Float, val c2y: Float,
    val endX: Float, val endY: Float,
)

/**
 * Pure Catmull-Rom → cubic-Bézier control-point computation with the overshoot clamp. Returns one
 * [CubicSegment] per gap between consecutive points (empty for < 2 points). Separated from [smoothPath]
 * so the clamp behaviour can be asserted in a plain JVM test (Android's Path is not available there).
 */
fun smoothSegments(points: List<Offset>, tension: Float = 0.5f): List<CubicSegment> {
    if (points.size < 2) return emptyList()
    val t = tension / 6f
    val straight = points.size == 2 || tension <= 0f
    return (0 until points.size - 1).map { i ->
        val p1 = points[i]
        val p2 = points[i + 1]
        if (straight) {
            // Control points on the line → the cubic is a straight segment.
            CubicSegment(p1.x, p1.y, p2.x, p2.y, p2.x, p2.y)
        } else {
            val p0 = points[if (i == 0) 0 else i - 1]
            val p3 = points[if (i + 2 > points.size - 1) points.size - 1 else i + 2]
            val c1x = p1.x + (p2.x - p0.x) * t
            val c2x = p2.x - (p3.x - p1.x) * t
            // Clamp control-point y into this segment's [min,max] so the curve never overshoots into a
            // value the data never reached (no invented peaks/dips).
            val loY = minOf(p1.y, p2.y)
            val hiY = maxOf(p1.y, p2.y)
            val c1y = (p1.y + (p2.y - p0.y) * t).coerceIn(loY, hiY)
            val c2y = (p2.y - (p3.y - p1.y) * t).coerceIn(loY, hiY)
            CubicSegment(c1x, c1y, c2x, c2y, p2.x, p2.y)
        }
    }
}

/**
 * Builds a SMOOTH, thread-like path through [points] (assumed already in pixel space, x strictly
 * non-decreasing left→right). Uses a Catmull-Rom spline converted to cubic Béziers, but with the
 * control points' y CLAMPED to the span of their bracketing data points (see [smoothSegments]).
 *
 * The clamp is the important part for a HEALTH chart: an unclamped spline overshoots on sharp
 * changes and would invent a dip or peak that the data never had (a false resting-HR trough, a fake
 * weight spike). Clamping each segment's control points keeps the curve inside the data envelope — it
 * softens the vertices without lying about the values.
 *
 * Degenerates safely: 0 points → empty path; 1 point → a single moveTo; 2 points → one straight (but
 * still rounded-joined when stroked) segment.
 *
 * @param tension 0f = straight polyline, 1f = standard Catmull-Rom. ~0.5f reads as a soft thread.
 */
fun smoothPath(points: List<Offset>, tension: Float = 0.5f): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    for (seg in smoothSegments(points, tension)) {
        path.cubicTo(seg.c1x, seg.c1y, seg.c2x, seg.c2y, seg.endX, seg.endY)
    }
    return path
}
