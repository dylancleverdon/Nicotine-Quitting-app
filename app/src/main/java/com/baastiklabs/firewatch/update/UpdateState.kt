package com.baastiklabs.firewatch.update

import android.content.Context
import android.content.SharedPreferences
import com.baastiklabs.firewatch.BuildConfig
import com.baastiklabs.firewatch.core.update.UpdateManifest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the updater last did, shown quietly in Settings. */
class UpdateState(context: Context) {
    data class Snapshot(
        val lastCheckAt: Long,
        val status: String,
        val error: String?,
        val manifest: UpdateManifest?,
        val pendingInstall: Boolean,
        val lastSeenVersion: String?,
    )

    private val prefs: SharedPreferences = context.getSharedPreferences("fw_update", Context.MODE_PRIVATE)
    private val _flow = MutableStateFlow(read())
    val flow: StateFlow<Snapshot> = _flow.asStateFlow()

    // Held as a field: SharedPreferences only keeps a weak reference to listeners.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> _flow.value = read() }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    val manifestUrl: String
        get() = prefs.getString(KEY_MANIFEST_URL, null) ?: BuildConfig.UPDATE_MANIFEST_URL

    fun followMove(newUrl: String) = prefs.edit().putString(KEY_MANIFEST_URL, newUrl).apply()

    fun recordCheck(now: Long, manifestJson: String) = prefs.edit()
        .putLong(KEY_LAST_CHECK, now)
        .putString(KEY_MANIFEST, manifestJson)
        .remove(KEY_ERROR)
        .apply()

    fun setStatus(status: String) = prefs.edit().putString(KEY_STATUS, status).apply()

    fun recordError(message: String, now: Long = System.currentTimeMillis()) = prefs.edit()
        .putString(KEY_ERROR, message)
        .putLong(KEY_LAST_CHECK, now)
        .apply()

    var pendingInstall: Boolean
        get() = prefs.getBoolean(KEY_PENDING, false)
        set(value) = prefs.edit().putBoolean(KEY_PENDING, value).apply()

    /** Counts how often a silent install of [versionCode] fell back to asking for a tap. */
    fun bumpSilentRetry(versionCode: Int): Int {
        val key = "silent_retry_$versionCode"
        val count = prefs.getInt(key, 0) + 1
        prefs.edit().putInt(key, count).apply()
        return count
    }

    var lastSeenVersion: String?
        get() = prefs.getString(KEY_LAST_SEEN, null)
        set(value) = prefs.edit().putString(KEY_LAST_SEEN, value).apply()

    private fun read() = Snapshot(
        lastCheckAt = prefs.getLong(KEY_LAST_CHECK, 0L),
        status = prefs.getString(KEY_STATUS, null) ?: "Not checked yet",
        error = prefs.getString(KEY_ERROR, null),
        manifest = prefs.getString(KEY_MANIFEST, null)?.let { UpdateManifest.parse(it) },
        pendingInstall = prefs.getBoolean(KEY_PENDING, false),
        lastSeenVersion = prefs.getString(KEY_LAST_SEEN, null),
    )

    private companion object {
        const val KEY_MANIFEST_URL = "manifest_url"
        const val KEY_LAST_CHECK = "last_check"
        const val KEY_MANIFEST = "manifest"
        const val KEY_STATUS = "status"
        const val KEY_ERROR = "error"
        const val KEY_PENDING = "pending_install"
        const val KEY_LAST_SEEN = "last_seen_version"
    }
}
