package com.hellohealth.ui.moodtimeline

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FaceRetouchingNatural
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hellohealth.domain.model.EmotionRecord
import com.hellohealth.domain.usecase.EmotionInsights
import com.hellohealth.ui.theme.moodAccentFor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Fraction the source-badge tint is lerped toward the emotion accent — mirrors the card's dot tint. */
private const val BADGE_TINT_FRACTION = 0.35f

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

/**
 * Browsable, day-grouped mood history. Header previews the 30-day Balance Score (taps through to the
 * full [com.hellohealth.ui.emotioninsights.EmotionInsightsScreen] breakdown). Each row shows the mood,
 * time, optional note, and a scan/manual source badge, with an overflow-menu Delete.
 *
 * Delete is a plain overflow action here — NOT a SwipeToDismissBox (the pinned Compose BOM predates
 * its stable API). The mandatory Undo snackbar is added in Step 7.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodTimelineScreen(
    viewModel: MoodTimelineViewModel,
    onBack: () -> Unit,
    onOpenInsights: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Mood Timeline", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            if (uiState.daySections.isEmpty()) {
                EmptyState(primaryColor)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "header") {
                        TimelineHeaderCard(
                            insights = uiState.insights,
                            windowDays = uiState.windowDays,
                            primaryColor = primaryColor,
                            onClick = onOpenInsights
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    uiState.daySections.forEach { section ->
                        item(key = "day-${section.date}") {
                            DayHeaderPill(section.date, primaryColor)
                        }
                        items(
                            items = section.records,
                            key = { it.id }
                        ) { record ->
                            MoodTimelineRow(
                                record = record,
                                primaryColor = primaryColor,
                                onDelete = { viewModel.delete(record.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineHeaderCard(
    insights: EmotionInsights,
    windowDays: Int,
    primaryColor: Color,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Balance Score",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = "${insights.score}",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    color = primaryColor
                )
                Text(
                    text = "${descriptorFor(insights.score)} · last $windowDays days",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun DayHeaderPill(date: LocalDate, primaryColor: Color) {
    Box(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .background(
                color = primaryColor.copy(alpha = 0.08f),
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = labelForDay(date),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = primaryColor
        )
    }
}

@Composable
private fun MoodTimelineRow(
    record: EmotionRecord,
    primaryColor: Color,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val badgeTint = lerp(primaryColor, moodAccentFor(record.emotion).accent, BADGE_TINT_FRACTION)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = record.emotion.emoji(), fontSize = 28.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.emotion.displayLabel(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = timeOf(record),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
                record.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        maxLines = 2
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            SourceBadge(record.source, badgeTint)
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

/** A tonal chip marking how the mood was captured — face-scan vs. manual note. */
@Composable
private fun SourceBadge(source: String, tint: Color) {
    val isCamera = source == EmotionRecord.SOURCE_CAMERA
    val icon = if (isCamera) Icons.Default.FaceRetouchingNatural else Icons.Default.EditNote
    val label = if (isCamera) "Scan" else "Manual"
    Row(
        modifier = Modifier
            .background(color = tint.copy(alpha = 0.14f), shape = RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}

@Composable
private fun EmptyState(primaryColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "🙂", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "No moods logged yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = primaryColor
        )
        Text(
            text = "Log a mood from the dashboard and it will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

/** A record's wall-clock time, rendered at the tz offset captured when it was logged. */
private fun timeOf(record: EmotionRecord): String =
    Instant.ofEpochMilli(record.timestampUtcEpochMs)
        .atOffset(ZoneOffset.ofTotalSeconds(record.tzOffsetMinutes * 60))
        .toLocalTime()
        .format(TIME_FORMAT)

private fun labelForDay(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DAY_FORMAT)
    }
}

private fun descriptorFor(score: Int): String = when {
    score >= 80 -> "Thriving"
    score >= 60 -> "Balanced"
    score >= 40 -> "Mixed"
    score >= 20 -> "Strained"
    else -> "Rough patch"
}
