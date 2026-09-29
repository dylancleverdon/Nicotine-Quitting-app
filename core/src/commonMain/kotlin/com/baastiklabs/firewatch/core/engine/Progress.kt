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

/** Pieces for one waking day, scaled to a 16-hour day so short/long days compare fairly. */
data class DayPace(val date: LocalDate, val pieces: Double, val awakeMinutes: Double) {
    val scaled: Double get() = if (awakeMinutes <= 0) pieces else pieces * Ladder.WAKING_MINUTES / awakeMinutes
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
) {
    val netMinutesToday: Double get() = stretchMinutesToday - pullMinutesToday
}

data class HeadsUp(val message: String)

/** Full days in a row held at or under the target, toward the next step-down offer. */
data class StepDownProgress(val held: Int, val needed: Int, val next: Rung) {
    val ready: Boolean get() = held >= needed
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

    /** 7-day rolling average of scaled pieces a day, ending with [today] (partial days excluded). */
    fun rollingAverage(data: FirewatchData, today: LocalDate, tz: TimeZone, days: Int = ROLLING_DAYS): Double? {
        val first = data.doses.minOfOrNull { it.at }?.localDate(tz) ?: return null
        val end = today.minus(1, DateTimeUnit.DAY)
        if (end < first) return null
        val start = maxOf(first, end.minus(days - 1, DateTimeUnit.DAY))
        val paces = generateSequence(start) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it <= end }
            .map { pace(data, it, tz) }.toList()
        return paces.sumOf { it.scaled } / paces.size
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
        // A practice day (or its "How was it?" follow-up) stands in for the offer.
        if (Control.practicingToday(data, now, tz) || Control.practiceFollowUp(data, now, tz) != null) return null
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
        while (held < hold && day > sinceDate && pace(data, day, tz).scaled <= target + 0.25) {
            held++
            day = day.minus(1, DateTimeUnit.DAY)
        }
        return StepDownProgress(held, hold, Ladder.nextDown(target))
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
