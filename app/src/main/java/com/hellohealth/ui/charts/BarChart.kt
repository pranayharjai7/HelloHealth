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
import com.hellohealth.ui.charts.interaction.ChartProjection
import com.hellohealth.ui.charts.interaction.CrosshairStyle
import com.hellohealth.ui.charts.interaction.InteractiveChart
import com.hellohealth.ui.charts.interaction.TooltipRow
import com.hellohealth.ui.charts.interaction.TooltipSpec

/** One bar: a [value] with an x-axis [label] (e.g. an hour tick like "06"). */
data class BarDatum(val value: Float, val label: String? = null)

/**
 * A dataviz-compliant bar chart: a magnitude y-axis + gridlines, sparse x-axis labels (from
 * [BarDatum.label]), bars **capped ≤24dp thick** (never fill the slot), and per-bar tap tooltips
 * (tap/scrub a bar to read its exact value). Wraps the shared [ChartScaffold] inside [InteractiveChart];
 * a collapsible value table sits beneath.
 *
 * @param unit magnitude unit for the y-axis + tooltip (e.g. "steps", "Cal").
 * @param xLabelFor optional label for bar i (e.g. the hour) shown in the tooltip title + x-axis.
 */
@Composable
fun BarChart(
    bars: List<BarDatum>,
    barColor: Color,
    unit: String,
    gridColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier,
    xLabelFor: (Int) -> String? = { bars.getOrNull(it)?.label },
) {
    if (bars.isEmpty()) return
    val maxV = bars.maxOf { it.value }
    val yAxis = niceAxis(0f, maxV, unit)
    // X ticks: sparse labels (every ~6th bar) placed at bar-center fractions.
    val xTicks = bars.indices.filter { xLabelFor(it) != null }
    val xAxis = if (xTicks.isNotEmpty()) Axis(
        min = 0f, max = 1f,
        ticks = xTicks.map { (it + 0.5f) / bars.size },
        format = { frac -> xLabelFor(((frac * bars.size)).toInt().coerceIn(0, bars.lastIndex)) ?: "" },
    ) else null
    val density = LocalDensity.current

    Column(modifier) {
        InteractiveChart(
            heightDp = 160.dp,
            accent = barColor,
            crosshair = CrosshairStyle.MarkLift,
            project = { size ->
                val plot = ChartGutters.plotRect(size, hasX = xAxis != null, hasY = true, density = density)
                val slot = plot.width / bars.size
                ChartProjection(
                    size = size,
                    count = bars.size,
                    hitRadiusPx = slot / 2f,
                    anchorOf = { i ->
                        val cx = plot.left + (i + 0.5f) * slot
                        val top = plot.bottom - (bars[i].value / yAxis.span) * plot.height
                        Offset(cx, top)
                    },
                )
            },
            tooltipFor = { i ->
                TooltipSpec(
                    title = xLabelFor(i)?.let { "${it}:00" } ?: "Bar ${i + 1}",
                    rows = listOf(TooltipRow("", "${bars[i].value.toInt()} $unit", barColor)),
                )
            },
        ) { m ->
            ChartScaffold(
                heightDp = 160.dp,
                xAxis = xAxis,
                yAxis = yAxis,
                gridColor = gridColor,
                labelColor = labelColor,
                modifier = m,
            ) { mapping ->
                val plot = mapping.plot
                val slot = plot.width / bars.size
                val barW = minOf(slot - 2.dp.toPx(), 24.dp.toPx()).coerceAtLeast(1f)
                bars.forEachIndexed { i, bar ->
                    if (bar.value <= 0f) return@forEachIndexed
                    val h = (bar.value / yAxis.span) * plot.height
                    val cx = plot.left + (i + 0.5f) * slot
                    drawRoundRect(
                        color = barColor.copy(alpha = 0.85f),
                        topLeft = Offset(cx - barW / 2f, plot.bottom - h),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    )
                }
            }
        }
    }
}
