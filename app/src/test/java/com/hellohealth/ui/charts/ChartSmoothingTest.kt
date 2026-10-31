package com.hellohealth.ui.charts

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartSmoothingTest {

    @Test
    fun `fewer than two points produce no segments`() {
        assertTrue(smoothSegments(emptyList()).isEmpty())
        assertTrue(smoothSegments(listOf(Offset(0f, 0f))).isEmpty())
    }

    @Test
    fun `two points produce one straight segment ending at the second point`() {
        val segs = smoothSegments(listOf(Offset(0f, 10f), Offset(100f, 20f)))
        assertEquals(1, segs.size)
        assertEquals(100f, segs[0].endX, 0.001f)
        assertEquals(20f, segs[0].endY, 0.001f)
    }

    @Test
    fun `each segment ends exactly on its data point (curve passes through points)`() {
        val pts = listOf(Offset(0f, 50f), Offset(10f, 10f), Offset(20f, 90f), Offset(30f, 40f))
        val segs = smoothSegments(pts)
        assertEquals(3, segs.size)
        // Endpoints are the data points themselves — smoothing only moves the control handles.
        assertEquals(10f, segs[0].endY, 0.001f)
        assertEquals(90f, segs[1].endY, 0.001f)
        assertEquals(40f, segs[2].endY, 0.001f)
    }

    @Test
    fun `control points never overshoot the segment's value envelope (no invented peaks or dips)`() {
        // A sharp spike: y goes 100 -> 0 -> 100. An unclamped Catmull-Rom would pull control points
        // below 0 / above 100 around the valley; the clamp must keep every control y within each
        // segment's own [min,max].
        val pts = listOf(
            Offset(0f, 100f),
            Offset(10f, 0f),
            Offset(20f, 100f),
            Offset(30f, 0f),
        )
        for (seg in smoothSegments(pts)) {
            // Every control point y must be inside the global data range [0,100].
            assertTrue("c1y ${seg.c1y} out of range", seg.c1y in 0f..100f)
            assertTrue("c2y ${seg.c2y} out of range", seg.c2y in 0f..100f)
        }
    }

    @Test
    fun `control point y stays within each segment's own min-max`() {
        val pts = listOf(Offset(0f, 20f), Offset(10f, 80f), Offset(20f, 30f), Offset(30f, 70f))
        val segs = smoothSegments(pts)
        // Segment 1: 80 -> 30, control ys must be within [30,80].
        val s = segs[1]
        assertTrue(s.c1y in 30f..80f)
        assertTrue(s.c2y in 30f..80f)
    }

    @Test
    fun `tension zero yields straight segments`() {
        val pts = listOf(Offset(0f, 0f), Offset(10f, 40f), Offset(20f, 10f))
        val segs = smoothSegments(pts, tension = 0f)
        // With no tension, control points collapse onto the segment endpoints → straight lines.
        assertEquals(segs[0].c1x, 0f, 0.001f); assertEquals(segs[0].c1y, 0f, 0.001f)
        assertEquals(segs[0].c2x, 10f, 0.001f); assertEquals(segs[0].c2y, 40f, 0.001f)
    }
}
