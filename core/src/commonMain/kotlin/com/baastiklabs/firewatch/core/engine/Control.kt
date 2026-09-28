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

    // ---- Steady days ----

    val STEADY_MILESTONES = listOf(7, 30, 60, 90, 180, 365)

    /**
     * Is this full waking day a steady day? No cigarette or vape, and net ≥ 0. Back-dated days (no
     * real times) use pieces at or under the rung instead. In Relapse prevention mode: no cigarette
     * or vape. Null before there's a rung to judge against.
     */
    fun isSteady(data: FirewatchData, date: LocalDate, tz: TimeZone, now: Long): Boolean? {
        val w = Waking.day(data, date, tz)
        val nextWake = Waking.day(data, date.plus(1, DateTimeUnit.DAY), tz).wakeAt
        val target = BatteryEngine.targetAt(data, nextWake - 1) ?: return null
        val doses = data.doses.filter { it.at >= w.wakeAt && it.at < nextWake }
        if (doses.any { Cravings.isRelapseDose(it) }) return false
        // A day with nothing logged only counts at Clear Air, or if the app was clearly in use
        // (a craving or Good morning / Good night): otherwise it's probably just untracked.
        if (doses.isEmpty() && target > 0 &&
            data.cravings.none { it.at >= w.wakeAt && it.at < nextWake } &&
            data.sleepEvents.none { it.at >= w.wakeAt && it.at < nextWake }
        ) return false
        if (Relapse.isModeDay(data, date, tz)) return true
        if (doses.isNotEmpty() && doses.all { it.estimated }) return doses.sumOf { data.piecesOf(it) } <= target + 0.25
        val day = BatteryEngine.day(data, date, tz, now) ?: return null
        return day.netMin >= 0
    }

    /** Steady days so far (full waking days only). It only ever goes up: bad days just don't add. */
    fun steadyDays(data: FirewatchData, now: Long, tz: TimeZone): Int = steadyDates(data, now, tz).size

    fun steadyDates(data: FirewatchData, now: Long, tz: TimeZone): List<LocalDate> {
        val first = data.rungChanges.firstOrNull()?.at ?: return emptyList()
        val start = BatteryEngine.currentDay(data, first, tz).first.date
        val today = BatteryEngine.currentDay(data, now, tz).first.date
        val out = ArrayList<LocalDate>()
        var d = start
        while (d < today) {
            if (isSteady(data, d, tz, now) == true) out += d
            d = d.plus(1, DateTimeUnit.DAY)
        }
        return out
    }

    /** The newest steady-days milestone reached but not yet celebrated, if any. */
    fun newSteadyMilestone(data: FirewatchData, steady: Int): Int? =
        STEADY_MILESTONES.lastOrNull { steady >= it }?.takeIf { it > data.settings.steadyMilestoneSeen }

    // ---- Practice day ----

    /** True while today (waking day) is a practice day at the next rung. */
    fun practicingToday(data: FirewatchData, now: Long, tz: TimeZone): Boolean {
        val s = data.settings
        return s.practiceDate.isNotEmpty() && s.practicePieces > 0 &&
            s.practiceDate == BatteryEngine.currentDay(data, now, tz).first.date.toString()
    }

    /** After a practice day: the rung that was practised, to ask "How was it?". Null otherwise. */
    fun practiceFollowUp(data: FirewatchData, now: Long, tz: TimeZone): Rung? {
        val s = data.settings
        if (s.practiceDate.isEmpty() || s.practicePieces <= 0) return null
        val today = BatteryEngine.currentDay(data, now, tz).first.date
        val practised = runCatching { LocalDate.parse(s.practiceDate) }.getOrNull() ?: return null
        return if (practised < today) Ladder.rung(s.practicePieces) else null
    }

    // ---- Taper forecast ----

    data class TaperStep(val rung: Rung, val date: LocalDate)
    data class TaperPlan(val steps: List<TaperStep>, val basis: String, val holdDays: Int)

    /**
     * "If you step down each time it's offered": from the current (or early) rung, one rung per hold
     * period, blended toward D's real taper pace as weeks of data build up. Always worded as an
     * option ("if you take each step"). Null before any level at all.
     */
    fun taperPlan(data: FirewatchData, now: Long, tz: TimeZone): TaperPlan? {
        val today = now.localDate(tz)
        val current = data.targetPieces ?: Progress.measuredRung(data, today, tz)?.pieces ?: return null
        if (current <= 0) return TaperPlan(emptyList(), "You're at Clear Air", data.settings.holdDays)
        val hold = data.settings.holdDays.coerceAtLeast(1)
        val since = data.rungChanges.lastOrNull()?.at?.localDate(tz)
        var daysLeft = (hold - (since?.daysUntil(today) ?: 0)).coerceIn(1, hold)
        var rung = current
        var date = today
        val plan = ArrayList<TaperStep>()
        var guard = 0
        while (rung > 0 && guard++ < 60) {
            date = date.plus(daysLeft, DateTimeUnit.DAY)
            rung = Ladder.nextDown(rung).pieces
            plan += TaperStep(Ladder.rung(rung).takeIf { rung > 0 } ?: Ladder.clearAir, date)
            daysLeft = hold
        }
        // Blend toward the real pace as full weeks of data build up (4 weeks = all pace).
        val ins = Insights(data, tz, now)
        val weeks = (ins.fullDays.size / 7).coerceAtMost(4)
        val pace = ins.arrivals().associateBy { it.rung.tier }
        val w = weeks / 4.0
        val steps = plan.map { st ->
            val paceDate = pace[st.rung.tier]?.date
            if (paceDate == null || w == 0.0) st
            else {
                val diff = st.date.daysUntil(paceDate)
                TaperStep(st.rung, st.date.plus((diff * w).toInt(), DateTimeUnit.DAY).let { if (it < today) today else it })
            }
        }
        val basis = when {
            w == 0.0 || pace.isEmpty() -> "Based on your plan"
            w < 1.0 -> "Based on your plan and your last $weeks ${if (weeks == 1) "week" else "weeks"}"
            else -> "Based on your pace"
        }
        return TaperPlan(steps, basis, hold)
    }
}
