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
    // Fewer copies, less clutter: 7 private copies, one rolling copy in Downloads/Firewatch,
    // 7 in a chosen folder.
    private const val KEEP = 7
    private const val PUBLIC_KEEP = 1
    private const val PUBLIC_DIR = "Firewatch"
    private const val DAILY_MS = 24 * 60 * 60_000L
    private const val FOLDER_KEEP = 7

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
        runCatching { writeToFolder(context, out.name, text) }
        out
    }

    // --- A folder D picked once (e.g. Google Drive): survives a lost or replaced phone. ---

    private fun prefs(context: Context) = context.getSharedPreferences("fw_backup", Context.MODE_PRIVATE)

    fun folder(context: Context): Uri? = prefs(context).getString("folder", null)?.let(Uri::parse)

    fun setFolder(context: Context, tree: Uri?) {
        if (tree != null) {
            context.contentResolver.takePersistableUriPermission(
                tree,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        prefs(context).edit().putString("folder", tree?.toString()).apply()
    }

    /** When a copy last left the phone (export or backup folder). */
    fun lastOffPhone(context: Context): Long = prefs(context).getLong("last_off_phone", 0L)

    fun markOffPhone(context: Context) = prefs(context).edit().putLong("last_off_phone", System.currentTimeMillis()).apply()

    private fun writeToFolder(context: Context, name: String, text: String) {
        val tree = folder(context) ?: return
        val resolver = context.contentResolver
        val parent = android.provider.DocumentsContract.buildDocumentUriUsingTree(
            tree, android.provider.DocumentsContract.getTreeDocumentId(tree),
        )
        val doc = android.provider.DocumentsContract.createDocument(resolver, parent, "application/json", name) ?: return
        resolver.openOutputStream(doc)?.use { it.write(text.toByteArray()) }
        markOffPhone(context)
        pruneFolder(context, tree)
    }

    private fun pruneFolder(context: Context, tree: Uri) {
        val resolver = context.contentResolver
        val children = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
            tree, android.provider.DocumentsContract.getTreeDocumentId(tree),
        )
        val found = ArrayList<Pair<String, Long>>()
        resolver.query(
            children,
            arrayOf(
                android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1)?.startsWith("firewatch-backup-") == true) found += c.getString(0) to c.getLong(2)
            }
        }
        found.sortedByDescending { it.second }.drop(FOLDER_KEEP).forEach { (id, _) ->
            runCatching {
                android.provider.DocumentsContract.deleteDocument(
                    resolver, android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, id),
                )
            }
        }
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
