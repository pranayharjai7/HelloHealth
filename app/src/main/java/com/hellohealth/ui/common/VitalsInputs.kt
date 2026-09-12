package com.hellohealth.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

/**
 * Shared vitals-entry composables and unit math, used by the onboarding wizard and the later
 * Profile/Goals editors so both present and validate the same way. Everything here is `internal`
 * (module-scoped) — the pure conversions are unit-tested (see `OnboardingConversionsTest`) without
 * widening the public API. Storage is always metric; imperial inputs convert at the edge.
 */

/** Single-select row of [FilterChip]s in a [FlowRow] — the app's established selection idiom. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> SingleSelectChips(
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

/**
 * A single numeric field that displays in some unit but reports a metric value. [toDisplay] maps
 * the stored metric value into the display unit for seeding; [fromDisplay] maps a parsed display
 * value back to metric. The raw text is held locally so in-progress edits survive, and re-seeded
 * whenever the incoming metric value changes from a source other than this field (e.g. prefill).
 */
@Composable
internal fun MetricNumberField(
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
internal fun ImperialHeightInput(
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
internal fun BirthDatePickerDialog(
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

// --- Display labels for the vitals enums ---

internal fun Gender.displayLabel() = when (this) {
    Gender.FEMALE -> "Female"
    Gender.MALE -> "Male"
    Gender.OTHER -> "Other"
    Gender.PREFER_NOT_TO_SAY -> "Prefer not to say"
}

internal fun UnitPreference.displayLabel() = when (this) {
    UnitPreference.METRIC -> "Metric (kg, cm)"
    UnitPreference.IMPERIAL -> "Imperial (lb, ft)"
}

internal fun ActivityLevel.displayLabel() = when (this) {
    ActivityLevel.SEDENTARY -> "Sedentary"
    ActivityLevel.LIGHT -> "Lightly active"
    ActivityLevel.MODERATE -> "Moderately active"
    ActivityLevel.ACTIVE -> "Active"
    ActivityLevel.VERY_ACTIVE -> "Very active"
}

internal fun GoalType.displayLabel() = when (this) {
    GoalType.LOSE -> "Lose weight"
    GoalType.MAINTAIN -> "Maintain"
    GoalType.GAIN -> "Gain weight"
}

// --- Unit conversions (storage is always metric) ---

private const val CM_PER_INCH = 2.54
internal const val INCHES_PER_FOOT = 12
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
internal fun formatNumber(value: Double): String {
    val rounded = kotlin.math.round(value * 10) / 10
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}
