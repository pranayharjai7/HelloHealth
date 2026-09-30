package com.hellohealth.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.domain.model.vitals.LatestVitals
import com.hellohealth.domain.model.vitals.ReadinessScore
import com.hellohealth.domain.model.vitals.ReadinessStatus
import com.hellohealth.domain.repository.VitalsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * State for the dashboard's Vitals & Recovery card. All display values are pre-formatted strings so
 * the card stays dumb: a missing sensor / no-data day surfaces as [DASH] rather than a crash or an
 * awkward "0". [readinessScore] drives the ring (0-100); [isEstablishingBaseline] swaps the ring's
 * number for the "n/7 days" establishing label.
 *
 * [hasReadiness] is false when there is no signed-in user (readiness flow emits null) — the card then
 * shows nothing recovery-related and leans on the connect prompt the screen supplies.
 */
data class VitalsUiState(
    val hasReadiness: Boolean = false,
    val readinessScore: Int = 0,
    val status: ReadinessStatus = ReadinessStatus.INSUFFICIENT_DATA,
    val isEstablishingBaseline: Boolean = false,
    val establishingDayCount: Int = 0,
    val restingHeartRate: String = DASH,
    val hrvRmssd: String = DASH,
    val respiratoryRate: String = DASH,
    val bodyTemperature: String = DASH,
    val hydrationMl: String = DASH,
    val spo2: String = DASH,
) {
    companion object {
        const val DASH = "—"
    }
}

/**
 * Own VM for the dashboard's Vitals & Recovery card — deliberately NOT folded into [DashboardViewModel]
 * (mirrors [WorkoutPlanViewModel]) so the vitals read surface stays in one place. Combines the derived
 * readiness score with the latest raw vitals, both sourced ONLY from the persisted daily rollup (never
 * live Health Connect). With no signed-in user both flows emit null and the state collapses to its
 * empty defaults without special-casing.
 */
@HiltViewModel
class VitalsViewModel @Inject constructor(
    repository: VitalsRepository,
) : ViewModel() {

    val uiState: StateFlow<VitalsUiState> =
        combine(
            repository.observeReadiness(),
            repository.observeLatestVitals(),
        ) { readiness, latest ->
            toUiState(readiness, latest)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = VitalsUiState(),
        )

    private fun toUiState(readiness: ReadinessScore?, latest: LatestVitals?): VitalsUiState =
        VitalsUiState(
            hasReadiness = readiness != null,
            readinessScore = readiness?.score ?: 0,
            status = readiness?.status ?: ReadinessStatus.INSUFFICIENT_DATA,
            isEstablishingBaseline = readiness?.isEstablishingBaseline ?: false,
            establishingDayCount = readiness?.establishingDayCount ?: 0,
            restingHeartRate = latest?.restingHeartRate.asBpm(),
            hrvRmssd = latest?.hrvRmssd.asMs(),
            respiratoryRate = latest?.respiratoryRate.asRate(),
            bodyTemperature = latest?.bodyTemperature.asCelsius(),
            hydrationMl = latest?.hydrationMl.asMl(),
            spo2 = latest?.spo2.asPercent(),
        )

    // Formatters — null (missing sensor / no rollup) always renders the dash, never "0".
    private fun Double?.asBpm() = this?.let { "${it.toInt()} bpm" } ?: VitalsUiState.DASH
    private fun Double?.asMs() = this?.let { "${it.toInt()} ms" } ?: VitalsUiState.DASH
    private fun Double?.asRate() = this?.let { "${it.toInt()} /min" } ?: VitalsUiState.DASH
    // SpO2 is stored 0-100 (NOT a fraction), so it renders directly with a % suffix.
    private fun Double?.asPercent() = this?.let { "${it.toInt()}%" } ?: VitalsUiState.DASH
    private fun Double?.asCelsius() = this?.let { String.format("%.1f°C", it) } ?: VitalsUiState.DASH
    private fun Double?.asMl() = this?.let {
        if (it >= 1000) String.format("%.1f L", it / 1000) else "${it.toInt()} ml"
    } ?: VitalsUiState.DASH
}
