package com.baastiklabs.firewatch.core.engine

import com.baastiklabs.firewatch.core.Absorption
import com.baastiklabs.firewatch.core.Baseline
import com.baastiklabs.firewatch.core.BaselineStatus
import com.baastiklabs.firewatch.core.absorbedMgHigh
import com.baastiklabs.firewatch.core.localDate
import com.baastiklabs.firewatch.core.pieces
import com.baastiklabs.firewatch.core.records.FirewatchData
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Pieces for one waking day. No scaling for short or long days (removed in 0.15): a day counts
 * exactly what D had. [scaled] is kept as a name only and equals [pieces].
 */
data class DayPace(val date: LocalDate, val pieces: Double, val awakeMinutes: Double) {
    val scaled: Double get() = pieces
}

/** WIND_DOWN is no longer produced (0.9): wind-down is a note on the normal guidance ([Battery.closeToBed]). */
enum class BatteryState { CLEAR, CHARGING, FULL_AT_WAKE, MORNING_DELAY, WIND_DOWN, ASLEEP }

data class Battery(
    val charge: Double,
    val state: BatteryState,
    /** When the next piece is recommended (null when clear now). */
    val readyAt: Long?,
    /** Minutes today the battery sat full while D held off. */
    val stretchMinutesToday: Double,
    val intervalMinutes: Double,
    /** Minutes today pieces were taken before the battery was full. */
    val pullMinutesToday: Double = 0.0,
    /** In the last hour before usual bedtime (wind-down on): show a bedtime note, keep the guidance. */
    val closeToBed: Boolean = false,
    /** Practice pace is on at this many pieces a day (the battery and preview use its gap). */
    val practicePieces: Double? = null,
) {
    val netMinutesToday: Double get() = stretchMinutesToday - pullMinutesToday
}

data class HeadsUp(val message: String)

/** Full days in a row held at or under the target, toward the next step-down offer. */
/**
 * [unlocked]: the hold was reached at some point since the last level change. Once unlocked it
 * stays unlocked (a step-down button in Insights) until the level changes, even after "Stay here"
 * or a heavier day.
 */
data class StepDownProgress(val held: Int, val needed: Int, val next: Rung, val unlocked: Boolean = false) {
    val ready: Boolean get() = unlocked || held >= needed
}

object Progress {
    private const val MIN = 60_000L
    const val ROLLING_DAYS = 7

    fun waking(data: FirewatchData, date: LocalDate, tz: TimeZone) = Waking.day(data, date, tz)

    /** Doses in a waking day window (so a 1am dose counts toward the previous day). */
    fun pace(data: FirewatchData, date: LocalDate, tz: TimeZone): DayPace {
        val day = Waking.day(data, date, tz)
        val next = Waking.day(data, date.plus(1, DateTimeUnit.DAY), tz)
        val ref = data.referenceMg
        val pieces = data.doses.filter { it.at >= day.wakeAt && it.at < next.wakeAt }.sumOf { data.piecesOf(it) }
        return DayPace(date, pieces, day.awakeMinutes)
    }

    /** Is this past day counted ("?" and ghost days are left out everywhere)? */
    fun known(data: FirewatchData, date: LocalDate, tz: TimeZone): Boolean =
        com.baastiklabs.firewatch.core.Days.state(data, date, tz, Long.MAX_VALUE).known

    /**
     * 7-day rolling average of scaled pieces a day over the known days among the last [days] full
     * days before [today] ("?" and ghost days left out; clear days count as 0).
     */
    fun rollingAverage(data: FirewatchData, today: LocalDate, tz: TimeZone, days: Int = ROLLING_DAYS): Double? {
        val first = data.doses.minOfOrNull { it.at }?.localDate(tz) ?: return null
        val end = today.minus(1, DateTimeUnit.DAY)
        if (end < first) return null
        val start = maxOf(first, end.minus(days - 1, DateTimeUnit.DAY))
        val paces = generateSequence(start) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it <= end }
            .filter { known(data, it, tz) }.map { pace(data, it, tz) }.toList()
        if (paces.isEmpty()) return null
        return paces.sumOf { it.scaled } / paces.size
    }

    /** The last [n] full days, oldest first, with their state (the confidence strip). */
    fun recentStates(data: FirewatchData, now: Long, tz: TimeZone, n: Int = 30): List<Pair<LocalDate, com.baastiklabs.firewatch.core.DayState>> {
        val today = BatteryEngine.currentDay(data, now, tz).first.date
        return (n downTo 1).map { today.minus(it, DateTimeUnit.DAY) }.map { it to com.baastiklabs.firewatch.core.Days.state(data, it, tz, now) }
            .filter { it.second != com.baastiklabs.firewatch.core.DayState.BEFORE }
    }

    /** How many of the last [days] full days are known (for "6 of 7 days known"). */
    fun knownDays(data: FirewatchData, today: LocalDate, tz: TimeZone, days: Int = ROLLING_DAYS): Int {
        val start = com.baastiklabs.firewatch.core.Days.startDate(data, tz) ?: return 0
        return (1..days).map { today.minus(it, DateTimeUnit.DAY) }.count { it >= start && known(data, it, tz) }
    }

    fun measuredRung(data: FirewatchData, today: LocalDate, tz: TimeZone): Rung? =
        rollingAverage(data, today, tz)?.let { Ladder.measured(it) }

    /** Tiers are revealed once the baseline week is done. */
    fun tiersRevealed(data: FirewatchData, today: LocalDate, tz: TimeZone): Boolean =
        Baseline.status(data, today, tz) is BaselineStatus.Complete

    /** The next-piece battery right now. See [BatteryEngine]. */
    fun battery(data: FirewatchData, targetPieces: Double, now: Long, tz: TimeZone, lastActivityAt: Long = 0L): Battery =
        BatteryEngine.now(data, targetPieces, now, tz, lastActivityAt)

    /** Offer the next rung down once D has held at or under the target for the chosen days. */
    fun stepDownOffer(data: FirewatchData, now: Long, tz: TimeZone): Rung? {
        val target = data.targetPieces ?: return null
        if (target <= 0) return null
        // First week: the "Your starting point" card replaces the early estimate instead.
        if (Control.isEarly(data)) return null
        // Practice pace (or its "How was it?" follow-up), or the lighter-level offer, stands in for it.
        if (Practice.active(data, now, tz) != null || Practice.followUp(data, now, tz) != null) return null
        if (Practice.lighterOffer(data, now, tz) != null || Practice.workFromOffer(data, now, tz) != null) return null
        if (now - data.settings.stepDownSnoozedAt < 24 * 60 * MIN) return null
        val progress = stepDownProgress(data, now, tz) ?: return null
        return if (progress.ready) progress.next else null
    }

    /**
     * How close the next step-down offer is: full days in a row (ending yesterday, after the last
     * rung change) at or under the target, out of the hold period. Uses the same test as
     * [stepDownOffer], so "3 of 3" and the offer always agree.
     */
    fun stepDownProgress(data: FirewatchData, now: Long, tz: TimeZone): StepDownProgress? {
        val target = data.targetPieces ?: return null
        if (target <= 0) return null
        val since = data.rungChanges.lastOrNull()?.at ?: return null
        val hold = data.settings.holdDays.coerceAtLeast(1)
        val sinceDate = since.localDate(tz)
        var day = now.localDate(tz).minus(1, DateTimeUnit.DAY)
        var held = 0
        // "?" and ghost days are skipped, not counted and not a reset: people have off days.
        while (held < hold && day > sinceDate) {
            if (known(data, day, tz)) {
                if (pace(data, day, tz).scaled > target + 0.25) break
                held++
            }
            day = day.minus(1, DateTimeUnit.DAY)
        }
        // Unlocked: did any run since the level change reach the hold? (Forward scan, same rules.)
        var run = 0
        var unlocked = held >= hold
        var d = sinceDate.plus(1, DateTimeUnit.DAY)
        val yesterday = now.localDate(tz).minus(1, DateTimeUnit.DAY)
        while (!unlocked && d <= yesterday) {
            if (known(data, d, tz)) {
                run = if (pace(data, d, tz).pieces > target + 0.25) 0 else run + 1
                if (run >= hold) unlocked = true
            }
            d = d.plus(1, DateTimeUnit.DAY)
        }
        return StepDownProgress(if (unlocked) hold else held, hold, Ladder.nextDown(target), unlocked)
    }

    fun headsUps(data: FirewatchData, now: Long, tz: TimeZone): List<HeadsUp> {
        val out = mutableListOf<HeadsUp>()
        val recent = data.doses.filter { !it.estimated && now - it.at in 0..(3 * 60 * MIN) }.sortedBy { it.at }
        if (recent.size >= 2) {
            val last = recent.last()
            val prev = recent[recent.size - 2]
            if (last.at - prev.at < Kinetics.peakMinutes(prev) * MIN) {
                out += HeadsUp("Double-up: the last dose went in while the one before was still peaking.")
            }
        }
        val today = pace(data, now.localDate(tz), tz).pieces
        if (today >= 20) out += HeadsUp("Today is near the 24-pieces-a-day maximum printed on gum boxes.")
        return out
    }
}
