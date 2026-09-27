package com.baastiklabs.firewatch.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.baastiklabs.firewatch.FirewatchApp
import com.baastiklabs.firewatch.MainActivity
import com.baastiklabs.firewatch.R
import com.baastiklabs.firewatch.core.localDate
import com.baastiklabs.firewatch.core.model.Settings
import com.baastiklabs.firewatch.data.BackupStore
import kotlinx.datetime.TimeZone
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Opt-in, neutral reminders (both off by default): a daily check-in nudge in the evening and a
 * weekly "keep a copy off your phone" nudge. Firewatch never sends "time for your next piece".
 */
object Reminders {
    private const val WORK = "fw-reminders"
    private const val CHANNEL = "reminders"

    fun sync(context: Context, settings: Settings) {
        val wm = WorkManager.getInstance(context)
        if (!settings.remindCheckIn && !settings.remindBackup) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(LocalTime.of(20, 0))
        if (next.isBefore(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun notify(context: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Reminders you turned on", NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as FirewatchApp
        val repo = app.graph.repository
        repo.ensureLoaded()
        val data = repo.data.value
        val s = data.settings
        val tz = TimeZone.currentSystemDefault()
        val today = System.currentTimeMillis().localDate(tz)
        if (s.remindCheckIn && s.dailyCheckIn && data.checkIns.none { it.at.localDate(tz) == today }) {
            Reminders.notify(app, 5101, "Daily check-in", "Three quick taps: cravings, mood and sleep.")
        }
        if (s.remindBackup && BackupStore.folder(app) == null &&
            System.currentTimeMillis() - BackupStore.lastOffPhone(app) > 7 * 24 * 3_600_000L
        ) {
            Reminders.notify(app, 5102, "Keep a copy of your history", "Export from Settings to Google Drive or email, so a lost phone doesn't lose it.")
        }
        return Result.success()
    }
}
