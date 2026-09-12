package com.hellohealth.ui.debug

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hellohealth.data.local.entities.SyncLogEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncDebugScreen(
    viewModel: SyncDebugViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val logs by viewModel.logs.collectAsState()
    val unsynced by viewModel.unsynced.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync Debug") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshUnsynced() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh counts")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    viewModel.forceSync()
                    viewModel.refreshUnsynced()
                },
                icon = { Icon(Icons.Default.Sync, contentDescription = null) },
                text = { Text("Force sync") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                UnsyncedCard(unsynced)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Recent sync runs (${logs.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (logs.isEmpty()) {
                item {
                    Text(
                        "No sync runs recorded yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            items(logs) { entry -> SyncLogRow(entry) }
        }
    }
}

@Composable
private fun UnsyncedCard(counts: SyncDebugViewModel.UnsyncedCounts) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Pending local writes: ${counts.total}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text("goals: ${counts.goals}", style = MaterialTheme.typography.bodySmall)
            Text("profile: ${counts.profile}", style = MaterialTheme.typography.bodySmall)
            Text("foodPrefs: ${counts.foodPrefs}", style = MaterialTheme.typography.bodySmall)
            Text("snapshots: ${counts.snapshots}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())

@Composable
private fun SyncLogRow(entry: SyncLogEntity) {
    val failed = entry.failures > 0
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (failed) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(entry.featureTag, fontWeight = FontWeight.Bold)
                Text(
                    timeFormat.format(Date(entry.runAtEpochMs)),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "push ${entry.pushed}  pull ${entry.pulled}  conflict ${entry.conflicts}  " +
                    "fail ${entry.failures}  ${entry.durationMs}ms  ${entry.resultLabel}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
