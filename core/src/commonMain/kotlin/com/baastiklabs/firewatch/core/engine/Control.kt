package com.baastiklabs.firewatch.core.engine

import com.baastiklabs.firewatch.core.Baseline
import com.baastiklabs.firewatch.core.BaselineStatus
import com.baastiklabs.firewatch.core.Cravings
import com.baastiklabs.firewatch.core.localDate
import com.baastiklabs.firewatch.core.records.FirewatchData
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * "Find, then control": the pieces that help D find their real level fast and celebrate holding it.
 */
object Control {
    private const val MIN = 60_000L
    private const val DAY = 24 * 60 * MIN

    /** New users start here, so guidance works from day one; it firms up over the first week. */
    const val EARLY_PIECES = 8.0
    const val EARLY = "early"

    // ---- Early target (first week) ----

    /** True while the target is the provisional first-week one. */
    fun isEarly(data: FirewatchData): Boolean = data.rungChanges.lastOrNull()?.reason == EARLY

    /**
     * The first-week target, firming up as days are logged: a blend of 8 pieces and the measured
     * average so far, weighted by full days logged (rounded to the heavier rung, never flattering).
     * Returns the new pieces when it should change, else null. Stops once the baseline week is done.
     */
    fun earlyTargetUpdate(data: FirewatchData, now: Long, tz: TimeZone): Double? {
        if (!isEarly(data)) return null
        val today = now.localDate(tz)
        val status = Baseline.status(data, today, tz)
        if (status !is BaselineStatus.InProgress) return null
        val first = data.doses.firstOrNull()?.at?.localDate(tz) ?: return null
        val full = (0 until first.daysUntil(today)).map { first.plus(it, DateTimeUnit.DAY) }
        if (full.isEmpty()) return null
        val avg = full.map { Progress.pace(data, it, tz).scaled }.average()
        val d = full.size.coerceAtMost(7)
        val blended = (EARLY_PIECES * (7 - d) + avg * d) / 7.0
        val rung = Ladder.measured(blended).pieces.takeIf { it > 0 } ?: Ladder.rungs.last().pieces
        val current = data.targetPieces ?: return rung
        return if (kotlin.math.abs(rung - current) > 1e-6) rung else null
    }

    // ---- Holding steady ----

    /** Full waking days at the current target where D stayed at or under it (plus a quarter piece). */
    fun heldDays(data: FirewatchData, now: Long, tz: TimeZone): Int {
        val target = data.targetPieces ?: return 0
        if (isEarly(data)) return 0
        val since = data.rungChanges.lastOrNull()?.at?.localDate(tz) ?: return 0
        val today = now.localDate(tz)
        return (1..since.daysUntil(today)).count { i ->
            val d = today.minus(i, DateTimeUnit.DAY)
            d > since && Progress.pace(data, d, tz).scaled <= target + 0.25
        }
    }

    /** Held days at each rung ever worked at (for "Held Bonfire for 30 days" badges). */
    fun heldByRung(data: FirewatchData, now: Long, tz: TimeZone): Map<Double, Pair<Int, LocalDate?>> {
        val today = now.localDate(tz)
        val out = HashMap<Double, Pair<Int, LocalDate?>>()
        data.rungChanges.forEachIndexed { i, rc ->
            if (rc.reason == EARLY) return@forEachIndexed
            val start = rc.at.localDate(tz)
            val end = data.rungChanges.getOrNull(i + 1)?.at?.localDate(tz) ?: today
            var d = start.plus(1, DateTimeUnit.DAY)
            var (n, _) = out[rc.pieces] ?: (0 to null)
            val milestones = HashMap<Int, LocalDate>()
            while (d < end && d < today) {
                if (Progress.pace(data, d, tz).scaled <= rc.pieces + 0.25) { n++; milestones[n] = d }
                d = d.plus(1, DateTimeUnit.DAY)
            }
            out[rc.pieces] = n to (milestones[n] ?: out[rc.pieces]?.second)
        }
        return out
    }

    /** Held/over per day for the last [n] full days: pace against that day's target (null before one). */
    fun heldSeries(data: FirewatchData, now: Long, tz: TimeZone, n: Int = 42): List<Triple<LocalDate, Double, Double?>> {
        val today = now.localDate(tz)
        val first = data.doses.firstOrNull()?.at?.localDate(tz) ?: return emptyList()
        return (n downTo 1).map { today.minus(it, DateTimeUnit.DAY) }.filter { it >= first }.map { d ->
            val wake = Waking.day(data, d, tz).wakeAt
            Triple(d, Progress.pace(data, d, tz).scaled, BatteryEngine.targetAt(data, wake + DAY - 1))
        }
    }

    /** "Lighter than when you started", as a fraction (only when true): baseline vs last 7 days. */
    fun lighterThanStart(data: FirewatchData, now: Long, tz: TimeZone): Double? {
        val ins = Insights(data, tz, now)
        val base = ins.baselineAverage ?: return null
        val recent = Progress.rollingAverage(data, now.localDate(tz), tz) ?: return null
        if (base <= 0 || ins.fullDays.size < 14) return null
        val drop = (base - recent) / base
        return drop.takeIf { it >= 0.05 }
    }

    /** Days since the last cigarette or vape, if D has ever logged one and has kept logging since. */
    fun daysOffSmokeAndVape(data: FirewatchData, now: Long, tz: TimeZone): Int? {
        val last = data.doses.lastOrNull { Cravings.isRelapseDose(it) } ?: return null
        if (data.doses.none { it.at > last.at && !Cravings.isRelapseDose(it) }) return null
        return last.at.localDate(tz).daysUntil(now.localDate(tz))
    }

    // ---- Welcome back ----

    /**
     * After a break (2+ whole days with nothing logged), the days D could back-date, oldest first.
     * Null when there's no gap, or D already answered "Welcome back" for this one.
     */
    fun welcomeBackDays(data: FirewatchData, now: Long, tz: TimeZone): List<LocalDate>? {
        if (!data.settings.onboardingDone) return null
        val lastLog = listOfNotNull(
            data.doses.lastOrNull { !it.estimated }?.at, data.cravings.lastOrNull()?.at, data.sleepEvents.lastOrNull()?.at,
        ).maxOrNull() ?: return null
        if (data.settings.welcomeBackDismissedAt > lastLog) return null
        val lastDate = lastLog.localDate(tz)
        val today = now.localDate(tz)
        val gap = (1 until lastDate.daysUntil(today)).map { lastDate.plus(it, DateTimeUnit.DAY) }
            .filter { d -> data.doses.none { it.at.localDate(tz) == d } }
        return gap.takeIf { it.size >= 2 }?.takeLast(14)
    }
}
