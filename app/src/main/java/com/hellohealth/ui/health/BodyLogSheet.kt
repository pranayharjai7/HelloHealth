package com.hellohealth.ui.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Manual body-log entry for the Health screen's Body composition section — the one UI path to
 * [com.hellohealth.ui.health.HealthViewModel.logWeight]. Weight (kg) is required; waist (cm) is an
 * optional secondary field. Writes land on the currently-selected day via the VM (load-then-merge,
 * so a same-day Health Connect reading is preserved).
 *
 * Reuses [com.hellohealth.ui.dashboard.components.LogMoodSheet]'s dismiss-then-act latch so the sheet
 * animates closed before the write fires and a double-tap can't log twice. Save is disabled until the
 * weight parses to a sane positive value. A manual log is reversible — the host shows an "Undo"
 * snackbar that restores the day's prior row (see [com.hellohealth.ui.health.HealthViewModel.undoLastLog]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyLogSheet(
    onDismiss: () -> Unit,
    onSave: (weightKg: Double, waistCm: Double?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var weightText by remember { mutableStateOf("") }
    var waistText by remember { mutableStateOf("") }

    // Sane human ranges so a fat-fingered entry (e.g. grams, or a stray digit) can't be saved.
    val weight = weightText.trim().replace(',', '.').toDoubleOrNull()
    val waist = waistText.trim().replace(',', '.').toDoubleOrNull()
    val weightValid = weight != null && weight in 20.0..400.0
    val waistValid = waistText.isBlank() || (waist != null && waist in 30.0..200.0)
    val canSave = weightValid && waistValid

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Log weight",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            OutlinedTextField(
                value = weightText,
                onValueChange = { weightText = it },
                label = { Text("Weight (kg)") },
                singleLine = true,
                isError = weightText.isNotBlank() && !weightValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = waistText,
                onValueChange = { waistText = it },
                label = { Text("Waist (cm) · optional") },
                singleLine = true,
                isError = !waistValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    enabled = canSave && !saving,
                    onClick = {
                        val w = weight ?: return@Button
                        if (saving) return@Button
                        saving = true
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            onSave(w, if (waistText.isBlank()) null else waist)
                        }
                    },
                ) {
                    Text("Save")
                }
            }
            Spacer(Modifier.size(4.dp))
        }
    }
}
