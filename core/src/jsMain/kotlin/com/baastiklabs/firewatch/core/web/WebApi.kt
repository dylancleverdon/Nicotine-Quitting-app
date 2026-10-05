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
import kotlinx.datetime.plus
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
@Serializable data class BatteryDto(val charge: Double, val state: String, val readyAt: Double?, val stretchMin: Double, val pullMin: Double, val fitsNow: String? = null, val closeToBed: Boolean = false)
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
@Serializable data class CravingView(val id: String, val at: Double, val intensity: Int, val name: String, val outcome: String, val result: String = "", val endedAt: Double?, val tags: List<String>)
@Serializable data class DayView(
    val date: String, val pieces: Double, val mg: Double, val doses: Int, val cravings: Int, val rodeOut: Int, val level: Int,
    val clearHours: Double, val quality: Double?, val estimated: Boolean, val hasRange: Boolean, val low: Double, val high: Double,
    val awakeHours: Double, val mouthMin: Double, val wakeToFirstMin: Double?, val spikeMg: Double, val labelMg: Double,
    val borrowedPieces: Double, val barcode: List<Boolean>, val doubleUps: Int, val volatility: Double = 0.0,
    /** Pieces by delivery method (product mix). */
    val kinds: Map<String, Double> = emptyMap(),
)
@Serializable data class PracticeDto(
    /** On right now: pieces, label, tier, untilBedtime; with practice net for the run and which day of it. */
    val active: RungDto?, val untilBedtime: Boolean, val netMin: Double?, val day: Int,
    /** Rungs that can be practiced now. */
    val allowed: List<RungDto>,
    val lighterOffer: RungDto?, val lighterTitle: String?, val lighterBody: String?,
    val workFrom: RungDto?, val workFromHours: Int, val workFromNet: Double,
    val lastSessionId: String?, val followUpId: String?,
    val explainer: String, val stopNote: String, val relapseNote: String,
)
@Serializable data class ClearAirDto(val active: Boolean, val daysFree: Int, val healing: Double?, val offer: Boolean)
@Serializable data class ChartsDto(
    val gapSizes: List<NamedValue>, val weekShape: List<NamedValue>, val dailyPeaks: List<NamedValue> = emptyList(),
    /** [week start, weekdays min or -1, weekends min or -1]. */
    val firstPiece: List<List<String>>,
    val longestGaps: List<NamedValue>, val cravingWeekly: List<NamedValue>,
    /** [date, timing min, size min]. */
    val netSplit: List<List<String>>,
    val kindsByHour: Map<String, List<Double>>,
    val steadyByMonth: List<NamedValue>,
    /** [date, pieces or "", level or ""]; empty unless there's been a step down. */
    val paceVsPlan: List<List<String>>,
    val practiceRuns: List<NamedValue>,
    val daysFree: List<NamedValue>,
)
@Serializable data class StepProgressDto(
    val held: Int, val needed: Int, val next: RungDto, val offered: Boolean, val unlocked: Boolean = false,
    val fraction: Double = 0.0, val heldHours: Double = 0.0, val neededHours: Double = 0.0, val todayOver: Boolean = false,
    val unlocksAt: Double? = null, val restartedOn: String? = null,
)
@Serializable data class ReviewDto(
    val date: String, val pieces: Double, val netMin: Double?, val volatility: Double, val mix: List<NamedValue>,
    val longestGapMin: Double?, val stacked: Int, val morningStretchMin: Double?, val tips: List<String>,
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
    /** Why the step-up offer is showing, in plain language. */
    val stepUpWhy: String?,
    val stepUpSameDay: Boolean,
    /** How cravings ended over the last 14 days: [title, count]. */
    val cravingEndings: List<NamedValue>,
    /** "Hide next piece timer": per waking day for the last 42 days [date, total, charging]. */
    val checks: List<NamedValue>,
    val checksToday: Int,
    /** The provisional first-week target (8 a day, firming up). */
    val early: Boolean,
    /** When the first-week target should firm up to this many pieces (the app writes it). */
    val earlyUpdate: Double?,
    val heldDays: Int,
    /** Last 42 full days: [date, pieces (scaled), target that day or -1]. */
    val held: List<List<String>>,
    val lighterThanStart: Double?,
    val daysOffSmokeAndVape: Int?,
    /** Dose preview per product id: minutes it would add to today's net (+ stretch, − pull). */
    val previews: Map<String, Double>,
    val steadyDays: Int,
    /** Today's waking day (ISO); before wake-up it's still yesterday. */
    val wakingToday: String,
    /** A steady-days milestone reached and not yet celebrated. */
    val steadyMilestone: Int?,
    val practicing: Boolean,
    /** After a practice day: "How was X pace?" */
    val practiceFollowUp: RungDto?,
    /** Taper forecast: [rung label, ISO date] steps, and what it's based on. */
    val taperSteps: List<NamedValue>,
    val taperBasis: String?,
    /** "Welcome back": the gap days to offer for back-dating (ISO dates), or null. */
    val welcomeBack: List<String>?,
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
    /** Minutes of morning stretch so far (no piece yet today), or null. */
    val morningStretch: Double?,
    /** Coaching tip for the Log tab (id, text), or null. */
    val tip: NamedValue?,
    val yesterday: ReviewDto?,
    /** Insights → Favourites card: label = chart id, extra = the fact. */
    val favourites: List<NamedValue> = emptyList(),
    val steadyExplainer: String,
    val netExplainer: String,
    val practice: PracticeDto,
    /** ISO date → logged / clear / unknown / ghost, from the first day to today. */
    val dayStates: Map<String, String>,
    /** ISO date → up / down (level changes, ▲ ▼ on the calendar). */
    val levelMarks: Map<String, String>,
    /** Level history, newest first: [text, 0, kind]. */
    val history: List<NamedValue>,
    /** Last 30 full days: [ISO date, 0, state]. */
    val recentStates: List<NamedValue>,
    val known7: Int,
    /** Days held at the current level in total (never resets). */
    val heldTotal: Int,
    val stepProgress: StepProgressDto?,
    val doubleUpsWeekly: List<NamedValue>,
    /** Average baseline day vs average day now (48 half-hours each), when there's enough data. */
    val thenCurve: List<Double>,
    val nowCurve: List<Double>,
    val unknownNote: String,
    val holdShortNote: String,
    val clearAir: ClearAirDto,
    val charts: ChartsDto,
)
@Serializable data class InsightsDto(
    val avoidedPieces: Double, val avoidedMg: Double, val money: Double, val winRate: Double?, val cravingMinutes: Double?,
    val heaviness: Double?, val taperPct: Double?, val taperText: String?, val journey: Double?, val arrivals: List<NamedValue>,
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
        val stepUpFull = if ((revealed || com.baastiklabs.firewatch.core.engine.Control.isEarly(d)) && stepDown == null) Coach.stepUp(d, now, tz) else null
        val stepUp = stepUpFull?.rung
        val todayDay = ins.days.lastOrNull()?.takeIf { it.date == today }
        // "Today" = the waking day (a 1 AM piece counts toward the night before).
        val wakingToday = com.baastiklabs.firewatch.core.Days.wakingDate(d, now, tz)
        val todayDoses = d.doses.filter { com.baastiklabs.firewatch.core.Days.wakingDate(d, it.at, tz) == wakingToday }
        val todayCravings = d.cravings.filter { com.baastiklabs.firewatch.core.Days.wakingDate(d, it.at, tz) == wakingToday }
        val ctl = com.baastiklabs.firewatch.core.engine.Control
        val steady = ctl.steadyDays(d, now, tz)
        val plan = ctl.taperPlan(d, now, tz)
        val w = Waking.day(d, today, tz)
        val prof = Coach.profile(d, now)
        val rec = ins.records()
        val (last, steps) = ins.clearAirTimeline()

        fun craving(c: com.baastiklabs.firewatch.core.model.Craving) = CravingView(
            c.id, c.at.toDouble(), c.intensity, CravingScale.level(c.intensity).name,
            Cravings.effectiveOutcome(d, c, now).name, Cravings.result(d, c, now, tz).title, c.endedAt?.toDouble(), c.tags,
        )

        val insights = InsightsDto(
            avoidedPieces = ins.piecesAvoided(), avoidedMg = ins.mgAvoided(), money = ins.moneySaved(),
            winRate = ins.cravingWinRate(), cravingMinutes = ins.averageCravingMinutes(), heaviness = ins.heaviness(),
            taperPct = ins.taperPercentPerWeek(), taperText = ins.taperSpeedText(), journey = ins.journey(),
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
            battery = battery?.let { BatteryDto(it.charge, it.state.name, it.readyAt?.toDouble(), it.stretchMinutesToday, it.pullMinutesToday, null, it.closeToBed) },
            stepDown = stepDown?.dto(),
            stepDownNote = listOfNotNull(readiness?.takeIf { it.confident }?.let {
                if (it.ready) "From your cravings, the next rung should feel like about a ${it.predictedNext.toInt()} out of 10, and you ride out ${it.capacity}s."
                else "Your cravings suggest the next rung may feel like a ${it.predictedNext.toInt()}, above the ${it.capacity} you usually ride out. Holding a bit longer is fine too."
            }, com.baastiklabs.firewatch.core.engine.Checks.trendNote(d, today, tz)).joinToString(" ").ifBlank { null },
            stepUp = stepUp?.dto(),
            stepUpWhy = stepUpFull?.why,
            stepUpSameDay = stepUpFull?.sameDay ?: false,
            cravingEndings = ins.cravingEndings().entries.sortedBy { it.key.ordinal }.map { NamedValue(it.key.title, it.value.toDouble(), if (it.key.win) "win" else "") },
            checks = com.baastiklabs.firewatch.core.engine.Checks.history(d, today, tz, 42).map { NamedValue(it.date.toString(), it.total.toDouble(), it.charging.toString()) },
            previews = if (target != null) d.homeProducts.mapNotNull { p ->
                com.baastiklabs.firewatch.core.engine.BatteryEngine.preview(d, p, if (target.pieces > 0) target.pieces else 1.0 / 3.0, now, tz, lastActivityMs.toLong())?.let { p.id to it }
            }.toMap() else emptyMap(),
            steadyDays = steady,
            morningStretch = com.baastiklabs.firewatch.core.engine.BatteryEngine.morningStretch(d, now, tz)?.takeIf { target != null },
            tip = com.baastiklabs.firewatch.core.engine.Coaching.bridgeTip(d, target?.pieces?.let { if (it > 0) it else 1.0 / 3.0 }, now, tz, lastActivityMs.toLong())?.let { NamedValue(it.id, 0.0, it.text) },
            yesterday = com.baastiklabs.firewatch.core.engine.Coaching.yesterday(d, now, tz)?.let { reviewDto(it) },
            favourites = com.baastiklabs.firewatch.core.engine.Favourites.facts(d, now, tz).map { NamedValue(it.id, 0.0, it.text) },
            practice = run {
                val P = com.baastiklabs.firewatch.core.engine.Practice
                val st = P.status(d, now, tz)
                val lo = if (revealed) P.lighterOffer(d, now, tz) else null
                val wf = if (revealed) P.workFromOffer(d, now, tz) else null
                PracticeDto(
                    active = st?.rung?.dto(), untilBedtime = st?.session?.untilBedtime ?: d.settings.practiceUntilBedtime,
                    netMin = st?.netMin, day = st?.day ?: 0,
                    allowed = P.allowedRungs(d, now, tz).map { it.dto() },
                    lighterOffer = lo?.dto(),
                    lighterTitle = lo?.let { com.baastiklabs.firewatch.core.Help.lighterOfferTitle(it.label) },
                    lighterBody = lo?.let { m -> target?.let { com.baastiklabs.firewatch.core.Help.lighterOfferBody(it.label, m.tier.title) } },
                    workFrom = wf?.rung?.dto(), workFromHours = ((wf?.coveredMin ?: 0.0) / 60).toInt(), workFromNet = wf?.netMin ?: 0.0,
                    lastSessionId = d.practices.lastOrNull()?.id, followUpId = P.followUp(d, now, tz)?.id,
                    explainer = com.baastiklabs.firewatch.core.Help.PRACTICE_EXPLAINER,
                    stopNote = com.baastiklabs.firewatch.core.Help.PRACTICE_STOP_NOTE,
                    relapseNote = com.baastiklabs.firewatch.core.Help.PRACTICE_RELAPSE_NOTE,
                )
            },
            dayStates = run {
                val start = com.baastiklabs.firewatch.core.Days.startDate(d, tz)
                if (start == null) emptyMap() else generateSequence(start) { it.plus(1, kotlinx.datetime.DateTimeUnit.DAY) }.takeWhile { it <= today }
                    .associate { it.toString() to com.baastiklabs.firewatch.core.Days.state(d, it, tz, now).name.lowercase() }
            },
            levelMarks = com.baastiklabs.firewatch.core.engine.Practice.levelMarks(d, tz),
            history = com.baastiklabs.firewatch.core.engine.Practice.history(d, now, tz) { "${it.dayOfMonth} ${it.month.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() }}" }
                .map { NamedValue(it.text, 0.0, it.kind) },
            recentStates = Progress.recentStates(d, now, tz, 30).map { NamedValue(it.first.toString(), 0.0, it.second.name.lowercase()) },
            known7 = Progress.knownDays(d, today, tz),
            heldTotal = target?.let { ctl.heldByRung(d, now, tz)[it.pieces]?.first } ?: 0,
            stepProgress = if (ctl.isEarly(d)) null else Progress.stepDownProgress(d, now, tz)?.let {
                StepProgressDto(it.held, it.needed, it.next.dto(), it.ready && Progress.stepDownOffer(d, now, tz) != null, it.unlocked, it.fraction, it.heldMinutes / 60, it.neededMinutes / 60, it.todayOver, it.unlocksAt?.toDouble(), it.restartedOn?.toString())
            },
            doubleUpsWeekly = ins.doubleUpsPerWeek().map { NamedValue(it.first.toString(), it.second.toDouble()) },
            thenCurve = if (ins.baselineComplete && ins.fullDays.size > ins.baselineDays.size + 3) ins.typicalCurve(ins.baselineDays) else emptyList(),
            nowCurve = if (ins.baselineComplete && ins.fullDays.size > ins.baselineDays.size + 3) ins.typicalCurve(ins.lastDays(7)) else emptyList(),
            clearAir = com.baastiklabs.firewatch.core.engine.ClearAir.let { CA ->
                ClearAirDto(CA.active(d), CA.daysFree(d, now, tz), if (CA.active(d)) CA.receptorHealing(d, now, tz) else null, revealed && CA.offer(d, now, tz))
            },
            charts = ChartsDto(
                gapSizes = ins.gapSizes().map { NamedValue(it.first, it.second.toDouble()) },
                dailyPeaks = ins.dailyPeaks().map { NamedValue(it.first.toString(), it.second) },
                weekShape = ins.weekShape().map { NamedValue(it.first, it.second) },
                firstPiece = ins.firstPieceWeekdaysVsWeekends().map { listOf(it.first.toString(), (it.second ?: -1.0).toString(), (it.third ?: -1.0).toString()) },
                longestGaps = ins.longestGaps().map { NamedValue(it.first.toString(), it.second ?: 0.0) },
                cravingWeekly = ins.cravingStrengthWeekly().map { NamedValue(it.first.toString(), it.second, it.third.toString()) },
                netSplit = ins.netSplit().map { listOf(it.first.toString(), it.second.toString(), it.third.toString()) },
                kindsByHour = ins.kindsByHour().mapKeys { it.key.name }.mapValues { it.value.toList() },
                steadyByMonth = ins.steadyByMonth().map { NamedValue(it.first, it.second.toDouble()) },
                paceVsPlan = ins.paceVsPlan().first.map { listOf(it.first.toString(), it.second?.toString() ?: "", it.third?.toString() ?: "") },
                practiceRuns = com.baastiklabs.firewatch.core.engine.Practice.runs(d, now, tz).map { NamedValue(it.rung.tier.title, it.netMin, it.hours.toString()) },
                daysFree = com.baastiklabs.firewatch.core.engine.ClearAir.daysFreeSeries(d, now, tz).map { NamedValue(it.first.toString(), it.second.toDouble()) },
            ),
            unknownNote = com.baastiklabs.firewatch.core.Help.UNKNOWN_DAYS_NOTE,
            holdShortNote = com.baastiklabs.firewatch.core.Help.HOLD_SHORT_NOTE,
            steadyExplainer = com.baastiklabs.firewatch.core.Help.STEADY_EXPLAINER,
            netExplainer = com.baastiklabs.firewatch.core.Help.NET_EXPLAINER,
            wakingToday = wakingToday.toString(),
            steadyMilestone = ctl.newSteadyMilestone(d, steady),
            practicing = com.baastiklabs.firewatch.core.engine.Practice.active(d, now, tz) != null,
            practiceFollowUp = ctl.practiceFollowUp(d, now, tz)?.dto(),
            taperSteps = plan?.steps?.map { NamedValue(it.rung.label, 0.0, it.date.toString()) } ?: emptyList(),
            taperBasis = plan?.basis,
            early = com.baastiklabs.firewatch.core.engine.Control.isEarly(d),
            earlyUpdate = com.baastiklabs.firewatch.core.engine.Control.earlyTargetUpdate(d, now, tz),
            heldDays = com.baastiklabs.firewatch.core.engine.Control.heldDays(d, now, tz),
            held = com.baastiklabs.firewatch.core.engine.Control.heldSeries(d, now, tz).map { (date, p, t) -> listOf(date.toString(), p.toString(), (t ?: -1.0).toString()) },
            lighterThanStart = com.baastiklabs.firewatch.core.engine.Control.lighterThanStart(d, now, tz),
            daysOffSmokeAndVape = com.baastiklabs.firewatch.core.engine.Control.daysOffSmokeAndVape(d, now, tz),
            welcomeBack = com.baastiklabs.firewatch.core.engine.Control.welcomeBackDays(d, now, tz)?.map { it.toString() },
            checksToday = com.baastiklabs.firewatch.core.engine.Checks.day(d, com.baastiklabs.firewatch.core.engine.BatteryEngine.currentDay(d, now, tz).first.date, tz).total,
            headsUps = Progress.headsUps(d, now, tz).map { it.message },
            qualityScore = Quality.of(todayDoses, ref),
            qualityLabel = Quality.of(todayDoses, ref)?.let { Quality.label(it) },
            swapTip = com.baastiklabs.firewatch.core.engine.Coaching.swapTip(d, todayDoses),
            activeCraving = Cravings.active(d, now)?.let { craving(it) },
            todayPieces = todayDoses.sumOf { d.piecesOf(it) },
            todayMg = todayDoses.sumOf { it.absorbedMg() },
            todayCravings = todayCravings.size,
            todayRodeOut = todayCravings.count { Cravings.effectiveOutcome(d, it, now) == com.baastiklabs.firewatch.core.model.CravingOutcome.RODE_OUT },
            lastDoseAt = d.doses.maxOfOrNull { it.at }?.toDouble(),
            todayDoses = todayDoses.sortedByDescending { it.at }.map { DoseView(it.id, it.at.toDouble(), it.productName, d.piecesOf(it), it.absorbedMg(), it.estimated, it.tags, it.kind.name) },
            todayCravingList = todayCravings.map { craving(it) },
            wave = ins.todayCurve(10).map { listOf(it.first.toDouble(), it.second) },
            wakeAt = w.wakeAt.toDouble(),
            sleepAt = w.sleepAt.toDouble(),
            typical = ins.typicalCurve(),
            days = ins.days.map { s ->
                DayView(
                    s.date.toString(), s.pieces, s.absorbedMg, s.doses.size, s.cravings, s.rodeOut, CalendarScale.level(s.pieces),
                    s.clearHours, s.quality, s.doses.any { it.estimated }, s.hasRange, s.lowPieces, s.highPieces, s.awakeHours,
                    s.mouthMinutes, s.wakeToFirstMin, s.spikeMg, s.labelMg, s.borrowedPieces, s.barcode, s.doubleUps, s.volatility,
                    s.kindPieces.mapKeys { it.key.name },
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

    private fun reviewDto(r: com.baastiklabs.firewatch.core.engine.DayReview) =
        ReviewDto(r.date.toString(), r.pieces, r.netMin, r.volatility, r.mix.map { NamedValue(com.baastiklabs.firewatch.core.engine.Coaching.kindName(it.first), it.second) },
            r.longestGapMin, r.stacked, r.morningStretchMin, r.tips)

    /** Charts that can be starred: label = id, extra = "title|section". */
    fun favouriteCharts(): String = FirewatchJson.encodeToString(ListSerializer(NamedValue.serializer()),
        com.baastiklabs.firewatch.core.engine.Favourites.charts.map { NamedValue(it.id, 0.0, it.title + "|" + it.section) })

    /** Full day details for the calendar's day view. */
    fun day(recordsJson: String, dayIso: String, nowMs: Double): String {
        val d = data(recordsJson)
        val tz = TimeZone.currentSystemDefault()
        val date = LocalDate.parse(dayIso)
        val ref = d.referenceMg
        val doses = d.doses.filter { com.baastiklabs.firewatch.core.Days.wakingDate(d, it.at, tz) == date }.sortedBy { it.at }
            .map { DoseView(it.id, it.at.toDouble(), it.productName, d.piecesOf(it), it.absorbedMg(), it.estimated, it.tags, it.kind.name) }
        val cravings = d.cravings.filter { com.baastiklabs.firewatch.core.Days.wakingDate(d, it.at, tz) == date }.map {
            CravingView(it.id, it.at.toDouble(), it.intensity, CravingScale.level(it.intensity).name,
                Cravings.effectiveOutcome(d, it, nowMs.toLong()).name, Cravings.result(d, it, nowMs.toLong(), tz).title, it.endedAt?.toDouble(), it.tags)
        }
        val w = Waking.day(d, date, tz)
        val dayIns = Insights(d, tz, nowMs.toLong())
        val wave = dayIns.dayCurve(date, 10).map { listOf(it.first.toDouble(), it.second) }
        val vol = dayIns.volatilityCurve(date, 10).map { listOf(it.first.toDouble(), it.second) }
        val next = Waking.day(d, date.plus(1, kotlinx.datetime.DateTimeUnit.DAY), tz).wakeAt
        val sleeps = d.sleepEvents.filter { it.at in w.wakeAt - 6 * 3_600_000L until next }
            .map { NamedValue(if (it.kind == com.baastiklabs.firewatch.core.model.SleepKind.WAKE) "Woke up" else "Went to sleep", it.at.toDouble(), it.id) }
        val levels = d.rungChanges.withIndex().filter { (i, rc) -> i > 0 && com.baastiklabs.firewatch.core.Days.wakingDate(d, rc.at, tz) == date }.map { (i, rc) ->
            val from = Ladder.rung(d.rungChanges[i - 1].pieces); val to = Ladder.rung(rc.pieces)
            if (from.tier == to.tier) "${from.tier.title} ${Ladder.piecesText(from.pieces)} → ${Ladder.piecesText(to.pieces)}"
            else "${from.tier.title} ${Ladder.piecesText(from.pieces)} → ${to.label}"
        }
        val state = com.baastiklabs.firewatch.core.Days.state(d, date, tz, nowMs.toLong()).name.lowercase()
        @Serializable data class DayDetail(
            val doses: List<DoseView>, val cravings: List<CravingView>, val wave: List<List<Double>>, val volatility: List<List<Double>>,
            val wakeAt: Double, val sleepAt: Double, val sleeps: List<NamedValue>, val levels: List<String>, val state: String,
            val pieces: Double, val mg: Double, val review: ReviewDto?,
        )
        return FirewatchJson.encodeToString(DayDetail.serializer(), DayDetail(doses, cravings, wave, vol, w.wakeAt.toDouble(), w.sleepAt.toDouble(),
            sleeps, levels, state, doses.sumOf { it.pieces }, doses.sumOf { it.mg },
            if (date < dayIns.today) com.baastiklabs.firewatch.core.engine.Coaching.review(d, date, nowMs.toLong(), tz)?.let { reviewDto(it) } else null))
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

    /** Suggestion / bug report: the form URL and encoded body (shared with Android). */
    fun feedbackUrl(): String = com.baastiklabs.firewatch.core.Feedback.FORM_URL
    fun feedbackBody(type: String, suggestion: String, details: String, name: String, appInfo: String): String =
        com.baastiklabs.firewatch.core.Feedback.encode(com.baastiklabs.firewatch.core.Feedback.fields(type, suggestion, details, name, appInfo))
    fun feedbackPrivacy(): String = com.baastiklabs.firewatch.core.Feedback.PRIVACY_NOTE

    /** Travel: the question to ask for the browser's current zone, as JSON (or "null"). */
    fun travelPrompt(recordsJson: String, zone: String, nowMs: Double): String {
        val p = com.baastiklabs.firewatch.core.engine.Travel.prompt(data(recordsJson), zone, nowMs.toLong()) ?: return "null"
        return FirewatchJson.encodeToString(NamedValue.serializer(), NamedValue(p.to, p.hoursAhead, com.baastiklabs.firewatch.core.engine.Travel.describe(p)))
    }

    /** Travel: settings after "first seen" / "use local" / "keep home" (JSON settings in and out; "null" = no change). */
    fun travelAnswer(settingsJson: String, zone: String, nowMs: Double, answer: String): String {
        val s = FirewatchJson.decodeFromString(com.baastiklabs.firewatch.core.model.Settings.serializer(), settingsJson)
        val T = com.baastiklabs.firewatch.core.engine.Travel
        val out = when (answer) {
            "first" -> T.firstSeen(s, zone, nowMs.toLong())
            "local" -> T.useLocal(s, zone, nowMs.toLong())
            else -> T.keepHome(s, zone)
        } ?: return "null"
        return FirewatchJson.encodeToString(com.baastiklabs.firewatch.core.model.Settings.serializer(), out)
    }

    /** Colour themes (id, name, feel) and one palette (shared with Android). */
    fun themes(): String = FirewatchJson.encodeToString(ListSerializer(com.baastiklabs.firewatch.core.Themes.Theme.serializer()), com.baastiklabs.firewatch.core.Themes.all)
    fun palette(id: String, mode: String, systemDark: Boolean, trueBlack: Boolean, calm: Boolean, colourBlind: Boolean): String =
        FirewatchJson.encodeToString(com.baastiklabs.firewatch.core.Themes.Palette.serializer(),
            com.baastiklabs.firewatch.core.Themes.palette(id, mode, systemDark, trueBlack, calm, colourBlind))

    /** Help articles, the welcome tour and "Why Firewatch works this way" (shared with Android). */
    fun help(): String = FirewatchJson.encodeToString(
        HelpDto.serializer(),
        HelpDto(com.baastiklabs.firewatch.core.Help.tour, com.baastiklabs.firewatch.core.Help.why, com.baastiklabs.firewatch.core.Help.articles),
    )

    fun encodeElement(json: String): String = FirewatchJson.parseToJsonElement(json).toString()

    @Suppress("unused")
    private fun keep(e: JsonElement) = e
}
