package com.baastiklabs.firewatch.core.backup

import com.baastiklabs.firewatch.core.Branding
import com.baastiklabs.firewatch.core.FirewatchJson
import com.baastiklabs.firewatch.core.records.RecordEnvelope
import com.baastiklabs.firewatch.core.records.RecordTypes
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** A backup/export file. Human-readable JSON; the same format on phone and (later) web. */
@Serializable
data class BackupFile(
    val app: String = Branding.FULL_NAME,
    val format: Int = FORMAT,
    val exportedAt: Long = 0,
    val appVersion: String = "",
    val reason: String = "",
    val records: List<BackupRecord> = emptyList(),
) {
    companion object {
        const val FORMAT = 1
    }
}

@Serializable
data class BackupRecord(
    val id: String,
    val type: String,
    val ts: Long? = null,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    val data: JsonElement = JsonObject(emptyMap()),
)

data class BackupPreview(
    val doses: Int,
    val cravings: Int,
    val products: Int,
    val sleepEvents: Int,
    val firstAt: Long?,
    val lastAt: Long?,
)

object Backups {
    fun build(records: Collection<RecordEnvelope>, now: Long, appVersion: String, reason: String): BackupFile =
        BackupFile(
            exportedAt = now,
            appVersion = appVersion,
            reason = reason,
            records = records.sortedWith(compareBy({ it.type }, { it.ts ?: 0L }, { it.id })).map { it.toBackupRecord() },
        )

    fun encode(file: BackupFile): String = FirewatchJson.encodeToString(BackupFile.serializer(), file)

    /** Returns null if the text isn't a Firewatch backup. */
    fun decode(text: String): BackupFile? =
        runCatching { FirewatchJson.decodeFromString(BackupFile.serializer(), text) }.getOrNull()
            ?.takeIf { it.records.isNotEmpty() || it.app.contains("Firewatch") }

    fun toEnvelopes(file: BackupFile): List<RecordEnvelope> = file.records.map {
        RecordEnvelope(it.id, it.type, it.ts, it.updatedAt, it.deleted, it.data.toString())
    }

    fun preview(file: BackupFile): BackupPreview {
        val live = file.records.filter { !it.deleted }
        val timed = live.filter { it.type == RecordTypes.DOSE || it.type == RecordTypes.CRAVING }.mapNotNull { it.ts }
        return BackupPreview(
            doses = live.count { it.type == RecordTypes.DOSE },
            cravings = live.count { it.type == RecordTypes.CRAVING },
            products = live.count { it.type == RecordTypes.PRODUCT },
            sleepEvents = live.count { it.type == RecordTypes.SLEEP },
            firstAt = timed.minOrNull(),
            lastAt = timed.maxOrNull(),
        )
    }

    /**
     * Merge-on-import: for each id, the most recently updated version wins. Returns only the
     * incoming records that should be written.
     */
    fun mergeIncoming(existing: Collection<RecordEnvelope>, incoming: Collection<RecordEnvelope>): List<RecordEnvelope> {
        val current = existing.associateBy { it.id }
        return incoming.filter { rec ->
            val have = current[rec.id]
            have == null || rec.updatedAt > have.updatedAt
        }
    }

    private fun RecordEnvelope.toBackupRecord(): BackupRecord = BackupRecord(
        id = id,
        type = type,
        ts = ts,
        updatedAt = updatedAt,
        deleted = deleted,
        data = runCatching { FirewatchJson.parseToJsonElement(json) }.getOrElse { JsonObject(emptyMap()) },
    )
}
