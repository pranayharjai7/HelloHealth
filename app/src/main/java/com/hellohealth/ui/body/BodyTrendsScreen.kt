package com.hellohealth.ui.body

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hellohealth.domain.model.BodyMetric
import com.hellohealth.ui.charts.chartSeries1
import com.hellohealth.ui.charts.chartSeries2
import com.hellohealth.ui.charts.chartSeries3
import kotlin.math.abs

/**
 * Body-composition trends over the recent window — weight, body-fat %, and lean mass over time. Each
 * metric is its own single-series card; days where a metric is null are dropped (a missing reading
 * never leaves a phantom zero). Reuses the Vitals-trends Canvas idiom, drawing from the shared
 * dataviz-validated [com.hellohealth.ui.charts.ChartPalette].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyTrendsScreen(
    viewModel: BodyTrendsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background

    val weightColor = chartSeries1()
    val bodyFatColor = chartSeries2()
    val leanMassColor = chartSeries3()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Body trends", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = Color.Transparent,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    text = "Your last ${BodyTrendsViewModel.TREND_WINDOW_DAYS} days — logged weigh-ins and " +
                        "synced body-composition readings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                )

                TrendChartCard("Weight", "kg", weightColor, state.metrics) { it.weightKg }
                TrendChartCard("Body fat", "%", bodyFatColor, state.metrics) { it.bodyFatPct }
                TrendChartCard("Lean mass", "kg", leanMassColor, state.metrics) { it.leanMassKg }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * One metric's trend card. [selector] pulls that metric out of each day's [BodyMetric]; days where it
 * is null are dropped. With fewer than two readings the card shows its empty state.
 */
@Composable
private fun TrendChartCard(
    title: String,
    unit: String,
    lineColor: Color,
    metrics: List<BodyMetric>,
    selector: (BodyMetric) -> Double?,
) {
    // metrics arrive ascending by date, so the list index is chronological on the X axis.
    val points = remember(metrics, title) {
        metrics.mapIndexedNotNull { index, m ->
            selector(m)?.let { index.toFloat() to it.toFloat() }
        }
    }
    val latest = points.lastOrNull()?.second

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Text(
                    text = "Latest: ${latest?.let { formatValue(it, unit) } ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                )
            }

            if (points.size < 2) {
                TrendEmptyState(
                    message = if (points.isEmpty()) {
                        "No readings yet. Log a weigh-in or sync a smart scale to start the trend."
                    } else {
                        "Only one reading so far — a trend needs a couple of entries."
                    }
                )
            } else {
                val minValue = remember(points) { points.minOf { it.second } }
                val maxValue = remember(points) { points.maxOf { it.second } }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatValue(maxValue, unit),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                    Text(
                        text = formatValue(minValue, unit),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.54f),
                    )
                }

                TrendLine(points = points, lineColor = lineColor)
            }
        }
    }
}

/** The Canvas line + gradient-fill, mapping day-index → X and value → Y. Copy of the Vitals idiom. */
@Composable
private fun TrendLine(points: List<Pair<Float, Float>>, lineColor: Color) {
    val chartProgress: Float by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 850),
        label = "body_trend_chart",
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .graphicsLayer(alpha = chartProgress, translationY = (1f - chartProgress) * 36f)
    ) {
        val chartWidth = size.width
        val chartHeight = size.height
        val safeMaxX = (points.maxOf { it.first }).coerceAtLeast(1f)
        val rawMinY = points.minOf { it.second }
        val rawMaxY = points.maxOf { it.second }
        val yPadding = if (abs(rawMaxY - rawMinY) < 0.01f) 1f else (rawMaxY - rawMinY) * 0.12f
        val minY = rawMinY - yPadding
        val maxY = rawMaxY + yPadding

        fun mapX(x: Float): Float = (x / safeMaxX) * chartWidth
        fun mapY(value: Float): Float {
            val ratio = (value - minY) / (maxY - minY)
            return chartHeight - (ratio * chartHeight)
        }

        val linePath = Path()
        val fillPath = Path()
        points.forEachIndexed { index, (px, py) ->
            val x = mapX(px)
            val y = mapY(py)
            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, chartHeight)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(chartWidth, chartHeight)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.42f), lineColor.copy(alpha = 0.03f))
            )
        )
        drawPath(path = linePath, color = lineColor, style = Stroke(width = 6f, cap = StrokeCap.Round))
        points.lastOrNull()?.let { (px, py) ->
            drawCircle(color = lineColor, radius = 8f, center = Offset(mapX(px), mapY(py)))
        }
    }
}

@Composable
private fun TrendEmptyState(message: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Monitor,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
            )
            Spacer(modifier = Modifier.padding(horizontal = 6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
            )
        }
    }
}

/** Unit-aware value formatting for the body metrics (kg / % / kcal / plain). */
private fun formatValue(value: Float, unit: String): String = when (unit) {
    "%" -> String.format("%.1f%%", value)
    "kg" -> String.format("%.1f kg", value)
    "kcal" -> "${value.toInt()} kcal"
    else -> "${value.toInt()} $unit"
}
