package com.baastiklabs.firewatch.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.baastiklabs.firewatch.BuildConfig
import com.baastiklabs.firewatch.core.Feedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends suggestions and bug reports to the maker's Google Form. If sending fails it's saved with the
 * reason; it is never retried automatically (no loops). Settings → Unsent suggestions has "Send now"
 * (one tap = one attempt) and "Delete".
 */
object FeedbackSender {
    data class Unsent(val id: String, val at: Long, val type: String, val text: String, val body: String, val error: String)

    private fun prefs(context: Context) = context.getSharedPreferences("fw_feedback", Context.MODE_PRIVATE)

    fun appInfo(): String = "Android ${BuildConfig.VERSION_NAME} · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"

    fun savedName(context: Context): String = prefs(context).getString("name", "") ?: ""
    fun saveName(context: Context, name: String) = prefs(context).edit().putString("name", name.trim()).apply()

    /** Sends now. Null if it went out; otherwise the reason, and it's saved for "Send now". */
    suspend fun send(context: Context, type: String, suggestion: String, details: String, name: String): String? {
        val body = Feedback.encode(Feedback.fields(type, suggestion, details, name, appInfo()))
        val error = post(context, body)
        if (error != null) {
            save(context, unsent(context) + Unsent(System.currentTimeMillis().toString(), System.currentTimeMillis(), type, suggestion, body, error))
        }
        return error
    }

    /** One attempt for a saved suggestion. Null if it went out (and it's removed). */
    suspend fun sendNow(context: Context, id: String): String? {
        val item = unsent(context).firstOrNull { it.id == id } ?: return null
        val error = post(context, item.body)
        save(context, if (error == null) unsent(context).filterNot { it.id == id } else unsent(context).map { if (it.id == id) it.copy(error = error) else it })
        return error
    }

    fun delete(context: Context, id: String) = save(context, unsent(context).filterNot { it.id == id })

    private fun online(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun post(context: Context, body: String): String? = withContext(Dispatchers.IO) {
        if (!online(context)) return@withContext "No connection"
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
            if (code in 200..399) null else "The suggestions form didn't accept it (error $code)"
        }.getOrElse { "Couldn't reach the suggestions form" }
    }

    fun unsent(context: Context): List<Unsent> = runCatching {
        val a = JSONArray(prefs(context).getString("unsent", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Unsent(o.getString("id"), o.getLong("at"), o.optString("type"), o.optString("text"), o.getString("body"), o.optString("error"))
        }
    }.getOrDefault(emptyList()) + legacyQueue(context)

    private fun save(context: Context, items: List<Unsent>) {
        val a = JSONArray()
        items.takeLast(50).forEach { u ->
            a.put(JSONObject().put("id", u.id).put("at", u.at).put("type", u.type).put("text", u.text).put("body", u.body).put("error", u.error))
        }
        prefs(context).edit().putString("unsent", a.toString()).remove("queue").apply()
    }

    /** 0.7.0 kept failed sends as bare bodies; show them in the list too. */
    private fun legacyQueue(context: Context): List<Unsent> = runCatching {
        val a = JSONArray(prefs(context).getString("queue", "[]"))
        (0 until a.length()).map { i ->
            val body = a.getString(i)
            val text = java.net.URLDecoder.decode(body.substringAfter("entry.1095248491=").substringBefore("&"), "UTF-8")
            Unsent("legacy-$i", 0L, "", text, body, "Not sent yet")
        }
    }.getOrDefault(emptyList())
}
