package com.baastiklabs.firewatch.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.baastiklabs.firewatch.BuildConfig
import com.baastiklabs.firewatch.core.backup.BackupFile
import com.baastiklabs.firewatch.core.backup.Backups
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Automatic backups: before every update, before imports/restores, and once a day.
 * Kept in the app's own storage (last [KEEP]) and, on Android 10+, also copied to
 * Downloads/Firewatch so they survive even an uninstall.
 */
object BackupStore {
    private const val KEEP = 10
    private const val PUBLIC_KEEP = 5
    private const val PUBLIC_DIR = "Firewatch"
    private const val DAILY_MS = 24 * 60 * 60_000L

    fun dir(context: Context): File = File(context.filesDir, "backups").apply { mkdirs() }

    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.name.endsWith(".json") }?.sortedByDescending { it.lastModified() } ?: emptyList()

    suspend fun write(context: Context, repository: Repository, reason: String): File = withContext(Dispatchers.IO) {
        val records = repository.allRecords()
        val file = Backups.build(records, repository.now(), BuildConfig.VERSION_NAME, reason)
        val text = Backups.encode(file)
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"))
        val safeReason = reason.replace(Regex("[^A-Za-z0-9.-]+"), "-").take(40)
        val out = File(dir(context), "firewatch-backup-$stamp-$safeReason.json")
        out.writeText(text)
        list(context).drop(KEEP).forEach { it.delete() }
        runCatching { writePublicCopy(context, out.name, text) }
        out
    }

    /** A daily safety net, called on app start. */
    suspend fun dailyIfDue(context: Context, repository: Repository) {
        val newest = list(context).firstOrNull()
        if (newest == null || System.currentTimeMillis() - newest.lastModified() > DAILY_MS) {
            if (repository.allRecords().isNotEmpty()) write(context, repository, "daily")
        }
    }

    fun read(file: File): BackupFile? = runCatching { Backups.decode(file.readText()) }.getOrNull()

    private fun writePublicCopy(context: Context, name: String, text: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val relativePath = Environment.DIRECTORY_DOWNLOADS + "/" + PUBLIC_DIR
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        }
        val uri = resolver.insert(collection, values) ?: return
        resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
        prunePublic(context, collection, relativePath)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun prunePublic(context: Context, collection: Uri, relativePath: String) {
        val resolver = context.contentResolver
        // Without storage permission this only ever sees files this app created.
        val ids = ArrayList<Long>()
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
            arrayOf("$relativePath%", "firewatch-backup-%"),
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { c -> while (c.moveToNext()) ids += c.getLong(0) }
        ids.drop(PUBLIC_KEEP).forEach { id ->
            runCatching { resolver.delete(ContentUris.withAppendedId(collection, id), null, null) }
        }
    }
}
