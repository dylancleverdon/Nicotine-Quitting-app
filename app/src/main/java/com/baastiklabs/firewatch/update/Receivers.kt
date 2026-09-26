package com.baastiklabs.firewatch.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import com.baastiklabs.firewatch.FirewatchApp
import java.io.File

/** Receives the result of an install session. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as FirewatchApp
        val state = app.graph.updateState
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Older Android, or "install unknown apps" not allowed yet: Android wants one tap.
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                    ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                state.setStatus("Waiting for a tap to finish updating")
                if (app.isInForeground) {
                    runCatching { context.startActivity(confirm) }
                        .onFailure { UpdateNotifications.showFinishUpdate(context, confirm) }
                } else {
                    UpdateNotifications.showFinishUpdate(context, confirm)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> state.setStatus("Updated")
            else -> {
                state.setStatus("Update didn't install; will try again")
                state.recordError("Install failed: ${message ?: "status $status"}")
            }
        }
    }
}

/** Runs in the new version right after an update (or rollback) is installed. */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext as FirewatchApp
        app.graph.updateState.setStatus("Up to date")
        app.graph.updateState.pendingInstall = false
        File(context.cacheDir, "updates").deleteRecursively()
        UpdateNotifications.cancel(context)
        UpdateScheduler.ensureScheduled(context)
    }
}
