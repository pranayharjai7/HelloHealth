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
import androidx.compose.ui.unit.dp
import com.hellohealth.domain.model.Gender
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
