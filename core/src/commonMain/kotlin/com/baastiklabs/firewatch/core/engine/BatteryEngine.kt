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

/** Stretch, pull and net for one waking day, in minutes. */
/** [paused]: a Relapse prevention mode day, where stretch and pull don't apply. */
/** [sizeNetMin]: the part of net that comes from dose size (the rest is timing). */
data class DayBattery(val date: LocalDate, val stretchMin: Double, val pullMin: Double, val paused: Boolean = false, val sizeNetMin: Double = 0.0) {
    val netMin: Double get() = stretchMin - pullMin
}

/**
 * The next-piece battery.
 * - Fresh start: full when D wakes up; yesterday never counts against today.
 * - Every dose restarts the countdown from the moment it's taken: it drains by the dose's pieces
 *   (the same middle estimate as "pieces today") and never below empty, so the wait is at most one gap.
 * - Refills one piece per target interval while awake.
 * - No waiting overnight: if it wouldn't be full before bedtime, it's full when D wakes.
 * - After bedtime D is assumed asleep; opening the app or logging says "I'm up" and the battery
 *   catches up on the time since bedtime (that catch-up never counts as stretch).
 * - Stretch: minutes held off with a full battery (before bedtime). Pull: the part of each dose
 *   that didn't fit in the battery, in minutes of refill; counted, then forgiven, so the wait
 *   never exceeds one gap. Net = stretch − pull.
 */
object BatteryEngine {
    private const val MIN = 60_000L
    /** "Up right now" if the app was opened or something logged within this window. */
    private const val ACTIVE_WINDOW = 30 * MIN

    private class Sim(val charge: Double, val stretch: Double, val pull: Double, val fullBeforeLast: Boolean?, val sizeNet: Double = 0.0)

    fun intervalFor(pieces: Double) = Ladder.WAKING_MINUTES / pieces.coerceAtLeast(0.2)

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

    /**
     * The battery for one waking day up to [until], jumping from event to event (fast). Awake = from
     * wake-up to bedtime, plus any late-night time up to [awakeUntil]. [stopBefore] stops just before
     * that dose (for the cheer).
     * - Every dose empties the battery, whatever its size; it refills over one gap while awake.
     * - Timing: a dose before the battery is full adds pull (the time it still needed); minutes
     *   held with a full battery (before bedtime) add stretch.
     * - Size: p pieces above one add (p − 1) × gap of pull; below one add (1 − p) × gap of stretch.
     */
    private fun simulate(
        data: FirewatchData,
        day: WakingDay,
        nextWake: Long,
        interval: Double,
        until: Long,
        awakeUntil: Long,
        stopBefore: Dose? = null,
        // Practice pace: the gap changes at these times (time → gap from then on). The charge
        // carries over; only the refill speed changes.
        segments: List<Pair<Long, Double>>? = null,
        // Only count stretch and pull inside these windows (practice net). Null = everywhere.
        countIn: List<Pair<Long, Long>>? = null,
    ): Sim {
        val doses = dayDoses(data, day, nextWake)
        var charge = 1.0
        var stretch = 0.0
        var pull = 0.0
        var fullBeforeLast: Boolean? = null
        var sizeNet = 0.0
        val awakeEnd = maxOf(day.sleepAt, awakeUntil)
        // Stretch only counts once there's a target to hold off against.
        val stretchFrom = data.rungChanges.firstOrNull()?.at ?: Long.MAX_VALUE
        val end = minOf(until, nextWake)
        var t = day.wakeAt
        fun gapAt(x: Long): Double = segments?.lastOrNull { it.first <= x }?.second ?: interval
        fun nextBreak(x: Long): Long = segments?.firstOrNull { it.first > x }?.first ?: Long.MAX_VALUE
        fun counted(a: Long, b: Long): Double =
            if (countIn == null) (b - a) / MIN.toDouble()
            else countIn.sumOf { (s, e) -> (minOf(b, e) - maxOf(a, s)).coerceAtLeast(0L) } / MIN.toDouble()
        fun inCount(x: Long) = countIn == null || countIn.any { x >= it.first && x < it.second }
        fun step(to: Long) {
            val gap = gapAt(t)
            val refillEnd = minOf(to, awakeEnd)
            if (refillEnd > t) {
                val fullAt = t + ((1.0 - charge).coerceAtLeast(0.0) * gap * MIN).toLong()
                val sStart = maxOf(fullAt, t, stretchFrom)
                val sEnd = minOf(refillEnd, day.sleepAt)
                if (sEnd > sStart) stretch += counted(sStart, sEnd)
                charge = (charge + (refillEnd - t) / MIN.toDouble() / gap).coerceAtMost(1.0)
            }
            t = to
        }
        fun advance(to: Long) {
            while (t < to) step(minOf(to, nextBreak(t)))
        }
        for (d in doses) {
            if (d.at > end) break
            advance(d.at)
            if (stopBefore != null && d.id == stopBefore.id) return Sim(charge, stretch, pull, charge >= 1.0 - 1e-9)
            val full = charge >= 1.0 - 1e-9
            val gap = gapAt(d.at)
            if (inCount(d.at)) {
                if (!full) pull += (1.0 - charge) * gap
                val p = data.piecesOf(d)
                if (p > 1.0) pull += (p - 1.0) * gap else stretch += (1.0 - p) * gap
                sizeNet += (1.0 - p) * gap
            }
            fullBeforeLast = full
            charge = 0.0
        }
        advance(end)
        return Sim(charge, stretch, pull, fullBeforeLast, sizeNet)
    }

    /** The same rules stepped minute by minute (the reference the fast version is tested against). */
    internal fun simulateByMinute(data: FirewatchData, day: WakingDay, nextWake: Long, interval: Double, until: Long, awakeUntil: Long): Triple<Double, Double, Double> {
        val doses = dayDoses(data, day, nextWake)
        var charge = 1.0
        var stretch = 0.0
        var pull = 0.0
        var di = 0
        val awakeEnd = maxOf(day.sleepAt, awakeUntil)
        val stretchFrom = data.rungChanges.firstOrNull()?.at ?: Long.MAX_VALUE
        var t = day.wakeAt
        val end = minOf(until, nextWake)
        while (t <= end) {
            while (di < doses.size && doses[di].at <= t) {
                val d = doses[di]
                if (charge < 1.0 - 1e-9) pull += (1.0 - charge) * interval
                val p = data.piecesOf(d)
                if (p > 1.0) pull += (p - 1.0) * interval else stretch += (1.0 - p) * interval
                charge = 0.0
                di++
            }
            if (t < awakeEnd && t < end) {
                if (charge >= 1.0 - 1e-9 && t < day.sleepAt && t >= stretchFrom) stretch += 1.0
                charge = (charge + 1.0 / interval).coerceAtMost(1.0)
            }
            t += MIN
        }
        return Triple(charge, stretch, pull)
    }

    /** For tests: the waking day that [t] falls in, with the next wake-up. */
    internal fun dayOf(data: FirewatchData, t: Long, tz: TimeZone) = currentDay(data, t, tz)

    /**
     * The battery's gap through a waking day with practice pace: (time → gap from then on), or null
     * when practice pace wasn't on that day.
     */
    internal fun practiceSegments(data: FirewatchData, day: WakingDay, nextWake: Long, realGap: Double, tz: TimeZone): List<Pair<Long, Double>>? {
        val windows = Practice.windows(data, day.wakeAt, nextWake, tz)
        if (windows.isEmpty()) return null
        val out = ArrayList<Pair<Long, Double>>()
        out += day.wakeAt to realGap
        for (w in windows) { out += w.start to intervalFor(w.pieces); out += w.end to realGap }
        return out.sortedBy { it.first }
    }

    /**
     * Practice net for one waking day: stretch − pull counted only while practice pace was on,
     * against the practice gap. Null when practice pace wasn't on that day.
     */
    fun practiceNetDay(data: FirewatchData, date: LocalDate, tz: TimeZone, now: Long): Double? {
        val day = Waking.day(data, date, tz)
        if (day.wakeAt > now) return null
        val nextWake = Waking.day(data, date.plus(1, DateTimeUnit.DAY), tz).wakeAt
        val target = targetAt(data, minOf(now, nextWake - 1)) ?: return null
        val realGap = intervalFor(target)
        val segments = practiceSegments(data, day, nextWake, realGap, tz) ?: return null
        val windows = Practice.windows(data, day.wakeAt, nextWake, tz).map { it.start to minOf(it.end, now) }
        val lateDose = dayDoses(data, day, nextWake).lastOrNull { it.at >= day.sleepAt }?.at ?: 0L
        val sim = simulate(data, day, nextWake, realGap, now, lateDose, segments = segments, countIn = windows)
        return sim.stretch - sim.pull
    }

    /**
     * What logging [product] now would do to today's net, in minutes: positive = stretch, negative =
     * pull (timing and size combined). Null without a target, or on a Relapse prevention mode day.
     */
    fun preview(data: FirewatchData, product: com.baastiklabs.firewatch.core.model.Product, targetPieces: Double, now: Long, tz: TimeZone, lastActivityAt: Long = 0L): Double? {
        val b = now(data, targetPieces, now, tz, lastActivityAt)
        val (day, _) = currentDay(data, now, tz)
        if (Relapse.isModeDay(data, day.date, tz)) return null
        val interval = b.intervalMinutes
        val p = Absorption.pieces(Absorption.absorbedMg(product), data.refMgAt(now))
        val timing = -(1.0 - b.charge).coerceAtLeast(0.0) * interval
        val size = if (p > 1.0) -(p - 1.0) * interval else (1.0 - p) * interval
        return timing + size
    }

    /** The target rung (pieces a day) in force at time [t], or null before any target. */
    fun targetAt(data: FirewatchData, t: Long): Double? = data.rungChanges.lastOrNull { it.at <= t }?.pieces

    fun now(data: FirewatchData, targetPieces: Double, now: Long, tz: TimeZone, lastActivityAt: Long = 0L): Battery {
        val (day, nextWake) = currentDay(data, now, tz)
        val realGap = intervalFor(targetPieces)
        val lateDose = dayDoses(data, day, nextWake).lastOrNull { it.at >= day.sleepAt }?.at ?: 0L
        val activity = maxOf(lastActivityAt, lateDose).takeIf { it in day.sleepAt..now } ?: 0L
        // Stretch, pull and net always stay against the real level; practice pace only changes the
        // battery's refill speed (and so the next-piece time and the dose preview).
        val sim = simulate(data, day, nextWake, realGap, now, activity)
        val segments = practiceSegments(data, day, nextWake, realGap, tz)
        val shown = if (segments == null) sim else simulate(data, day, nextWake, realGap, now, activity, segments = segments)
        val charge = shown.charge
        val active = Practice.active(data, now, tz)
        val interval = active?.let { intervalFor(it.pieces) } ?: realGap

        val state: BatteryState
        var readyAt: Long? = null
        if (now >= day.sleepAt) {
            val upNow = activity > 0 && now - activity <= ACTIVE_WINDOW
            if (!upNow) {
                state = BatteryState.ASLEEP
                readyAt = nextWake
            } else if (charge >= 1.0 - 1e-9) {
                state = BatteryState.CLEAR
            } else {
                state = BatteryState.CHARGING
                readyAt = now + ceil((1.0 - charge) * interval).toLong() * MIN
            }
        } else {
            val goal = morningGoal(data, day, tz)
            val firstDose = dayDoses(data, day, nextWake).any { it.at <= now && !it.estimated }
            when {
                goal != null && !firstDose && now < goal -> { state = BatteryState.MORNING_DELAY; readyAt = goal }
                charge < 1.0 - 1e-9 -> {
                    val ready = now + ceil((1.0 - charge) * interval).toLong() * MIN
                    if (ready > day.sleepAt) { state = BatteryState.FULL_AT_WAKE; readyAt = nextWake }
                    else { state = BatteryState.CHARGING; readyAt = ready }
                }
                else -> state = BatteryState.CLEAR
            }
        }
        // Relapse prevention mode: waiting longer isn't the goal, so stretch and pull pause.
        // Wind-down is a note only: it never replaces the guidance.
        val closeToBed = data.settings.windDown && now in (day.sleepAt - 60 * MIN) until day.sleepAt
        if (Relapse.isModeDay(data, day.date, tz)) return Battery(charge, state, readyAt, 0.0, interval, 0.0, closeToBed)
        return Battery(charge, state, readyAt, sim.stretch, interval, sim.pull, closeToBed, practicePieces = active?.pieces)
    }

    /**
     * Morning stretch: the battery is full at wake-up, so every minute before the first piece is
     * stretch. Minutes since waking until the first piece; null once a piece is logged, after
     * bedtime or on a Relapse prevention mode day.
     */
    fun morningStretch(data: FirewatchData, now: Long, tz: TimeZone): Double? {
        val (day, nextWake) = currentDay(data, now, tz)
        if (now >= day.sleepAt || now < day.wakeAt) return null
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

    /** Stretch, pull and net for a past (or the current) waking day, judged against that day's tier. */
    fun day(data: FirewatchData, date: LocalDate, tz: TimeZone, now: Long): DayBattery? {
        val day = Waking.day(data, date, tz)
        if (day.wakeAt > now) return null
        val target = targetAt(data, day.wakeAt) ?: data.rungChanges.firstOrNull { it.at < day.sleepAt }?.pieces ?: return null
        val nextWake = Waking.day(data, date.plus(1, DateTimeUnit.DAY), tz).wakeAt
        val lateDose = dayDoses(data, day, nextWake).lastOrNull { it.at >= day.sleepAt }?.at ?: 0L
        val sim = simulate(data, day, nextWake, intervalFor(target), now, lateDose)
        if (Relapse.isModeDay(data, date, tz)) return DayBattery(date, 0.0, 0.0, paused = true)
        return DayBattery(date, sim.stretch, sim.pull, sizeNetMin = sim.sizeNet)
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
        val sim = simulate(data, day, nextWake, intervalFor(target), dose.at, dose.at, stopBefore = dose)
        return sim.fullBeforeLast == true || sim.charge >= 1.0 - 1e-9
    }

    /** True if [dose] was taken with a full battery and wasn't the day's first piece (the cheer). */
    fun waitedForFull(data: FirewatchData, dose: Dose, tz: TimeZone): Boolean {
        val target = targetAt(data, dose.at) ?: return false
        val (day, nextWake) = currentDay(data, dose.at, tz)
        val earlier = dayDoses(data, day, nextWake).any { it.at < dose.at && it.id != dose.id }
        if (!earlier) return false
        val sim = simulate(data, day, nextWake, intervalFor(target), dose.at, dose.at, stopBefore = dose)
        return sim.fullBeforeLast == true || sim.charge >= 1.0 - 1e-9
    }

}
