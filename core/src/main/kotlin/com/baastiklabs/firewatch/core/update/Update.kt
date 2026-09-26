package com.baastiklabs.firewatch.core.update

import com.baastiklabs.firewatch.core.FirewatchJson
import kotlinx.serialization.Serializable

/**
 * The update manifest (`update.json`) published with every Android release. The app polls it
 * and installs newer versions by itself.
 */
@Serializable
data class UpdateManifest(
    val format: Int = 1,
    val channel: String = "apk",
    val latest: UpdateAsset? = null,
    /** The previous release rebuilt with a higher version code, so it can install over the latest. */
    val rollback: UpdateAsset? = null,
    /** If hosting ever moves, the new manifest URL. The app remembers and follows it. */
    val movedTo: String? = null,
) {
    companion object {
        fun parse(text: String): UpdateManifest? =
            runCatching { FirewatchJson.decodeFromString(serializer(), text) }.getOrNull()
    }
}

@Serializable
data class UpdateAsset(
    val versionCode: Int,
    val versionName: String,
    val url: String,
    val sha256: String,
    val size: Long = 0,
    val notes: String = "",
)

object UpdatePolicy {
    /** A newer release to install automatically, if any. Rollback builds are never auto-installed. */
    fun updateTarget(installedVersionCode: Int, manifest: UpdateManifest): UpdateAsset? =
        manifest.latest?.takeIf { it.versionCode > installedVersionCode }

    /** The previous version, if going back to it is possible from what's installed. */
    fun rollbackTarget(installedVersionCode: Int, manifest: UpdateManifest): UpdateAsset? =
        manifest.rollback?.takeIf { it.versionCode > installedVersionCode }
}

/** Decides when a freshly installed version is crash-looping and should be rolled back. */
object CrashPolicy {
    const val CRASH_COUNT = 3
    const val WINDOW_MS = 15 * 60_000L
    const val FRESH_INSTALL_MS = 48 * 60 * 60_000L

    fun shouldRollback(crashTimes: List<Long>, now: Long, installedAt: Long): Boolean {
        if (now - installedAt > FRESH_INSTALL_MS) return false
        val recent = crashTimes.count { it >= installedAt && now - it <= WINDOW_MS }
        return recent >= CRASH_COUNT
    }
}

data class ChangelogSection(val version: String, val title: String, val body: String)

/** Parses CHANGELOG.md: each release is a `## <version> ...` heading followed by notes. */
object Changelog {
    fun parse(markdown: String): List<ChangelogSection> {
        val sections = mutableListOf<ChangelogSection>()
        var title: String? = null
        val body = StringBuilder()
        fun flush() {
            val t = title ?: return
            val version = t.split(' ', ' ').firstOrNull { it.isNotBlank() } ?: t
            sections += ChangelogSection(version, t, body.toString().trim())
        }
        for (line in markdown.lines()) {
            if (line.startsWith("## ")) {
                flush()
                title = line.removePrefix("## ").trim()
                body.clear()
            } else if (title != null) {
                body.appendLine(line)
            }
        }
        flush()
        return sections
    }

    /** Sections newer than [lastSeenVersion] (newest first). Unknown/first run → just the newest. */
    fun since(sections: List<ChangelogSection>, lastSeenVersion: String?): List<ChangelogSection> {
        if (lastSeenVersion == null || sections.none { it.version == lastSeenVersion }) return sections.take(1)
        return sections.takeWhile { it.version != lastSeenVersion }
    }

    fun section(sections: List<ChangelogSection>, version: String): ChangelogSection? =
        sections.firstOrNull { it.version == version }
}
