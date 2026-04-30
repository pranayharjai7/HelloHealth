package com.hellohealth.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    viewModel: GoalsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Daily Goals", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = primaryColor
                        )
                    } else {
                        TextButton(onClick = { viewModel.saveGoals() }) {
                            Text("Save", fontWeight = FontWeight.Bold, color = primaryColor)
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
                        colors = listOf(primaryColor.copy(alpha = 0.1f), backgroundColor)
                    )
                )
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = "Set your daily activity targets to stay motivated and healthy.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                GoalAdjuster(
                    icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                    label = "Daily Steps",
                    value = uiState.goals.steps.toFloat(),
                    range = 1000f..30000f,
                    step = 500f,
                    unit = "steps",
                    color = primaryColor,
                    onValueChange = { viewModel.updateStepsGoal(it.roundToInt()) }
                )

                GoalAdjuster(
                    icon = Icons.Default.LocalFireDepartment,
                    label = "Active Calories",
                    value = uiState.goals.activeCalories.toFloat(),
                    range = 100f..2000f,
                    step = 50f,
                    unit = "Cal",
                    color = Color(0xFFFF7043),
                    onValueChange = { viewModel.updateCaloriesGoal(it.roundToInt()) }
                )

                GoalAdjuster(
                    icon = Icons.Default.Timer,
                    label = "Active Minutes",
                    value = uiState.goals.activeMinutes.toFloat(),
                    range = 10f..300f,
                    step = 5f,
                    unit = "min",
                    color = Color(0xFF42A5F5),
                    onValueChange = { viewModel.updateMinutesGoal(it.roundToInt()) }
                )

                Spacer(modifier = Modifier.height(32.dp))
                
                if (uiState.error != null) {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }

                if (uiState.successMessage != null) {
                    Text(
                        text = uiState.successMessage!!,
                        color = primaryColor,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }

                Text(
                    text = "These saved targets become the shared daily limit used throughout the app, including the dashboard card and Health Stats screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun GoalAdjuster(
    icon: ImageVector,
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    unit: String,
    color: Color,
    onValueChange: (Float) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val stepsCount = ((range.endInclusive - range.start) / step).toInt() - 1

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onValueChange((value - step).coerceIn(range))
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Remove, null, tint = color.copy(alpha = 0.7f))
                    }
                    
                    Text(
                        text = "${value.roundToInt()}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = color,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onValueChange((value + step).coerceIn(range))
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Add, null, tint = color.copy(alpha = 0.7f))
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Slider(
                value = value,
                onValueChange = {
                    if (it != value) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onValueChange(it)
                    }
                },
                valueRange = range,
                steps = stepsCount,
                colors = SliderDefaults.colors(
                    thumbColor = color,
                    activeTrackColor = color,
                    inactiveTrackColor = color.copy(alpha = 0.2f)
                )
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${range.start.roundToInt()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                Text("${range.endInclusive.roundToInt()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }
        }
    }
}
