package com.baastiklabs.firewatch.core.records

import com.baastiklabs.firewatch.core.FirewatchJson
import com.baastiklabs.firewatch.core.model.CheckIn
import com.baastiklabs.firewatch.core.model.Craving
import com.baastiklabs.firewatch.core.model.DefaultProducts
import com.baastiklabs.firewatch.core.model.Dose
import com.baastiklabs.firewatch.core.model.ModeChange
import com.baastiklabs.firewatch.core.model.TimerCheck
import com.baastiklabs.firewatch.core.model.Product
import com.baastiklabs.firewatch.core.model.RungChange
import com.baastiklabs.firewatch.core.model.Settings
import com.baastiklabs.firewatch.core.model.SleepEvent
import com.baastiklabs.firewatch.core.Absorption
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * One stored record. This shape never changes: it is the row in the phone's database, the
 * entry in a backup file, and (later) the web app's storage format. New features add new
 * record types or new JSON fields, never new columns.
 */
data class RecordEnvelope(
    val id: String,
    val type: String,
    /** Main timestamp for sorting/queries (epoch ms), if the record has one. */
    val ts: Long?,
    val updatedAt: Long,
    /** Tombstone: deleted records are kept so undo and merge-on-import work. */
    val deleted: Boolean,
    /** The record's JSON object. May contain fields this version doesn't know about. */
    val json: String,
)

object RecordTypes {
    const val PRODUCT = "product"
    const val DOSE = "dose"
    const val CRAVING = "craving"
    const val SLEEP = "sleep"
    const val SETTINGS = "settings"
    const val RUNG = "rung"
    const val CHECKIN = "checkin"
    const val MODE = "mode"
    const val TIMER_CHECK = "timercheck"

    const val SETTINGS_ID = "settings"
}

object RecordCodec {
    /**
     * Encodes [value] into an envelope. When [previousJson] is given, fields this version doesn't
     * know about are carried over, so an older version never erases a newer version's data.
     */
    fun <T> encode(
        type: String,
        id: String,
        ts: Long?,
        value: T,
        serializer: KSerializer<T>,
        previousJson: String?,
        now: Long,
        deleted: Boolean = false,
    ): RecordEnvelope {
        val fresh = FirewatchJson.encodeToJsonElement(serializer, value).jsonObject
        val merged = if (previousJson == null) fresh else merge(parseObject(previousJson), fresh)
        return RecordEnvelope(id, type, ts, now, deleted, merged.toString())
    }

    fun <T> decode(json: String, serializer: KSerializer<T>): T? =
        runCatching { FirewatchJson.decodeFromString(serializer, json) }.getOrNull()

    fun merge(old: JsonObject?, new: JsonObject): JsonObject {
        if (old == null) return new
        return JsonObject(old + new)
    }

    fun parseObject(json: String): JsonObject? =
        runCatching { FirewatchJson.parseToJsonElement(json).jsonObject }.getOrNull()

    fun product(p: Product, previousJson: String?, now: Long, deleted: Boolean = false) =
        encode(RecordTypes.PRODUCT, p.id, p.createdAt, p, Product.serializer(), previousJson, now, deleted)

    fun dose(d: Dose, previousJson: String?, now: Long, deleted: Boolean = false) =
        encode(RecordTypes.DOSE, d.id, d.at, d, Dose.serializer(), previousJson, now, deleted)

    fun craving(c: Craving, previousJson: String?, now: Long, deleted: Boolean = false) =
        encode(RecordTypes.CRAVING, c.id, c.at, c, Craving.serializer(), previousJson, now, deleted)

    fun sleep(s: SleepEvent, previousJson: String?, now: Long, deleted: Boolean = false) =
        encode(RecordTypes.SLEEP, s.id, s.at, s, SleepEvent.serializer(), previousJson, now, deleted)

    fun rung(r: RungChange, previousJson: String?, now: Long, deleted: Boolean = false) =
        encode(RecordTypes.RUNG, r.id, r.at, r, RungChange.serializer(), previousJson, now, deleted)

    fun checkIn(c: CheckIn, previousJson: String?, now: Long, deleted: Boolean = false) =
        encode(RecordTypes.CHECKIN, c.id, c.at, c, CheckIn.serializer(), previousJson, now, deleted)

    fun mode(m: ModeChange, previousJson: String?, now: Long, deleted: Boolean = false) =
        encode(RecordTypes.MODE, m.id, m.at, m, ModeChange.serializer(), previousJson, now, deleted)

    fun timerCheck(c: TimerCheck, previousJson: String?, now: Long, deleted: Boolean = false) =
        encode(RecordTypes.TIMER_CHECK, c.id, c.at, c, TimerCheck.serializer(), previousJson, now, deleted)

    fun settings(s: Settings, previousJson: String?, now: Long) =
        encode(RecordTypes.SETTINGS, RecordTypes.SETTINGS_ID, null, s, Settings.serializer(), previousJson, now)
}

/** Everything the app knows, decoded from records. Deleted records are left out. */
data class FirewatchData(
    val products: List<Product> = emptyList(),
    val doses: List<Dose> = emptyList(),
    val cravings: List<Craving> = emptyList(),
    val sleepEvents: List<SleepEvent> = emptyList(),
    val settings: Settings = Settings(),
    val rungChanges: List<RungChange> = emptyList(),
    val checkIns: List<CheckIn> = emptyList(),
    val modeChanges: List<ModeChange> = emptyList(),
    val timerChecks: List<TimerCheck> = emptyList(),
) {
    /** Relapse prevention mode is on right now. */
    val relapseOn: Boolean get() = modeChanges.lastOrNull { it.mode == "relapse" }?.on == true

    /** The rung D is working at (pieces a day), or null before one is chosen. */
    val targetPieces: Double? get() = rungChanges.lastOrNull()?.pieces

    val productsById: Map<String, Product> by lazy { products.associateBy { it.id } }

    /** Products for the home screen, in order. */
    val homeProducts: List<Product> by lazy {
        products.filter { it.onHome && !it.archived }.sortedWith(compareBy({ it.order }, { it.name }))
    }

    /** Absorbed mg that counts as one piece. */
    val referenceMg: Double by lazy {
        productsById[settings.referenceProductId]?.let { Absorption.absorbedMg(it) }
            ?.takeIf { it > 0.0 } ?: DefaultProducts.REFERENCE_MG
    }

    companion object {
        fun fromRecords(records: Collection<RecordEnvelope>): FirewatchData {
            val live = records.filter { !it.deleted }
            fun <T> decodeAll(type: String, serializer: KSerializer<T>): List<T> =
                live.filter { it.type == type }.mapNotNull { RecordCodec.decode(it.json, serializer) }

            val settings = live.firstOrNull { it.type == RecordTypes.SETTINGS }
                ?.let { RecordCodec.decode(it.json, Settings.serializer()) } ?: Settings()
            return FirewatchData(
                products = decodeAll(RecordTypes.PRODUCT, Product.serializer()),
                doses = decodeAll(RecordTypes.DOSE, Dose.serializer()).sortedBy { it.at },
                cravings = decodeAll(RecordTypes.CRAVING, Craving.serializer()).sortedBy { it.at },
                sleepEvents = decodeAll(RecordTypes.SLEEP, SleepEvent.serializer()).sortedBy { it.at },
                settings = settings,
                rungChanges = decodeAll(RecordTypes.RUNG, RungChange.serializer()).sortedBy { it.at },
                checkIns = decodeAll(RecordTypes.CHECKIN, CheckIn.serializer()).sortedBy { it.at },
                modeChanges = decodeAll(RecordTypes.MODE, ModeChange.serializer()).sortedBy { it.at },
                timerChecks = decodeAll(RecordTypes.TIMER_CHECK, TimerCheck.serializer()).sortedBy { it.at },
            )
        }
    }
}
