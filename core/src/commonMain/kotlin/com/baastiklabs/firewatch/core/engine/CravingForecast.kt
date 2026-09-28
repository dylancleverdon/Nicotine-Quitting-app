package com.baastiklabs.firewatch.core.engine

import com.baastiklabs.firewatch.core.absorbedMg
import com.baastiklabs.firewatch.core.model.Dose
import com.baastiklabs.firewatch.core.model.SpeedProfile
import com.baastiklabs.firewatch.core.records.FirewatchData
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sqrt

/** One point of the 24-hour craving forecast. */
data class CravingPoint(
    val at: Long,
    /** Chance of a craving in the hour around [at], 0..1. */
    val likelihood: Double,
    /** How strong it would probably be, 1..10. */
    val strength: Double,
    val asleep: Boolean,
)

data class CravingWindow(val from: Long, val to: Long, val peakAt: Long, val likelihood: Double, val strength: Double)

data class CravingOutlook(
    val points: List<CravingPoint>,
    /** Too few logged cravings to lean on D's own pattern yet. */
    val learning: Boolean,
    val cravingsUsed: Int,
    /** The next likely craving window (awake). */
    val next: CravingWindow?,
    /** The likeliest windows in the next 24 hours, soonest first. */
    val windows: List<CravingWindow>,
    /** Start of the quietest awake hour in the next 24 hours. */
    val quietestAt: Long?,
    /** Back-test over the last 14 days: cravings that came during a predicted high window. */
    val hits: Int,
    val tested: Int,
)

/**
 * Predicts when cravings are likely in the next 24 hours, and how strong they'd be. A simple,
 * explainable model built only from D's own logs:
 * 1. Time-of-day pattern: when D's logged cravings usually happen (recent weeks count more).
 * 2. Nicotine dropping: cravings get likelier when the estimated blood level is below what's
 *    normal for D at that time of day. How much this matters is learned from D's own cravings.
 * 3. A few days after a step down, cravings run a little higher and stronger.
 * The blood level ahead assumes pieces are taken when the battery (or Relapse prevention mode)
 * suggests. With few logged cravings it leans on a generic pattern and says it's still learning.
 */
object CravingForecast {
    private const val MIN = 60_000L
    private const val H = 60 * MIN
    private const val DAY = 24 * H
    const val SLOT_MIN = 15
    private const val SLOTS = 24 * 60 / SLOT_MIN
    private const val HISTORY_DAYS = 42
    private const val RECENCY_DAYS = 21.0
    private const val KERNEL_MIN = 45.0
    const val MIN_CRAVINGS = 10
    /** Generic prior: about one craving every 6 waking hours. */
    private const val PRIOR_PER_HOUR = 1.0 / 6.0
    private const val PRIOR_WEIGHT = 10.0
    private const val PRIOR_BETA = 0.8
    /** One timer check while refilling counts as this much of a logged craving. */
    private const val CHECK_WEIGHT = 0.3

    fun outlook(data: FirewatchData, now: Long, tz: TimeZone): CravingOutlook {
        val doses = data.doses.sortedBy { it.at }
        val planned = plannedDoses(data, now, tz)
        val allDoses = doses + planned
        val typical = typicalLevels(data, doses, now, tz)
        val model = fit(data, doses, typical, now, tz, excludeDay = null)

        val end = now + DAY
        val points = ArrayList<CravingPoint>()
        var t = now - now % (SLOT_MIN * MIN)
        while (t <= end) {
            points += pointAt(data, model, allDoses, typical, t, tz)
            t += SLOT_MIN * MIN
        }
        val windows = windows(points.filter { it.at >= now })
        val next = windows.firstOrNull()
        val awake = points.filter { !it.asleep && it.at >= now }
        val quietest = awake.windowed(4).minByOrNull { w -> w.sumOf { it.likelihood } }?.first()?.at
        val (hits, tested) = backtest(data, doses, typical, now, tz)
        return CravingOutlook(
            points = points,
            learning = model.n < MIN_CRAVINGS,
            cravingsUsed = model.n,
            next = next,
            windows = spaced(windows).take(3).sortedBy { it.from },
            quietestAt = quietest,
            hits = hits,
            tested = tested,
        )
    }

    // ---- Model ----

    private class Model(
        /** Expected cravings per slot, by slot of the day (D's own pattern blended with the prior). */
        val rate: DoubleArray,
        /** Typical strength by slot of the day. */
        val strength: DoubleArray,
        /** How much a level below normal raises the chance (learned, shrunk toward the prior). */
        val beta: Double,
        val n: Int,
    )

    private fun slotOf(t: Long, tz: TimeZone): Int {
        val dt = Instant.fromEpochMilliseconds(t).toLocalDateTime(tz)
        return (dt.hour * 60 + dt.minute) / SLOT_MIN
    }

    private fun circularGap(a: Int, b: Int): Int { val d = kotlin.math.abs(a - b); return min(d, SLOTS - d) }

    private fun fit(
        data: FirewatchData,
        doses: List<Dose>,
        typical: Typical,
        now: Long,
        tz: TimeZone,
        excludeDay: LocalDate?,
    ): Model {
        val from = now - HISTORY_DAYS * DAY
        val cravings = data.cravings.filter { it.at in from until now }
            .filter { excludeDay == null || Instant.fromEpochMilliseconds(it.at).toLocalDateTime(tz).date != excludeDay }
        val weight = { t: Long -> exp(-(now - t) / (RECENCY_DAYS * DAY)) }

        // Days the pattern is spread over: tracked days in the window, recency-weighted.
        val firstTracked = maxOf(from, minOf(data.doses.firstOrNull()?.at ?: now, data.cravings.firstOrNull()?.at ?: now))
        var effectiveDays = 0.0
        var d = firstTracked
        while (d < now) { effectiveDays += weight(d) * min(1.0, (now - d) / DAY.toDouble()); d += DAY }
        effectiveDays = effectiveDays.coerceAtLeast(1.0)

        val sigma = KERNEL_MIN / SLOT_MIN
        val norm = 1.0 / (sigma * sqrt(2 * PI))
        val own = DoubleArray(SLOTS)
        val strengthSum = DoubleArray(SLOTS)
        val strengthW = DoubleArray(SLOTS)
        var totalW = 0.0
        var totalStrength = 0.0
        // Timer checks while refilling ("Hide next piece timer") count as a weak "wanting" signal.
        val checks = data.timerChecks.filter { it.charging && it.at in from until now }
            .filter { excludeDay == null || Instant.fromEpochMilliseconds(it.at).toLocalDateTime(tz).date != excludeDay }
        checks.forEach { c ->
            val s = slotOf(c.at, tz)
            val w = CHECK_WEIGHT * weight(c.at)
            for (k in 0 until SLOTS) {
                val g = circularGap(s, k)
                if (g > 4 * sigma) continue
                own[k] += w * norm * exp(-0.5 * (g / sigma) * (g / sigma))
            }
        }
        cravings.forEach { c ->
            val s = slotOf(c.at, tz)
            val w = weight(c.at)
            totalW += w
            totalStrength += w * c.intensity
            for (k in 0 until SLOTS) {
                val g = circularGap(s, k)
                if (g > 4 * sigma) continue
                val kern = norm * exp(-0.5 * (g / sigma) * (g / sigma))
                own[k] += w * kern
                strengthSum[k] += w * kern * c.intensity
                strengthW[k] += w * kern
            }
        }
        for (k in 0 until SLOTS) own[k] /= effectiveDays

        val n = cravings.size
        val priorSlot = PRIOR_PER_HOUR * SLOT_MIN / 60.0
        val blend = n / (n + PRIOR_WEIGHT)
        val rate = DoubleArray(SLOTS) { k -> blend * own[k] + (1 - blend) * priorSlot }
        val overall = if (totalW > 0) totalStrength / totalW else 5.0
        val strength = DoubleArray(SLOTS) { k -> (strengthSum[k] + 0.5 * overall) / (strengthW[k] + 0.5) }

        // Learn how much a low level matters: deficit at cravings vs at ordinary awake times.
        val atCravings = cravings.map { deficit(doses, typical, it.at, tz) }
        val baseline = ArrayList<Double>()
        var t = maxOf(from, firstTracked)
        while (t < now) {
            if (isAwake(data, t, tz)) baseline += deficit(doses, typical, t, tz)
            t += 2 * H
        }
        val learned = if (atCravings.isNotEmpty() && baseline.isNotEmpty())
            ((atCravings.average() - baseline.average()) * 3.0).coerceIn(0.0, 2.0) else PRIOR_BETA
        val beta = (n * learned + PRIOR_WEIGHT * PRIOR_BETA) / (n + PRIOR_WEIGHT)
        return Model(rate, strength, beta, n)
    }

    /**
     * How far below D's normal level for that time of day the estimated level is, -1..1. Measured
     * against D's average level (at least half a piece), and smoothed, so small levels don't swing
     * it from one extreme to the other.
     */
    private fun deficit(doses: List<Dose>, typical: Typical, t: Long, tz: TimeZone): Double {
        val normal = typical.bySlot[slotOf(t, tz) / 2]
        return kotlin.math.tanh((normal - levelAt(doses, t)) / typical.scale)
    }

    private class Typical(val bySlot: DoubleArray, val scale: Double)

    private fun pointAt(data: FirewatchData, model: Model, doses: List<Dose>, typical: Typical, t: Long, tz: TimeZone): CravingPoint {
        if (!isAwake(data, t, tz)) return CravingPoint(t, 0.0, 0.0, asleep = true)
        val s = slotOf(t, tz)
        val def = deficit(doses, typical, t, tz)
        val step = stepDownBoost(data, t)
        // Expected cravings in the hour around t.
        var hour = 0.0
        for (k in -2..1) hour += model.rate[(s + k + SLOTS) % SLOTS]
        val lambda = hour * exp(model.beta * def) * (1 + 0.4 * step)
        val strength = (model.strength[s] + 1.5 * maxOf(0.0, def) + step).coerceIn(1.0, 10.0)
        return CravingPoint(t, 1 - exp(-lambda), strength, asleep = false)
    }

    /** 1 right after a step down, fading to 0 over 5 days. */
    private fun stepDownBoost(data: FirewatchData, t: Long): Double {
        val last = data.rungChanges.lastOrNull { it.at <= t && it.reason == "down" } ?: return 0.0
        val days = (t - last.at) / DAY.toDouble()
        return if (days < 5) 1 - days / 5 else 0.0
    }

    private fun isAwake(data: FirewatchData, t: Long, tz: TimeZone): Boolean {
        val (day, _) = BatteryEngine.currentDay(data, t, tz)
        return t >= day.wakeAt && t < day.sleepAt
    }

    /** Estimated level at [t]; [doses] must be sorted by time (binary search to the last day). */
    private fun levelAt(doses: List<Dose>, t: Long): Double {
        var lo = 0
        var hi = doses.size
        val from = t - 24 * H
        while (lo < hi) { val mid = (lo + hi) ushr 1; if (doses[mid].at < from) lo = mid + 1 else hi = mid }
        var sum = 0.0
        var i = lo
        while (i < doses.size && doses[i].at <= t) {
            val d = doses[i]
            sum += Kinetics.contribution(d.absorbedMg(), d.speed, (t - d.at) / 60_000.0)
            i++
        }
        return sum
    }

    /** D's normal level by half hour of the day (last 14 days), for comparison. */
    private fun typicalLevels(data: FirewatchData, doses: List<Dose>, now: Long, tz: TimeZone): Typical {
        val bySlot = typicalBySlot(data, doses, now, tz)
        return Typical(bySlot, maxOf(bySlot.average(), 0.5 * data.referenceMg, 0.3))
    }

    private fun typicalBySlot(data: FirewatchData, doses: List<Dose>, now: Long, tz: TimeZone): DoubleArray {
        val today = Instant.fromEpochMilliseconds(now).toLocalDateTime(tz).date
        val days = (1..14).map { today.minus(it, DateTimeUnit.DAY) }
            .filter { d -> data.doses.firstOrNull()?.let { it.at <= d.atStartOfDayIn(tz).toEpochMilliseconds() + DAY } == true }
        if (days.isEmpty()) {
            // No history yet: a flat "normal" at the average level over the last day.
            val avg = (0 until 48).map { levelAt(doses, now - it * 30 * MIN) }.average()
            return DoubleArray(48) { avg }
        }
        return DoubleArray(48) { slot ->
            days.map { d -> levelAt(doses, d.atStartOfDayIn(tz).toEpochMilliseconds() + slot * 30 * MIN) }.average()
        }
    }

    /**
     * Pieces ahead, as suggested: at the battery's (or Relapse prevention mode's) next time, then
     * one gap apart while awake. Before there's a target, D's recent average gap.
     */
    fun plannedDoses(data: FirewatchData, now: Long, tz: TimeZone): List<Dose> {
        val target = data.targetPieces
        if (target != null && target <= 0.0 && !data.relapseOn) return emptyList()
        val gapMin: Double
        var next: Long
        when {
            data.relapseOn -> {
                gapMin = Relapse.gapMinutes(data, now)
                next = Relapse.nextAt(data, now, tz) ?: now
            }
            target != null -> {
                gapMin = BatteryEngine.intervalFor(target)
                val b = BatteryEngine.now(data, target, now, tz)
                next = b.readyAt ?: now
            }
            else -> {
                val recent = data.doses.filter { !it.estimated && it.at in (now - 7 * DAY)..now }
                val awakeMin = 7 * Ladder.WAKING_MINUTES
                // No target and too little recent use to go on: don't invent doses.
                if (recent.size < 3) return emptyList()
                gapMin = (awakeMin / recent.size).coerceIn(30.0, 12 * 60.0)
                next = (data.doses.lastOrNull()?.at ?: now) + (gapMin * MIN).toLong()
            }
        }
        val gap = (gapMin * MIN).toLong().coerceAtLeast(15 * MIN)
        next = maxOf(next, now)
        val out = ArrayList<Dose>()
        val ref = data.referenceMg
        var guard = 0
        while (next < now + DAY && guard++ < 200) {
            val (day, nextWake) = BatteryEngine.currentDay(data, next, tz)
            if (next < day.wakeAt || next >= day.sleepAt) {
                next = if (next < day.wakeAt) day.wakeAt else nextWake
                continue
            }
            out += Dose(id = "planned-$next", productId = "planned", at = next, kind = com.baastiklabs.firewatch.core.model.ProductKind.OTHER,
                labelMg = ref, absorption = 1.0, speed = SpeedProfile.BUILD)
            next += gap
        }
        return out
    }

    /** Peaks in the curve, each widened to where it stays near the peak. */
    private fun windows(points: List<CravingPoint>): List<CravingWindow> {
        val awake = points.filter { !it.asleep }
        if (awake.isEmpty()) return emptyList()
        val top = awake.maxOf { it.likelihood }
        val median = awake.map { it.likelihood }.sorted()[awake.size / 2]
        // A flat curve has no real peaks: say nothing rather than point at an arbitrary time.
        if (top <= 0.02 || top < median * 1.15) return emptyList()
        val out = ArrayList<CravingWindow>()
        var i = 0
        while (i < points.size) {
            val p = points[i]
            val prev = points.getOrNull(i - 1)
            val nextP = points.getOrNull(i + 1)
            val isPeak = !p.asleep && p.likelihood >= 0.6 * top &&
                (prev == null || prev.asleep || prev.likelihood <= p.likelihood) &&
                (nextP == null || nextP.asleep || nextP.likelihood < p.likelihood)
            if (isPeak) {
                var a = i
                while (a > 0 && !points[a - 1].asleep && points[a - 1].likelihood >= 0.85 * p.likelihood && i - a < 3) a--
                var b = i
                while (b < points.lastIndex && !points[b + 1].asleep && points[b + 1].likelihood >= 0.85 * p.likelihood && b - i < 3) b++
                val span = points.subList(a, b + 1)
                out += CravingWindow(points[a].at, points[b].at + SLOT_MIN * MIN, p.at, p.likelihood, span.maxOf { it.strength })
                i = b + 1
            } else i++
        }
        return out
    }

    /** The likeliest windows, at least 2 hours apart (so one broad peak isn't listed three times). */
    private fun spaced(windows: List<CravingWindow>): List<CravingWindow> {
        val out = ArrayList<CravingWindow>()
        windows.sortedByDescending { it.likelihood }.forEach { w ->
            if (out.none { kotlin.math.abs(it.peakAt - w.peakAt) < 2 * H }) out += w
        }
        return out
    }

    /**
     * Honesty check over the last 14 days: for each day, the model (fitted without that day)
     * marks the likeliest quarter of waking time; how many of that day's cravings fell inside?
     */
    private fun backtest(data: FirewatchData, doses: List<Dose>, typical: Typical, now: Long, tz: TimeZone): Pair<Int, Int> {
        val today = Instant.fromEpochMilliseconds(now).toLocalDateTime(tz).date
        var hits = 0
        var tested = 0
        for (i in 1..14) {
            val date = today.minus(i, DateTimeUnit.DAY)
            val w = Waking.day(data, date, tz)
            val nextWake = Waking.day(data, date.plus(1, DateTimeUnit.DAY), tz).wakeAt
            val cravings = data.cravings.filter { it.at >= w.wakeAt && it.at < minOf(nextWake, w.sleepAt) }
            if (cravings.isEmpty()) continue
            val model = fit(data, doses, typical, now, tz, excludeDay = date)
            if (model.n < 3) continue
            val slots = ArrayList<CravingPoint>()
            var t = w.wakeAt
            while (t < w.sleepAt) { slots += pointAt(data, model, doses, typical, t, tz); t += SLOT_MIN * MIN }
            if (slots.isEmpty()) continue
            val cutoff = slots.map { it.likelihood }.sortedDescending()[(slots.size / 4).coerceAtMost(slots.lastIndex)]
            cravings.forEach { c ->
                tested++
                val p = pointAt(data, model, doses, typical, c.at - c.at % (SLOT_MIN * MIN), tz)
                if (p.likelihood >= cutoff) hits++
            }
        }
        return hits to tested
    }
}
