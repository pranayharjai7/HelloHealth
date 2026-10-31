package com.hellohealth.ui.nutrition.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hellohealth.domain.health.MicronutrientTargets.Micro
import com.hellohealth.domain.model.nutrition.MacroMode

/** One-line "why it matters" per micronutrient — settings should teach, not just toggle. */
private val MICRO_WHY = mapOf(
    Micro.SODIUM to "Blood pressure — most people get too much.",
    Micro.SATURATED_FAT to "Heart health — a limit to stay under.",
    Micro.SUGAR to "Added sugars spike energy and metabolic load.",
    Micro.CALCIUM to "Bone strength and muscle function.",
    Micro.IRON to "Energy and oxygen transport; low iron = fatigue.",
    Micro.POTASSIUM to "Balances sodium, supports healthy blood pressure.",
    Micro.VITAMIN_D to "Bone, immunity, mood — widely under-consumed.",
    Micro.VITAMIN_C to "Immunity and iron absorption.",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionSettingsScreen(
    viewModel: NutritionSettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val primary = MaterialTheme.colorScheme.primary
    val bg = MaterialTheme.colorScheme.background
    val d = state.derived

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Nutrition settings", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (state.isSaving) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = primary)
                    } else {
                        TextButton(onClick = { viewModel.save() }, enabled = state.canSave) {
                            Text("Save", fontWeight = FontWeight.Bold, color = primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = Color.Transparent,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(primary.copy(alpha = 0.10f), bg)))
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "By default HelloHealth sets your targets from your profile. Turn on Custom to pin your own.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )

            // ---- Calories ----
            SettingsCard("Calories", "Daily energy budget") {
                ToggleRow(
                    label = "Custom calorie budget",
                    hint = d?.calorieBudget?.let { "Auto: $it kcal (from your profile)" } ?: "Auto: add profile details",
                    checked = state.goals.useCustomCalories,
                    onToggle = viewModel::setUseCustomCalories,
                )
                if (state.goals.useCustomCalories) {
                    Spacer(Modifier.height(10.dp))
                    NumberField(
                        label = "Calorie budget (kcal)",
                        value = state.goals.calorieBudget,
                        onChange = viewModel::setCalorieBudget,
                    )
                }
            }

            // ---- Macros ----
            SettingsCard("Macronutrients", "Protein / carbs / fat / fiber") {
                ToggleRow(
                    label = "Custom macro targets",
                    hint = if (d?.proteinG != null) "Auto: P ${d.proteinG} · C ${d.carbsG} · F ${d.fatG} · Fiber ${d.fiberG} g" else "Auto from your budget",
                    checked = state.goals.useCustomMacros,
                    onToggle = viewModel::setUseCustomMacros,
                )
                if (state.goals.useCustomMacros) {
                    Spacer(Modifier.height(10.dp))
                    // GRAMS/PERCENT edit mode (persisted as grams; percent is a display convenience).
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.goals.macroMode == MacroMode.GRAMS,
                            onClick = { viewModel.setMacroMode(MacroMode.GRAMS) },
                            label = { Text("grams") },
                        )
                        FilterChip(
                            selected = state.goals.macroMode == MacroMode.PERCENT,
                            onClick = { viewModel.setMacroMode(MacroMode.PERCENT) },
                            label = { Text("% of calories") },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    NumberField("Protein (g)", state.goals.proteinG, viewModel::setProtein)
                    NumberField("Carbs (g)", state.goals.carbsG, viewModel::setCarbs)
                    NumberField("Fat (g)", state.goals.fatG, viewModel::setFat)
                    NumberField("Fiber (g)", state.goals.fiberG, viewModel::setFiber)
                    Spacer(Modifier.height(8.dp))
                    Text("Set protein by body weight:", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProteinPreset("0.8 g/kg", "RDA", 0.8, viewModel::applyProteinPerKg)
                        ProteinPreset("1.4 g/kg", "Fat loss", 1.4, viewModel::applyProteinPerKg)
                        ProteinPreset("2.0 g/kg", "Muscle", 2.0, viewModel::applyProteinPerKg)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Guideline: protein 10–35%, carbs 45–65%, fat 20–35% of calories (AMDR); fiber ≈14 g per 1000 kcal.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    )
                }
            }

            // ---- Micronutrients to track ----
            SettingsCard("Micronutrients", "Choose which to show on the Nutrition screen") {
                Micro.entries.forEach { micro ->
                    MicroToggle(
                        micro = micro,
                        tracked = micro in state.goals.trackedMicros,
                        onToggle = { on -> viewModel.toggleMicro(micro, on) },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Targets use FDA Daily Values — a reference, not medical advice.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                )
            }

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            state.successMessage?.let { Text(it, color = primary, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, hint: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun NumberField(label: String, value: Int?, onChange: (Int?) -> Unit) {
    OutlinedTextField(
        value = value?.toString() ?: "",
        onValueChange = { s -> onChange(s.filter { it.isDigit() }.toIntOrNull()) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
private fun ProteinPreset(amount: String, caption: String, gPerKg: Double, onApply: (Double) -> Unit) {
    FilterChip(
        selected = false,
        onClick = { onApply(gPerKg) },
        label = { Column { Text(amount, fontWeight = FontWeight.SemiBold); Text(caption, style = MaterialTheme.typography.labelSmall) } },
    )
}

@Composable
private fun MicroToggle(micro: Micro, tracked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(micro.label, style = MaterialTheme.typography.bodyMedium)
            MICRO_WHY[micro]?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
        Checkbox(checked = tracked, onCheckedChange = onToggle)
    }
}
