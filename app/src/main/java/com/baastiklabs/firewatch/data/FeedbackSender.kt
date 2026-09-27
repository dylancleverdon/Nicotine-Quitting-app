package com.baastiklabs.firewatch.data

import android.content.Context
import android.os.Build
import com.baastiklabs.firewatch.BuildConfig
import com.baastiklabs.firewatch.core.Feedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends suggestions and bug reports to the maker's Google Form. If sending fails (offline), the
 * encoded body is queued and retried on the next app start.
 */
object FeedbackSender {
    private fun prefs(context: Context) = context.getSharedPreferences("fw_feedback", Context.MODE_PRIVATE)

    fun appInfo(): String = "Android ${BuildConfig.VERSION_NAME} · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"

    fun savedName(context: Context): String = prefs(context).getString("name", "") ?: ""
    fun saveName(context: Context, name: String) = prefs(context).edit().putString("name", name.trim()).apply()

    /** True if it went out now; false if it was queued for later. */
    suspend fun send(context: Context, type: String, suggestion: String, details: String, name: String): Boolean {
        val body = Feedback.encode(Feedback.fields(type, suggestion, details, name, appInfo()))
        val ok = post(body)
        if (!ok) enqueue(context, body)
        return ok
    }

    /** Retries anything queued while offline. */
    suspend fun flush(context: Context) {
        val queued = queue(context)
        if (queued.isEmpty()) return
        val left = queued.filterNot { post(it) }
        saveQueue(context, left)
    }

    fun pending(context: Context): Int = queue(context).size

    private suspend fun post(body: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(Feedback.FORM_URL).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..399
        }.getOrDefault(false)
    }

    private fun queue(context: Context): List<String> = runCatching {
        val a = JSONArray(prefs(context).getString("queue", "[]"))
        (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())

    private fun enqueue(context: Context, body: String) = saveQueue(context, (queue(context) + body).takeLast(50))

    private fun saveQueue(context: Context, items: List<String>) =
        prefs(context).edit().putString("queue", JSONArray(items).toString()).apply()
}
