package com.hellohealth.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hellohealth.domain.model.GoalType
import com.hellohealth.domain.model.UnitPreference
import com.hellohealth.ui.common.MetricNumberField
import com.hellohealth.ui.common.SingleSelectChips
import com.hellohealth.ui.common.displayLabel
import com.hellohealth.ui.common.kgToLb
import com.hellohealth.ui.common.lbToKg

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen(
    viewModel: PreferencesViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Preferences", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        // Disabled until the profile snapshot has loaded and validation passes —
                        // saving before the weight-goal fields seed would wipe the stored goal.
                        TextButton(onClick = { viewModel.savePreferences() }, enabled = uiState.canSave) {
                            Text(
                                "Save",
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.canSave) primaryColor else primaryColor.copy(alpha = 0.4f)
                            )
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
                    // imePadding() BEFORE verticalScroll() shrinks the scroll viewport when the
                    // keyboard shows (this screen now has text fields); inside a Scaffold, so the
                    // nav-bar inset is already supplied by the Scaffold padding — no navigationBarsPadding.
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // --- Weight preferences card ---
                WeightPreferencesCard(
                    goalType = uiState.goalType,
                    targetWeightKg = uiState.targetWeightKg,
                    targetRateKgPerWeek = uiState.targetRateKgPerWeek,
                    unitPreference = uiState.unitPreference,
                    targetWeightError = uiState.targetWeightError,
                    targetRateError = uiState.targetRateError,
                    primaryColor = primaryColor,
                    onGoalTypeChange = { viewModel.updateGoalType(it) },
                    onTargetWeightChange = { viewModel.updateTargetWeightKg(it) },
                    onTargetRateChange = { viewModel.updateTargetRateKgPerWeek(it) }
                )

                // --- Food preferences card ---
                FoodPreferencesCard(
                    dietType = uiState.preferences.dietType,
                    allergies = uiState.preferences.allergies,
                    cuisines = uiState.preferences.cuisinePreferences,
                    primaryColor = primaryColor,
                    onDietTypeChange = { viewModel.updateDietType(it) },
                    onToggleAllergy = { viewModel.toggleAllergy(it) },
                    onToggleCuisine = { viewModel.toggleCuisine(it) }
                )

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        text = "We use these preferences to tailor your goals and nutrition guidance inside Insights and to prepare the app for meal and recovery recommendations.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        modifier = Modifier.padding(20.dp)
                    )
                }

                if (uiState.error != null) {
                    Text(uiState.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                if (uiState.successMessage != null) {
                    Text(uiState.successMessage!!, color = primaryColor, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/**
 * The weight-goal fields (goal type + target weight + weekly rate) — moved here from the Daily Goals
 * screen. `unitPreference` governs the target-weight field's display units; rate is always kg/week.
 */
@Composable
private fun WeightPreferencesCard(
    goalType: GoalType?,
    targetWeightKg: Double?,
    targetRateKgPerWeek: Double?,
    unitPreference: UnitPreference,
    targetWeightError: String?,
    targetRateError: String?,
    primaryColor: Color,
    onGoalTypeChange: (GoalType) -> Unit,
    onTargetWeightChange: (Double?) -> Unit,
    onTargetRateChange: (Double?) -> Unit
) {
    val isImperial = unitPreference == UnitPreference.IMPERIAL
    // Targets only make sense for a directional goal; MAINTAIN hides them (the VM also clears the
    // stored values on MAINTAIN so a hidden field can't smuggle a stale/out-of-range value to save).
    val showTargets = goalType == GoalType.LOSE || goalType == GoalType.GAIN

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Flag, null, tint = primaryColor, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text("Weight preferences", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            SingleSelectChips(
                options = GoalType.entries,
                selected = goalType,
                labelOf = { it.displayLabel() },
                onSelect = onGoalTypeChange
            )

            if (showTargets) {
                Text(
                    text = "Target weight",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                // key(isImperial): re-seed the field's display text through the right toDisplay when
                // the stored unit preference loads/flips, so a kg value never lingers under an "lb"
                // label (the unit-toggle corruption fixed in the profile editor).
                key(isImperial) {
                    MetricNumberField(
                        valueMetric = targetWeightKg,
                        unitSuffix = if (isImperial) "lb" else "kg",
                        primaryColor = primaryColor,
                        toDisplay = { if (isImperial) it.kgToLb() else it },
                        fromDisplay = { if (isImperial) it.lbToKg() else it },
                        onMetricChange = onTargetWeightChange,
                        errorText = targetWeightError
                    )
                }

                Text(
                    text = "Weekly rate",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                // Rate is always stored and shown in kg/week (metric-only, like onboarding).
                MetricNumberField(
                    valueMetric = targetRateKgPerWeek,
                    unitSuffix = "kg / week",
                    primaryColor = primaryColor,
                    toDisplay = { it },
                    fromDisplay = { it },
                    onMetricChange = onTargetRateChange,
                    errorText = targetRateError
                )
            }
        }
    }
}

/** Diet type / allergies / cuisines, wrapped in a single card below the weight preferences. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FoodPreferencesCard(
    dietType: String,
    allergies: List<String>,
    cuisines: List<String>,
    primaryColor: Color,
    onDietTypeChange: (String) -> Unit,
    onToggleAllergy: (String) -> Unit,
    onToggleCuisine: (String) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Restaurant, null, tint = primaryColor, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text("Food preferences", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            PreferenceSection(title = "Diet Type") {
                val types = listOf("non-vegetarian", "vegetarian", "vegan", "pescatarian")
                PreferenceFlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    types.forEach { type ->
                        FilterChip(
                            selected = dietType == type,
                            onClick = { onDietTypeChange(type) },
                            label = { Text(type.replaceFirstChar { it.uppercase() }) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            PreferenceSection(title = "Food Allergies") {
                val allergyOptions = listOf("dairy", "gluten", "nuts", "soy")
                PreferenceFlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    allergyOptions.forEach { allergy ->
                        FilterChip(
                            selected = allergies.contains(allergy),
                            onClick = { onToggleAllergy(allergy) },
                            label = { Text(allergy.replaceFirstChar { it.uppercase() }) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            PreferenceSection(title = "Cuisine Preferences") {
                val cuisineOptions = listOf("Indian", "Western", "Asian", "Mixed")
                PreferenceFlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    cuisineOptions.forEach { cuisine ->
                        FilterChip(
                            selected = cuisines.contains(cuisine),
                            onClick = { onToggleCuisine(cuisine) },
                            label = { Text(cuisine) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferenceSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreferenceFlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable FlowRowScope.() -> Unit
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        content = content
    )
}
