package com.hellohealth.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hellohealth.domain.model.ActivityLevel
import com.hellohealth.domain.model.Gender
import com.hellohealth.domain.model.GoalType
import com.hellohealth.domain.model.UnitPreference
import com.hellohealth.ui.common.BirthDatePickerDialog
import com.hellohealth.ui.common.ImperialHeightInput
import com.hellohealth.ui.common.MetricNumberField
import com.hellohealth.ui.common.SingleSelectChips
import com.hellohealth.ui.common.cmToFeetInches
import com.hellohealth.ui.common.displayLabel
import com.hellohealth.ui.common.formatNumber
import com.hellohealth.ui.common.kgToLb
import com.hellohealth.ui.common.lbToKg
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * First-run onboarding wizard. Step 6 delivers the scaffold (progress header + animated step
 * switcher) and the real **Basics** step (name, gender, birth date, units). The Body / Activity /
 * Confirm steps are placeholders until Steps 7-9; `onFinished` is retained so the current
 * gate-and-nav wiring keeps working (Continue on the last step calls it).
 *
 * All color comes from [ColorScheme] roles — the gradient sources [ColorScheme.primary] (never a
 * raw hex) so the deferred P1 mood-tint drops in with no rework.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.1f), backgroundColor)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            OnboardingProgressHeader(step = uiState.step, primaryColor = primaryColor)

            AnimatedContent(
                targetState = uiState.step,
                transitionSpec = {
                    if (targetState.ordinal > initialState.ordinal) {
                        (fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it / 3 })
                            .togetherWith(fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { -it / 3 })
                    } else {
                        (fadeIn(tween(300)) + slideInHorizontally(tween(300)) { -it / 3 })
                            .togetherWith(fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { it / 3 })
                    }
                },
                label = "OnboardingStepAnimation"
            ) { step ->
                when (step) {
                    OnboardingStep.BASICS -> BasicsStep(
                        uiState = uiState,
                        primaryColor = primaryColor,
                        onNameChange = viewModel::updateDisplayName,
                        onGenderChange = viewModel::updateGender,
                        onBirthDateChange = viewModel::updateBirthDate,
                        onUnitChange = viewModel::updateUnitPreference
                    )
                    OnboardingStep.BODY -> BodyStep(
                        uiState = uiState,
                        primaryColor = primaryColor,
                        onPrefill = viewModel::prefillBodyFromHealthConnect,
                        onHeightCmChange = viewModel::updateHeightCm,
                        onWeightKgChange = viewModel::updateWeightKg
                    )
                    OnboardingStep.ACTIVITY -> ActivityStep(
                        uiState = uiState,
                        primaryColor = primaryColor,
                        onActivityLevelChange = viewModel::updateActivityLevel,
                        onGoalTypeChange = viewModel::updateGoalType,
                        onTargetWeightKgChange = viewModel::updateTargetWeightKg,
                        onTargetRateChange = viewModel::updateTargetRateKgPerWeek
                    )
                    OnboardingStep.CONFIRM -> ConfirmStep(
                        uiState = uiState,
                        calorieBudget = viewModel.calorieBudgetPreview,
                        suggestedGoals = viewModel.suggestedGoalsPreview,
                        primaryColor = primaryColor
                    )
                }
            }

            StepControls(
                uiState = uiState,
                primaryColor = primaryColor,
                onBack = viewModel::previousStep,
                onSkip = { viewModel.skip(onFinished) },
                onNext = {
                    if (uiState.step == OnboardingStep.entries.last()) {
                        viewModel.finish(onFinished)
                    } else {
                        viewModel.nextStep()
                    }
                }
            )

            uiState.error?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun OnboardingProgressHeader(
    step: OnboardingStep,
    primaryColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Step ${step.ordinal + 1} of ${OnboardingStep.count}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        LinearProgressIndicator(
            progress = { (step.ordinal + 1).toFloat() / OnboardingStep.count },
            modifier = Modifier.fillMaxWidth(),
            color = primaryColor,
            trackColor = primaryColor.copy(alpha = 0.15f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BasicsStep(
    uiState: OnboardingUiState,
    primaryColor: Color,
    onNameChange: (String) -> Unit,
    onGenderChange: (Gender) -> Unit,
    onBirthDateChange: (Long?) -> Unit,
    onUnitChange: (UnitPreference) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }

    StepScaffold(
        title = "The basics",
        subtitle = "Tell us who you are so we can personalize your goals and calories."
    ) {
        OutlinedTextField(
            value = uiState.displayName,
            onValueChange = onNameChange,
            label = { Text("Your name") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = primaryColor,
                unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
            )
        )

        FieldLabel("Gender")
        SingleSelectChips(
            options = Gender.entries,
            selected = uiState.gender,
            labelOf = { it.displayLabel() },
            onSelect = onGenderChange
        )

        FieldLabel("Date of birth")
        val formatted = uiState.birthDateEpochDay?.let {
            LocalDate.ofEpochDay(it).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
        }
        OutlinedButton(
            onClick = { showDatePicker = true },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                text = formatted ?: "Select your date of birth",
                fontWeight = FontWeight.Bold
            )
        }

        FieldLabel("Units")
        SingleSelectChips(
            options = UnitPreference.entries,
            selected = uiState.unitPreference,
            labelOf = { it.displayLabel() },
            onSelect = onUnitChange
        )
    }

    if (showDatePicker) {
        BirthDatePickerDialog(
            initialEpochDay = uiState.birthDateEpochDay,
            onConfirm = {
                onBirthDateChange(it)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

/**
 * Body step: height + weight, unit-aware per the Basics selection. On first entry it kicks off a
 * one-shot Health Connect prefill; the user can override anything. Storage is always metric — the
 * imperial path parses ft/in→cm and lb→kg before pushing values up. Each input keeps its own raw
 * text in [remember] (keyed by unit) so partial entries like "5" / "." don't get erased by the
 * metric round-trip, and so switching units re-seeds the fields from the stored metric value.
 */
@Composable
private fun BodyStep(
    uiState: OnboardingUiState,
    primaryColor: Color,
    onPrefill: () -> Unit,
    onHeightCmChange: (Double?) -> Unit,
    onWeightKgChange: (Double?) -> Unit
) {
    // Fire the one-shot prefill exactly once when this step first composes.
    LaunchedEffect(Unit) { onPrefill() }

    val imperial = uiState.unitPreference == UnitPreference.IMPERIAL

    StepScaffold(
        title = "Your body",
        subtitle = "Used to estimate your daily calories. Pull from Health Connect or enter it yourself."
    ) {
        if (uiState.isPrefillingBody) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = primaryColor
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Checking Health Connect…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
        }

        FieldLabel("Height")
        if (imperial) {
            ImperialHeightInput(
                heightCm = uiState.heightCm,
                primaryColor = primaryColor,
                onHeightCmChange = onHeightCmChange
            )
        } else {
            MetricNumberField(
                valueMetric = uiState.heightCm,
                unitSuffix = "cm",
                primaryColor = primaryColor,
                toDisplay = { it },
                fromDisplay = { it },
                onMetricChange = onHeightCmChange
            )
        }

        FieldLabel("Weight")
        if (imperial) {
            MetricNumberField(
                valueMetric = uiState.weightKg,
                unitSuffix = "lb",
                primaryColor = primaryColor,
                toDisplay = { it.kgToLb() },
                fromDisplay = { it.lbToKg() },
                onMetricChange = onWeightKgChange
            )
        } else {
            MetricNumberField(
                valueMetric = uiState.weightKg,
                unitSuffix = "kg",
                primaryColor = primaryColor,
                toDisplay = { it },
                fromDisplay = { it },
                onMetricChange = onWeightKgChange
            )
        }
    }
}

/**
 * Activity & goal step: activity level, goal direction, and — only for a directional goal — an
 * optional target weight and weekly rate. Weight/rate inputs are unit-aware (kg or lb; the rate is
 * kg/week or lb/week) and stored metric. Targets are optional, so leaving them blank is valid; the
 * ViewModel bounds any value the user does enter.
 */
@Composable
private fun ActivityStep(
    uiState: OnboardingUiState,
    primaryColor: Color,
    onActivityLevelChange: (ActivityLevel) -> Unit,
    onGoalTypeChange: (GoalType) -> Unit,
    onTargetWeightKgChange: (Double?) -> Unit,
    onTargetRateChange: (Double?) -> Unit
) {
    val imperial = uiState.unitPreference == UnitPreference.IMPERIAL

    StepScaffold(
        title = "Activity & goals",
        subtitle = "How active are you, and what are you aiming for? This shapes your daily calorie target."
    ) {
        FieldLabel("Activity level")
        SingleSelectChips(
            options = ActivityLevel.entries,
            selected = uiState.activityLevel,
            labelOf = { it.displayLabel() },
            onSelect = onActivityLevelChange
        )

        FieldLabel("Goal")
        SingleSelectChips(
            options = GoalType.entries,
            selected = uiState.goalType,
            labelOf = { it.displayLabel() },
            onSelect = onGoalTypeChange
        )

        // Target weight/rate only make sense for a directional goal.
        if (uiState.goalType == GoalType.LOSE || uiState.goalType == GoalType.GAIN) {
            // Out-of-range errors are surfaced inline, in the unit the user is typing, so a disabled
            // Next button always has a visible reason (the gate itself checks the stored metric value).
            val target = uiState.targetWeightKg
            val targetError = if (target != null &&
                target !in OnboardingViewModel.MIN_WEIGHT_KG..OnboardingViewModel.MAX_WEIGHT_KG
            ) {
                if (imperial) {
                    "Enter a weight between ${formatNumber(OnboardingViewModel.MIN_WEIGHT_KG.kgToLb())}" +
                        " and ${formatNumber(OnboardingViewModel.MAX_WEIGHT_KG.kgToLb())} lb."
                } else {
                    "Enter a weight between ${formatNumber(OnboardingViewModel.MIN_WEIGHT_KG)}" +
                        " and ${formatNumber(OnboardingViewModel.MAX_WEIGHT_KG)} kg."
                }
            } else {
                null
            }

            FieldLabel("Target weight (optional)")
            MetricNumberField(
                valueMetric = uiState.targetWeightKg,
                unitSuffix = if (imperial) "lb" else "kg",
                primaryColor = primaryColor,
                toDisplay = { if (imperial) it.kgToLb() else it },
                fromDisplay = { if (imperial) it.lbToKg() else it },
                onMetricChange = onTargetWeightKgChange,
                errorText = targetError
            )

            val rateUnit = if (imperial) "lb / week" else "kg / week"
            val rate = uiState.targetRateKgPerWeek
            val rateError = if (rate != null &&
                rate !in OnboardingViewModel.MIN_RATE_KG_PER_WEEK..OnboardingViewModel.MAX_RATE_KG_PER_WEEK
            ) {
                if (imperial) {
                    "Keep it between ${formatNumber(OnboardingViewModel.MIN_RATE_KG_PER_WEEK.kgToLb())}" +
                        " and ${formatNumber(OnboardingViewModel.MAX_RATE_KG_PER_WEEK.kgToLb())} lb/week."
                } else {
                    "Keep it between ${formatNumber(OnboardingViewModel.MIN_RATE_KG_PER_WEEK)}" +
                        " and ${formatNumber(OnboardingViewModel.MAX_RATE_KG_PER_WEEK)} kg/week."
                }
            } else {
                null
            }

            FieldLabel("Weekly rate (optional)")
            MetricNumberField(
                valueMetric = uiState.targetRateKgPerWeek,
                unitSuffix = rateUnit,
                primaryColor = primaryColor,
                toDisplay = { if (imperial) it.kgToLb() else it },
                fromDisplay = { if (imperial) it.lbToKg() else it },
                onMetricChange = onTargetRateChange,
                errorText = rateError
            )
            Text(
                text = "A steady 0.25–1 kg (about 0.5–2 lb) per week is a healthy pace. Leave blank for our default.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

/** A bold section label above a group of inputs, matching the onboarding form style. */
@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )
}

/** A titled card wrapper matching the app's ElevatedCard section style. */
@Composable
private fun StepScaffold(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(8.dp))
        ElevatedCard(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content
            )
        }
    }
}

/**
 * Confirm step: a read-only summary of what onboarding collected, the derived daily calorie budget
 * (or a defaults note when vitals were skipped), and the suggested activity-ring goals that finish
 * will seed. Values shown in the user's chosen units; everything is editable later from Profile/Goals.
 */
@Composable
private fun ConfirmStep(
    uiState: OnboardingUiState,
    calorieBudget: Int?,
    suggestedGoals: com.hellohealth.domain.model.ActivityGoals,
    primaryColor: Color
) {
    val imperial = uiState.unitPreference == UnitPreference.IMPERIAL

    val heightSummary = uiState.heightCm?.let { cm ->
        if (imperial) {
            val (f, i) = cmToFeetInches(cm)
            "$f ft ${formatNumber(i)} in"
        } else {
            "${formatNumber(cm)} cm"
        }
    }
    val weightSummary = uiState.weightKg?.let { kg ->
        if (imperial) "${formatNumber(kg.kgToLb())} lb" else "${formatNumber(kg)} kg"
    }

    StepScaffold(
        title = "You're all set",
        subtitle = "Here's what we'll use to personalize your day. You can change any of it later in Profile and Goals."
    ) {
        // Headline: the derived daily calorie budget.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Daily calorie target",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (calorieBudget != null) {
                Text(
                    text = "$calorieBudget kcal",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = primaryColor
                )
            } else {
                Text(
                    text = "We'll estimate this from sensible defaults until you add your height, weight, and birth date.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))

        // Your details.
        SummaryRow("Name", uiState.displayName.trim().ifBlank { "—" })
        SummaryRow("Gender", uiState.gender?.displayLabel() ?: "—")
        SummaryRow("Height", heightSummary ?: "—")
        SummaryRow("Weight", weightSummary ?: "—")
        SummaryRow("Activity", uiState.activityLevel?.displayLabel() ?: "—")
        SummaryRow("Goal", uiState.goalType?.displayLabel() ?: "—")

        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))

        // Suggested daily goals we'll seed.
        Text(
            text = "Suggested daily goals",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        SummaryRow("Steps", "%,d".format(suggestedGoals.steps))
        SummaryRow("Active calories", "${suggestedGoals.activeCalories} kcal")
        SummaryRow("Active minutes", "${suggestedGoals.activeMinutes} min")
    }
}

/** A label/value line for the Confirm summary. */
@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StepControls(
    uiState: OnboardingUiState,
    primaryColor: Color,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit
) {
    val isFirst = uiState.step == OnboardingStep.entries.first()
    val isLast = uiState.step == OnboardingStep.entries.last()
    val isSaving = uiState.isSaving
    // Only Basics gates Next today; later steps gate in their own step (7-9).
    val nextEnabled = !isSaving && when (uiState.step) {
        OnboardingStep.BASICS -> uiState.isBasicsValid
        OnboardingStep.BODY -> uiState.isBodyValid
        OnboardingStep.ACTIVITY -> uiState.isActivityValid
        else -> true
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!isFirst) {
                OutlinedButton(
                    onClick = onBack,
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Back", fontWeight = FontWeight.Bold)
                }
            }
            Button(
                onClick = onNext,
                enabled = nextEnabled,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
            ) {
                if (isSaving && isLast) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (isLast) "Finish" else "Next", fontWeight = FontWeight.Bold)
                }
            }
        }
        // Skip is available from any step (persists an explicit row with whatever's filled so far).
        // Hidden on the last step, where Finish already commits the same way.
        if (!isLast) {
            TextButton(
                onClick = onSkip,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isSaving) "Saving…" else "Skip for now",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }
    }
}
