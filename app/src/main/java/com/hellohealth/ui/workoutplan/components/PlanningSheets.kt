@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hellohealth.ui.workoutplan.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
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
import androidx.compose.ui.unit.dp
import com.hellohealth.domain.model.PlanType
import com.hellohealth.ui.common.SingleSelectChips
import com.hellohealth.ui.workoutplan.SlotOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Themed planning sheets that replace the old workout-plan AlertDialogs ([CreateRoutineSheet],
 * [AddDaySheet], [RenameSheet]). They share the Health screen's [com.hellohealth.ui.health.BodyLogSheet]
 * chrome — a surface-colored [ModalBottomSheet] with a 32dp top-rounded shape, the default drag handle,
 * an `imePadding()` content column so the keyboard pushes the fields and action button up (Stage 1 house
 * rule), and the dismiss-then-act latch so the sheet animates closed before the write fires and a
 * double-tap can't submit twice. Confirm is a filled [Button]; dismiss is via drag/scrim/Cancel.
 */

/** Hides [sheetState] then runs [action] once — the shared dismiss-then-act latch. */
private fun dismissThenAct(scope: CoroutineScope, sheetState: SheetState, action: () -> Unit) {
    scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRoutineSheet(
    onCreate: (name: String, planType: PlanType) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var planType by remember { mutableStateOf(PlanType.WEEKLY) }
    val canCreate = name.isNotBlank()

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
            Text("New routine", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "Schedule",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            SingleSelectChips(
                options = PlanType.entries,
                selected = planType,
                labelOf = { it.displayName },
                onSelect = { planType = it },
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    enabled = canCreate && !submitting,
                    onClick = {
                        if (submitting || !canCreate) return@Button
                        submitting = true
                        dismissThenAct(scope, sheetState) { onCreate(name, planType) }
                    },
                ) {
                    Text("Create")
                }
            }
            Spacer(Modifier.size(4.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDaySheet(
    slots: List<SlotOption>,
    onAdd: (slotKey: String, name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    var selectedSlot by remember(slots) { mutableStateOf(slots.firstOrNull()) }
    var name by remember { mutableStateOf("") }

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
            Text("Add day", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            Text(
                text = "Slot",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            // The slot list can run up to 99 chips (CUSTOM) / 31 (MONTHLY). Bound its height and let it
            // scroll so the Name field and the action button stay reachable. SingleSelectChips has no
            // Modifier param, so the sizing lives on this wrapper Box (same approach as the old dialog).
            Box(
                modifier = Modifier
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                SingleSelectChips(
                    options = slots,
                    selected = selectedSlot,
                    labelOf = { it.label },
                    onSelect = { selectedSlot = it },
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    enabled = selectedSlot != null && !submitting,
                    onClick = {
                        val slot = selectedSlot ?: return@Button
                        if (submitting) return@Button
                        submitting = true
                        dismissThenAct(scope, sheetState) { onAdd(slot.key, name) }
                    },
                ) {
                    Text("Add")
                }
            }
            Spacer(Modifier.size(4.dp))
        }
    }
}

/**
 * Rename sheet reused for both a day and a routine — [title] distinguishes them ("Rename day" /
 * "Rename routine"). Save is disabled until the name is non-blank.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameSheet(
    title: String,
    initialName: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(initialName) }
    val canSave = name.isNotBlank()

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
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    enabled = canSave && !submitting,
                    onClick = {
                        if (submitting || !canSave) return@Button
                        submitting = true
                        dismissThenAct(scope, sheetState) { onRename(name) }
                    },
                ) {
                    Text("Save")
                }
            }
            Spacer(Modifier.size(4.dp))
        }
    }
}
