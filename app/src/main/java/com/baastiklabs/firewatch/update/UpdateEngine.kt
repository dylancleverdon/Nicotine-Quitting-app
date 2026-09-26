package com.baastiklabs.firewatch.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import com.baastiklabs.firewatch.BuildConfig
import com.baastiklabs.firewatch.FirewatchApp
import com.baastiklabs.firewatch.core.update.UpdateAsset
import com.baastiklabs.firewatch.core.update.UpdateManifest
import com.baastiklabs.firewatch.core.update.UpdatePolicy
import com.baastiklabs.firewatch.data.BackupStore
import com.baastiklabs.firewatch.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * The self-updater. Nothing here may ever need D to do anything: it finds, downloads,
 * verifies, backs up and installs on its own. See docs/updater.md.
 */
class UpdateEngine(
    private val app: FirewatchApp,
    private val state: UpdateState,
    private val repository: Repository,
) {
    enum class Outcome { UP_TO_DATE, INSTALLING, DEFERRED, RETRY, FAILED }

    suspend fun run(rollback: Boolean, installNow: Boolean): Outcome = withContext(Dispatchers.IO) {
        try {
            val manifest = fetchManifest() ?: return@withContext Outcome.RETRY
            val installed = BuildConfig.VERSION_CODE
            val target = if (rollback) {
                UpdatePolicy.rollbackTarget(installed, manifest)
            } else {
                UpdatePolicy.updateTarget(installed, manifest)
            }
            if (target == null) {
                if (!rollback) {
                    state.pendingInstall = false
                    state.setStatus("Up to date")
                } else {
                    state.setStatus("No earlier version to go back to")
                }
                cleanDownloads(keep = null)
                return@withContext Outcome.UP_TO_DATE
            }

            val label = if (rollback) "earlier version ${target.versionName}" else "version ${target.versionName}"
            val apk = download(target, label) ?: return@withContext Outcome.RETRY
            if (!verify(apk, target)) {
                apk.delete()
                return@withContext Outcome.RETRY
            }

            // An open app is never yanked away by an automatic update; it installs once D leaves.
            if (!installNow && app.isInForeground && SelfInstaller.canInstallSilently(app)) {
                state.pendingInstall = true
                state.setStatus("${label.replaceFirstChar { it.uppercase() }} is ready and installs when you leave the app")
                return@withContext Outcome.DEFERRED
            }

            state.setStatus("Backing up before installing $label")
            BackupStore.write(app, repository, "before-${if (rollback) "rollback" else "update"}-to-${target.versionName}")

            state.setStatus("Installing $label")
            state.pendingInstall = false
            SelfInstaller.install(app, apk, target.versionCode)
            Outcome.INSTALLING
        } catch (e: Exception) {
            Log.w(TAG, "Update run failed", e)
            state.recordError("Update check failed: ${e.message ?: e.javaClass.simpleName}")
            Outcome.RETRY
        }
    }

    private fun fetchManifest(): UpdateManifest? {
        var url = state.manifestUrl
        repeat(3) {
            val text = try {
                httpGetText(url)
            } catch (e: IOException) {
                state.recordError("Couldn't reach the update server (${e.message ?: "offline"})")
                return null
            }
            val manifest = UpdateManifest.parse(text)
            if (manifest == null) {
                state.recordError("The update server sent something unexpected")
                return null
            }
            val moved = manifest.movedTo
            if (!moved.isNullOrBlank() && moved != url) {
                state.followMove(moved)
                url = moved
                return@repeat
            }
            state.recordCheck(System.currentTimeMillis(), text)
            return manifest
        }
        return null
    }

    private fun download(target: UpdateAsset, label: String): File? {
        val dir = File(app.cacheDir, "updates").apply { mkdirs() }
        val apk = File(dir, "firewatch-${target.versionCode}.apk")
        cleanDownloads(keep = apk)
        if (apk.exists() && sha256(apk).equals(target.sha256, ignoreCase = true)) return apk

        state.setStatus("Downloading $label")
        val part = File(dir, apk.name + ".part")
        try {
            openConnection(target.url).let { conn ->
                try {
                    conn.inputStream.use { input -> part.outputStream().use { input.copyTo(it) } }
                } finally {
                    conn.disconnect()
                }
            }
        } catch (e: IOException) {
            part.delete()
            state.recordError("Download of $label didn't finish; will retry")
            return null
        }
        if (!part.renameTo(apk)) {
            part.delete()
            return null
        }
        return apk
    }

    private fun verify(apk: File, target: UpdateAsset): Boolean {
        val hash = sha256(apk)
        if (!hash.equals(target.sha256, ignoreCase = true)) {
            state.recordError("Downloaded file didn't match (checksum); will retry")
            return false
        }
        val pm = app.packageManager
        val archive = archiveInfo(pm, apk)
        if (archive == null || archive.packageName != app.packageName) {
            state.recordError("Downloaded file isn't a Firewatch update")
            return false
        }
        if (PackageInfoCompat.getLongVersionCode(archive) != target.versionCode.toLong()) {
            state.recordError("Downloaded file has the wrong version")
            return false
        }
        // Android refuses an update signed with a different key anyway; this just fails earlier.
        val archiveSigners = signers(archive)
        val installedSigners = signers(installedInfo(pm))
        if (archiveSigners.isNotEmpty() && installedSigners.isNotEmpty() && archiveSigners != installedSigners) {
            state.recordError("Downloaded file isn't signed by Baastik Labs")
            return false
        }
        return true
    }

    private fun cleanDownloads(keep: File?) {
        File(app.cacheDir, "updates").listFiles()?.forEach { f ->
            if (keep == null || (f.name != keep.name && f.name != keep.name + ".part")) f.delete()
        }
    }

    @Suppress("DEPRECATION")
    private fun archiveInfo(pm: PackageManager, apk: File): PackageInfo? =
        pm.getPackageArchiveInfo(apk.absolutePath, signingFlags())

    @Suppress("DEPRECATION")
    private fun installedInfo(pm: PackageManager): PackageInfo = pm.getPackageInfo(app.packageName, signingFlags())

    @Suppress("DEPRECATION")
    private fun signingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        }
        return sigs?.map { sha256(it.toByteArray()) }?.toSet() ?: emptySet()
    }

    private fun httpGetText(url: String): String {
        val conn = openConnection(url)
        try {
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** Follows redirects (GitHub release links redirect to its file host). */
    private fun openConnection(start: String): HttpURLConnection {
        var url = start
        repeat(6) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Firewatch/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Cache-Control", "no-cache")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location") ?: throw IOException("Redirect without location")
                conn.disconnect()
                url = URL(URL(url), location).toString()
                return@repeat
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw IOException("HTTP $code")
            }
            return conn
        }
        throw IOException("Too many redirects")
    }

    companion object {
        private const val TAG = "FirewatchUpdate"

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
