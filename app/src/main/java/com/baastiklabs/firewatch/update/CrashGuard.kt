package com.baastiklabs.firewatch.update

import android.content.Context
import com.baastiklabs.firewatch.BuildConfig
import com.baastiklabs.firewatch.core.update.CrashPolicy

/**
 * If a freshly installed version keeps crashing, go back to the previous one automatically.
 * Kept tiny and dependency-free so it works even when the rest of the app doesn't.
 */
object CrashGuard {
    private const val PREFS = "fw_crash"
    private const val KEY_CRASHES = "crashes"
    private const val KEY_ROLLED_BACK_FROM = "rolled_back_from"

    fun install(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val crashes = (read(context) + System.currentTimeMillis()).takeLast(10)
                prefs.edit().putString(KEY_CRASHES, crashes.joinToString(",")).commit()
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Called on start. Returns true if an automatic rollback was queued. */
    fun rollbackIfCrashLooping(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_ROLLED_BACK_FROM, -1) == BuildConfig.VERSION_CODE) return false
        val installedAt = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        }.getOrDefault(0L)
        if (!CrashPolicy.shouldRollback(read(context), System.currentTimeMillis(), installedAt)) return false
        prefs.edit().putInt(KEY_ROLLED_BACK_FROM, BuildConfig.VERSION_CODE).commit()
        UpdateScheduler.rollbackNow(context, installNow = true)
        return true
    }

    private fun read(context: Context): List<Long> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CRASHES, "")
            .orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }
}
