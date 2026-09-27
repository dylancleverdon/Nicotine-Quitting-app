package com.baastiklabs.firewatch.data

import android.content.Context

/**
 * When D last opened the app. After bedtime this tells the battery "I'm up" so it can catch up on
 * the time since bedtime. Home-screen widgets never touch it.
 */
object AppActivity {
    private fun prefs(context: Context) = context.getSharedPreferences("fw_activity", Context.MODE_PRIVATE)

    fun mark(context: Context) = prefs(context).edit().putLong("last", System.currentTimeMillis()).apply()

    fun last(context: Context): Long = prefs(context).getLong("last", 0L)
}
