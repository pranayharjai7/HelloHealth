package com.hellohealth.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.hellohealth.ui.dashboard.components.MultiActivityRings
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailsScreen(
    viewModel: DashboardViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val summary = uiState.healthSummary
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Health Stats", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadHealthSummary() }) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = primaryColor)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
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
                        colors = listOf(
                            primaryColor.copy(alpha = 0.2f),
                            backgroundColor
                        )
                    )
                )
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 1. Activity Section (Core Rings)
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
                                MultiActivityRings(
                                    stepsProgress = summary.steps.toFloat() / summary.stepsGoal,
                                    caloriesProgress = summary.activeCalories.toFloat() / summary.caloriesGoal.toFloat(),
                                    minutesProgress = summary.activeTimeMinutes.toFloat() / summary.activeTimeGoal.toFloat(),
                                    modifier = Modifier.size(200.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                RingLegendItem("Steps", primaryColor, summary.steps.toString())
                                RingLegendItem("Kcal", Color(0xFFFF7043), summary.activeCalories.toInt().toString())
                                RingLegendItem("Min", Color(0xFF42A5F5), summary.activeTimeMinutes.toString())
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MiniStat(Icons.Default.Route, "Distance", String.format("%.2f km", summary.distanceKm), Modifier.weight(1f))
                                MiniStat(Icons.Default.LocalFireDepartment, "Total Burn", "${summary.totalCalories.toInt()} kcal", Modifier.weight(1f))
                            }
                        }
                    }
                }

                // 2. Body Metrics Section
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

                // 3. Heart & Vitals Section
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

                // 4. Cardio Fitness Section
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

                // 5. Sleep Section
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
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF5C6BC0).copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Bedtime, null, tint = Color(0xFF5C6BC0), modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text("Sleep Duration", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
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
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), modifier = Modifier.size(16.dp))
                                    SleepTimeInfo("Awake", summary.sleepEndTime)
                                }
                            }
                        }
                    }
                }

                // 6. Activity Log
                item {
                    SectionHeader("Activity Log")
                }

                if (summary.exerciseSessions.isEmpty()) {
                    item {
                        EmptyState()
                    }
                } else {
                    itemsIndexed(summary.exerciseSessions) { index, session ->
                        AnimatedSessionItem(session, index)
                    }
                }
                
                item { Spacer(modifier = Modifier.height(48.dp)) }
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
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SleepTimeInfo(label: String, time: java.time.Instant) {
    val formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneId.systemDefault())
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
                modifier = Modifier.size(32.dp).clip(CircleShape).background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun AnimatedSessionItem(session: ExerciseSession, index: Int) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 100L)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally() + fadeIn(),
        modifier = Modifier.fillMaxWidth()
    ) {
        ExerciseSessionItem(session)
    }
}

@Composable
private fun ExerciseSessionItem(session: ExerciseSession) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
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
                    imageVector = when(session.typeLabel) {
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
                    text = "${session.durationMinutes} min • ${session.startTime.atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            if (session.calories != null) {
                Text(
                    text = "${session.calories.toInt()} kcal",
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFFF7043),
                    fontSize = 14.sp
                )
            }
        }
    }
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
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.History, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(16.dp))
        Text("No workouts yet today.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
    }
}
