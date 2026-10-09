package com.hellohealth.workoutsession

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.hellohealth.MainActivity
import com.hellohealth.R
import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.domain.repository.WorkoutSessionRepository
import com.hellohealth.ui.navigation.Screen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

/**
 * Foreground service that keeps an active workout session alive while the app is backgrounded, with
 * an ongoing notification showing the session title. Started from [start] when a session begins;
 * it observes the active session and stops itself (removing the notification) as soon as there is no
 * active session — so finishing or abandoning a workout, from anywhere, tears the service down.
 *
 * Typed `health` (manifest `foregroundServiceType`) for Android 14+ FGS-type compliance; the matching
 * runtime bits are FOREGROUND_SERVICE_HEALTH (install-time) and POST_NOTIFICATIONS (requested by the
 * UI before start on Android 13+). The notification is best-effort: if the user denied notifications,
 * startForeground still runs the service; the OS simply doesn't display the notification.
 */
@AndroidEntryPoint
class WorkoutSessionService : Service() {

    @Inject lateinit var sessionRepository: WorkoutSessionRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Enter the foreground immediately with a placeholder so we satisfy the 5s startForeground
        // deadline, then refine the text once the active session is observed.
        startForegroundCompat(buildNotification(title = intent?.getStringExtra(EXTRA_TITLE)))

        observeJob?.cancel()
        observeJob = sessionRepository.observeActiveSession()
            .onEach { session ->
                if (session == null) {
                    AppLogger.d(FeatureTag.WORKOUT_SESSION, "no active session; stopping foreground service")
                    stopSelf()
                } else {
                    notificationManager().notify(NOTIFICATION_ID, buildNotification(session.title))
                }
            }
            .launchIn(scope)

        // Re-deliver so a process restart re-attaches the observer (and re-checks for an active session).
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        observeJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(title: String?): Notification {
        // Deep-link straight back into the ActiveWorkout screen (explicit component → stays private,
        // no manifest <data> filter needed). dayId=none → the screen re-attaches to the live session.
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(Screen.ActiveWorkout.deepLinkUri()),
                this,
                MainActivity::class.java,
            ).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title?.ifBlank { null } ?: "Workout in progress")
            .setContentText("Tap to return to your workout")
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val CHANNEL_ID = "workout_session"
        private const val NOTIFICATION_ID = 4201
        private const val EXTRA_TITLE = "title"

        /** Create the (low-importance) notification channel. Idempotent; safe to call repeatedly. */
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Active workout",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "Shows while a workout session is in progress" }
            )
        }

        /** Start the foreground service for a session titled [title]. */
        fun start(context: Context, title: String?) {
            val intent = Intent(context, WorkoutSessionService::class.java).apply {
                putExtra(EXTRA_TITLE, title)
            }
            context.startForegroundService(intent)
        }

        /** Stop the foreground service (called when a session finishes / is abandoned). */
        fun stop(context: Context) {
            context.stopService(Intent(context, WorkoutSessionService::class.java))
        }
    }
}
