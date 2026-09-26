package com.baastiklabs.firewatch.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.baastiklabs.firewatch.FirewatchApp

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as FirewatchApp
        val mode = inputData.getString(KEY_MODE) ?: MODE_UPDATE
        val installNow = inputData.getBoolean(KEY_INSTALL_NOW, false)
        val engine = UpdateEngine(app, app.graph.updateState, app.graph.repository)
        return when (engine.run(rollback = mode == MODE_ROLLBACK, installNow = installNow)) {
            UpdateEngine.Outcome.RETRY -> if (runAttemptCount < 5) Result.retry() else Result.failure()
            else -> Result.success()
        }
    }

    companion object {
        const val KEY_MODE = "mode"
        const val KEY_INSTALL_NOW = "install_now"
        const val MODE_UPDATE = "update"
        const val MODE_ROLLBACK = "rollback"
    }
}
