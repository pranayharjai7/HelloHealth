package com.hellohealth.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hellohealth.domain.health.VitalsBounds
import com.hellohealth.domain.model.Gender
import com.hellohealth.domain.model.UnitPreference
import com.hellohealth.ui.auth.AuthViewModel
import com.hellohealth.ui.common.BirthDatePickerDialog
import com.hellohealth.ui.common.ImperialHeightInput
import com.hellohealth.ui.common.MetricNumberField
import com.hellohealth.ui.common.SingleSelectChips
import com.hellohealth.ui.common.displayLabel
import com.hellohealth.ui.common.kgToLb
import com.hellohealth.ui.common.lbToKg
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Full-screen editor for the Basics + Body vitals collected during onboarding (name, gender, birth
 * date, height, weight, units) — the "editable later" requirement (P0.5 Step 10b). Draft state is
 * held locally and seeded once from the profile loaded into [ProfileEditorState.profile]; Save calls
 * [AuthViewModel.saveVitals], which load-then-`copy()`s over the stored row (preserving
 * `hasOnboarded` and untouched fields) and re-derives only the active-calorie goal.
 *
 * Reuses the shared vitals inputs (`ui/common/VitalsInputs.kt`) and the same
 * validation bounds as onboarding so a value accepted here is one onboarding would accept.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit
) {
    val editorState by viewModel.profileEditorState.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background

    // Load the stored profile once when the editor opens.
    LaunchedEffect(Unit) { viewModel.loadProfileForEditing() }

    // Close on a successful save.
    LaunchedEffect(editorState.successMessage) {
        if (editorState.successMessage != null) {
            viewModel.clearProfileEditorMessage()
            onBack()
        }
    }

    // Draft state, seeded once from the loaded profile. `seeded` flips true after the async load
    // SUCCEEDS (editorState.loaded) so the fields populate exactly once without clobbering
    // in-progress edits on later recompositions. A brand-new user's profile is legitimately null,
    // so we key on `loaded`, not on the profile being non-null.
    val loaded = editorState.profile
    var seeded by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf<Gender?>(null) }
    var birthDateEpochDay by remember { mutableStateOf<Long?>(null) }
    var heightCm by remember { mutableStateOf<Double?>(null) }
    var weightKg by remember { mutableStateOf<Double?>(null) }
    var unitPreference by remember { mutableStateOf(UnitPreference.METRIC) }

    if (!seeded && editorState.loaded) {
        seeded = true
        name = loaded?.displayName.orEmpty()
        gender = loaded?.gender
        birthDateEpochDay = loaded?.birthDateEpochDay
        heightCm = loaded?.heightCm
        weightKg = loaded?.weightKg
        unitPreference = loaded?.unitPreference ?: UnitPreference.METRIC
    }

    var showDatePicker by remember { mutableStateOf(false) }

    val isImperial = unitPreference == UnitPreference.IMPERIAL

    // Field-level validation mirrors onboarding's bounds. Blank is allowed (a partial profile is
    // valid); only out-of-range entries are rejected and block Save.
    val heightError = heightCm?.let {
        if (it in VitalsBounds.MIN_HEIGHT_CM..VitalsBounds.MAX_HEIGHT_CM) null else "Enter a realistic height"
    }
    val weightError = weightKg?.let {
        if (it in VitalsBounds.MIN_WEIGHT_KG..VitalsBounds.MAX_WEIGHT_KG) null else "Enter a realistic weight"
    }
    val age = birthDateEpochDay?.let {
        val birth = LocalDate.ofEpochDay(it)
        val today = LocalDate.now()
        if (birth.isAfter(today)) null else java.time.Period.between(birth, today).years
    }
    val ageError = age?.let {
        if (it in VitalsBounds.MIN_AGE..VitalsBounds.MAX_AGE) null else "Age must be between ${VitalsBounds.MIN_AGE} and ${VitalsBounds.MAX_AGE}"
    }
    // Save only once the editor has SEEDED from a successful load — otherwise an empty draft (from
    // a failed load) could clobber a live row when saveVitals re-reads and succeeds.
    val canSave = editorState.loaded && seeded &&
        heightError == null && weightError == null && ageError == null &&
        !editorState.isSaving && !editorState.isLoading

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Edit Profile", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            if (editorState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(24.dp),
                    color = primaryColor
                )
                return@Box
            }

            // Load failed: show the error + retry rather than an empty form that could overwrite a
            // live row on save (canSave already blocks saving while unseeded).
            if (!editorState.loaded) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = editorState.error ?: "Couldn't load your profile.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Button(
                        onClick = { viewModel.loadProfileForEditing() },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                    ) {
                        Text("Retry", fontWeight = FontWeight.Bold)
                    }
                }
                return@Box
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                SectionLabel("Name")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
                    )
                )

                SectionLabel("Gender")
                SingleSelectChips(
                    options = Gender.entries,
                    selected = gender,
                    labelOf = { it.displayLabel() },
                    onSelect = { gender = it }
                )

                SectionLabel("Units")
                SingleSelectChips(
                    options = UnitPreference.entries,
                    selected = unitPreference,
                    labelOf = { it.displayLabel() },
                    onSelect = { unitPreference = it }
                )

                SectionLabel("Birth date")
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        birthDateEpochDay
                            ?.let { LocalDate.ofEpochDay(it).format(DateTimeFormatter.ofPattern("MMM d, yyyy")) }
                            ?: "Select birth date"
                    )
                }
                if (ageError != null) {
                    Text(
                        text = ageError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                SectionLabel("Height")
                if (isImperial) {
                    ImperialHeightInput(
                        heightCm = heightCm,
                        primaryColor = primaryColor,
                        onHeightCmChange = { heightCm = it }
                    )
                    heightError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    MetricNumberField(
                        valueMetric = heightCm,
                        unitSuffix = "cm",
                        primaryColor = primaryColor,
                        toDisplay = { it },
                        fromDisplay = { it },
                        onMetricChange = { heightCm = it },
                        errorText = heightError
                    )
                }

                SectionLabel("Weight")
                // key(isImperial): the weight field is the same composable in both units, so its
                // remembered display text would survive a unit switch and show the kg number under
                // an "lb" label (then save it as lb → corrupt). Re-keying forces a fresh re-seed
                // from the unchanged metric value through the new toDisplay when the unit flips.
                key(isImperial) {
                    MetricNumberField(
                        valueMetric = weightKg,
                        unitSuffix = if (isImperial) "lb" else "kg",
                        primaryColor = primaryColor,
                        toDisplay = { if (isImperial) it.kgToLb() else it },
                        fromDisplay = { if (isImperial) it.lbToKg() else it },
                        onMetricChange = { weightKg = it },
                        errorText = weightError
                    )
                }

                if (editorState.error != null) {
                    Text(
                        text = editorState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        viewModel.saveVitals(
                            displayName = name,
                            gender = gender,
                            birthDateEpochDay = birthDateEpochDay,
                            heightCm = heightCm,
                            weightKg = weightKg,
                            unitPreference = unitPreference
                        )
                    },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    if (editorState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        BirthDatePickerDialog(
            initialEpochDay = birthDateEpochDay,
            onConfirm = {
                birthDateEpochDay = it
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )
}
