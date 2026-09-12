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
                    else -> PlaceholderStep(step = step)
                }
            }

            StepControls(
                uiState = uiState,
                primaryColor = primaryColor,
                onBack = viewModel::previousStep,
                onNext = {
                    if (uiState.step == OnboardingStep.entries.last()) {
                        onFinished()
                    } else {
                        viewModel.nextStep()
                    }
                }
            )
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

/**
 * A single numeric field that displays in some unit but reports a metric value. [toDisplay] maps
 * the stored metric value into the display unit for seeding; [fromDisplay] maps a parsed display
 * value back to metric. The raw text is held locally so in-progress edits survive, and re-seeded
 * whenever the incoming metric value changes from a source other than this field (e.g. prefill).
 */
@Composable
private fun MetricNumberField(
    valueMetric: Double?,
    unitSuffix: String,
    primaryColor: Color,
    toDisplay: (Double) -> Double,
    fromDisplay: (Double) -> Double,
    onMetricChange: (Double?) -> Unit,
    errorText: String? = null
) {
    // The field owns its raw text so in-progress edits ("70.", "5") survive. We re-seed ONLY when
    // the stored metric changes from an external source (prefill, unit switch) — detected by the
    // incoming value differing from what this field last emitted — so typing a decimal point isn't
    // stripped by a value round-trip on every keystroke.
    var text by remember { mutableStateOf(valueMetric?.let { formatNumber(toDisplay(it)) } ?: "") }
    var lastEmitted by remember { mutableStateOf(valueMetric) }
    if (valueMetric != lastEmitted) {
        lastEmitted = valueMetric
        text = valueMetric?.let { formatNumber(toDisplay(it)) } ?: ""
    }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw
            val parsed = raw.replace(',', '.').toDoubleOrNull()
            val metric = parsed?.let { fromDisplay(it) }
            lastEmitted = metric
            onMetricChange(metric)
        },
        label = { Text(unitSuffix) },
        singleLine = true,
        isError = errorText != null,
        supportingText = errorText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = primaryColor,
            unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
        )
    )
}

/**
 * Feet + inches → centimeters. Two side-by-side fields; either edit recomputes cm from both. Text is
 * re-seeded from the stored [heightCm] only when it changes externally (prefill) — detected by the
 * incoming value differing from what this field last emitted — so partial edits aren't stripped.
 */
@Composable
private fun ImperialHeightInput(
    heightCm: Double?,
    primaryColor: Color,
    onHeightCmChange: (Double?) -> Unit
) {
    var feet by remember { mutableStateOf(heightCm?.let { cmToFeetInches(it).first.toString() } ?: "") }
    var inches by remember { mutableStateOf(heightCm?.let { formatNumber(cmToFeetInches(it).second) } ?: "") }
    var lastEmitted by remember { mutableStateOf(heightCm) }
    if (heightCm != lastEmitted) {
        lastEmitted = heightCm
        val (f, i) = heightCm?.let { cmToFeetInches(it) } ?: (null to null)
        feet = f?.toString() ?: ""
        inches = i?.let { formatNumber(it) } ?: ""
    }

    fun push() {
        val f = feet.toIntOrNull()
        val i = inches.replace(',', '.').toDoubleOrNull()
        // Reject nonsense components (negative, or inches outside a single foot) rather than letting
        // them combine into an in-range cm that would pass the Body gate. Both blank → null (nothing
        // entered yet); any invalid component present → null (blocks Next until corrected).
        val cm = when {
            f == null && i == null -> null
            (f != null && f < 0) || (i != null && (i < 0 || i >= INCHES_PER_FOOT)) -> null
            else -> feetInchesToCm(f ?: 0, i ?: 0.0)
        }
        lastEmitted = cm
        onHeightCmChange(cm)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = feet,
            onValueChange = { feet = it; push() },
            label = { Text("ft") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = primaryColor,
                unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
            )
        )
        OutlinedTextField(
            value = inches,
            onValueChange = { inches = it; push() },
            label = { Text("in") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = primaryColor,
                unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthDatePickerDialog(
    initialEpochDay: Long?,
    onConfirm: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val today = LocalDate.now()
    val initialMillis = (initialEpochDay?.let { LocalDate.ofEpochDay(it) } ?: today.minusYears(25))
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        // A birth date can't be in the future; DatePicker disables later days.
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
            override fun isSelectableYear(year: Int) = year <= today.year
        }
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val epochDay = state.selectedDateMillis?.let {
                    Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                }
                onConfirm(epochDay)
            }) { Text("OK", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DatePicker(state = state)
    }
}

/** Single-select row of [FilterChip]s in a [FlowRow] — the app's established selection idiom. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> SingleSelectChips(
    options: List<T>,
    selected: T?,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(labelOf(option)) },
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

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

@Composable
private fun PlaceholderStep(step: OnboardingStep) {
    StepScaffold(
        title = when (step) {
            OnboardingStep.BODY -> "Your body"
            OnboardingStep.ACTIVITY -> "Activity & goals"
            OnboardingStep.CONFIRM -> "All set"
            OnboardingStep.BASICS -> "The basics"
        },
        subtitle = "Coming soon."
    ) {
        Text(
            text = "This step is under construction.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun StepControls(
    uiState: OnboardingUiState,
    primaryColor: Color,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val isFirst = uiState.step == OnboardingStep.entries.first()
    val isLast = uiState.step == OnboardingStep.entries.last()
    // Only Basics gates Next today; later steps gate in their own step (7-9).
    val nextEnabled = when (uiState.step) {
        OnboardingStep.BASICS -> uiState.isBasicsValid
        OnboardingStep.BODY -> uiState.isBodyValid
        OnboardingStep.ACTIVITY -> uiState.isActivityValid
        else -> true
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!isFirst) {
            OutlinedButton(
                onClick = onBack,
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
            Text(if (isLast) "Finish" else "Next", fontWeight = FontWeight.Bold)
        }
    }
}

private fun Gender.displayLabel() = when (this) {
    Gender.FEMALE -> "Female"
    Gender.MALE -> "Male"
    Gender.OTHER -> "Other"
    Gender.PREFER_NOT_TO_SAY -> "Prefer not to say"
}

private fun UnitPreference.displayLabel() = when (this) {
    UnitPreference.METRIC -> "Metric (kg, cm)"
    UnitPreference.IMPERIAL -> "Imperial (lb, ft)"
}

private fun ActivityLevel.displayLabel() = when (this) {
    ActivityLevel.SEDENTARY -> "Sedentary"
    ActivityLevel.LIGHT -> "Lightly active"
    ActivityLevel.MODERATE -> "Moderately active"
    ActivityLevel.ACTIVE -> "Active"
    ActivityLevel.VERY_ACTIVE -> "Very active"
}

private fun GoalType.displayLabel() = when (this) {
    GoalType.LOSE -> "Lose weight"
    GoalType.MAINTAIN -> "Maintain"
    GoalType.GAIN -> "Gain weight"
}

// --- Unit conversions (storage is always metric) ---
// `internal` so the pure conversion math is unit-testable (see OnboardingConversionsTest) without
// widening it to the public API.

private const val CM_PER_INCH = 2.54
private const val INCHES_PER_FOOT = 12
private const val LB_PER_KG = 2.2046226218

internal fun feetInchesToCm(feet: Int, inches: Double): Double =
    (feet * INCHES_PER_FOOT + inches) * CM_PER_INCH

/**
 * Decomposes a cm height into a consistent (feet, inches) pair for display. Rounds the total inches
 * to one decimal FIRST, then derives feet — so the inches component is always in [0, 12) and never
 * shows a nonsensical "12 in" when rounding pushes the remainder to a whole foot. e.g. 182.755 cm →
 * (6, 0.0), not (5, 12.0).
 */
internal fun cmToFeetInches(cm: Double): Pair<Int, Double> {
    val totalInches = kotlin.math.round(cm / CM_PER_INCH * 10) / 10
    var feet = (totalInches / INCHES_PER_FOOT).toInt()
    var inches = totalInches - feet * INCHES_PER_FOOT
    // Guard the boundary: rounding can leave inches == 12.0 (or a hair over) — carry into feet.
    if (inches >= INCHES_PER_FOOT) {
        feet += 1
        inches -= INCHES_PER_FOOT
    }
    return feet to inches
}

internal fun Double.kgToLb(): Double = this * LB_PER_KG
internal fun Double.lbToKg(): Double = this / LB_PER_KG

/** Trims a trailing ".0" so whole numbers show cleanly, else one decimal place. */
private fun formatNumber(value: Double): String {
    val rounded = kotlin.math.round(value * 10) / 10
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}
