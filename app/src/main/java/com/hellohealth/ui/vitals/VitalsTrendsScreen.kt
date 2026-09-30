package com.hellohealth.ui.vitals

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.MonitorHeart
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
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
import com.hellohealth.domain.model.vitals.LatestVitals
import kotlin.math.abs

/**
 * Vitals & Recovery trends screen. One line chart per metric (RHR, HRV, SpO2, respiratory rate, body
 * temperature, hydration), each derived from the last ~30 days of daily rollups. Reuses the
 * [ActivityDetailScreen] chart's Canvas line+fill visual language, but with a day-indexed X axis (day
 * trends, not intra-workout time) — so it is a purpose-built trend chart rather than the activity one
 * bent out of shape. A metric with no readings shows an empty state, never a crash.
 *
 * All values render in natural human units; SpO2 is 0-100 (NOT a fraction).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VitalsTrendsScreen(
    viewModel: VitalsTrendsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Vitals & Recovery",
                        fontWeight = FontWeight.Black,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.15f), backgroundColor)
                    )
                )
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Last ${VitalsTrendsViewModel.TREND_WINDOW_DAYS} days · one point per day",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )

                val rollups = state.rollups
                TrendChartCard("Resting Heart Rate", "bpm", Color(0xFFEF5350), rollups) { it.restingHeartRate }
                TrendChartCard("Heart Rate Variability", "ms", Color(0xFF42A5F5), rollups) { it.hrvRmssd }
                TrendChartCard("Blood Oxygen (SpO₂)", "%", Color(0xFF66BB6A), rollups) { it.spo2 }
                TrendChartCard("Respiratory Rate", "breaths/min", Color(0xFFAB47BC), rollups) { it.respiratoryRate }
                TrendChartCard("Body Temperature", "°C", Color(0xFFFFA726), rollups) { it.bodyTemperature }
                TrendChartCard("Hydration", "ml", Color(0xFF26C6DA), rollups) { it.hydrationMl }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * One metric's trend card. [selector] pulls that metric out of each day's [LatestVitals]; days where
 * it is null are dropped (a missing sensor doesn't leave a phantom zero). With no readings the card
 * shows its empty state.
 */
@Composable
private fun TrendChartCard(
    title: String,
    unit: String,
    lineColor: Color,
    rollups: List<LatestVitals>,
    selector: (LatestVitals) -> Double?,
) {
    // day index (X) → value (Y); rollups arrive ascending by date, so index is chronological.
    val points = remember(rollups, title) {
        rollups.mapIndexedNotNull { index, v ->
            selector(v)?.let { index.toFloat() to it.toFloat() }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "Latest: ${latest?.let { formatValue(it, unit) } ?: "—"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                    )
                }
            }

            if (points.size < 2) {
                TrendEmptyState(
                    message = if (points.isEmpty()) {
                        "No readings yet. Data appears as it syncs from Health Connect."
                    } else {
                        "Only one reading so far — a trend needs a couple of days."
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

/** The Canvas line + gradient-fill, mapping day-index → X and value → Y. */
@Composable
private fun TrendLine(points: List<Pair<Float, Float>>, lineColor: Color) {
    val chartProgress: Float by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 850),
        label = "trend_chart",
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .graphicsLayer(
                alpha = chartProgress,
                translationY = (1f - chartProgress) * 36f,
            )
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
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 6f, cap = StrokeCap.Round),
        )
        // Emphasise the latest point.
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
                imageVector = Icons.Default.MonitorHeart,
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

/** Unit-aware value formatting shared by the axis labels and the "Latest" line. */
private fun formatValue(value: Float, unit: String): String = when (unit) {
    "°C" -> String.format("%.1f°C", value)
    "%" -> "${value.toInt()}%"
    "ml" -> if (value >= 1000) String.format("%.1f L", value / 1000) else "${value.toInt()} ml"
    else -> "${value.toInt()} $unit"
}
