package com.baastiklabs.firewatch.core.engine

import com.baastiklabs.firewatch.core.Absorption
import com.baastiklabs.firewatch.core.absorbedMg
import com.baastiklabs.firewatch.core.localDate
import com.baastiklabs.firewatch.core.model.Dose
import com.baastiklabs.firewatch.core.records.FirewatchData
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.math.ceil

/**
 * Stretch, pull and net for one waking day, in minutes. [netMin]: room left at the end of the day (or
 * now, for today): (level − pieces) × [gapMin] at bedtime in daily mode. [paused]: a Relapse
 * prevention mode day, where stretch, pull and net don't apply.
 */
data class DayBattery(
    val date: LocalDate,
    val stretchMin: Double,
    val pullMin: Double,
    val paused: Boolean = false,
    val netMin: Double = 0.0,
    val gapMin: Double = 0.0,
    /** Minutes per step unit (0.22): one pouch's time when stepping in pouches, else [gapMin]. */
    val unitGapMin: Double = gapMin,
)

/**
 * The next-piece battery (0.20: fitted to the day).
 * - Level for a day: the rung in force at wake-up (a change applies from the next wake-up).
 * - Daily mode (above 1 a day): at wake-up the battery starts at Settings' wake amount (full by
 *   default; at 2 a day "full" is half a piece, so the morning holds a quarter of the day) and
 *   refills at a steady speed so start + refill = the level exactly at the usual bedtime (Settings,
 *   not Good night). Longer day → slower refill. No refill after bedtime.
 * - Continuous mode (1 a day or less): the battery refills around the clock at level ÷ 24 h, never
 *   resets, holds up to one piece and starts empty when D first moves to that level.
 * - 0.21: 2 a day moved from continuous to daily mode from the waking day of
 *   [TWO_A_DAY_FITTED_FROM]. Days before it keep the old rule, so they are never recounted.
 * - 0.22: with "Battery and steps in" set to a product, the battery holds one of it ([DayPlan.cap]
 *   pieces, e.g. 0.4 for a 2 mg pouch) from the next wake-up: full means one pouch, and a full start
 *   is one pouch. Net and costs are the same formulas (room in pieces × minutes per piece).
 * - A tank: each dose uses its share (p pieces), never below empty, so the wait is at most one gap.
 *   Pull: the part of a dose that didn't fit, in minutes. Stretch: minutes held with a full battery
 *   before bedtime. Both are for Insights.
 * - Net = room left: the start amount plus refill so far minus pieces so far, in minutes. So net
 *   counts the battery, and at bedtime it is exactly (level − pieces) × the day's time per piece:
 *   the same nicotine gives the same net whatever the dose size or the day's length.
 */
object BatteryEngine {
    private const val MIN = 60_000L
    private const val DAY_MIN = 24 * 60.0
    /**
     * From this waking day on, 2 a day uses the fitted day (0.21). Before it, the battery ran around
     * the clock at 2 a day or less; those days keep that rule.
     */
    val TWO_A_DAY_FITTED_FROM: LocalDate = LocalDate(2026, 11, 10)

    /** At or below this many pieces a day, the battery runs around the clock on the day [date]. */
    fun continuousAt(date: LocalDate): Double = if (date >= TWO_A_DAY_FITTED_FROM) 1.0 else 2.0

    /** Share of a piece a full start holds at [level] (daily mode): half a piece at 2 a day. */
    private fun startShare(level: Double): Double = if (level <= 2.0 + 1e-9) 0.5 else 1.0

    private class Sim(val charge: Double, val stretch: Double, val pull: Double, val fullBeforeLast: Boolean?)

    /** A plain gap for a level (16 waking hours ÷ pieces): Relapse prevention and craving estimates. */
    fun intervalFor(pieces: Double) = Ladder.WAKING_MINUTES / pieces.coerceAtLeast(0.2)

    /**
     * One day's battery: [c0] at wake-up, refilling [rate] pieces a minute (until [WakingDay.bedtime]
     * in daily mode; always in continuous mode).
     */
    internal class DayPlan(
        val day: WakingDay,
        val nextWake: Long,
        val level: Double,
        val continuous: Boolean,
        val c0: Double,
        val rate: Double,
        /** What a full battery holds, in pieces: 1, or one step unit (a pouch ≈ 0.4). */
        val cap: Double = 1.0,
    ) {
        val gap: Double get() = 1.0 / rate
        val refillEnd: Long get() = if (continuous) Long.MAX_VALUE else day.bedtime
    }

    /** The waking day [now] belongs to (before today's wake-up it's still last night). */
    fun currentDay(data: FirewatchData, now: Long, tz: TimeZone): Pair<WakingDay, Long> {
        val today = now.localDate(tz)
        val w = Waking.day(data, today, tz)
        val day = if (now < w.wakeAt) Waking.day(data, today.minus(1, DateTimeUnit.DAY), tz) else w
        val nextWake = Waking.day(data, day.date.plus(1, DateTimeUnit.DAY), tz).wakeAt
        return day to nextWake
    }

    private fun dayDoses(data: FirewatchData, day: WakingDay, nextWake: Long): List<Dose> =
        data.doses.filter { it.at >= day.wakeAt && it.at < nextWake }.sortedBy { it.at }

    private fun levelOf(pieces: Double) = pieces.coerceAtLeast(1.0 / 3.0)

    /** Daily-mode refill speed for [level] across the day, from a start of [c0] (pieces a minute). */
    private fun fittedRate(level: Double, c0: Double, day: WakingDay): Double =
        (level - c0).coerceAtLeast(0.05) / ((day.bedtime - day.wakeAt) / MIN.toDouble()).coerceAtLeast(60.0)

    /** When the current run of levels at or below [limit] began (the battery starts empty there). */
    private fun continuousSince(data: FirewatchData, t: Long, limit: Double): Long? {
        var since: Long? = null
        for (rc in data.rungChanges) {
            if (rc.at > t) break
            since = if (rc.pieces <= limit + 1e-9) (since ?: rc.at) else null
        }
        return since
    }

    /**
     * The continuous battery's charge just before [t]: empty at the start of the run of levels at or
     * below [limit], then L ÷ 24 h.
     */
    internal fun continuousCharge(data: FirewatchData, t: Long, limit: Double): Double {
        val since = continuousSince(data, t, limit) ?: return 0.0
        var c = 0.0
        var x = since
        // Holds at most one step unit (one piece, or one pouch), the unit in force at each point.
        val events = (data.rungChanges.filter { it.at in since until t }.map { it.at to null as Dose? } +
            data.unitChanges.filter { it.at in since until t }.map { it.at to null as Dose? } +
            data.doses.filter { it.at in since until t }.map { it.at to it as Dose? }).sortedBy { it.first }
        for ((at, dose) in events) {
            c = (c + levelOf(targetAt(data, x) ?: 1.0) / DAY_MIN * (at - x) / MIN).coerceAtMost(data.unitPiecesAt(x))
            x = at
            if (dose != null) c = (c - data.piecesOf(dose)).coerceAtLeast(0.0)
        }
        return (c + levelOf(targetAt(data, x) ?: 1.0) / DAY_MIN * (t - x) / MIN).coerceAtMost(data.unitPiecesAt(x))
    }

    /** The day's battery at its level (null before any level). [fallback]: the level on day one. */
    internal fun plan(data: FirewatchData, day: WakingDay, nextWake: Long, fallback: Double? = null): DayPlan? {
        val raw = targetAt(data, day.wakeAt) ?: fallback ?: data.rungChanges.firstOrNull { it.at < day.bedtime }?.pieces ?: return null
        val level = levelOf(raw)
        val limit = continuousAt(day.date)
        // The step unit in force at wake-up: a switch applies from the next wake-up.
        val cap = data.unitPiecesAt(day.wakeAt)
        if (level <= limit + 1e-9) {
            return DayPlan(day, nextWake, level, true, continuousCharge(data, day.wakeAt, limit).coerceAtMost(cap), level / DAY_MIN, cap)
        }
        val c0 = (data.settings.wakeBattery.coerceIn(0.0, 1.0) * minOf(cap, startShare(level))).coerceAtMost(level)
        return DayPlan(day, nextWake, level, false, c0, fittedRate(level, c0, day), cap)
    }

    /** Refill speed for a practice level, fitted the same way as the real level. */
    private fun rateFor(level: Double, plan: DayPlan): Double =
        if (plan.continuous || level <= continuousAt(plan.day.date) + 1e-9) levelOf(level) / DAY_MIN
        else fittedRate(level, plan.c0.coerceAtMost(level), plan.day)

    /**
     * The battery for one waking day up to [until], jumping from event to event (fast). [stopBefore]
     * stops just before that dose (for the cheer).
     * - A tank: each dose uses its share (p pieces), never below empty; refills at the plan's speed
     *   (daily mode: only until bedtime).
     * - Pull: the part of a dose that didn't fit, max(0, p − charge) × gap. Stretch: minutes held
     *   with a full battery, before bedtime.
     */
    private fun simulate(
        data: FirewatchData,
        plan: DayPlan,
        until: Long,
        stopBefore: Dose? = null,
        // Practice pace: the refill speed changes at these times (time → pieces a minute). The
        // charge carries over; only the speed changes.
        segments: List<Pair<Long, Double>>? = null,
        // Only count stretch and pull inside these windows (practice net). Null = everywhere.
        countIn: List<Pair<Long, Long>>? = null,
    ): Sim {
        val day = plan.day
        val doses = dayDoses(data, day, plan.nextWake)
        var charge = plan.c0
        var stretch = 0.0
        var pull = 0.0
        var fullBeforeLast: Boolean? = null
        // Stretch only counts once there's a target to hold off against.
        val stretchFrom = data.rungChanges.firstOrNull()?.at ?: Long.MAX_VALUE
        val end = minOf(until, plan.nextWake)
        var t = day.wakeAt
        fun rateAt(x: Long): Double = segments?.lastOrNull { it.first <= x }?.second ?: plan.rate
        fun nextBreak(x: Long): Long = segments?.firstOrNull { it.first > x }?.first ?: Long.MAX_VALUE
        fun counted(a: Long, b: Long): Double =
            if (countIn == null) (b - a) / MIN.toDouble()
            else countIn.sumOf { (s, e) -> (minOf(b, e) - maxOf(a, s)).coerceAtLeast(0L) } / MIN.toDouble()
        fun inCount(x: Long) = countIn == null || countIn.any { x >= it.first && x < it.second }
        fun step(to: Long) {
            val r = rateAt(t)
            val refillEnd = minOf(to, plan.refillEnd)
            if (refillEnd > t) {
                val fullAt = t + ((plan.cap - charge).coerceAtLeast(0.0) / r * MIN).toLong()
                val sStart = maxOf(fullAt, t, stretchFrom)
                val sEnd = minOf(refillEnd, day.bedtime)
                if (sEnd > sStart) stretch += counted(sStart, sEnd)
                charge = (charge + (refillEnd - t) / MIN.toDouble() * r).coerceAtMost(plan.cap)
            }
            t = to
        }
        fun advance(to: Long) {
            while (t < to) step(minOf(to, nextBreak(t)))
        }
        for (d in doses) {
            if (d.at > end) break
            advance(d.at)
            if (stopBefore != null && d.id == stopBefore.id) return Sim(charge, stretch, pull, charge >= plan.cap - 1e-9)
            val full = charge >= plan.cap - 1e-9
            val p = data.piecesOf(d)
            if (inCount(d.at)) pull += (p - charge).coerceAtLeast(0.0) / rateAt(d.at)
            fullBeforeLast = full
            charge = (charge - p).coerceAtLeast(0.0)
        }
        advance(end)
        return Sim(charge, stretch, pull, fullBeforeLast)
    }

    /** Net = room left at [t]: start amount + refill so far − pieces so far, in minutes at the real level. */
    private fun room(data: FirewatchData, plan: DayPlan, t: Long): Double {
        val day = plan.day
        val pieces = dayDoses(data, day, plan.nextWake).filter { it.at <= t }.sumOf { data.piecesOf(it) }
        val refillMin = ((minOf(t, plan.refillEnd, plan.nextWake) - day.wakeAt) / MIN.toDouble()).coerceAtLeast(0.0)
        return (plan.c0 + plan.rate * refillMin - pieces) * plan.gap
    }

    /** The same rules stepped minute by minute (the reference the fast version is tested against). */
    internal fun simulateByMinute(data: FirewatchData, day: WakingDay, nextWake: Long, until: Long): Triple<Double, Double, Double> {
        val plan = plan(data, day, nextWake) ?: return Triple(0.0, 0.0, 0.0)
        val doses = dayDoses(data, day, nextWake)
        var charge = plan.c0
        var stretch = 0.0
        var pull = 0.0
        var di = 0
        val stretchFrom = data.rungChanges.firstOrNull()?.at ?: Long.MAX_VALUE
        var t = day.wakeAt
        val end = minOf(until, nextWake)
        while (t <= end) {
            while (di < doses.size && doses[di].at <= t) {
                val p = data.piecesOf(doses[di])
                pull += (p - charge).coerceAtLeast(0.0) * plan.gap
                charge = (charge - p).coerceAtLeast(0.0)
                di++
            }
            if (t < plan.refillEnd && t < end) {
                if (charge >= plan.cap - 1e-9 && t < day.bedtime && t >= stretchFrom) stretch += 1.0
                charge = (charge + plan.rate).coerceAtMost(plan.cap)
            }
            t += MIN
        }
        return Triple(charge, stretch, pull)
    }

    /** For tests: the waking day that [t] falls in, with the next wake-up. */
    internal fun dayOf(data: FirewatchData, t: Long, tz: TimeZone) = currentDay(data, t, tz)

    /**
     * The battery's refill speed through a waking day with practice pace: (time → pieces a minute
     * from then on), or null when practice pace wasn't on that day.
     */
    internal fun practiceSegments(data: FirewatchData, plan: DayPlan, tz: TimeZone): List<Pair<Long, Double>>? {
        val windows = Practice.windows(data, plan.day.wakeAt, plan.nextWake, tz)
        if (windows.isEmpty()) return null
        val out = ArrayList<Pair<Long, Double>>()
        out += plan.day.wakeAt to plan.rate
        for (w in windows) { out += w.start to rateFor(w.pieces, plan); out += w.end to plan.rate }
        return out.sortedBy { it.first }
    }

    /**
     * Practice net for one waking day: stretch − pull counted only while practice pace was on,
     * against the practice speed. Null when practice pace wasn't on that day.
     */
    fun practiceNetDay(data: FirewatchData, date: LocalDate, tz: TimeZone, now: Long): Double? {
        val day = Waking.day(data, date, tz)
        if (day.wakeAt > now) return null
        val nextWake = Waking.day(data, date.plus(1, DateTimeUnit.DAY), tz).wakeAt
        val plan = plan(data, day, nextWake, targetAt(data, minOf(now, nextWake - 1))) ?: return null
        val segments = practiceSegments(data, plan, tz) ?: return null
        val windows = Practice.windows(data, day.wakeAt, nextWake, tz).map { it.start to minOf(it.end, now) }
        val sim = simulate(data, plan, now, segments = segments, countIn = windows)
        return sim.stretch - sim.pull
    }

    /**
     * What logging [product] now costs from today's net, in minutes (negative): its pieces × the
     * day's time per piece. Null without a target, or on a Relapse prevention mode day.
     */
    fun preview(data: FirewatchData, product: com.baastiklabs.firewatch.core.model.Product, targetPieces: Double, now: Long, tz: TimeZone, lastActivityAt: Long = 0L): Double? {
        val (day, nextWake) = currentDay(data, now, tz)
        if (Relapse.isModeDay(data, day.date, tz)) return null
        val plan = plan(data, day, nextWake, targetPieces) ?: return null
        val p = Absorption.pieces(Absorption.absorbedMg(product), data.refMgAt(now))
        return -p * plan.gap
    }

    /** The target rung (pieces a day) in force at time [t], or null before any target. */
    fun targetAt(data: FirewatchData, t: Long): Double? = data.rungChanges.lastOrNull { it.at <= t }?.pieces

    /**
     * The battery now. [targetPieces] is only used before any level is in force at today's wake-up
     * (day one); [lastActivityAt] is no longer used (0.20: no refill after bedtime).
     */
    @Suppress("UNUSED_PARAMETER")
    fun now(data: FirewatchData, targetPieces: Double, now: Long, tz: TimeZone, lastActivityAt: Long = 0L): Battery {
        val (day, nextWake) = currentDay(data, now, tz)
        val plan = plan(data, day, nextWake, targetPieces)!!
        // Stretch, pull and net always stay against the real level; practice pace only changes the
        // battery's refill speed (and so the next-piece time).
        val sim = simulate(data, plan, now)
        val segments = practiceSegments(data, plan, tz)
        val shown = if (segments == null) sim else simulate(data, plan, now, segments = segments)
        val charge = shown.charge
        val active = Practice.active(data, now, tz)
        val rate = segments?.lastOrNull { it.first <= now }?.second ?: plan.rate

        val state: BatteryState
        var readyAt: Long? = null
        val goal = morningGoal(data, day, tz)
        val firstDose = dayDoses(data, day, nextWake).any { it.at <= now && !it.estimated }
        when {
            !plan.continuous && now >= day.bedtime -> { state = BatteryState.ASLEEP; readyAt = nextWake }
            goal != null && !firstDose && now < goal -> { state = BatteryState.MORNING_DELAY; readyAt = goal }
            charge < plan.cap - 1e-9 -> {
                val ready = now + ceil((plan.cap - charge) / rate).toLong() * MIN
                if (!plan.continuous && ready > day.bedtime) { state = BatteryState.FULL_AT_WAKE; readyAt = nextWake }
                else { state = BatteryState.CHARGING; readyAt = ready }
            }
            else -> state = BatteryState.CLEAR
        }
        // Relapse prevention mode: waiting longer isn't the goal, so stretch, pull and net pause.
        // Wind-down is a note only: it never replaces the guidance.
        val closeToBed = data.settings.windDown && now in (day.bedtime - 60 * MIN) until day.bedtime
        // The bar shows how full the battery is (one piece, or one pouch when stepping in pouches).
        val fraction = (charge / plan.cap).coerceIn(0.0, 1.0)
        val noun = data.unitAt(day.wakeAt)?.noun ?: "piece"
        if (Relapse.isModeDay(data, day.date, tz)) return Battery(fraction, state, readyAt, 0.0, plan.gap, 0.0, closeToBed, gapMin = plan.gap,
            unitGapMin = plan.gap * plan.cap, unitNoun = noun)
        return Battery(fraction, state, readyAt, sim.stretch, plan.gap, sim.pull, closeToBed,
            netMin = room(data, plan, now), gapMin = plan.gap, practicePieces = active?.pieces,
            unitGapMin = plan.gap * plan.cap, unitNoun = noun)
    }

    /**
     * Morning stretch: the battery is full at wake-up, so every minute before the first piece is
     * stretch. Minutes since waking until the first piece; null once a piece is logged, after
     * bedtime or on a Relapse prevention mode day.
     */
    fun morningStretch(data: FirewatchData, now: Long, tz: TimeZone): Double? {
        val (day, nextWake) = currentDay(data, now, tz)
        if (now >= day.bedtime || now < day.wakeAt) return null
        if (Relapse.isModeDay(data, day.date, tz)) return null
        if (dayDoses(data, day, nextWake).any { it.at <= now }) return null
        return (now - day.wakeAt) / MIN.toDouble()
    }

    /** When the first piece is allowed, from the morning-delay goal (clock time or minutes after waking). */
    fun morningGoal(data: FirewatchData, day: WakingDay, tz: TimeZone): Long? {
        val s = data.settings
        if (s.morningDelayClock >= 0) {
            return day.date.atTime(LocalTime((s.morningDelayClock / 60) % 24, s.morningDelayClock % 60)).toInstant(tz).toEpochMilliseconds()
                .takeIf { it > day.wakeAt }
        }
        return if (s.morningDelayMinutes > 0) day.wakeAt + s.morningDelayMinutes * MIN else null
    }

    /** Stretch, pull and net for a past (or the current) waking day, judged against that day's level. */
    fun day(data: FirewatchData, date: LocalDate, tz: TimeZone, now: Long): DayBattery? {
        val day = Waking.day(data, date, tz)
        if (day.wakeAt > now) return null
        val nextWake = Waking.day(data, date.plus(1, DateTimeUnit.DAY), tz).wakeAt
        val plan = plan(data, day, nextWake) ?: return null
        if (Relapse.isModeDay(data, date, tz)) return DayBattery(date, 0.0, 0.0, paused = true, gapMin = plan.gap, unitGapMin = plan.gap * plan.cap)
        val until = minOf(now, nextWake - 1)
        val sim = simulate(data, plan, until)
        return DayBattery(date, sim.stretch, sim.pull, netMin = room(data, plan, until), gapMin = plan.gap, unitGapMin = plan.gap * plan.cap)
    }

    /**
     * Was [dose] on time? In Relapse prevention mode: at or after the scheduled time. Otherwise: the
     * battery was full (and, for the day's first piece, any first-piece goal had passed). Null before
     * there's a target to judge against (the baseline week).
     */
    fun onTime(data: FirewatchData, dose: Dose, tz: TimeZone): Boolean? {
        val modeOn = data.modeChanges.lastOrNull { it.mode == "relapse" && it.at <= dose.at }?.on == true
        if (modeOn) {
            Relapse.nextAt(data, dose.at - 1, tz)?.let { return dose.at >= it - 5 * MIN }
        }
        val target = targetAt(data, dose.at) ?: return null
        val (day, nextWake) = currentDay(data, dose.at, tz)
        val earlier = dayDoses(data, day, nextWake).any { it.at < dose.at && it.id != dose.id && !it.estimated }
        if (!earlier) morningGoal(data, day, tz)?.let { if (dose.at < it) return false }
        val plan = plan(data, day, nextWake, target) ?: return null
        val sim = simulate(data, plan, dose.at, stopBefore = dose)
        return sim.fullBeforeLast == true || sim.charge >= plan.cap - 1e-9
    }

    /** True if [dose] was taken with a full battery and wasn't the day's first piece (the cheer). */
    fun waitedForFull(data: FirewatchData, dose: Dose, tz: TimeZone): Boolean {
        val target = targetAt(data, dose.at) ?: return false
        val (day, nextWake) = currentDay(data, dose.at, tz)
        val earlier = dayDoses(data, day, nextWake).any { it.at < dose.at && it.id != dose.id }
        if (!earlier) return false
        val plan = plan(data, day, nextWake, target) ?: return false
        val sim = simulate(data, plan, dose.at, stopBefore = dose)
        return sim.fullBeforeLast == true || sim.charge >= plan.cap - 1e-9
    }

}
