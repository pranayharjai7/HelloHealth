package com.hellohealth.ui.charts

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.hellohealth.domain.health.SleepStageSegment
import com.hellohealth.domain.health.SleepStages
import com.hellohealth.ui.charts.interaction.ChartProjection
import com.hellohealth.ui.charts.interaction.CrosshairStyle
import com.hellohealth.ui.charts.interaction.InteractiveChart
import com.hellohealth.ui.charts.interaction.TooltipRow
import com.hellohealth.ui.charts.interaction.TooltipSpec
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A hypnogram: the night's sleep stages drawn as filled bands across four lanes (Awake / REM / Light /
 * Deep, top→bottom) on a wall-clock time x-axis. Reuses the shared chart layer — [ChartScaffold] for
 * the time axis + lane labels and [InteractiveChart] for tap-to-inspect ("01:40–02:15 · Deep"). The
 * y-axis is categorical (lane centers), not numeric.
 *
 * Segments are the flattened `SleepSessionRecord.stages`; stages map to lanes via [laneOf]. Awake
 * segments (STAGE_TYPE_AWAKE / AWAKE_IN_BED) share the top lane; SLEEPING folds into Light.
 */
@Composable
fun Hypnogram(
    segments: List<SleepStageSegment>,
    gridColor: Color,
    labelColor: Color,
    awakeColor: Color,
    remColor: Color,
    lightColor: Color,
    deepColor: Color,
    zone: ZoneId = ZoneId.systemDefault(),
    modifier: Modifier = Modifier,
) {
    val shown = segments.filter { laneOf(it.stage) != null }.sortedBy { it.startMs }
    if (shown.isEmpty()) return
    val startMs = shown.minOf { it.startMs }
    val endMs = shown.maxOf { it.endMs }
    val spanMs = (endMs - startMs).coerceAtLeast(1L).toFloat()
    val laneColors = listOf(awakeColor, remColor, lightColor, deepColor) // lane 0..3
    val density = LocalDensity.current
    val fmt = DateTimeFormatter.ofPattern("HH:mm")

    // Categorical y-axis: 4 lanes, ticks at lane centers, labels = lane names (top=Awake).
    val yAxis = Axis(
        min = 0f, max = 4f,
        ticks = listOf(0.5f, 1.5f, 2.5f, 3.5f),
        format = { v -> LANE_NAMES.getOrElse((3 - v.toInt()).coerceIn(0, 3)) { "" } },
    )
    // X-axis: a few clock ticks across the actual sleep span.
    val xAxis = Axis(
        min = 0f, max = 1f,
        ticks = listOf(0f, 0.5f, 1f),
        format = { frac ->
            Instant.ofEpochMilli(startMs + (frac * spanMs).toLong()).atZone(zone).toLocalTime().format(fmt)
        },
    )

    fun fracOf(ms: Long): Float = ((ms - startMs) / spanMs).coerceIn(0f, 1f)

    Column(modifier) {
        InteractiveChart(
            heightDp = 150.dp,
            accent = deepColor,
            crosshair = CrosshairStyle.MarkLift,
            project = { size ->
                val plot = ChartGutters.plotRect(size, hasX = true, hasY = true, density = density)
                val laneH = plot.height / 4f
                ChartProjection(
                    size = size,
                    count = shown.size,
                    hitRadiusPx = with(density) { 20.dp.toPx() },
                    anchorOf = { i ->
                        val seg = shown[i]
                        val lane = laneOf(seg.stage)!! // filtered above
                        val cx = plot.left + (fracOf(seg.startMs) + fracOf(seg.endMs)) / 2f * plot.width
                        val cy = plot.top + (lane + 0.5f) * laneH
                        Offset(cx, cy)
                    },
                )
            },
            tooltipFor = { i ->
                val seg = shown[i]
                val from = Instant.ofEpochMilli(seg.startMs).atZone(zone).toLocalTime().format(fmt)
                val to = Instant.ofEpochMilli(seg.endMs).atZone(zone).toLocalTime().format(fmt)
                val lane = laneOf(seg.stage)!!
                TooltipSpec("$from–$to", listOf(TooltipRow("", LANE_NAMES[lane], laneColors[lane])))
            },
        ) { m ->
            ChartScaffold(heightDp = 150.dp, xAxis = xAxis, yAxis = yAxis, gridColor = gridColor, labelColor = labelColor, modifier = m) { mapping ->
                val plot = mapping.plot
                val laneH = plot.height / 4f
                val gap = 2.dp.toPx()
                shown.forEach { seg ->
                    val lane = laneOf(seg.stage)!!
                    val x0 = plot.left + fracOf(seg.startMs) * plot.width
                    val x1 = plot.left + fracOf(seg.endMs) * plot.width
                    val top = plot.top + lane * laneH + gap / 2f
                    drawRoundRect(
                        color = laneColors[lane],
                        topLeft = Offset(x0, top),
                        size = Size((x1 - x0).coerceAtLeast(1f), (laneH - gap).coerceAtLeast(1f)),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                    )
                }
            }
        }
    }
}

/** Lane names top→bottom (index 0 = top lane). */
private val LANE_NAMES = listOf("Awake", "REM", "Light", "Deep")

/** Map a stage int to a lane row (0 Awake · 1 REM · 2 Light · 3 Deep), or null to skip (out-of-bed/unknown). */
private fun laneOf(stage: Int): Int? = when (stage) {
    SleepStages.AWAKE, SleepStages.AWAKE_IN_BED -> 0
    SleepStages.REM -> 1
    SleepStages.LIGHT, SleepStages.SLEEPING -> 2
    SleepStages.DEEP -> 3
    else -> null // OUT_OF_BED, UNKNOWN
}
