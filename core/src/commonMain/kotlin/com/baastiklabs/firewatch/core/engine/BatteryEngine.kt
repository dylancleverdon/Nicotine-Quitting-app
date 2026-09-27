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
data class DayBattery(val date: LocalDate, val stretchMin: Double, val pullMin: Double) {
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

    private class Sim(val charge: Double, val stretch: Double, val pull: Double, val fullBeforeLast: Boolean?)

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
     * Simulates a waking day minute by minute up to [until]. Awake = from wake-up to bedtime, plus
     * any late-night time up to [awakeUntil]. [stopBefore] stops just before that dose (for the cheer).
     */
    private fun simulate(
        data: FirewatchData,
        day: WakingDay,
        nextWake: Long,
        interval: Double,
        until: Long,
        awakeUntil: Long,
        stopBefore: Dose? = null,
    ): Sim {
        val doses = dayDoses(data, day, nextWake)
        val ref = data.referenceMg
        var charge = 1.0
        var stretch = 0.0
        var pull = 0.0
        var di = 0
        var fullBeforeLast: Boolean? = null
        val awakeEnd = maxOf(day.sleepAt, awakeUntil)
        var t = day.wakeAt
        val end = minOf(until, nextWake)
        while (t <= end) {
            while (di < doses.size && doses[di].at <= t) {
                val d = doses[di]
                if (stopBefore != null && d.id == stopBefore.id) {
                    return Sim(charge, stretch, pull, charge >= 1.0 - 1e-9)
                }
                // Size-honest pull: only the part of the dose that didn't fit in the battery.
                val pieces = Absorption.pieces(d.absorbedMg(), ref)
                pull += (pieces - charge).coerceAtLeast(0.0) * interval
                fullBeforeLast = charge >= 1.0 - 1e-9
                charge = (charge - pieces).coerceAtLeast(0.0)
                di++
            }
            if (t < awakeEnd) {
                if (charge >= 1.0 - 1e-9 && t < day.sleepAt) stretch += 1.0
                charge = (charge + 1.0 / interval).coerceAtMost(1.0)
            }
            t += MIN
        }
        return Sim(charge, stretch, pull, fullBeforeLast)
    }

    /** The target rung (pieces a day) in force at time [t], or null before any target. */
    fun targetAt(data: FirewatchData, t: Long): Double? = data.rungChanges.lastOrNull { it.at <= t }?.pieces

    fun now(data: FirewatchData, targetPieces: Double, now: Long, tz: TimeZone, lastActivityAt: Long = 0L): Battery {
        val interval = intervalFor(targetPieces)
        val (day, nextWake) = currentDay(data, now, tz)
        val lateDose = dayDoses(data, day, nextWake).lastOrNull { it.at >= day.sleepAt }?.at ?: 0L
        val activity = maxOf(lastActivityAt, lateDose).takeIf { it in day.sleepAt..now } ?: 0L
        val sim = simulate(data, day, nextWake, interval, now, activity)
        val charge = sim.charge

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
                data.settings.windDown && now > day.sleepAt - 60 * MIN -> state = BatteryState.WIND_DOWN
                else -> state = BatteryState.CLEAR
            }
        }
        return Battery(charge, state, readyAt, sim.stretch, interval, sim.pull)
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
        return DayBattery(date, sim.stretch, sim.pull)
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

    /**
     * "A gum 2 mg fits now": while charging with at least half a piece of room, the biggest home
     * product that fits the current charge (by its estimated pieces). Null otherwise.
     */
    fun fitsNow(data: FirewatchData, battery: Battery): com.baastiklabs.firewatch.core.model.Product? {
        if (battery.state != BatteryState.CHARGING || battery.charge < 0.5) return null
        val ref = data.referenceMg
        return data.products
            .filter { it.onHome && !it.archived && it.borrowedFrom == null }
            .map { it to Absorption.pieces(Absorption.absorbedMg(it), ref) }
            .filter { (_, p) -> p > 0.0 && p <= battery.charge + 1e-9 }
            .maxByOrNull { it.second }?.first
    }
}
