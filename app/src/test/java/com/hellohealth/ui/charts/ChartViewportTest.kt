package com.hellohealth.ui.charts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartViewportTest {

    @Test
    fun `identity viewport maps fractions unchanged`() {
        val vp = ChartViewport()
        assertTrue(vp.isIdentity)
        assertEquals(0f, vp.toWindow(0f), 1e-4f)
        assertEquals(0.5f, vp.toWindow(0.5f), 1e-4f)
        assertEquals(1f, vp.toWindow(1f), 1e-4f)
    }

    @Test
    fun `2x zoom halves the window width`() {
        val vp = ChartViewport(scale = 2f, panFraction = 0f)
        assertEquals(0.5f, vp.windowFraction, 1e-4f)
        // The left half [0,0.5] of the data fills the plot: 0→0, 0.5→1.
        assertEquals(0f, vp.toWindow(0f), 1e-4f)
        assertEquals(1f, vp.toWindow(0.5f), 1e-4f)
        // A point at 0.75 is off the right edge (>1).
        assertTrue(vp.toWindow(0.75f) > 1f)
    }

    @Test
    fun `pan shifts the visible window`() {
        // 2x zoom, panned to show [0.5,1.0].
        val vp = ChartViewport(scale = 2f, panFraction = 0.5f)
        assertEquals(0f, vp.toWindow(0.5f), 1e-4f)
        assertEquals(1f, vp.toWindow(1f), 1e-4f)
        assertTrue(vp.toWindow(0.25f) < 0f) // left of the window
    }

    @Test
    fun `clamp keeps the window inside the data at both ends`() {
        // Over-panned right: window would run past 1.0 → clamp pan to 0.5 (so window ends at 1.0).
        val over = ChartViewport(scale = 2f, panFraction = 0.9f).clamped()
        assertEquals(0.5f, over.panFraction, 1e-4f)
        // Negative pan clamps to 0.
        val under = ChartViewport(scale = 2f, panFraction = -0.3f).clamped()
        assertEquals(0f, under.panFraction, 1e-4f)
    }

    @Test
    fun `scale below one is clamped up to one`() {
        val vp = ChartViewport(scale = 0.4f, panFraction = 0f).clamped()
        assertEquals(1f, vp.scale, 1e-4f)
        assertEquals(0f, vp.panFraction, 1e-4f)
    }
}
