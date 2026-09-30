package com.hellohealth.data.vitals

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.data.health.HealthConnectManager
import com.hellohealth.data.repository.SupabaseSessionManager
import com.hellohealth.domain.model.ActivityGoals
import com.hellohealth.domain.repository.VitalsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-call, idempotent seeding of the daily vitals rollup from Health Connect history, invoked from
 * `MainActivity.onCreate` (Step 11). Modeled on [com.hellohealth.data.exercise.ExerciseSeeder]:
 * running every launch and gating on existing data covers fresh installs AND migrated users, and
 * self-heals a prior partial run.
 *
 * Behaviour:
 *  - No-op when no user is signed in, Health Connect is unavailable, or the vitals permissions
 *    aren't granted — the rollup simply fills in once those hold.
 *  - Count-gated: if the user already has a healthy number of rollup rows, skip entirely (the
 *    daily-fetch path in ActivityRepositoryImpl keeps it current from then on).
 *  - Otherwise iterate the last [BACKFILL_DAYS] days, reading each day's summary from Health Connect
 *    and upserting a `kind="rollup"` row. Deterministic ids make every pass idempotent.
 *
 * Runs on [Dispatchers.IO] and never throws: the whole sweep is wrapped in `runCatching`, so a
 * Health Connect hiccup can never crash app start or block the first frame.
 */
@Singleton
class VitalsBackfiller @Inject constructor(
    private val healthConnectManager: HealthConnectManager,
    private val vitalsRepository: VitalsRepository,
    private val sessionManager: SupabaseSessionManager,
) {

    suspend fun backfillIfNeeded() = withContext(Dispatchers.IO) {
        runCatching {
            val userId = sessionManager.getCurrentUserId()
            if (userId == null) {
                AppLogger.d(FeatureTag.VITALS, "backfill skipped: no signed-in user")
                return@runCatching
            }
            if (!healthConnectManager.isAvailable || !healthConnectManager.hasAllPermissions()) {
                AppLogger.d(FeatureTag.VITALS, "backfill skipped: HC unavailable or permissions not granted")
                return@runCatching
            }

            // Count gate: enough recent rollups already → the daily-fetch path keeps things current.
            val existing = vitalsRepository.observeRecentRollups(BACKFILL_DAYS).first().size
            if (existing >= BACKFILL_GATE_ROWS) {
                AppLogger.d(FeatureTag.VITALS, "backfill skipped: $existing rollups already present")
                return@runCatching
            }

            AppLogger.i(FeatureTag.VITALS, "backfilling up to $BACKFILL_DAYS days of vitals rollups")
            val today = LocalDate.now()
            val goals = ActivityGoals()
            var written = 0
            for (offset in 0 until BACKFILL_DAYS) {
                val date = today.minusDays(offset.toLong())
                val summary = healthConnectManager.fetchHealthSummary(goals, date)
                // Health Connect returns lastUpdated=0 when it has nothing for the day (e.g. the
                // past-N-days read restriction) — skip those so we don't write empty rollup rows.
                if (summary.lastUpdated <= 0L) continue
                vitalsRepository.upsertRollup(
                    localDate = date.toString(),
                    timestampUtcEpochMs = summary.lastUpdated,
                    restingHeartRate = summary.restingHeartRate,
                    hrvRmssd = summary.hrvRmssd,
                    respiratoryRate = summary.respiratoryRate,
                    bodyTemperature = summary.bodyTemperature,
                    hydrationMl = summary.hydrationMl,
                    spo2 = summary.oxygenSaturation,
                    sleepDurationMinutes = summary.sleepDurationMinutes.toInt().takeIf { it > 0 },
                    deepSleepMinutes = null
                )
                written++
            }
            AppLogger.i(FeatureTag.VITALS, "backfill complete: wrote $written rollup rows")
        }.onFailure { e ->
            AppLogger.e(FeatureTag.VITALS, "backfillIfNeeded failed; rollups may be incomplete", e)
        }
    }

    companion object {
        /** How many days back the first-run backfill reaches. ~30 days seeds a full baseline window. */
        private const val BACKFILL_DAYS = 30

        /**
         * At or above this many existing rollups, skip the sweep. Kept below [BACKFILL_DAYS] so a
         * device that simply lacks some days of history (HC read restriction) isn't re-swept forever.
         */
        private const val BACKFILL_GATE_ROWS = 7
    }
}
