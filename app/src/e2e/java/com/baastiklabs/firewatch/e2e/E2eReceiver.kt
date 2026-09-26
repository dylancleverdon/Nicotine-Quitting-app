package com.baastiklabs.firewatch.e2e

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.baastiklabs.firewatch.BuildConfig
import com.baastiklabs.firewatch.FirewatchApp
import com.baastiklabs.firewatch.core.model.DefaultProducts
import com.baastiklabs.firewatch.core.toDose
import com.baastiklabs.firewatch.update.UpdateScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Test build only (see app/build.gradle.kts, build type "e2e"). Lets scripts/e2e-updater.sh
 * seed data, trigger update checks and rollbacks, and read back the installed version.
 */
class E2eReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as FirewatchApp
        val repo = app.graph.repository
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action?.substringAfterLast('.')) {
                    "SEED" -> {
                        repo.ensureLoaded()
                        repo.updateSettings { it.copy(onboardingDone = true) }
                        val gum = DefaultProducts.all().first { it.id == DefaultProducts.GUM_4MG }
                        repeat(5) { i ->
                            val now = repo.now()
                            repo.logDose(gum.toDose(repo.newId(), now - i * 3_600_000L, now))
                        }
                        report(repo.data.value.doses.size, "seeded")
                    }
                    "CHECK" -> UpdateScheduler.checkNow(context)
                    "ROLLBACK" -> UpdateScheduler.rollbackNow(context, installNow = false)
                    "REPORT" -> {
                        repo.ensureLoaded()
                        report(repo.data.value.doses.size, "report")
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun report(doses: Int, what: String) {
        Log.i(TAG, "$what versionCode=${BuildConfig.VERSION_CODE} doses=$doses status=${BuildConfig.VERSION_NAME}")
    }

    private companion object {
        const val TAG = "FW_E2E"
    }
}
