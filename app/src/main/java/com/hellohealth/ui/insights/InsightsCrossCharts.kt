package com.hellohealth.ui.insights

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hellohealth.domain.model.CrossDimensionInsights
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/**
 * Cross-dimension charts for the Insights screen. All descriptive, never diagnostic — the copy says
 * "these moved together," never "X caused Y," and carries no medical judgement.
 *
 * Colors come from [com.hellohealth.ui.charts.ChartPalette], the dataviz-validated categorical set
 * (blue/orange/aqua; passes the colorblind-safety + lightness + contrast checks in both light and
 * dark), rather than the app's older ad-hoc chart hues which fail CVD separation. Identity is never
 * color-alone: every multi-series chart carries a legend AND direct min/max value labels. One axis
 * only — a second series with a different scale is indexed to its own normalised band, not a second
 * y-axis.
 */

@Composable private fun series1() = com.hellohealth.ui.charts.chartSeries1()
@Composable private fun series2() = com.hellohealth.ui.charts.chartSeries2()
@Composable private fun series3() = com.hellohealth.ui.charts.chartSeries3()

/**
 * The "Across your day" section: the four cross-dimension views. Each renders its own empty state, so
 * the section always draws; the parent gates the whole section on [CrossDimensionInsights.hasAnyData].
 */
@Composable
fun CrossInsightsSection(cross: CrossDimensionInsights) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(
            text = "Across your day",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "How your dimensions moved together over the last 7 days. Descriptive only — not medical advice.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )

        if (!cross.hasAnyData) {
            ChartCard(title = "Not enough data yet") {
                ChartEmpty("Log mood, meals and wear your device for a few days to see cross-dimension trends.")
            }
            return@Column
        }

        // 1. Mood × recovery — dual normalised line (each series to its own 0..1 band, one axis).
        val moodSeries = cross.days.mapIndexedNotNull { i, d -> d.moodPositivity?.let { i.toFloat() to it.toFloat() } }
        val readySeries = cross.days.mapIndexedNotNull { i, d -> d.readiness?.let { i.toFloat() to it.toFloat() } }
        ChartCard(
            title = "Mood vs recovery",
            subtitle = "Positive-mood share and recovery readiness, each day.",
        ) {
            DualTrend(
                labels = cross.days.map { it.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()) },
                seriesA = moodSeries, seriesAName = "Mood +", seriesAColor = series1(), seriesAUnit = "%",
                seriesB = readySeries, seriesBName = "Readiness", seriesBColor = series2(), seriesBUnit = "",
                aIsPercent = true,
            )
        }

        // 2. Nutrition × energy — calories in vs out, two lines on a shared kcal axis (same unit -> ok).
        val inSeries = cross.days.mapIndexedNotNull { i, d -> d.caloriesIn?.let { i.toFloat() to it.toFloat() } }
        val outSeries = cross.days.mapIndexedNotNull { i, d -> d.caloriesOut?.let { i.toFloat() to it.toFloat() } }
        ChartCard(
            title = "Energy balance",
            subtitle = "Calories in vs out, each day (kcal).",
        ) {
            DualTrend(
                labels = cross.days.map { it.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()) },
                seriesA = inSeries, seriesAName = "In", seriesAColor = series2(), seriesAUnit = "",
                seriesB = outSeries, seriesBName = "Out", seriesBColor = series1(), seriesBUnit = "",
                sharedScale = true,
            )
        }

        // 3. Readiness trend — single line.
        ChartCard(
            title = "Readiness trend",
            subtitle = "Daily recovery readiness (0–100).",
        ) {
            if (readySeries.size < 2) {
                ChartEmpty("Readiness needs about a week of vitals history to establish a baseline.")
            } else {
                DualTrend(
                    labels = cross.days.map { it.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()) },
                    seriesA = readySeries, seriesAName = "Readiness", seriesAColor = series3(), seriesAUnit = "",
                    seriesB = emptyList(), seriesBName = "", seriesBColor = series3(), seriesBUnit = "",
                )
            }
        }

        // 4. Correlation scatter — sleep vs NEXT day's readiness.
        ChartCard(
            title = "Sleep vs next-day readiness",
            subtitle = "Each dot is one night's sleep against how ready you felt the next day. Observational.",
        ) {
            val pairs = cross.sleepVsNextReadiness
            if (pairs.size < 2) {
                ChartEmpty("Need a few nights of sleep + readiness data to plot this.")
            } else {
                Scatter(points = pairs, xLabel = "Sleep (h)", yLabel = "Readiness", dotColor = series1())
            }
        }
    }
}

/**
 * A one-axis trend of up to two series. Each series is normalised to its own min..max over the
 * shared X (day index) so two different-scale measures share one plot WITHOUT a second y-axis (the
 * dataviz non-negotiable) — direct min/max labels per series carry the real values. When [sharedScale]
 * both series share one min..max (valid because they're the same unit, e.g. kcal in vs out).
 * Pass an empty [seriesB] for a single-series line.
 */
@Composable
private fun DualTrend(
    labels: List<String>,
    seriesA: List<Pair<Float, Float>>, seriesAName: String, seriesAColor: Color, seriesAUnit: String,
    seriesB: List<Pair<Float, Float>>, seriesBName: String, seriesBColor: Color, seriesBUnit: String,
    aIsPercent: Boolean = false,
    sharedScale: Boolean = false,
) {
    if (seriesA.size < 2 && seriesB.size < 2) {
        ChartEmpty("Not enough days logged yet.")
        return
    }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val progress by animateFloatAsState(targetValue = 1f, animationSpec = tween(800), label = "cross_trend")

    // Legend + direct value labels (identity never color-alone).
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (seriesA.isNotEmpty()) LegendChip(seriesAName, seriesAColor, valueRangeLabel(seriesA, aIsPercent, seriesAUnit))
        if (seriesB.isNotEmpty()) LegendChip(seriesBName, seriesBColor, valueRangeLabel(seriesB, false, seriesBUnit))
    }
    Spacer(Modifier.height(10.dp))

    val allX = (seriesA + seriesB).map { it.first }
    val maxX = (allX.maxOrNull() ?: 1f).coerceAtLeast(1f)
    // Shared band when sharedScale, else each series normalised independently.
    val sharedMin = (seriesA + seriesB).minOfOrNull { it.second } ?: 0f
    val sharedMax = (seriesA + seriesB).maxOfOrNull { it.second } ?: 1f

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .graphicsLayer(alpha = progress, translationY = (1f - progress) * 30f)
    ) {
        fun bounds(s: List<Pair<Float, Float>>): Pair<Float, Float> {
            if (sharedScale) return sharedMin to sharedMax
            val lo = s.minOf { it.second }; val hi = s.maxOf { it.second }
            val pad = if (abs(hi - lo) < 0.01f) 1f else (hi - lo) * 0.12f
            return (lo - pad) to (hi + pad)
        }
        fun mapX(x: Float) = (x / maxX) * size.width
        fun draw(s: List<Pair<Float, Float>>, color: Color) {
            if (s.size < 2) {
                if (s.size == 1) drawCircle(color, 7f, Offset(mapX(s[0].first), size.height / 2))
                return
            }
            val (lo, hi) = bounds(s)
            fun mapY(v: Float) = size.height - ((v - lo) / (hi - lo)) * size.height
            val line = Path(); val fill = Path()
            s.forEachIndexed { i, (px, py) ->
                val x = mapX(px); val y = mapY(py)
                if (i == 0) { line.moveTo(x, y); fill.moveTo(x, size.height); fill.lineTo(x, y) }
                else { line.lineTo(x, y); fill.lineTo(x, y) }
            }
            fill.lineTo(mapX(s.last().first), size.height); fill.close()
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.03f))))
            drawPath(line, color = color, style = Stroke(width = 6f, cap = StrokeCap.Round))
            s.lastOrNull()?.let { drawCircle(color, 8f, Offset(mapX(it.first), mapY(it.second))) }
        }
        // Draw B first so A sits on top; both are directly labelled in the legend.
        if (seriesB.isNotEmpty()) draw(seriesB, seriesBColor)
        if (seriesA.isNotEmpty()) draw(seriesA, seriesAColor)
    }

    Spacer(Modifier.height(6.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEach { Text(it, fontSize = 10.sp, color = onSurface.copy(alpha = 0.5f)) }
    }
}

/** Scatter of (x,y) points sharing one plot; dashed baseline omitted (no causal trend line). */
@Composable
private fun Scatter(points: List<Pair<Double, Int>>, xLabel: String, yLabel: String, dotColor: Color) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val xs = points.map { it.first }; val ys = points.map { it.second.toDouble() }
    val xLo = xs.min(); val xHi = xs.max().coerceAtLeast(xLo + 0.01)
    val yLo = ys.min(); val yHi = ys.max().coerceAtLeast(yLo + 0.01)
    val progress by animateFloatAsState(targetValue = 1f, animationSpec = tween(800), label = "scatter")

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("↕ $yLabel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = onSurface.copy(alpha = 0.7f))
        Text("↔ $xLabel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = onSurface.copy(alpha = 0.7f))
    }
    Spacer(Modifier.height(8.dp))
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .graphicsLayer(alpha = progress)
    ) {
        val padL = 8f; val padB = 8f
        fun mapX(x: Double) = padL + ((x - xLo) / (xHi - xLo)).toFloat() * (size.width - padL)
        fun mapY(y: Double) = (size.height - padB) - ((y - yLo) / (yHi - yLo)).toFloat() * (size.height - padB)
        points.forEach { (x, y) ->
            drawCircle(dotColor.copy(alpha = 0.85f), 11f, Offset(mapX(x), mapY(y.toDouble())))
            drawCircle(Color.White.copy(alpha = 0.5f), 3f, Offset(mapX(x), mapY(y.toDouble())))
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${"%.1f".format(xLo)}h", fontSize = 10.sp, color = onSurface.copy(alpha = 0.5f))
        Text("${"%.1f".format(xHi)}h", fontSize = 10.sp, color = onSurface.copy(alpha = 0.5f))
    }
}

@Composable
private fun LegendChip(name: String, color: Color, valueLabel: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) { drawCircle(color) }
        Spacer(Modifier.width(6.dp))
        Column {
            Text(name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(valueLabel, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
        }
    }
}

private fun valueRangeLabel(series: List<Pair<Float, Float>>, isPercent: Boolean, unit: String): String {
    if (series.isEmpty()) return "—"
    val lo = series.minOf { it.second }; val hi = series.maxOf { it.second }
    return if (isPercent) "${(lo * 100).toInt()}–${(hi * 100).toInt()}%"
    else "${lo.toInt()}–${hi.toInt()}${if (unit.isNotEmpty()) " $unit" else ""}"
}

@Composable
private fun ChartCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun ChartEmpty(message: String) {
    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
    }
}
