package com.hellohealth.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FaceRetouchingNatural
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hellohealth.domain.model.EmotionType
import com.hellohealth.ui.common.SingleSelectChips
import kotlinx.coroutines.launch

/**
 * The single "Log your mood" entry point, opened from the dashboard [EmotionsCard]. Presents the
 * three logging paths — camera scan, gallery photo, manual note — as EQUAL peers, plus inline
 * emotion quick-chips so the common case (tap a mood) never leaves the sheet.
 *
 * Copies [com.hellohealth.ui.dashboard.ProfileBottomSheet]'s verified `dismissThen` latch: every
 * action hides the sheet FIRST, then fires on completion, so the sheet slides away cleanly and a
 * double-tap can't trigger two actions (the `dismissing` flag one-shots it).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogMoodSheet(
    onDismiss: () -> Unit,
    onScan: () -> Unit,
    onPickGallery: () -> Unit,
    onLogManually: () -> Unit,
    onQuickLog: (EmotionType) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Latch so only the FIRST tap is honored, and the sheet animates closed before we act.
    var dismissing by remember { mutableStateOf(false) }

    fun dismissThen(action: () -> Unit) {
        if (dismissing) return
        dismissing = true
        scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            Text(
                text = "Log your mood",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.size(16.dp))

            MoodEntryRow(
                icon = Icons.Default.FaceRetouchingNatural,
                title = "Scan my face",
                subtitle = "Read your mood on-device",
                onClick = { dismissThen(onScan) }
            )
            MoodEntryRow(
                icon = Icons.Default.PhotoLibrary,
                title = "Pick a photo",
                subtitle = "Analyze a photo from your gallery",
                onClick = { dismissThen(onPickGallery) }
            )
            MoodEntryRow(
                icon = Icons.Default.EditNote,
                title = "Add a note",
                subtitle = "Log with a note on its own screen",
                onClick = { dismissThen(onLogManually) }
            )

            Spacer(modifier = Modifier.size(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.size(16.dp))

            Text(
                text = "Or tap how you feel",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.size(12.dp))
            // selected = null: this is a fire-and-close picker, not a persistent selection.
            SingleSelectChips(
                options = EmotionType.entries,
                selected = null,
                labelOf = { "${it.emoji()} ${it.displayLabel()}" },
                onSelect = { emotion -> dismissThen { onQuickLog(emotion) } }
            )
        }
    }
}

/**
 * One peer entry option. Extends the flat [com.hellohealth.ui.dashboard.ProfileMenuItem] row with a
 * tonal circular icon and a subtitle so the three logging paths read as substantial, equal choices.
 */
@Composable
private fun MoodEntryRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}
