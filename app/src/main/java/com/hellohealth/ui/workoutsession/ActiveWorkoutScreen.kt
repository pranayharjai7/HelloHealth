package com.hellohealth.ui.workoutsession

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hellohealth.domain.model.PlannedExerciseWithDetails
import com.hellohealth.domain.model.SessionSet
import com.hellohealth.workoutsession.RestState
import com.hellohealth.workoutsession.WorkoutSessionService
import kotlinx.coroutines.delay

/**
 * The live workout screen. On first entry it starts a session (from the planned day if a `dayId` was
 * passed, else ad-hoc). It shows the running rest timer (ticked from the controller's absolute
 * deadline, so it survives process death — see [WorkoutSessionController]), the logged sets grouped
 * by exercise, a form to log the next set (prefilled from the exercise's last working set), and a
 * Finish action that completes the session and shows a summary.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    viewModel: ActiveWorkoutViewModel,
    onFinished: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background
    val context = LocalContext.current

    var showAbandonDialog by remember { mutableStateOf(false) }

    // Ask for POST_NOTIFICATIONS once (Android 13+) so the foreground-service notification can show.
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* best-effort; the service runs regardless */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Start the session once, when the screen is first shown.
    LaunchedEffect(Unit) {
        if (viewModel.dayId != null) viewModel.startFromPlannedDay() else viewModel.startAdHoc()
    }

    // Keep the foreground service in lockstep with the active session: start it when a session is
    // active, stop it when the session ends. The service also self-stops when it observes no active
    // session, so this is belt-and-suspenders.
    LaunchedEffect(uiState.session?.id, uiState.isActive) {
        if (uiState.isActive) {
            WorkoutSessionService.start(context, uiState.session?.title)
        }
    }

    // Navigate away once the session is finished / abandoned.
    LaunchedEffect(uiState.finished) {
        if (uiState.finished) {
            WorkoutSessionService.stop(context)
            onFinished()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = uiState.session?.title ?: "Workout",
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { showAbandonDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { RestTimerBanner(restState = uiState.restState, onStop = viewModel::stopRest) }

                item {
                    SummaryHeader(
                        setCount = uiState.sets.count { !it.isSkipped },
                        totalVolumeKg = uiState.totalVolumeKg,
                    )
                }

                if (uiState.sets.isEmpty()) {
                    item { EmptySets(primaryColor) }
                } else {
                    uiState.sets.forEach { set ->
                        item(key = set.id) {
                            LoggedSetRow(
                                set = set,
                                onSkip = { viewModel.skipSet(set.id) },
                                onDelete = { viewModel.deleteSet(set.id) },
                            )
                        }
                    }
                }

                item {
                    LogSetForm(
                        planned = uiState.plannedExercises,
                        onLog = { exerciseId, plannedId, reps, weight, rest ->
                            viewModel.logSet(
                                exerciseId = exerciseId,
                                plannedExerciseId = plannedId,
                                reps = reps,
                                weightKg = weight,
                                restSeconds = rest,
                            )
                        },
                    )
                }

                item {
                    Button(
                        onClick = viewModel::finish,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        enabled = uiState.isActive,
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Finish workout")
                    }
                }
            }
        }
    }

    if (showAbandonDialog) {
        AlertDialog(
            onDismissRequest = { showAbandonDialog = false },
            title = { Text("Leave this workout?") },
            text = { Text("Your logged sets are kept, but the session will be marked abandoned.") },
            confirmButton = {
                TextButton(onClick = {
                    showAbandonDialog = false
                    viewModel.abandon()
                }) { Text("Abandon") }
            },
            dismissButton = {
                TextButton(onClick = { showAbandonDialog = false }) { Text("Keep going") }
            }
        )
    }
}

@Composable
private fun RestTimerBanner(restState: RestState, onStop: () -> Unit) {
    if (restState !is RestState.Resting) return
    // Tick once a second off the ABSOLUTE deadline — correct even after process death.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(restState.restEndsAtEpochMs) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= restState.restEndsAtEpochMs) break
            delay(250L)
        }
    }
    val remainingMs = (restState.restEndsAtEpochMs - now).coerceAtLeast(0L)
    if (remainingMs <= 0L) return
    val totalSeconds = ((remainingMs + 999L) / 1000L).toInt()
    val mm = totalSeconds / 60
    val ss = totalSeconds % 60

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Resting",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = "%d:%02d".format(mm, ss),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            TextButton(onClick = onStop) { Text("Skip") }
        }
    }
}

@Composable
private fun SummaryHeader(setCount: Int, totalVolumeKg: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatTile(label = "Sets", value = setCount.toString(), modifier = Modifier.weight(1f))
        StatTile(
            label = "Volume",
            value = if (totalVolumeKg > 0) "${trimDouble(totalVolumeKg)} kg" else "—",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun LoggedSetRow(set: SessionSet, onSkip: () -> Unit, onDelete: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Set ${set.setNumber}" + if (set.isWarmup) " · warmup" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    text = setSummary(set),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (set.isSkipped) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
            if (!set.isSkipped) {
                TextButton(onClick = onSkip) { Text("Skip") }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Delete set",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun LogSetForm(
    planned: List<PlannedExerciseWithDetails>,
    onLog: (exerciseId: String, plannedId: String?, reps: Int?, weightKg: Double?, restSeconds: Int?) -> Unit,
) {
    // Default the exercise to the first planned one (if any); for ad-hoc the user types an id-free
    // free entry isn't supported in F1's minimal form, so this surfaces the planned list only.
    val firstPlanned = planned.firstOrNull()
    var reps by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var rest by remember { mutableStateOf("90") }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = firstPlanned?.exercise?.name ?: "Log a set",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = reps,
                    onValueChange = { reps = it.filter(Char::isDigit).take(4) },
                    label = { Text("Reps") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
                    label = { Text("kg") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = rest,
                    onValueChange = { rest = it.filter(Char::isDigit).take(4) },
                    label = { Text("Rest s") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            val exerciseId = firstPlanned?.planned?.exerciseId
            FilledTonalButton(
                onClick = {
                    if (exerciseId != null) {
                        onLog(
                            exerciseId,
                            firstPlanned.planned.id,
                            reps.toIntOrNull(),
                            weight.toDoubleOrNull(),
                            rest.toIntOrNull(),
                        )
                        reps = ""
                        weight = ""
                    }
                },
                enabled = exerciseId != null && (reps.toIntOrNull() ?: 0) > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Log set")
            }
        }
    }
}

@Composable
private fun EmptySets(primaryColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "🏋️", fontSize = 40.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "No sets yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = primaryColor
        )
        Text(
            text = "Log your first set below.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

private fun setSummary(set: SessionSet): String {
    if (set.isSkipped) return "Skipped"
    val parts = buildList {
        set.reps?.let { add("$it reps") }
        set.weightKg?.let { add("@ ${trimDouble(it)} kg") }
        set.durationSeconds?.let { add("${it}s") }
        set.distanceKm?.let { add("${trimDouble(it)} km") }
    }
    return if (parts.isEmpty()) "Logged" else parts.joinToString(" ")
}

/** "80.0" → "80", "1.5" → "1.5". */
private fun trimDouble(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
