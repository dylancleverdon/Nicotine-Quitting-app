package com.baastiklabs.firewatch.update

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Checks for updates every few hours in the background, plus whenever the app starts. */
object UpdateScheduler {
    private const val PERIODIC = "fw-update-periodic"
    private const val NOW = "fw-update-now"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(3, TimeUnit.HOURS)
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * @param installNow install even if the app is open (the user asked). Otherwise a
     *   downloaded update waits until the app is closed.
     */
    fun checkNow(context: Context, installNow: Boolean = false) =
        enqueue(context, UpdateWorker.MODE_UPDATE, installNow)

    fun rollbackNow(context: Context, installNow: Boolean) =
        enqueue(context, UpdateWorker.MODE_ROLLBACK, installNow)

    /** Tries the same install again shortly (Android briefly throttles repeated silent updates). */
    fun retryLater(context: Context, rollback: Boolean, installNow: Boolean) =
        enqueue(context, if (rollback) UpdateWorker.MODE_ROLLBACK else UpdateWorker.MODE_UPDATE, installNow, delaySeconds = 60)

    private fun enqueue(context: Context, mode: String, installNow: Boolean, delaySeconds: Long = 0) {
        val request = OneTimeWorkRequestBuilder<UpdateWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setConstraints(network)
            .setInputData(workDataOf(UpdateWorker.KEY_MODE to mode, UpdateWorker.KEY_INSTALL_NOW to installNow))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        // Automatic checks never interrupt one already running; explicit requests take over.
        val automatic = mode == UpdateWorker.MODE_UPDATE && !installNow && delaySeconds == 0L
        val policy = if (automatic) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, policy, request)
    }
}
