package com.hellohealth.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hellohealth.domain.model.ExerciseSession
import com.hellohealth.ui.common.formatCalories
import com.hellohealth.ui.common.formatProgress
import com.hellohealth.ui.dashboard.components.MultiActivityRings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailsScreen(
    viewModel: DashboardViewModel,
    onBack: () -> Unit,
    onOpenActivityDetail: (ExerciseSession) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val summary = uiState.healthSummary
    val selectedDate = uiState.selectedDate

    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Health Stats", fontWeight = FontWeight.Black)
                        Text(
                            text = if (selectedDate == LocalDate.now()) {
                                "Today"
                            } else {
                                selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Removed refresh button
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        val pullRefreshState = rememberPullToRefreshState()
        
        if (pullRefreshState.isRefreshing) {
            LaunchedEffect(true) {
                viewModel.refreshSelectedDate()
            }
        }
        
        LaunchedEffect(uiState.isLoading) {
            if (!uiState.isLoading && pullRefreshState.isRefreshing) {
                pullRefreshState.endRefresh()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.2f),
                            backgroundColor
                        )
                    )
                )
                .padding(padding)
                .nestedScroll(pullRefreshState.nestedScrollConnection)
        ) {
            if (uiState.isLoading && !pullRefreshState.isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .align(Alignment.TopCenter),
                    color = primaryColor,
                    trackColor = Color.Transparent
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    SectionHeader("Daily Activity")
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(32.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                        ),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier.size(220.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val safeStepsGoal = summary.stepsGoal.coerceAtLeast(1)
                                val safeCaloriesGoal = summary.caloriesGoal.coerceAtLeast(1.0)
                                val safeActiveTimeGoal = summary.activeTimeGoal.coerceAtLeast(1)
                                MultiActivityRings(
                                    stepsProgress = summary.steps.toFloat() / safeStepsGoal,
                                    caloriesProgress = summary.activeCalories.toFloat() / safeCaloriesGoal.toFloat(),
                                    minutesProgress = summary.activeTimeMinutes.toFloat() / safeActiveTimeGoal.toFloat(),
                                    modifier = Modifier.size(200.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                RingLegendItem("Steps", primaryColor, formatProgress(summary.steps, summary.stepsGoal))
                                RingLegendItem("Cal", Color(0xFFFF7043), formatProgress(summary.activeCalories.toInt(), summary.caloriesGoal.toInt()))
                                RingLegendItem("Min", Color(0xFF42A5F5), formatProgress(summary.activeTimeMinutes.toInt(), summary.activeTimeGoal.toInt()))
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                MiniStat(Icons.Default.Route, "Distance", String.format("%.2f km", summary.distanceKm), Modifier.weight(1f))
                                MiniStat(Icons.Default.LocalFireDepartment, "Total Burn", formatCalories(summary.totalCalories), Modifier.weight(1f))
                            }
                        }
                    }
                }

                item {
                    SectionHeader("Body Metrics")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.MonitorWeight,
                            label = "Weight",
                            value = summary.weight?.let { String.format("%.1f kg", it) } ?: "--",
                            color = Color(0xFF9C27B0)
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Height,
                            label = "Height",
                            value = summary.height?.let { String.format("%.0f cm", it * 100) } ?: "--",
                            color = Color(0xFF795548)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    StatCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.Percent,
                        label = "Body Fat",
                        value = summary.bodyFat?.let { String.format("%.1f %%", it) } ?: "--",
                        color = Color(0xFFFF9800)
                    )
                }

                item {
                    SectionHeader("Heart & Vitals")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Favorite,
                            label = "Avg Heart Rate",
                            value = summary.heartRateAvg?.let { "$it bpm" } ?: "--",
                            color = Color(0xFFE91E63)
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Opacity,
                            label = "SpO2",
                            value = summary.oxygenSaturation?.let { String.format("%.0f %%", it) } ?: "--",
                            color = Color(0xFF03A9F4)
                        )
                    }
                    if (summary.bloodPressureSystolic != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        StatCard(
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Default.MonitorHeart,
                            label = "Blood Pressure",
                            value = "${summary.bloodPressureSystolic.toInt()}/${summary.bloodPressureDiastolic?.toInt()} mmHg",
                            color = Color(0xFFF44336)
                        )
                    }
                }

                item {
                    SectionHeader("Cardio Fitness")
                    StatCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.Speed,
                        label = "VO2 Max",
                        value = summary.vo2max?.let { String.format("%.1f ml/kg/min", it) } ?: "--",
                        color = Color(0xFF009688)
                    )
                }

                item {
                    SectionHeader("Sleep")
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF5C6BC0).copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Bedtime, null, tint = Color(0xFF5C6BC0), modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        "Sleep Duration",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = formatSleepDuration(summary.sleepDurationMinutes),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                            if (summary.sleepStartTime != null && summary.sleepEndTime != null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    SleepTimeInfo("Asleep", summary.sleepStartTime)
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForward,
                                        null,
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    SleepTimeInfo("Awake", summary.sleepEndTime)
                                }
                            }
                        }
                    }
                }

                item {
                    SectionHeader("Activity Log")
                }

                if (summary.exerciseSessions.isEmpty()) {
                    item {
                        EmptyState(selectedDate)
                    }
                } else {
                    itemsIndexed(summary.exerciseSessions) { index, session ->
                        AnimatedSessionItem(
                            session = session,
                            index = index,
                            onClick = { onOpenActivityDetail(session) }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(48.dp)) }
            }
            
            if (pullRefreshState.isRefreshing || pullRefreshState.progress > 0f) {
                PullToRefreshContainer(
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = primaryColor
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun MiniStat(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier, 
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SleepTimeInfo(label: String, time: Instant) {
    val formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    Column {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(formatter.format(time), fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

private fun formatSleepDuration(minutes: Long): String {
    if (minutes == 0L) return "--"
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return "${hours}h ${remainingMinutes}m"
}

@Composable
private fun StatCard(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun AnimatedSessionItem(
    session: ExerciseSession,
    index: Int,
    onClick: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 100L)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally() + fadeIn(),
        modifier = Modifier.fillMaxWidth()
    ) {
        ExerciseSessionItem(session = session, onClick = onClick)
    }
}

@Composable
private fun ExerciseSessionItem(
    session: ExerciseSession,
    onClick: () -> Unit
) {
    val isClickable = session.id.isNotBlank()

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = isClickable, onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (session.typeLabel) {
                        "Running" -> Icons.AutoMirrored.Filled.DirectionsRun
                        "Walking" -> Icons.AutoMirrored.Filled.DirectionsWalk
                        "Cycling" -> Icons.AutoMirrored.Filled.DirectionsBike
                        else -> Icons.Default.FitnessCenter
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title ?: session.typeLabel,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = sessionMetadataLabel(session),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (session.calories != null) {
                    Text(
                        text = formatCalories(session.calories),
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFFF7043),
                        fontSize = 14.sp
                    )
                }
                if (isClickable) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open activity detail",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }
}

private fun sessionMetadataLabel(session: ExerciseSession): String {
    val startTime = session.startTime.atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))
    return "${session.durationMinutes} min - $startTime"
}

@Composable
private fun RingLegendItem(label: String, color: Color, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun EmptyState(selectedDate: LocalDate) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.History,
            null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (selectedDate == LocalDate.now()) {
                "No workouts yet today."
            } else {
                "No workouts recorded for this day."
            },
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
    }
}
