package com.hellohealth.ui.health

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hellohealth.domain.model.ExerciseSession
import com.hellohealth.domain.model.HealthSummary
import com.hellohealth.domain.model.vitals.LatestVitals
import com.hellohealth.domain.model.vitals.ReadinessScore
import com.hellohealth.domain.model.vitals.ReadinessStatus
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The unified Health screen — one destination for Activity, Vitals & Recovery, Body composition, and
 * Sleep (replacing the old "Health Stats" screen + the separate Vitals surface). Date-aware via
 * [HealthViewModel]/[com.hellohealth.core.date.SelectedDateHolder]. Every metric dashes when absent,
 * never shows a fake 0. Mood-tinted like the other full screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(
    viewModel: HealthViewModel,
    onBack: () -> Unit,
    onOpenActivityDetail: (ExerciseSession) -> Unit,
    onOpenVitalsTrends: () -> Unit,
    onOpenBodyTrends: () -> Unit,
    onOpenInsights: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background
    val isToday = state.selectedDate == LocalDate.now()

    var showLogSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    if (showLogSheet) {
        BodyLogSheet(
            onDismiss = { showLogSheet = false },
            onSave = { weightKg, waistCm ->
                showLogSheet = false
                viewModel.logWeight(weightKg, waistCm)
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "Weight logged",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoLastLog()
                }
            },
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Health", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(primaryColor.copy(alpha = 0.15f), backgroundColor)))
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Text(
                        text = if (isToday) "Today" else state.selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM d")),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                item { ActivitySection(state.summary, state.goals) }
                item { VitalsSection(state.readiness, state.latestVitals, onOpenVitalsTrends) }
                item { BodySection(state.body, onLogWeight = { showLogSheet = true }, onOpenTrends = onOpenBodyTrends) }
                item { SleepSection(state.summary) }

                if (state.sessions.isNotEmpty()) {
                    item { SectionHeader("Activity Log") }
                    items(state.sessions, key = { it.id.ifBlank { it.startTime.toString() } }) { session ->
                        SessionRow(session, onClick = { onOpenActivityDetail(session) })
                    }
                }

                item { WeeklyInsightsRow(onClick = onOpenInsights) }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun ActivitySection(summary: HealthSummary, goals: com.hellohealth.domain.model.ActivityGoals) {
    SectionCard("Activity") {
        com.hellohealth.ui.dashboard.components.ActivityCard(
            steps = summary.steps,
            goalSteps = goals.steps.toLong(),
            activeCalories = summary.activeCalories,
            goalActiveCalories = goals.activeCalories,
            activeMinutes = summary.activeTimeMinutes,
            goalActiveMinutes = goals.activeMinutes,
            ringSize = 120.dp,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            MiniStat("Distance", if (summary.distanceKm > 0) String.format("%.2f km", summary.distanceKm) else DASH)
            MiniStat("Total burn", if (summary.totalCalories > 0) "${summary.totalCalories.roundToInt()} Cal" else DASH)
        }
    }
}

@Composable
private fun VitalsSection(readiness: ReadinessScore?, latest: LatestVitals?, onOpenTrends: () -> Unit) {
    SectionCard("Vitals & Recovery") {
        val score = readiness?.takeIf { it.status != ReadinessStatus.INSUFFICIENT_DATA }?.score
        Text(
            text = score?.let { "Readiness $it/100 · ${readiness.status.name.lowercase().replace('_', ' ')}" }
                ?: "Readiness — establishing baseline",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            MiniStat("RHR", latest?.restingHeartRate?.let { "${it.roundToInt()} bpm" } ?: DASH)
            MiniStat("HRV", latest?.hrvRmssd?.let { "${it.roundToInt()} ms" } ?: DASH)
            MiniStat("SpO₂", latest?.spo2?.let { "${it.roundToInt()}%" } ?: DASH)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            MiniStat("Resp", latest?.respiratoryRate?.let { "${it.roundToInt()} /min" } ?: DASH)
            MiniStat("Temp", latest?.bodyTemperature?.let { String.format("%.1f°C", it) } ?: DASH)
            MiniStat("Water", latest?.hydrationMl?.let { if (it >= 1000) String.format("%.1f L", it / 1000) else "${it.roundToInt()} ml" } ?: DASH)
        }
        Spacer(Modifier.height(12.dp))
        LinkRow("View full trends", onOpenTrends)
    }
}

@Composable
private fun BodySection(
    body: com.hellohealth.domain.model.BodyAnalytics,
    onLogWeight: () -> Unit,
    onOpenTrends: () -> Unit,
) {
    SectionCard("Body composition") {
        if (!body.hasAnyData) {
            ChartEmpty("Log your weight or sync a smart scale to track body composition.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = onLogWeight, modifier = Modifier.fillMaxWidth()) {
                Text("Log weight")
            }
            return@SectionCard
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            MiniStat("Weight", body.latestWeightKg?.let { String.format("%.1f kg", it) } ?: DASH)
            MiniStat("BMI", body.bmi?.let { String.format("%.1f", it) } ?: DASH)
            MiniStat("Body fat", body.bodyFatPct?.let { String.format("%.1f%%", it) } ?: DASH)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            MiniStat("Fat mass", body.fatMassKg?.let { String.format("%.1f kg", it) } ?: DASH)
            MiniStat("Lean mass", body.leanMassKg?.let { String.format("%.1f kg", it) } ?: DASH)
            MiniStat("BMR", body.bmr?.let { "${it.roundToInt()} kcal" } ?: DASH)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            MiniStat("Body water", body.bodyWaterKg?.let { String.format("%.1f kg", it) } ?: DASH)
            MiniStat("Bone mass", body.boneMassKg?.let { String.format("%.1f kg", it) } ?: DASH)
            MiniStat("TDEE", body.tdee?.let { "${it.roundToInt()} kcal" } ?: DASH)
        }
        body.weightChangeKg?.let { change ->
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Weight ${if (change <= 0) "down" else "up"} ${String.format("%.1f", kotlin.math.abs(change))} kg over the last 30 days",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        Spacer(Modifier.height(16.dp))
        // Two clearly-differentiated affordances (was two cramped text LinkRows 4dp apart): a primary
        // "Log weight" action + a secondary outlined "View full trends".
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onLogWeight, modifier = Modifier.weight(1f)) {
                Text("Log weight")
            }
            OutlinedButton(onClick = onOpenTrends, modifier = Modifier.weight(1f)) {
                Text("View full trends")
            }
        }
    }
}

@Composable
private fun SleepSection(summary: HealthSummary) {
    SectionCard("Sleep") {
        val mins = summary.sleepDurationMinutes
        MiniStat("Duration", if (mins > 0) "${mins / 60}h ${mins % 60}m" else DASH)
    }
}

// --- shared bits ---------------------------------------------------------------------------------

private const val DASH = "—"

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
private fun LinkRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun WeeklyInsightsRow(onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Weekly insights", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("How your dimensions moved together", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun SessionRow(session: ExerciseSession, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(session.title?.ifBlank { session.typeLabel } ?: session.typeLabel, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "${session.durationMinutes} min" + (session.calories?.let { " · ${it.roundToInt()} Cal" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun ChartEmpty(message: String) {
    Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
    }
}
