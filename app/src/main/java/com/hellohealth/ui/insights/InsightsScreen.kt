package com.hellohealth.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hellohealth.domain.model.DailyStat
import com.hellohealth.domain.model.WeeklyInsights
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    viewModel: InsightsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Weekly Insights", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadWeeklyStats() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.1f), backgroundColor)
                    )
                )
                .padding(padding)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    InsightSummarySection(uiState.weeklyInsights)

                    InsightChartSection(
                        title = "Weekly Steps",
                        subtitle = "${uiState.weeklyInsights.stepGoalDays}/${uiState.weeklyInsights.completedDays.coerceAtLeast(1)} completed days hit goal",
                        stats = uiState.weeklyStats.dailyStats,
                        getValue = { it.steps.toFloat() },
                        color = primaryColor
                    )

                    InsightChartSection(
                        title = "Active Burn",
                        subtitle = "${uiState.weeklyInsights.calorieGoalDays}/${uiState.weeklyInsights.completedDays.coerceAtLeast(1)} completed days hit goal",
                        stats = uiState.weeklyStats.dailyStats,
                        getValue = { it.calories.toFloat() },
                        color = Color(0xFFFF7043)
                    )

                    InsightChartSection(
                        title = "Active Minutes",
                        subtitle = "${uiState.weeklyInsights.activeMinutesGoalDays}/${uiState.weeklyInsights.completedDays.coerceAtLeast(1)} completed days hit goal",
                        stats = uiState.weeklyStats.dailyStats,
                        getValue = { it.activeMinutes.toFloat() },
                        color = Color(0xFF42A5F5)
                    )

                    InsightChartSection(
                        title = "Sleep Hours",
                        subtitle = "Recovery trend across the week",
                        stats = uiState.weeklyStats.dailyStats,
                        getValue = { it.sleepMinutes.toFloat() / 60f },
                        color = Color(0xFF5C6BC0)
                    )

                    InsightCardsSection(uiState.weeklyInsights)
                }
            }
        }
    }
}

@Composable
private fun InsightSummarySection(insights: WeeklyInsights) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InsightMetricCard(
                modifier = Modifier.weight(1f),
                title = "Avg Steps",
                value = insights.averageSteps.toString()
            )
            InsightMetricCard(
                modifier = Modifier.weight(1f),
                title = "Avg Burn",
                value = "${insights.averageCalories} Cal"
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InsightMetricCard(
                modifier = Modifier.weight(1f),
                title = "Step Streak",
                value = "${insights.currentStepStreak} days"
            )
            InsightMetricCard(
                modifier = Modifier.weight(1f),
                title = "Best Day",
                value = insights.bestDayLabel
            )
        }
    }
}

@Composable
private fun InsightMetricCard(
    modifier: Modifier,
    title: String,
    value: String
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun InsightChartSection(
    title: String,
    subtitle: String,
    stats: List<DailyStat>,
    getValue: (DailyStat) -> Float,
    color: Color
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (stats.isEmpty()) {
                Box(
                    modifier = Modifier
                        .height(150.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No data available", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
            } else {
                val maxVal = stats.maxOfOrNull(getValue)?.coerceAtLeast(1f) ?: 1f

                Row(
                    modifier = Modifier
                        .height(150.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    stats.forEach { stat ->
                        val value = getValue(stat)
                        val heightFactor = value / maxVal

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .width(30.dp)
                                    .fillMaxWidth()
                            )
                            Box(
                                modifier = Modifier
                                    .width(30.dp)
                                    .height((120 * heightFactor.coerceIn(0.05f, 1f)).dp)
                                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                    .background(color)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stat.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightCardsSection(insights: WeeklyInsights) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        insights.cards.forEach { card ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(card.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = card.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                }
            }
        }
    }
}
