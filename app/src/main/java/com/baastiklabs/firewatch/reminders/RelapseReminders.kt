package com.baastiklabs.firewatch.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.baastiklabs.firewatch.FirewatchApp
import com.baastiklabs.firewatch.MainActivity
import com.baastiklabs.firewatch.R
import com.baastiklabs.firewatch.core.engine.Relapse
import com.baastiklabs.firewatch.core.records.FirewatchData
import com.baastiklabs.firewatch.core.toDose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/**
 * Relapse prevention mode reminders: the one opt-in exception to "no next-piece reminders".
 * One reminder per gap while awake (the tier's gap, or 2 hours before there's a tier), silent
 * during sleeping hours, restarted by every logged piece. Uses inexact alarms: no extra
 * permission, gentle on battery, and a few minutes' drift doesn't matter for a 2-hour gap.
 */
object RelapseReminders {
    const val ACTION_CHECK = "com.baastiklabs.firewatch.relapse.CHECK"
    const val ACTION_LOG = "com.baastiklabs.firewatch.relapse.LOG"
    const val ACTION_LOG_ANYWAY = "com.baastiklabs.firewatch.relapse.LOG_ANYWAY"
    private const val CHANNEL = "relapse"
    private const val NOTIFICATION_ID = 5201
    private const val RECENT_MS = 10 * 60_000L

    private fun prefs(context: Context) = context.getSharedPreferences("fw_relapse", Context.MODE_PRIVATE)
    fun lastNotified(context: Context): Long = prefs(context).getLong("last_notified", 0L)
    private fun markNotified(context: Context, at: Long) = prefs(context).edit().putLong("last_notified", at).apply()

    private fun pending(context: Context, action: String, code: Int) = PendingIntent.getBroadcast(
        context, code, Intent(context, RelapseReceiver::class.java).setAction(action).setPackage(context.packageName),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Schedules (or cancels) the next check from the current data. Safe to call often. */
    fun sync(context: Context, data: FirewatchData) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = pending(context, ACTION_CHECK, 1)
        val now = System.currentTimeMillis()
        val at = Relapse.nextCheckAt(data, now, TimeZone.currentSystemDefault(), lastNotified(context))
        if (at == null) {
            alarms.cancel(intent)
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            return
        }
        runCatching { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent) }
    }

    /** Called when the alarm fires: remind if a piece is due, then schedule the next check. */
    fun check(context: Context, data: FirewatchData) {
        val now = System.currentTimeMillis()
        val tz = TimeZone.currentSystemDefault()
        if (Relapse.shouldRemind(data, now, tz, lastNotified(context))) {
            val product = Relapse.product(data)
            notify(
                context,
                "Piece time: staying ahead of cravings",
                product?.let { "A ${it.name} now keeps cravings from catching you off guard." } ?: "A piece now keeps cravings from catching you off guard.",
                product?.let { "Log it" },
                ACTION_LOG,
            )
            markNotified(context, now)
        }
        sync(context, data)
    }

    fun notify(context: Context, title: String, text: String, actionLabel: String?, action: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Relapse prevention mode", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context, NOTIFICATION_ID, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
        if (actionLabel != null) builder.addAction(0, actionLabel, pending(context, action, if (action == ACTION_LOG) 2 else 3))
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build()) }
    }

    /** "Log it" on the notification. If something was logged in the last few minutes, asks first. */
    suspend fun logFromNotification(context: Context, force: Boolean) {
        val repo = (context.applicationContext as FirewatchApp).graph.repository
        repo.ensureLoaded()
        val data = repo.data.value
        val product = Relapse.product(data) ?: return
        val now = repo.now()
        val recent = data.doses.lastOrNull { !it.estimated && now - it.at in 0..RECENT_MS }
        if (recent != null && !force) {
            val mins = ((now - recent.at) / 60_000).coerceAtLeast(1)
            notify(context, "You logged ${recent.productName} $mins min ago", "Log another ${product.name} as well?", "Log anyway", ACTION_LOG_ANYWAY)
            return
        }
        repo.logDose(product.toDose(id = repo.newId(), at = now, loggedAt = now))
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        sync(context, repo.data.value)
    }
}

class RelapseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as FirewatchApp
        val done = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    RelapseReminders.ACTION_LOG -> RelapseReminders.logFromNotification(app, force = false)
                    RelapseReminders.ACTION_LOG_ANYWAY -> RelapseReminders.logFromNotification(app, force = true)
                    else -> {
                        app.graph.repository.ensureLoaded()
                        RelapseReminders.check(app, app.graph.repository.data.value)
                    }
                }
            } finally {
                done.finish()
            }
        }
    }
}
