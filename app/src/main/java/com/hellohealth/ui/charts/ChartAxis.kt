package com.hellohealth.ui.charts

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * A resolved chart axis: the value range it spans, the tick positions to label, and a formatter that
 * renders a tick value with its unit. Pure data — the drawing happens in [ChartScaffold].
 */
data class Axis(
    val min: Float,
    val max: Float,
    val ticks: List<Float>,
    val format: (Float) -> String,
) {
    val span: Float get() = (max - min).takeIf { it > 0f } ?: 1f
}

/**
 * Builds a value axis with "nice" tick values (steps of 1/2/5 × 10ⁿ) covering [rawMin]..[rawMax],
 * aiming for ~[targetTicks] ticks, each formatted with [unit]. The nice-rounding is the standard
 * axis algorithm (Heckbert): pick a step that is a 1/2/5 multiple of a power of ten so ticks read as
 * clean round numbers (0 / 1,000 / 2,000; 50 / 60 / 70). Degenerate input (flat series) still yields
 * a usable 1-unit band so a tick row renders. Values are thousands-comma'd via [format].
 */
fun niceAxis(rawMin: Float, rawMax: Float, unit: String = "", targetTicks: Int = 4): Axis {
    var lo = minOf(rawMin, rawMax).toDouble()
    var hi = maxOf(rawMin, rawMax).toDouble()
    if (hi - lo < 1e-6) {
        // Flat series: pad to a sensible band around the value so a tick row still renders.
        val pad = if (abs(hi) > 1.0) abs(hi) * 0.1 else 1.0
        lo -= pad; hi += pad
    }
    val range = niceNum((hi - lo).coerceAtLeast(1e-6), round = false)
    val step = niceNum(range / (targetTicks - 1).coerceAtLeast(1), round = true)
    val niceMin = floor(lo / step) * step
    val niceMax = ceil(hi / step) * step
    val ticks = buildList {
        var t = niceMin
        // guard against fp drift adding a stray extra tick
        while (t <= niceMax + step * 0.5) { add(t.toFloat()); t += step }
    }
    val decimals = if (step < 1.0) maxOf(0, -floor(log10(step)).toInt()) else 0
    return Axis(
        min = niceMin.toFloat(),
        max = niceMax.toFloat(),
        ticks = ticks,
        format = { v -> formatTick(v, decimals, unit) },
    )
}

/** Fixed wall-clock axis for a time-of-day chart: ticks at 00/06/12/18/24h as day-fractions 0..1. */
fun hourAxis(): Axis = Axis(
    min = 0f,
    max = 1f,
    ticks = listOf(0f, 0.25f, 0.5f, 0.75f, 1f),
    format = { frac -> "%02d".format((frac * 24).toInt()) },
)

private fun formatTick(value: Float, decimals: Int, unit: String): String {
    val rounded = if (decimals == 0) {
        val n = value.toLong()
        if (abs(n) >= 1000) "%,d".format(n) else n.toString()
    } else {
        "%.${decimals}f".format(value)
    }
    return if (unit.isBlank()) rounded else "$rounded $unit"
}

/** The 1/2/5 ×10ⁿ "nice number" nearest (or ceiling of) [x]. */
private fun niceNum(x: Double, round: Boolean): Double {
    val exp = floor(log10(x))
    val f = x / 10.0.pow(exp)
    val nf = if (round) {
        when {
            f < 1.5 -> 1.0
            f < 3.0 -> 2.0
            f < 7.0 -> 5.0
            else -> 10.0
        }
    } else {
        when {
            f <= 1.0 -> 1.0
            f <= 2.0 -> 2.0
            f <= 5.0 -> 5.0
            else -> 10.0
        }
    }
    return nf * 10.0.pow(exp)
}
