package com.hellohealth.wellness

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the once-a-day wellness reminder ([WellnessReminderWorker]) via WorkManager, mirroring
 * [com.hellohealth.sync.SyncScheduler.ensurePeriodicSync]'s KEEP idiom so app relaunches don't reset
 * the cadence. The first run is aligned to the next local [REMINDER_HOUR] with an initial delay; the
 * work then repeats on a daily period.
 */
@Singleton
class WellnessReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager get() = WorkManager.getInstance(context)

    /** Enqueue the daily reminder once; safe to call on every app start (KEEP). */
    fun ensureDailyReminder() {
        val request = PeriodicWorkRequestBuilder<WellnessReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMinutes(), TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
        AppLogger.d(FeatureTag.WELLNESS, "daily wellness reminder ensured (target hour=$REMINDER_HOUR)")
    }

    /** Minutes from now until the next local [REMINDER_HOUR]:00 (always in (0, 24h]). */
    private fun initialDelayMinutes(): Long {
        val now = Calendar.getInstance()
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, REMINDER_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return ((target.timeInMillis - now.timeInMillis) / 60_000L).coerceAtLeast(1L)
    }

    private companion object {
        const val WORK_NAME = "wellness_daily_reminder"
        /** Local hour of day to nudge (19:00 — evening wind-down, when the day is loggable). */
        const val REMINDER_HOUR = 19
    }
}
