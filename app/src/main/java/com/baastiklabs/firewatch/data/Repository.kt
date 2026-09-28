package com.baastiklabs.firewatch.data

import com.baastiklabs.firewatch.core.Ids
import com.baastiklabs.firewatch.core.model.CheckIn
import com.baastiklabs.firewatch.core.model.Craving
import com.baastiklabs.firewatch.core.model.CravingOutcome
import com.baastiklabs.firewatch.core.model.DefaultProducts
import com.baastiklabs.firewatch.core.model.Dose
import com.baastiklabs.firewatch.core.model.ModeChange
import com.baastiklabs.firewatch.core.model.TimerCheck
import com.baastiklabs.firewatch.core.model.Product
import com.baastiklabs.firewatch.core.model.RungChange
import com.baastiklabs.firewatch.core.model.Settings
import com.baastiklabs.firewatch.core.model.SleepEvent
import com.baastiklabs.firewatch.core.model.SleepKind
import com.baastiklabs.firewatch.core.records.FirewatchData
import com.baastiklabs.firewatch.core.records.RecordCodec
import com.baastiklabs.firewatch.core.records.RecordEnvelope
import com.baastiklabs.firewatch.core.records.RecordTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * All reads and writes of D's data. Keeps every record in memory (a few thousand a year) and
 * writes through to [RecordStore]. Deletes are tombstones, so nothing is ever hard-deleted.
 */
class Repository(
    private val store: RecordStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val records = HashMap<String, RecordEnvelope>()
    private val products = HashMap<String, Product>()
    private val doses = HashMap<String, Dose>()
    private val cravings = HashMap<String, Craving>()
    private val sleepEvents = HashMap<String, SleepEvent>()
    private val rungChanges = HashMap<String, RungChange>()
    private val checkIns = HashMap<String, CheckIn>()
    private val modeChanges = HashMap<String, ModeChange>()
    private val timerChecks = HashMap<String, TimerCheck>()
    private var settings = Settings()

    private val _data = MutableStateFlow(FirewatchData())
    val data: StateFlow<FirewatchData> = _data.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    suspend fun ensureLoaded() {
        if (_loaded.value) return
        mutex.withLock {
            if (_loaded.value) return
            val all = withContext(Dispatchers.IO) { store.all() }
            all.forEach { index(it) }
            // Add any default product this install has never had (new defaults arrive with updates).
            val missing = DefaultProducts.all().filter { it.id !in records }
            if (missing.isNotEmpty()) {
                val now = clock()
                writeLocked(missing.map { RecordCodec.product(it.copy(createdAt = now), null, now) })
            }
            publish()
            _loaded.value = true
        }
    }

    /** Every record, including tombstones: what backups contain. */
    suspend fun allRecords(): List<RecordEnvelope> {
        ensureLoaded()
        return mutex.withLock { records.values.toList() }
    }

    // --- Doses ---

    suspend fun logDose(dose: Dose): Dose {
        mutate { now -> listOf(RecordCodec.dose(dose, records[dose.id]?.json, now)) }
        return dose
    }

    suspend fun updateDose(dose: Dose) = logDose(dose)

    /** Writes many doses in one go (the back-dated week). */
    suspend fun logDoses(doses: List<Dose>) {
        mutate { now -> doses.map { RecordCodec.dose(it, records[it.id]?.json, now) } }
    }

    suspend fun deleteDose(id: String) = tombstone(id)

    // --- Cravings ---

    suspend fun startCraving(intensity: Int): Craving {
        val now = clock()
        val craving = Craving(id = Ids.newId(now), at = now, intensity = intensity.coerceIn(1, 10))
        mutate { t -> listOf(RecordCodec.craving(craving, null, t)) }
        return craving
    }

    suspend fun finishCraving(id: String, outcome: CravingOutcome) {
        val existing = cravings[id] ?: return
        val ended = existing.copy(outcome = outcome, endedAt = existing.endedAt ?: clock())
        saveCraving(ended)
    }

    suspend fun saveCraving(craving: Craving) {
        mutate { now -> listOf(RecordCodec.craving(craving, records[craving.id]?.json, now)) }
    }

    suspend fun deleteCraving(id: String) = tombstone(id)

    // --- Sleep ---

    suspend fun logSleep(kind: SleepKind, at: Long): SleepEvent {
        val event = SleepEvent(Ids.newId(clock()), at, kind)
        mutate { now -> listOf(RecordCodec.sleep(event, null, now)) }
        return event
    }

    suspend fun deleteSleep(id: String) = tombstone(id)

    // --- Rungs & check-ins ---

    /** Moves the target rung. [reason] is "start", "down" or "up". */
    suspend fun setTarget(pieces: Double, reason: String) {
        val now = clock()
        mutate { t -> listOf(RecordCodec.rung(RungChange(Ids.newId(now), now, pieces, reason), null, t)) }
    }

    /** "Hide next piece timer": a tap to see the time. */
    suspend fun logTimerCheck(charging: Boolean) {
        val now = clock()
        mutate { t -> listOf(RecordCodec.timerCheck(TimerCheck(Ids.newId(now), now, charging), null, t)) }
    }

    /** Switches Relapse prevention mode on or off (kept as history, so past days stay marked). */
    suspend fun setRelapse(on: Boolean) {
        val now = clock()
        mutate { t -> listOf(RecordCodec.mode(ModeChange(Ids.newId(now), now, on), null, t)) }
    }

    /** "Just starting gum": gum first on the home screen, reminders for gum, then the mode on. */
    suspend fun startGum() {
        val gum = products.values.filter { it.kind == com.baastiklabs.firewatch.core.model.ProductKind.GUM && !it.archived }.sortedBy { it.labelMg }
        gum.forEachIndexed { i, p -> saveProduct(p.copy(onHome = true, order = -10 + i)) }
        val reminder = gum.firstOrNull { it.id == DefaultProducts.GUM_4MG } ?: gum.firstOrNull()
        if (reminder != null) updateSettings { it.copy(relapseProductId = reminder.id) }
        setRelapse(true)
    }

    suspend fun saveCheckIn(checkIn: CheckIn) {
        mutate { now -> listOf(RecordCodec.checkIn(checkIn, records[checkIn.id]?.json, now)) }
    }

    // --- Products & settings ---

    suspend fun saveProduct(product: Product) {
        mutate { now -> listOf(RecordCodec.product(product, records[product.id]?.json, now)) }
    }

    suspend fun updateSettings(transform: (Settings) -> Settings) {
        mutate { now ->
            listOf(RecordCodec.settings(transform(settings), records[RecordTypes.SETTINGS_ID]?.json, now))
        }
    }

    // --- Import ---

    /** Writes already-merged records (see core Backups.mergeIncoming). */
    suspend fun putRecords(incoming: List<RecordEnvelope>) {
        ensureLoaded()
        mutex.withLock {
            writeLocked(incoming)
            publish()
        }
    }

    /** Tombstones every live record not in [keepIds] (used by "replace" imports). */
    suspend fun tombstoneAllExcept(keepIds: Set<String>) {
        mutate { now ->
            records.values.filter { !it.deleted && it.id !in keepIds && it.type != RecordTypes.SETTINGS }
                .map { it.copy(deleted = true, updatedAt = now) }
        }
    }

    fun newId(): String = Ids.newId(clock())

    fun now(): Long = clock()

    // --- Internals ---

    private suspend fun tombstone(id: String) {
        mutate { now ->
            val existing = records[id] ?: return@mutate emptyList()
            listOf(existing.copy(deleted = true, updatedAt = now))
        }
    }

    private suspend fun mutate(build: (now: Long) -> List<RecordEnvelope>) {
        ensureLoaded()
        mutex.withLock {
            writeLocked(build(clock()))
            publish()
        }
    }

    private suspend fun writeLocked(envelopes: List<RecordEnvelope>) {
        if (envelopes.isEmpty()) return
        withContext(Dispatchers.IO) { store.putAll(envelopes) }
        envelopes.forEach { index(it) }
    }

    private fun index(env: RecordEnvelope) {
        records[env.id] = env
        when (env.type) {
            RecordTypes.PRODUCT -> put(products, env, Product.serializer())
            RecordTypes.DOSE -> put(doses, env, Dose.serializer())
            RecordTypes.CRAVING -> put(cravings, env, Craving.serializer())
            RecordTypes.SLEEP -> put(sleepEvents, env, SleepEvent.serializer())
            RecordTypes.RUNG -> put(rungChanges, env, RungChange.serializer())
            RecordTypes.CHECKIN -> put(checkIns, env, CheckIn.serializer())
            RecordTypes.MODE -> put(modeChanges, env, ModeChange.serializer())
            RecordTypes.TIMER_CHECK -> put(timerChecks, env, TimerCheck.serializer())
            RecordTypes.SETTINGS -> settings =
                (if (env.deleted) null else RecordCodec.decode(env.json, Settings.serializer())) ?: Settings()
            else -> Unit // A newer version's record type: kept in storage and backups, ignored here.
        }
    }

    private fun <T> put(map: HashMap<String, T>, env: RecordEnvelope, serializer: kotlinx.serialization.KSerializer<T>) {
        val value = if (env.deleted) null else RecordCodec.decode(env.json, serializer)
        if (value == null) map.remove(env.id) else map[env.id] = value
    }

    private fun publish() {
        _data.value = FirewatchData(
            products = products.values.toList(),
            doses = doses.values.sortedBy { it.at },
            cravings = cravings.values.sortedBy { it.at },
            sleepEvents = sleepEvents.values.sortedBy { it.at },
            settings = settings,
            rungChanges = rungChanges.values.sortedBy { it.at },
            checkIns = checkIns.values.sortedBy { it.at },
            modeChanges = modeChanges.values.sortedBy { it.at },
            timerChecks = timerChecks.values.sortedBy { it.at },
        )
        onChange?.invoke()
    }

    /** Called after every change (the app uses it to refresh home-screen widgets). */
    var onChange: (() -> Unit)? = null
}
