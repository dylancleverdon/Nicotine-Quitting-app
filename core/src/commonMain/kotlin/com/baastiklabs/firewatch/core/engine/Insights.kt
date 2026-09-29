package com.baastiklabs.firewatch.core.engine

import com.baastiklabs.firewatch.core.Baseline
import com.baastiklabs.firewatch.core.BaselineStatus
import com.baastiklabs.firewatch.core.Cravings
import com.baastiklabs.firewatch.core.absorbedMg
import com.baastiklabs.firewatch.core.localDate
import com.baastiklabs.firewatch.core.model.CravingOutcome
import com.baastiklabs.firewatch.core.model.DefaultProducts
import com.baastiklabs.firewatch.core.model.Dose
import com.baastiklabs.firewatch.core.model.Duration
import com.baastiklabs.firewatch.core.model.ProductKind
import com.baastiklabs.firewatch.core.model.SpeedProfile
import com.baastiklabs.firewatch.core.pieces
import com.baastiklabs.firewatch.core.records.FirewatchData
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/** Everything about one waking day that the insights need. */
data class DayStat(
    val date: LocalDate,
    val wakeAt: Long,
    val sleepAt: Long,
    val doses: List<Dose>,
    val pieces: Double,
    val scaledPieces: Double,
    val absorbedMg: Double,
    val labelMg: Double,
    val spikeMg: Double,
    val borrowedPieces: Double,
    val lowPieces: Double,
    val highPieces: Double,
    val hasRange: Boolean,
    val cravings: Int,
    val rodeOut: Int,
    val mouthMinutes: Double,
    val clearHours: Double,
    val awakeHours: Double,
    /** Minutes from waking to the first dose (null = none that day). */
    val wakeToFirstMin: Double?,
    val doubleUps: Int,
    val quality: Double?,
    val costSpent: Double,
    /** 48 half-hour cells: true = nicotine in the system. */
    val barcode: List<Boolean>,
    val kindPieces: Map<ProductKind, Double>,
    /** Average one-hour swing (mg) across the waking day: nicotine volatility. */
    val volatility: Double = 0.0,
    /** Longest gap between two timed doses (minutes), and the dose that started it. */
    val longestGapMin: Double? = null,
    val longestGapStart: Dose? = null,
)

data class Badge(val title: String, val detail: String, val earnedOn: LocalDate)

data class Forecast(val rung: Rung, val date: LocalDate?)

class Insights(private val data: FirewatchData, private val tz: TimeZone, private val now: Long) {
    private val ref = data.referenceMg
    private val MIN = 60_000L
    val today: LocalDate = now.localDate(tz)
    val firstDate: LocalDate? = data.doses.minOfOrNull { it.at }?.localDate(tz)

    /** All days from the first log to today. */
    val days: List<DayStat> by lazy {
        val first = firstDate ?: return@lazy emptyList()
        generateSequence(first) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it <= today }.map { day(it) }.toList()
    }

    val baselineDays: List<DayStat> by lazy {
        val first = firstDate ?: return@lazy emptyList()
        days.filter { first.daysUntil(it.date) < Baseline.DAYS }
    }

    val baselineComplete: Boolean get() = Baseline.status(data, today, tz) is BaselineStatus.Complete

    /** Full days only (today is still in progress). */
    val fullDays: List<DayStat> get() = days.filter { it.date < today }

    fun lastDays(n: Int): List<DayStat> = fullDays.takeLast(n)

    /** One waking day on its own (cheaper than [days] when only one is needed). */
    fun dayStat(date: LocalDate): DayStat = day(date)

    private fun day(date: LocalDate): DayStat {
        val w = Waking.day(data, date, tz)
        val nextWake = Waking.day(data, date.plus(1, DateTimeUnit.DAY), tz).wakeAt
        val doses = data.doses.filter { it.at >= w.wakeAt && it.at < nextWake }
        val pieces = doses.sumOf { data.piecesOf(it) }
        val awake = w.awakeMinutes
        val cravings = data.cravings.filter { it.at >= w.wakeAt && it.at < nextWake }
        val clearMinutes = run {
            var clear = 0.0
            val relevant = data.doses.filter { it.at in (w.wakeAt - 24 * 60 * MIN)..w.sleepAt }
            var t = w.wakeAt
            val end = minOf(w.sleepAt, now)
            while (t < end) {
                if (Kinetics.level(relevant, t) < Kinetics.CLEAR_THRESHOLD_MG) clear += 10
                t += 10 * MIN
            }
            clear
        }
        val barcode = (0 until 48).map { i ->
            val t = w.wakeAt - 2 * 60 * MIN + i * 30 * MIN
            t <= now && Kinetics.level(data.doses.filter { it.at in (t - 24 * 60 * MIN)..t }, t) >= Kinetics.CLEAR_THRESHOLD_MG
        }
        val sorted = doses.sortedBy { it.at }
        val timedToday = sorted.filter { !it.estimated }
        val doubleUps = timedToday.zipWithNext().count { (a, b) -> b.at - a.at < Kinetics.peakMinutes(a) * MIN }
        val prices = data.productsById
        val volatility = run {
            val end = minOf(w.sleepAt, now)
            if (end <= w.wakeAt) 0.0
            else Kinetics.swing(Kinetics.curve(data.doses.filter { it.at in (w.wakeAt - 13 * 60 * MIN)..end }, w.wakeAt - 60 * MIN, end, 5))
                .filter { it.first >= w.wakeAt }.map { it.second }.average()
        }
        val gap = timedToday.zipWithNext().maxByOrNull { (a, b) -> b.at - a.at }
        return DayStat(
            date = date,
            wakeAt = w.wakeAt,
            sleepAt = w.sleepAt,
            doses = sorted,
            pieces = pieces,
            scaledPieces = if (awake > 0) pieces * Ladder.WAKING_MINUTES / awake else pieces,
            absorbedMg = doses.sumOf { it.absorbedMg() },
            labelMg = doses.sumOf { it.labelMg * it.multiplier },
            spikeMg = doses.filter { it.speed == SpeedProfile.SPIKE }.sumOf { it.absorbedMg() },
            borrowedPieces = doses.filter { it.borrowed }.sumOf { data.piecesOf(it) },
            lowPieces = doses.sumOf { (it.rangeLowMg ?: it.absorbedMg()) / ref },
            highPieces = doses.sumOf { (it.rangeHighMg ?: it.absorbedMg()) / ref },
            hasRange = doses.any { it.rangeLowMg != null },
            cravings = cravings.size,
            rodeOut = cravings.count { Cravings.effectiveOutcome(data, it, now) == CravingOutcome.RODE_OUT },
            mouthMinutes = doses.sumOf { mouthMinutes(it) },
            clearHours = clearMinutes / 60.0,
            awakeHours = awake / 60.0,
            wakeToFirstMin = timedToday.firstOrNull()?.let { (it.at - w.wakeAt) / 60_000.0 },
            doubleUps = doubleUps,
            quality = Quality.of(doses, ref),
            costSpent = doses.sumOf { (prices[it.productId]?.unitPrice ?: 0.0) * it.multiplier },
            barcode = barcode,
            kindPieces = doses.groupBy { it.kind }.mapValues { (_, v) -> v.sumOf { data.piecesOf(it) } },
            volatility = volatility,
            longestGapMin = gap?.let { (a, b) -> (b.at - a.at) / 60_000.0 },
            longestGapStart = gap?.first,
        )
    }

    private fun mouthMinutes(d: Dose): Double {
        if (!DefaultProducts.isOral(d.kind)) return 0.0
        val full = if (d.kind == ProductKind.POUCH) 40.0 else 30.0
        return full * when (d.duration) { Duration.FULL -> 1.0; Duration.HALF -> 0.5; Duration.QUICK -> 0.25 } * d.multiplier.coerceAtLeast(1.0)
    }

    // ---- Today ----

    /** The blood-level curve for any waking day (the day stepper on day charts). */
    fun dayCurve(date: LocalDate, stepMin: Int = 5): List<Pair<Long, Double>> {
        if (date == today) return todayCurve(stepMin)
        val w = Waking.day(data, date, tz)
        return Kinetics.curve(data.doses, w.wakeAt - 2 * 60 * MIN, w.sleepAt + 60 * MIN, stepMin)
    }

    /** Nicotine volatility along a day's wave: the one-hour swing at each point (mg). */
    fun volatilityCurve(date: LocalDate, stepMin: Int = 5): List<Pair<Long, Double>> {
        val curve = dayCurve(date, stepMin)
        if (curve.isEmpty()) return curve
        val lead = Kinetics.curve(data.doses, curve.first().first - 60 * MIN, curve.first().first - stepMin * MIN, stepMin)
        return Kinetics.swing(lead + curve).drop(lead.size)
    }

    /** Daily volatility: each full day's average one-hour swing (mg). */
    fun dailyVolatility(): List<Pair<LocalDate, Double>> = fullDays.map { it.date to it.volatility }

    fun todayCurve(stepMin: Int = 5): List<Pair<Long, Double>> {
        val w = Waking.day(data, today, tz)
        val from = minOf(w.wakeAt - 2 * 60 * MIN, now)
        val to = max(w.sleepAt, now)
        return Kinetics.curve(data.doses, from, to, stepMin)
    }

    /** A typical day: average level at each half hour of the day across [fromDays] (default: last 14 full days). */
    fun typicalCurve(fromDays: List<DayStat>? = null): List<Double> {
        val source = fromDays ?: lastDays(14)
        if (source.isEmpty()) return emptyList()
        return (0 until 48).map { slot ->
            source.map { d ->
                val ms = d.date.atStartOfDayIn(tz).toEpochMilliseconds() + slot * 30 * MIN
                Kinetics.level(data.doses.filter { it.at in (ms - 24 * 60 * MIN)..ms }, ms)
            }.average()
        }
    }

    // ---- Trends ----

    fun sevenDayAverage(index: Int): Double {
        val from = max(0, index - 6)
        return days.subList(from, index + 1).map { it.scaledPieces }.average()
    }

    /** Average minutes between doses (awake gaps), per week, oldest first. */
    fun weeklyGaps(): List<Pair<LocalDate, Double>> = days.chunked(7).mapNotNull { week ->
        val gaps = week.flatMap { d -> d.doses.filter { !it.estimated }.zipWithNext().map { (a, b) -> (b.at - a.at) / 60_000.0 } }
        if (gaps.isEmpty()) null else week.first().date to gaps.average()
    }

    /** Tier over time: measured rung at the end of each week. */
    fun tierStaircase(): List<Pair<LocalDate, Rung>> = days.chunked(7).mapNotNull { week ->
        val avg = week.map { it.scaledPieces }.average()
        if (week.isEmpty()) null else week.last().date to Ladder.measured(avg)
    }

    // ---- Patterns ----

    /** [dayOfWeek 0=Mon..6][hour 0..23] -> pieces. */
    fun heatmap(): Array<DoubleArray> {
        val grid = Array(7) { DoubleArray(24) }
        data.doses.filter { !it.estimated }.forEach { d ->
            val dt = Instant.fromEpochMilliseconds(d.at).toLocalDateTime(tz)
            grid[dt.dayOfWeek.ordinal][dt.hour] += data.piecesOf(d)
        }
        return grid
    }

    fun triggerCounts(): List<Pair<String, Int>> =
        data.doses.flatMap { it.tags }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }

    /** For each trigger: of its last 10 appearances, how many passed without nicotine. */
    fun beatenTriggers(): List<Triple<String, Int, Int>> {
        val events = data.doses.flatMap { d -> d.tags.map { Triple(it, d.at, false) } } +
            data.cravings.flatMap { c ->
                val beat = Cravings.effectiveOutcome(data, c, now) == CravingOutcome.RODE_OUT
                c.tags.map { Triple(it, c.at, beat) }
            }
        return events.groupBy { it.first }.map { (tag, list) ->
            val last = list.sortedBy { it.second }.takeLast(10)
            Triple(tag, last.count { it.third }, last.size)
        }.sortedByDescending { it.second.toDouble() / it.third }
    }

    data class Comparison(val label: String, val a: Double, val b: Double)

    fun comparisons(): List<Comparison> {
        val last7 = lastDays(7)
        val prev7 = fullDays.dropLast(7).takeLast(7)
        val out = mutableListOf<Comparison>()
        if (last7.isNotEmpty() && prev7.isNotEmpty()) {
            out += Comparison("This week vs last (pieces a day)", last7.map { it.pieces }.average(), prev7.map { it.pieces }.average())
        }
        val recent = lastDays(28)
        val weekdays = recent.filter { it.date.dayOfWeek != DayOfWeek.SATURDAY && it.date.dayOfWeek != DayOfWeek.SUNDAY }
        val weekends = recent - weekdays.toSet()
        if (weekdays.isNotEmpty() && weekends.isNotEmpty()) {
            out += Comparison("Weekdays vs weekends (pieces a day)", weekdays.map { it.pieces }.average(), weekends.map { it.pieces }.average())
        }
        return out
    }

    // ---- Going up ----

    val baselineAverage: Double? get() = baselineDays.takeIf { baselineComplete && it.isNotEmpty() }?.map { it.pieces }?.average()

    /** Pieces not taken since the baseline week, compared with the baseline pace. */
    fun piecesAvoided(): Double {
        val base = baselineAverage ?: return 0.0
        return fullDays.drop(baselineDays.size).sumOf { (base - it.pieces) }.coerceAtLeast(0.0)
    }

    fun mgAvoided(): Double = piecesAvoided() * ref

    fun moneySaved(): Double {
        if (!baselineComplete) return 0.0
        val base = baselineDays.map { it.costSpent }.average()
        return fullDays.drop(baselineDays.size).sumOf { base - it.costSpent }.coerceAtLeast(0.0)
    }

    /** Tins/boxes not bought, from the product D used most in the baseline. */
    fun packsNotBought(): Pair<Double, String>? {
        val main = baselineDays.flatMap { it.doses }.groupBy { it.productId }.maxByOrNull { it.value.size }?.key ?: return null
        val product = data.productsById[main] ?: return null
        if (product.unitsPerPack <= 0) return null
        val baseUnits = baselineDays.flatMap { it.doses }.count { it.productId == main }.toDouble() / baselineDays.size
        val nowUnits = fullDays.drop(baselineDays.size).sumOf { d -> d.doses.count { it.productId == main }.toDouble() }
        val avoided = (baseUnits * fullDays.drop(baselineDays.size).size - nowUnits).coerceAtLeast(0.0)
        return avoided / product.unitsPerPack to product.name
    }

    /** How cravings ended over the last [days] days (in-progress ones left out). */
    fun cravingEndings(days: Int = 14): Map<com.baastiklabs.firewatch.core.CravingResult, Int> =
        data.cravings.filter { now - it.at < days * 24 * 60 * MIN }
            .map { com.baastiklabs.firewatch.core.Cravings.result(data, it, now, tz) }
            .filter { it != com.baastiklabs.firewatch.core.CravingResult.PENDING }
            .groupingBy { it }.eachCount()

    fun cravingWinRate(): Double? {
        val resolved = data.cravings.map { Cravings.effectiveOutcome(data, it, now) }.filter { it != CravingOutcome.OPEN }
        return if (resolved.isEmpty()) null else resolved.count { it == CravingOutcome.RODE_OUT }.toDouble() / resolved.size
    }

    fun averageCravingMinutes(): Double? =
        data.cravings.mapNotNull { c -> c.endedAt?.let { (it - c.at) / 60_000.0 } }.filter { it in 0.0..240.0 }.takeIf { it.isNotEmpty() }?.average()

    /** Minutes from last dose one night to first dose the next morning, per day. */
    fun overnightGaps(): List<Pair<LocalDate, Double>> = days.zipWithNext().mapNotNull { (a, b) ->
        val last = a.doses.lastOrNull { !it.estimated } ?: return@mapNotNull null
        val first = b.doses.firstOrNull { !it.estimated } ?: return@mapNotNull null
        b.date to (first.at - last.at) / 60_000.0
    }

    // ---- Going down ----

    /** A heaviness score from time-to-first-use and amount, the two things dependence tests weight most (0–6). */
    fun heaviness(d: List<DayStat> = lastDays(7)): Double? {
        if (d.isEmpty()) return null
        val ttf = d.mapNotNull { it.wakeToFirstMin }.takeIf { it.isNotEmpty() }?.average() ?: 999.0
        val first = when { ttf <= 5 -> 3; ttf <= 30 -> 2; ttf <= 60 -> 1; else -> 0 }
        val perDay = d.map { it.pieces }.average()
        val amount = when { perDay > 30 -> 3; perDay > 20 -> 2; perDay > 10 -> 1; else -> 0 }
        return (first + amount).toDouble()
    }

    fun backgroundSeries(): List<Pair<LocalDate, Double>> = days.map { d ->
        d.date to Kinetics.background(data.doses, minOf(d.sleepAt, now))
    }

    fun doubleUpsPerWeek(): List<Pair<LocalDate, Int>> = days.chunked(7).map { w -> w.first().date to w.sumOf { it.doubleUps } }

    // ---- Forecasts ----

    /** Average % drop per week, from a fitted line through the last 28 days (positive = going down). */
    fun taperPercentPerWeek(): Double? {
        val pts = lastDays(28).mapIndexedNotNull { i, d -> if (d.pieces > 0.05) i.toDouble() to ln(d.scaledPieces) else null }
        if (pts.size < 7) return null
        val mx = pts.map { it.first }.average()
        val my = pts.map { it.second }.average()
        val sxx = pts.sumOf { (it.first - mx) * (it.first - mx) }
        if (sxx <= 0) return null
        val slope = pts.sumOf { (it.first - mx) * (it.second - my) } / sxx
        return (1 - exp(slope * 7)) * 100
    }

    /** Taper speed in plain words: a win when lighter, neutral otherwise (no spin). */
    fun taperSpeedText(): String? {
        val p = taperPercentPerWeek() ?: return null
        val r = kotlin.math.round(kotlin.math.abs(p)).toInt()
        return when {
            r < 1 -> "About level (last 4 weeks)"
            p > 0 -> "About $r% lighter each week (last 4 weeks)"
            else -> "About $r% more each week (last 4 weeks)"
        }
    }

    /** When D reaches each tier (and Clear Air) at the current pace. */
    fun arrivals(): List<Forecast> {
        val rate = taperPercentPerWeek() ?: return emptyList()
        val current = lastDays(7).map { it.scaledPieces }.average().takeIf { !it.isNaN() } ?: return emptyList()
        val tiers = Tier.entries.filter { it != Tier.WILDFIRE }
        return tiers.map { tier ->
            val targetPieces = when (tier) {
                Tier.CLEAR_AIR -> 0.2
                else -> Ladder.rungs.first { it.tier == tier }.pieces
            }
            val rung = if (tier == Tier.CLEAR_AIR) Ladder.clearAir else Ladder.rungs.first { it.tier == tier }
            if (current <= targetPieces) return@map Forecast(rung, today)
            if (rate <= 0.5) return@map Forecast(rung, null)
            val weekly = 1 - rate / 100
            val weeks = ln(targetPieces / current) / ln(weekly)
            Forecast(rung, today.plus((weeks * 7).toInt().coerceAtMost(3650), DateTimeUnit.DAY))
        }
    }

    /** 0..1 from baseline to Clear Air. */
    fun journey(): Double? {
        val base = baselineAverage ?: return null
        val current = lastDays(7).map { it.scaledPieces }.average().takeIf { !it.isNaN() } ?: return null
        if (base <= 0) return 1.0
        return ((base - current) / base).coerceIn(0.0, 1.0)
    }

    // ---- Stretch & pull ----

    /** Stretch, pull and net per day (last 90 days), each judged against that day's tier. */
    val stretchPull: List<DayBattery> by lazy {
        days.takeLast(90).mapNotNull { BatteryEngine.day(data, it.date, tz, now) }
    }

    /** Plain-language summary of the last 7 days (full days only). */
    fun stretchSummary(): String? {
        val all = stretchPull.filter { it.date < today }.takeLast(7)
        if (all.isEmpty()) return null
        val week = all.filter { !it.paused }
        if (week.isEmpty()) return "Stretch and pull are paused on Relapse prevention mode days: waiting longer isn't the goal right now."
        val s = week.map { it.stretchMin }.average()
        val n = week.map { it.netMin }.average()
        fun hm(m: Double): String { val t = kotlin.math.abs(m).toInt(); return if (t >= 60) "${t / 60}h ${t % 60}m" else "${t}m" }
        return if (n >= 0) "This week you averaged ${hm(s)} of stretch a day, net +${hm(n)}: living below your target pace."
        else "This week you averaged ${hm(s)} of stretch a day, net −${hm(n)}. Pieces came a little early on average; the battery resets every morning."
    }

    // ---- Milestones ----

    data class Records(
        val longestGapMin: Double,
        val lightestDay: DayStat?,
        val totalStretchMin: Double,
        val daysAtCurrentRung: Int,
    )

    fun records(): Records {
        val sorted = data.doses.filter { !it.estimated }.sortedBy { it.at }
        val gaps = sorted.zipWithNext().map { (a, b) -> (b.at - a.at) / 60_000.0 } +
            listOfNotNull(sorted.lastOrNull()?.let { (now - it.at) / 60_000.0 })
        val stretch = stretchPull.sumOf { it.stretchMin }
        val since = data.rungChanges.lastOrNull()?.at
        return Records(
            longestGapMin = gaps.maxOrNull() ?: 0.0,
            lightestDay = fullDays.drop(baselineDays.size).minByOrNull { it.pieces },
            totalStretchMin = stretch,
            daysAtCurrentRung = since?.localDate(tz)?.daysUntil(today) ?: 0,
        )
    }

    fun badges(): List<Badge> {
        val out = mutableListOf<Badge>()
        data.rungChanges.filter { it.reason == "down" || it.reason == "start" }.forEach { rc ->
            val r = Ladder.rung(rc.pieces)
            out += Badge(r.label, if (rc.reason == "start") "Started the climb down" else "Reached a new rung", rc.at.localDate(tz))
        }
        val sorted = data.doses.filter { !it.estimated }.sortedBy { it.at }
        listOf(6 to "First 6-hour gap", 12 to "First 12-hour gap", 24 to "First full day gap", 72 to "Three days clear").forEach { (h, title) ->
            sorted.zipWithNext().firstOrNull { (a, b) -> b.at - a.at >= h * 60 * MIN }?.let {
                out += Badge(title, "Between two doses", it.second.at.localDate(tz))
            }
        }
        days.firstOrNull { d -> d.date < today && d.doses.none { it.estimated } && d.doses.none { it.at < d.wakeAt + 4 * 60 * MIN } && d.doses.isNotEmpty() }?.let {
            out += Badge("Nicotine-free morning", "Nothing for 4 hours after waking", it.date)
        }
        days.firstOrNull { it.date < today && it.pieces == 0.0 }?.let { out += Badge("First clear day", "A whole day with no nicotine", it.date) }
        val resolved = data.cravings.filter { Cravings.effectiveOutcome(data, it, now) == CravingOutcome.RODE_OUT }.sortedBy { it.at }
        listOf(1, 10, 50, 100).forEach { n ->
            resolved.getOrNull(n - 1)?.let { out += Badge("$n craving${if (n > 1) "s" else ""} ridden out", "Urges beaten without nicotine", it.at.localDate(tz)) }
        }
        Control.heldByRung(data, now, tz).forEach { (pieces, held) ->
            val (n, lastDay) = held
            if (lastDay == null) return@forEach
            listOf(7, 30, 90, 180).filter { n >= it }.forEach { m ->
                out += Badge("Held ${Ladder.rung(pieces).label} for $m days", "Holding steady is a win", lastDay)
            }
        }
        Control.steadyDates(data, now, tz).let { steady ->
            Control.STEADY_MILESTONES.filter { steady.size >= it }.forEach { m ->
                out += Badge("$m steady days", "Days at or under your pace, no cigarettes or vapes", steady[m - 1])
            }
        }
        Control.daysOffSmokeAndVape(data, now, tz)?.let { off ->
            listOf(7, 30, 90, 365).filter { off >= it }.forEach { m ->
                out += Badge("$m days off cigarettes and vapes", "A big deal", today.minus(off - m, DateTimeUnit.DAY))
            }
        }
        return out.sortedBy { it.earnedOn }
    }

    /** Plain-English observations. */
    fun insightCards(): List<String> {
        val out = mutableListOf<String>()
        val heat = heatmap()
        val byHour = (0 until 24).map { h -> h to heat.sumOf { it[h] } }
        byHour.maxByOrNull { it.second }?.takeIf { it.second > 0 }?.let { (h, _) ->
            out += "Your heaviest hour is ${hourLabel(h)}–${hourLabel((h + 1) % 24)}."
        }
        comparisons().forEach { c ->
            if (c.b > 0 && c.a > 0) {
                val pct = ((c.a - c.b) / c.b * 100).toInt()
                if (c.label.startsWith("Weekdays") && kotlin.math.abs(pct) >= 10) {
                    out += if (pct < 0) "Weekends run about ${-pct}% higher than weekdays." else "Weekdays run about $pct% higher than weekends."
                }
                if (c.label.startsWith("This week") && pct <= -5) out += "This week is ${-pct}% lighter than last week."
            }
        }
        triggerCounts().firstOrNull()?.let { out += "\"${it.first}\" is the tag that shows up most around doses (${it.second} times)." }
        cravingWinRate()?.let { out += "You've ridden out ${(it * 100).toInt()}% of logged cravings." }
        return out
    }

    data class Recap(
        val month: String,
        val pieces: Double,
        val biggestWeeklyDropPct: Double?,
        val longestGapMin: Double,
        val mostBeatenTrigger: String?,
        val rungsReached: List<String>,
        val cravingsRidden: Int,
    )

    fun monthlyRecap(year: Int, month: Int): Recap? =
        recapFor(days.filter { it.date.year == year && it.date.monthNumber == month },
            "${kotlinx.datetime.Month(month).name.lowercase().replaceFirstChar { it.uppercase() }} $year")

    /** Year in review. */
    fun yearRecap(year: Int): Recap? = recapFor(days.filter { it.date.year == year }, "$year in review")

    /** Months with any data, newest first, as (year, month). */
    fun months(): List<Pair<Int, Int>> = days.map { it.date.year to it.date.monthNumber }.distinct().reversed()

    private fun recapFor(inRange: List<DayStat>, label: String): Recap? {
        if (inRange.isEmpty()) return null
        val weeks = inRange.chunked(7).map { w -> w.map { it.pieces }.average() }
        val drop = weeks.zipWithNext().maxOfOrNull { (a, b) -> if (a > 0) (a - b) / a * 100 else 0.0 }
        val doses = inRange.flatMap { it.doses }.filter { !it.estimated }.sortedBy { it.at }
        val gap = doses.zipWithNext().maxOfOrNull { (a, b) -> (b.at - a.at) / 60_000.0 } ?: 0.0
        val from = inRange.first().date
        val to = inRange.last().date
        val rungs = data.rungChanges.filter { it.reason == "down" }.filter { it.at.localDate(tz) in from..to }
            .map { Ladder.rung(it.pieces).label }
        return Recap(
            month = label,
            pieces = inRange.sumOf { it.pieces },
            biggestWeeklyDropPct = drop?.takeIf { it > 0 },
            longestGapMin = gap,
            mostBeatenTrigger = beatenTriggers().firstOrNull { it.second > 0 }?.first,
            rungsReached = rungs,
            cravingsRidden = inRange.sumOf { it.rodeOut },
        )
    }

    data class Silly(val pouchesSkipped: Double, val pouchLengthMetres: Double, val chewingHoursAvoided: Double, val cigarettesNotSmoked: Double)

    fun silly(): Silly {
        val pieces = piecesAvoided()
        val pouches = pieces / 1.2 // a 6 mg pouch is ~1.2 pieces
        return Silly(
            pouchesSkipped = pouches,
            pouchLengthMetres = pouches * 0.038,
            chewingHoursAvoided = pieces * 0.5,
            cigarettesNotSmoked = mgAvoided() / 1.0, // ~1 mg absorbed per cigarette
        )
    }

    /** Recovery timeline once nicotine-free (research-based, approximate). */
    fun clearAirTimeline(): Pair<Long?, List<Pair<String, Long>>> {
        val last = data.doses.maxOfOrNull { it.at } ?: return null to emptyList()
        val h = 3_600_000L
        val d = 24 * h
        val steps = listOf(
            "Nicotine mostly out of your blood" to 10 * h,
            "Cotinine (its breakdown product) mostly cleared" to 4 * d,
            "Withdrawal usually easing off" to 14 * d,
            "Brain nicotine receptors heading back to normal (6–12 weeks)" to 6 * 7 * d,
            "Receptors typically back to normal" to 12 * 7 * d,
            "Cravings rare for most people" to 26 * 7 * d,
        )
        return last to steps.map { it.first to last + it.second }
    }

    private fun hourLabel(h: Int) = when {
        h == 0 -> "12am"
        h < 12 -> "${h}am"
        h == 12 -> "12pm"
        else -> "${h - 12}pm"
    }
}
