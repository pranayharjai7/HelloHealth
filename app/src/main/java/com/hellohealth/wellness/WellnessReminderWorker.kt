package com.hellohealth.wellness

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hellohealth.MainActivity
import com.hellohealth.R
import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Posts the once-a-day wellness reminder notification. Scheduled by [WellnessReminderScheduler]. The
 * notification is best-effort: if POST_NOTIFICATIONS is denied (Android 13+), the worker still
 * succeeds — it simply doesn't post. Tapping the notification opens the app.
 */
@HiltWorker
class WellnessReminderWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        ensureChannel(appContext)

        // Respect the runtime permission on Android 13+; never crash if it's denied.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                AppLogger.d(FeatureTag.WELLNESS, "reminder: POST_NOTIFICATIONS denied; skipping")
                return Result.success()
            }
        }

        val contentIntent = PendingIntent.getActivity(
            appContext,
            0,
            Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("How's your day going?")
            .setContentText("Log a dimension to keep your wellness streak alive.")
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        (appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, notification)
        AppLogger.d(FeatureTag.WELLNESS, "reminder posted")
        return Result.success()
    }

    companion object {
        private const val CHANNEL_ID = "wellness_reminder"
        private const val NOTIFICATION_ID = 4301

        /** Create the reminder notification channel. Idempotent. */
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Wellness reminders",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "A gentle daily nudge to log your wellness" }
            )
        }
    }
}
