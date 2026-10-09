package com.hellohealth.ui.vitals

import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hellohealth.domain.model.vitals.LatestVitals
import com.hellohealth.ui.charts.TrendHybridLayout
import com.hellohealth.ui.charts.TrendMetric
import com.hellohealth.ui.charts.relativeLastRecorded

/**
 * Vitals & Recovery trends — a hero Resting-Heart-Rate chart plus a 2-column grid of sparkline tiles
 * (HRV, SpO₂, respiratory rate, body temperature, hydration); tap a tile to expand it. Each card
 * shows the latest value + a relative "last recorded" label. Reuses the shared [TrendHybridLayout].
 * Values render in natural units; SpO₂ is 0-100 (NOT a fraction).
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

    val rollups = state.rollups
    val metrics = listOf(
        vitalsMetric("Resting Heart Rate", "bpm", VitalsColors.rhr, rollups) { it.restingHeartRate },
        vitalsMetric("Heart Rate Variability", "ms", VitalsColors.hrv, rollups) { it.hrvRmssd },
        vitalsMetric("Blood Oxygen (SpO₂)", "%", VitalsColors.spo2, rollups) { it.spo2 },
        vitalsMetric("Respiratory Rate", "breaths/min", VitalsColors.resp, rollups) { it.respiratoryRate },
        vitalsMetric("Body Temperature", "°C", VitalsColors.temp, rollups) { it.bodyTemperature },
        vitalsMetric("Hydration", "ml", VitalsColors.hydration, rollups) { it.hydrationMl },
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Vitals & Recovery", fontWeight = FontWeight.Black) },
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
                .background(brush = Brush.verticalGradient(listOf(primaryColor.copy(alpha = 0.15f), backgroundColor)))
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Last ${VitalsTrendsViewModel.TREND_WINDOW_DAYS} days · one point per day",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )

                TrendHybridLayout(metrics = metrics)

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/** Per-metric identity colors (each chart is its own single series, not a categorical set). */
private object VitalsColors {
    val rhr = Color(0xFFEF5350)
    val hrv = Color(0xFF42A5F5)
    val spo2 = Color(0xFF66BB6A)
    val resp = Color(0xFFAB47BC)
    val temp = Color(0xFFFFA726)
    val hydration = Color(0xFF26C6DA)
}

private fun vitalsMetric(
    title: String,
    unit: String,
    color: Color,
    rollups: List<LatestVitals>,
    selector: (LatestVitals) -> Double?,
): TrendMetric {
    val indexed = rollups.mapIndexedNotNull { index, v -> selector(v)?.let { Triple(index, it, v.localDate) } }
    val points = indexed.map { (i, value, _) -> i.toFloat() to value.toFloat() }
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

private fun formatValue(value: Double, unit: String): String = when (unit) {
    "°C" -> String.format("%.1f°C", value)
    "%" -> "${value.toInt()}%"
    "ml" -> if (value >= 1000) String.format("%.1f L", value / 1000) else "${value.toInt()} ml"
    else -> "${value.toInt()} $unit"
}
