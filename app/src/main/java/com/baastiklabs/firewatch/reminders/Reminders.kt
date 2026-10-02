package com.baastiklabs.firewatch.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.baastiklabs.firewatch.core.model.Settings

/**
 * The old optional check-in and backup reminders were removed in 0.14: Firewatch only notifies in
 * Relapse prevention mode. [sync] now just cancels anything an older version scheduled, and the
 * worker class stays (doing nothing) so queued work from before the update can't fail.
 */
object Reminders {
    private const val WORK = "fw-reminders"

    @Suppress("UNUSED_PARAMETER")
    fun sync(context: Context, settings: Settings) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = Result.success()
}
