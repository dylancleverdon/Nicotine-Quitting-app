package com.baastiklabs.firewatch.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

/**
 * Installs a new version of this app over itself with Android's PackageInstaller.
 *
 * On Android 12+ with "install unknown apps" allowed once, this needs no taps at all:
 * an app may update itself without user action when it holds
 * UPDATE_PACKAGES_WITHOUT_USER_ACTION and targets a recent SDK. Otherwise Android asks for
 * one confirmation tap (see [InstallResultReceiver]).
 */
object SelfInstaller {
    const val EXTRA_VERSION_CODE = "com.baastiklabs.firewatch.VERSION_CODE"
    const val EXTRA_ROLLBACK = "com.baastiklabs.firewatch.ROLLBACK"
    const val EXTRA_INSTALL_NOW = "com.baastiklabs.firewatch.INSTALL_NOW"

    /** Whether updates can install with zero taps on this phone right now. */
    fun canInstallSilently(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && canRequestInstalls(context)

    /** The one-time "Allow from this source" switch for Firewatch. */
    fun canRequestInstalls(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun install(context: Context, apk: File, versionCode: Int, rollback: Boolean, installNow: Boolean) {
        val installer = context.packageManager.packageInstaller
        // Leftover sessions from interrupted attempts would only get in the way.
        installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }

        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                setPackageSource(PackageInstaller.PACKAGE_SOURCE_OTHER)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("firewatch.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallResultReceiver::class.java)
                .setPackage(context.packageName)
                .putExtra(EXTRA_VERSION_CODE, versionCode)
                .putExtra(EXTRA_ROLLBACK, rollback)
                .putExtra(EXTRA_INSTALL_NOW, installNow)
            // Mutable: the system adds the result extras to this intent.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
    }
}
