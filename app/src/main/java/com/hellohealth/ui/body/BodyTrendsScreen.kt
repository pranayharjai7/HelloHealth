package com.hellohealth.ui.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hellohealth.domain.model.BodyMetric
import com.hellohealth.ui.charts.TrendHybridLayout
import com.hellohealth.ui.charts.TrendMetric
import com.hellohealth.ui.charts.chartSeries1
import com.hellohealth.ui.charts.chartSeries2
import com.hellohealth.ui.charts.chartSeries3
import com.hellohealth.ui.charts.relativeLastRecorded

/**
 * Body-composition trends — a hero Weight chart plus sparkline tiles for body-fat and lean mass
 * (tap a tile to expand it in place). Each card shows the latest value + a relative "last recorded"
 * label. Reuses the shared [TrendHybridLayout]; metrics drop null days so a missing reading never
 * plots a phantom zero.
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

    val metrics = listOf(
        bodyMetric("Weight", "kg", chartSeries1(), state.metrics) { it.weightKg },
        bodyMetric("Body fat", "%", chartSeries2(), state.metrics) { it.bodyFatPct },
        bodyMetric("Lean mass", "kg", chartSeries3(), state.metrics) { it.leanMassKg },
    )

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
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Your last ${BodyTrendsViewModel.TREND_WINDOW_DAYS} days — logged weigh-ins and " +
                        "synced body-composition readings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                )

                TrendHybridLayout(metrics = metrics)

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** Build a [TrendMetric] from the body history: points (null days dropped) + latest + relative date. */
private fun bodyMetric(
    title: String,
    unit: String,
    color: Color,
    metrics: List<BodyMetric>,
    selector: (BodyMetric) -> Double?,
): TrendMetric {
    // metrics arrive ascending by date, so the list index is chronological on the X axis.
    val indexed = metrics.mapIndexedNotNull { index, m -> selector(m)?.let { Triple(index, it, m.localDate) } }
    val points = indexed.map { (i, v, _) -> i.toFloat() to v.toFloat() }
    val last = indexed.lastOrNull()
    return TrendMetric(
        title = title,
        unit = unit,
        color = color,
        points = points,
        latestFormatted = last?.let { formatValue(it.second, unit) },
        lastRecorded = last?.third?.let { relativeLastRecorded(it) },
    )
}

/** Unit-aware value formatting for the body metrics. */
private fun formatValue(value: Double, unit: String): String = when (unit) {
    "%" -> String.format("%.1f%%", value)
    "kg" -> String.format("%.1f kg", value)
    "kcal" -> "${value.toInt()} kcal"
    else -> "${value.toInt()} $unit"
}
