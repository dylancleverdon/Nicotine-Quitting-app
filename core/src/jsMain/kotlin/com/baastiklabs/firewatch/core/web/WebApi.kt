@file:OptIn(ExperimentalJsExport::class)

package com.baastiklabs.firewatch.core.web

import com.baastiklabs.firewatch.core.Baseline
import com.baastiklabs.firewatch.core.BaselineStatus
import com.baastiklabs.firewatch.core.CalendarScale
import com.baastiklabs.firewatch.core.CravingScale
import com.baastiklabs.firewatch.core.Cravings
import com.baastiklabs.firewatch.core.FirewatchJson
import com.baastiklabs.firewatch.core.Ids
import com.baastiklabs.firewatch.core.absorbedMg
import com.baastiklabs.firewatch.core.backup.BackupFile
import com.baastiklabs.firewatch.core.backup.BackupRecord
import com.baastiklabs.firewatch.core.backup.Backups
import com.baastiklabs.firewatch.core.engine.Backfill
import com.baastiklabs.firewatch.core.engine.Coach
import com.baastiklabs.firewatch.core.engine.FriendVape
import com.baastiklabs.firewatch.core.engine.Insights
import com.baastiklabs.firewatch.core.engine.Ladder
import com.baastiklabs.firewatch.core.engine.Progress
import com.baastiklabs.firewatch.core.engine.Quality
import com.baastiklabs.firewatch.core.engine.Rung
import com.baastiklabs.firewatch.core.engine.Tier
import com.baastiklabs.firewatch.core.engine.Waking
import com.baastiklabs.firewatch.core.localDate
import com.baastiklabs.firewatch.core.model.DefaultProducts
import com.baastiklabs.firewatch.core.model.Dose
import com.baastiklabs.firewatch.core.model.Duration
import com.baastiklabs.firewatch.core.model.Product
import com.baastiklabs.firewatch.core.pieces
import com.baastiklabs.firewatch.core.records.FirewatchData
import com.baastiklabs.firewatch.core.toDose
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement

@JsModule("@js-joda/timezone")
@JsNonModule
external object JsJodaTimeZoneModule

@Serializable data class RungDto(val pieces: Double, val tier: String, val label: String, val plain: String)
@Serializable data class BatteryDto(val charge: Double, val state: String, val readyAt: Double?, val stretchMin: Double, val pullMin: Double, val fitsNow: String? = null)
@Serializable data class StretchDay(val date: String, val stretchMin: Double, val pullMin: Double, val paused: Boolean = false)
@Serializable data class RelapseDto(
    val on: Boolean, val nextAt: Double?, val gapMin: Double, val productId: String?, val productName: String?,
    /** Why the "try Relapse prevention mode" card shows (null = hidden). */
    val recommend: String?, val movingOn: Boolean, val modeDays: List<String>,
)
@Serializable data class WindowDto(val from: Double, val to: Double, val peakAt: Double, val likelihood: Double, val strength: Double)
@Serializable data class ForecastDto(
    /** [at, likelihood 0..1, strength 1..10, asleep 0/1] every 15 minutes. */
    val points: List<List<Double>>, val learning: Boolean, val cravingsUsed: Int, val next: WindowDto?, val windows: List<WindowDto>,
    val quietestAt: Double?, val hits: Int, val tested: Int,
)
@Serializable data class ReceptorsDto(
    val history: List<NamedValue>, val plan: List<NamedValue>, val stay: List<NamedValue>, val todayLoad: Double,
    val clearAirOnPlan: String?, val typicalOnPlan: String?, val typicalIfStay: String?, val typical: Double,
)
@Serializable data class OutlooksDto(val forecast: ForecastDto, val receptors: ReceptorsDto?)
@Serializable data class HelpDto(val tour: List<com.baastiklabs.firewatch.core.Help.Page>, val why: List<com.baastiklabs.firewatch.core.Help.Page>, val articles: List<com.baastiklabs.firewatch.core.Help.Article>)
@Serializable data class DoseView(val id: String, val at: Double, val name: String, val pieces: Double, val mg: Double, val estimated: Boolean, val tags: List<String>, val kind: String)
@Serializable data class CravingView(val id: String, val at: Double, val intensity: Int, val name: String, val outcome: String, val endedAt: Double?, val tags: List<String>)
@Serializable data class DayView(
    val date: String, val pieces: Double, val mg: Double, val doses: Int, val cravings: Int, val rodeOut: Int, val level: Int,
    val clearHours: Double, val quality: Double?, val estimated: Boolean, val hasRange: Boolean, val low: Double, val high: Double,
    val awakeHours: Double, val mouthMin: Double, val wakeToFirstMin: Double?, val spikeMg: Double, val labelMg: Double,
    val borrowedPieces: Double, val barcode: List<Boolean>, val doubleUps: Int,
)
@Serializable data class NamedValue(val label: String, val value: Double, val extra: String = "")
@Serializable data class Snapshot(
    val today: String,
    val baselineState: String,
    val baselineDay: Int,
    val baselineAverage: Double?,
    val revealed: Boolean,
    val measured: RungDto?,
    val target: RungDto?,
    val battery: BatteryDto?,
    val stepDown: RungDto?,
    val stepDownNote: String?,
    val stepUp: RungDto?,
    val headsUps: List<String>,
    val qualityScore: Double?,
    val qualityLabel: String?,
    val swapTip: String?,
    val activeCraving: CravingView?,
    val todayPieces: Double,
    val todayMg: Double,
    val todayCravings: Int,
    val todayRodeOut: Int,
    val lastDoseAt: Double?,
    val todayDoses: List<DoseView>,
    val todayCravingList: List<CravingView>,
    val wave: List<List<Double>>,
    val wakeAt: Double,
    val sleepAt: Double,
    val typical: List<Double>,
    val days: List<DayView>,
    val sevenDayAverage: List<Double>,
    val insights: InsightsDto,
    val ladder: List<RungDto>,
    val tiers: List<NamedValue>,
    val stretchDays: List<StretchDay>,
    val stretchSummary: String?,
    val relapse: RelapseDto,
    /** Estimated nicotine in the body right now, mg (the home graph's value at "now"). */
    val nowMg: Double,
)
@Serializable data class InsightsDto(
    val avoidedPieces: Double, val avoidedMg: Double, val money: Double, val winRate: Double?, val cravingMinutes: Double?,
    val heaviness: Double?, val taperPct: Double?, val journey: Double?, val arrivals: List<NamedValue>,
    val longestGapMin: Double, val lightestDay: String?, val lightestPieces: Double?, val stretchMin: Double, val daysAtRung: Int,
    val badges: List<NamedValue>, val cards: List<String>, val pouches: Double, val pouchMetres: Double, val chewHours: Double, val cigarettes: Double,
    val clearAirLast: Double?, val clearAirSteps: List<NamedValue>, val heatmap: List<List<Double>>, val triggers: List<NamedValue>,
    val beaten: List<NamedValue>, val comparisons: List<NamedValue>, val weeklyGaps: List<Double>, val staircase: List<NamedValue>,
    val overnight: List<Double>, val background: List<Double>, val recaps: List<RecapDto>, val capacity: Int, val coachConfident: Boolean,
    val coachLevels: List<NamedValue>, val honest: String?, val checkIns: List<NamedValue>,
)
@Serializable data class RecapDto(val key: String, val title: String, val pieces: Double, val drop: Double?, val longestGapMin: Double, val trigger: String?, val rungs: List<String>, val cravings: Int)

private fun Rung.dto() = RungDto(pieces, tier.title, label, plainLine)

/** The web app's door into the shared engine. Everything in and out is JSON. */
@JsExport
object FirewatchCore {
    private val tzModule = JsJodaTimeZoneModule

    private fun data(recordsJson: String): FirewatchData {
        val records = FirewatchJson.decodeFromString(ListSerializer(BackupRecord.serializer()), recordsJson)
        return FirewatchData.fromRecords(Backups.toEnvelopes(BackupFile(records = records)))
    }

    fun defaultProducts(): String = FirewatchJson.encodeToString(ListSerializer(Product.serializer()), DefaultProducts.all())

    fun cravingScale(): String = FirewatchJson.encodeToString(
        ListSerializer(NamedValue.serializer()),
        CravingScale.levels.map { NamedValue(it.name, it.value.toDouble(), it.feels) },
    )

    fun newId(nowMs: Double): String = Ids.newId(nowMs.toLong())

    fun absorbedPieces(productJson: String, recordsJson: String): Double {
        val p = FirewatchJson.decodeFromString(Product.serializer(), productJson)
        val d = data(recordsJson)
        return p.toDose("x", 0, 0).pieces(d.referenceMg)
    }

    /** A dose from a product, as record data JSON. */
    fun doseFor(productJson: String, id: String, atMs: Double, nowMs: Double, multiplier: Double, duration: String, acidic: Boolean, tagsCsv: String): String {
        val p = FirewatchJson.decodeFromString(Product.serializer(), productJson)
        val dose = p.toDose(
            id, atMs.toLong(), nowMs.toLong(), multiplier,
            Duration.entries.firstOrNull { it.name == duration } ?: Duration.FULL, acidic,
            tagsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        )
        return FirewatchJson.encodeToString(Dose.serializer(), dose)
    }

    /** A friend's-vape dose: strength mg/mL (NaN = no idea) and a puff range. */
    fun vapeDose(id: String, atMs: Double, nowMs: Double, strength: Double, puffsLow: Int, puffsHigh: Int, name: String): String {
        val (lo, hi) = FriendVape.range(strength.takeIf { !it.isNaN() }, puffsLow..puffsHigh)
        val d = Dose(
            id = id, productId = "friends-vape", at = atMs.toLong(), productName = name,
            kind = com.baastiklabs.firewatch.core.model.ProductKind.VAPE, speed = com.baastiklabs.firewatch.core.model.SpeedProfile.SPIKE,
            rangeLowMg = lo, rangeHighMg = hi, borrowed = true, loggedAt = nowMs.toLong(),
        )
        return FirewatchJson.encodeToString(Dose.serializer(), d)
    }

    /** Estimated doses for one back-dated day. counts: {productId: n}; vapes: {FEW|SESSION|ALL_NIGHT: n}. */
    fun backfillDay(recordsJson: String, dayIso: String, countsJson: String, vapesJson: String, nowMs: Double): String {
        val d = data(recordsJson)
        val counts = FirewatchJson.decodeFromString(MapSerializer(String.serializer(), Int.serializer()), countsJson)
        val vapes = FirewatchJson.decodeFromString(MapSerializer(String.serializer(), Int.serializer()), vapesJson)
            .mapNotNull { (k, v) -> FriendVape.Amount.entries.firstOrNull { it.name == k }?.let { it to v } }.toMap()
        var n = 0
        val doses = Backfill.doses(d, LocalDate.parse(dayIso), Backfill.DayEntry(counts, vapes), TimeZone.currentSystemDefault(), nowMs.toLong()) {
            Ids.newId(nowMs.toLong() + n++)
        }
        return FirewatchJson.encodeToString(ListSerializer(Dose.serializer()), doses)
    }

    fun backfillDays(todayIso: String): String =
        FirewatchJson.encodeToString(ListSerializer(String.serializer()), Backfill.days(LocalDate.parse(todayIso)).map { it.toString() })

    fun compute(recordsJson: String, nowMs: Double, lastActivityMs: Double): String {
        jsTypeOf(tzModule) // keeps the time zone data loaded
        val now = nowMs.toLong()
        val d = data(recordsJson)
        val tz = TimeZone.currentSystemDefault()
        val today = now.localDate(tz)
        val ref = d.referenceMg
        val ins = Insights(d, tz, now)
        val baseline = Baseline.status(d, today, tz)
        val revealed = baseline is BaselineStatus.Complete
        val measured = if (revealed) Progress.measuredRung(d, today, tz) else null
        val target = d.targetPieces?.let { Ladder.rung(it) }
        val battery = target?.let { Progress.battery(d, if (it.pieces > 0) it.pieces else 1.0 / 3.0, now, tz, lastActivityMs.toLong()) }
        val stepDown = if (revealed) Progress.stepDownOffer(d, now, tz) else null
        val readiness = d.targetPieces?.let { Coach.readiness(d, it, now) }
        val stepUp = if (revealed && stepDown == null) Coach.stepUpOffer(d, now, tz) else null
        val todayDay = ins.days.lastOrNull()?.takeIf { it.date == today }
        val todayDoses = d.doses.filter { it.at.localDate(tz) == today }
        val todayCravings = d.cravings.filter { it.at.localDate(tz) == today }
        val w = Waking.day(d, today, tz)
        val prof = Coach.profile(d, now)
        val rec = ins.records()
        val (last, steps) = ins.clearAirTimeline()

        fun craving(c: com.baastiklabs.firewatch.core.model.Craving) = CravingView(
            c.id, c.at.toDouble(), c.intensity, CravingScale.level(c.intensity).name,
            Cravings.effectiveOutcome(c, d.doses, now).name, c.endedAt?.toDouble(), c.tags,
        )

        val insights = InsightsDto(
            avoidedPieces = ins.piecesAvoided(), avoidedMg = ins.mgAvoided(), money = ins.moneySaved(),
            winRate = ins.cravingWinRate(), cravingMinutes = ins.averageCravingMinutes(), heaviness = ins.heaviness(),
            taperPct = ins.taperPercentPerWeek(), journey = ins.journey(),
            arrivals = ins.arrivals().map { NamedValue(it.rung.tier.title, 0.0, it.date?.toString() ?: "") },
            longestGapMin = rec.longestGapMin, lightestDay = rec.lightestDay?.date?.toString(), lightestPieces = rec.lightestDay?.pieces,
            stretchMin = rec.totalStretchMin, daysAtRung = rec.daysAtCurrentRung,
            badges = ins.badges().map { NamedValue(it.title, 0.0, "${it.detail} · ${it.earnedOn}") },
            cards = ins.insightCards(),
            pouches = ins.silly().pouchesSkipped, pouchMetres = ins.silly().pouchLengthMetres,
            chewHours = ins.silly().chewingHoursAvoided, cigarettes = ins.silly().cigarettesNotSmoked,
            clearAirLast = last?.toDouble(), clearAirSteps = steps.map { NamedValue(it.first, it.second.toDouble()) },
            heatmap = ins.heatmap().map { it.toList() },
            triggers = ins.triggerCounts().map { NamedValue(it.first, it.second.toDouble()) },
            beaten = ins.beatenTriggers().map { NamedValue(it.first, it.second.toDouble(), "${it.second} of ${it.third} without") },
            comparisons = ins.comparisons().map { NamedValue(it.label, it.a, it.b.toString()) },
            weeklyGaps = ins.weeklyGaps().map { it.second },
            staircase = ins.tierStaircase().map { NamedValue(it.second.tier.title, it.second.pieces, it.first.toString()) },
            overnight = ins.overnightGaps().map { it.second },
            background = ins.backgroundSeries().map { it.second },
            recaps = ins.months().mapNotNull { (y, m) -> ins.monthlyRecap(y, m)?.let { RecapDto("$y-$m", it.month, it.pieces, it.biggestWeeklyDropPct, it.longestGapMin, it.mostBeatenTrigger, it.rungsReached, it.cravingsRidden) } } +
                ins.months().map { it.first }.distinct().mapNotNull { y -> ins.yearRecap(y)?.let { RecapDto("year-$y", it.month, it.pieces, it.biggestWeeklyDropPct, it.longestGapMin, it.mostBeatenTrigger, it.rungsReached, it.cravingsRidden) } },
            capacity = prof.capacity, coachConfident = prof.confident,
            coachLevels = prof.levels.filter { it.total > 0 }.map { NamedValue("Level ${it.level}", it.rate, "${it.rodeOut} of ${it.total} beaten") },
            honest = Coach.honestPieces(d, now, tz)?.let { Ladder.measured(it).label },
            checkIns = d.checkIns.map { NamedValue(it.at.toString(), it.craving.toDouble(), it.mood.toString()) },
        )

        val snapshot = Snapshot(
            today = today.toString(),
            baselineState = when (baseline) { BaselineStatus.NotStarted -> "none"; is BaselineStatus.InProgress -> "progress"; is BaselineStatus.Complete -> "complete" },
            baselineDay = (baseline as? BaselineStatus.InProgress)?.dayNumber ?: 0,
            baselineAverage = ins.baselineAverage,
            revealed = revealed,
            measured = measured?.dto(),
            target = target?.dto(),
            battery = battery?.let { BatteryDto(it.charge, it.state.name, it.readyAt?.toDouble(), it.stretchMinutesToday, it.pullMinutesToday, com.baastiklabs.firewatch.core.engine.BatteryEngine.fitsNow(d, it)?.name) },
            stepDown = stepDown?.dto(),
            stepDownNote = readiness?.takeIf { it.confident }?.let {
                if (it.ready) "From your cravings, the next rung should feel like about a ${it.predictedNext.toInt()} out of 10, and you ride out ${it.capacity}s."
                else "Your cravings suggest the next rung may feel like a ${it.predictedNext.toInt()}, above the ${it.capacity} you usually ride out. Holding a bit longer is fine too."
            },
            stepUp = stepUp?.dto(),
            headsUps = Progress.headsUps(d, now, tz).map { it.message },
            qualityScore = Quality.of(todayDoses, ref),
            qualityLabel = Quality.of(todayDoses, ref)?.let { Quality.label(it) },
            swapTip = Quality.swapTip(todayDoses, ref),
            activeCraving = Cravings.active(d, now)?.let { craving(it) },
            todayPieces = todayDoses.sumOf { it.pieces(ref) },
            todayMg = todayDoses.sumOf { it.absorbedMg() },
            todayCravings = todayCravings.size,
            todayRodeOut = todayCravings.count { Cravings.effectiveOutcome(it, d.doses, now) == com.baastiklabs.firewatch.core.model.CravingOutcome.RODE_OUT },
            lastDoseAt = d.doses.maxOfOrNull { it.at }?.toDouble(),
            todayDoses = todayDoses.sortedByDescending { it.at }.map { DoseView(it.id, it.at.toDouble(), it.productName, it.pieces(ref), it.absorbedMg(), it.estimated, it.tags, it.kind.name) },
            todayCravingList = todayCravings.map { craving(it) },
            wave = ins.todayCurve(10).map { listOf(it.first.toDouble(), it.second) },
            wakeAt = w.wakeAt.toDouble(),
            sleepAt = w.sleepAt.toDouble(),
            typical = ins.typicalCurve(),
            days = ins.days.map { s ->
                DayView(
                    s.date.toString(), s.pieces, s.absorbedMg, s.doses.size, s.cravings, s.rodeOut, CalendarScale.level(s.pieces),
                    s.clearHours, s.quality, s.doses.any { it.estimated }, s.hasRange, s.lowPieces, s.highPieces, s.awakeHours,
                    s.mouthMinutes, s.wakeToFirstMin, s.spikeMg, s.labelMg, s.borrowedPieces, s.barcode, s.doubleUps,
                )
            },
            sevenDayAverage = ins.days.indices.map { ins.sevenDayAverage(it) },
            insights = insights,
            ladder = (Ladder.rungs + Ladder.clearAir).map { it.dto() },
            tiers = Tier.entries.map { NamedValue(it.title, 0.0, it.pace) },
            stretchDays = ins.stretchPull.map { StretchDay(it.date.toString(), it.stretchMin, it.pullMin, it.paused) },
            stretchSummary = ins.stretchSummary(),
            relapse = com.baastiklabs.firewatch.core.engine.Relapse.let { r ->
                val p = r.product(d)
                RelapseDto(
                    on = d.relapseOn, nextAt = r.nextAt(d, now, tz)?.toDouble(), gapMin = r.gapMinutes(d, now),
                    productId = p?.id, productName = p?.name,
                    recommend = r.recommendation(d, now, tz)?.let { r.reasonText(it) }, movingOn = r.movingOn(d, now),
                    modeDays = ins.days.filter { r.isModeDay(d, it.date, tz) }.map { it.date.toString() },
                )
            },
            nowMg = com.baastiklabs.firewatch.core.engine.Kinetics.level(d.doses, now),
        )
        return FirewatchJson.encodeToString(Snapshot.serializer(), snapshot)
    }

    /** Full day details for the calendar's day view. */
    fun day(recordsJson: String, dayIso: String, nowMs: Double): String {
        val d = data(recordsJson)
        val tz = TimeZone.currentSystemDefault()
        val date = LocalDate.parse(dayIso)
        val ref = d.referenceMg
        val doses = d.doses.filter { it.at.localDate(tz) == date }.sortedBy { it.at }
            .map { DoseView(it.id, it.at.toDouble(), it.productName, it.pieces(ref), it.absorbedMg(), it.estimated, it.tags, it.kind.name) }
        val cravings = d.cravings.filter { it.at.localDate(tz) == date }.map {
            CravingView(it.id, it.at.toDouble(), it.intensity, CravingScale.level(it.intensity).name,
                Cravings.effectiveOutcome(it, d.doses, nowMs.toLong()).name, it.endedAt?.toDouble(), it.tags)
        }
        @Serializable data class DayDetail(val doses: List<DoseView>, val cravings: List<CravingView>)
        return FirewatchJson.encodeToString(DayDetail.serializer(), DayDetail(doses, cravings))
    }

    /** The cheer: was this dose taken with a full battery (and not the day's first)? */
    fun waitedForFull(recordsJson: String, doseId: String): Boolean {
        val d = data(recordsJson)
        val dose = d.doses.firstOrNull { it.id == doseId } ?: return false
        return com.baastiklabs.firewatch.core.engine.BatteryEngine.waitedForFull(d, dose, TimeZone.currentSystemDefault())
    }

    /** The craving forecast and receptor healing charts (Insights only, so not in every compute). */
    fun outlooks(recordsJson: String, nowMs: Double): String {
        jsTypeOf(tzModule)
        val d = data(recordsJson)
        val tz = TimeZone.currentSystemDefault()
        val now = nowMs.toLong()
        val f = com.baastiklabs.firewatch.core.engine.CravingForecast.outlook(d, now, tz)
        fun w(x: com.baastiklabs.firewatch.core.engine.CravingWindow) = WindowDto(x.from.toDouble(), x.to.toDouble(), x.peakAt.toDouble(), x.likelihood, x.strength)
        val forecast = ForecastDto(
            points = f.points.map { listOf(it.at.toDouble(), it.likelihood, it.strength, if (it.asleep) 1.0 else 0.0) },
            learning = f.learning, cravingsUsed = f.cravingsUsed, next = f.next?.let { w(it) }, windows = f.windows.map { w(it) },
            quietestAt = f.quietestAt?.toDouble(), hits = f.hits, tested = f.tested,
        )
        val r = com.baastiklabs.firewatch.core.engine.Receptors.outlook(d, now, tz)
        val receptors = r?.let { o ->
            fun pts(l: List<com.baastiklabs.firewatch.core.engine.ReceptorPoint>) = l.map { NamedValue(it.date.toString(), it.load) }
            ReceptorsDto(
                pts(o.history.takeLast(90)), pts(o.plan), pts(o.stay), o.todayLoad,
                o.clearAirOnPlan?.toString(), o.typicalOnPlan?.toString(), o.typicalIfStay?.toString(),
                com.baastiklabs.firewatch.core.engine.Receptors.TYPICAL,
            )
        }
        return FirewatchJson.encodeToString(OutlooksDto.serializer(), OutlooksDto(forecast, receptors))
    }

    /** Help articles, the welcome tour and "Why Firewatch works this way" (shared with Android). */
    fun help(): String = FirewatchJson.encodeToString(
        HelpDto.serializer(),
        HelpDto(com.baastiklabs.firewatch.core.Help.tour, com.baastiklabs.firewatch.core.Help.why, com.baastiklabs.firewatch.core.Help.articles),
    )

    fun encodeElement(json: String): String = FirewatchJson.parseToJsonElement(json).toString()

    @Suppress("unused")
    private fun keep(e: JsonElement) = e
}
