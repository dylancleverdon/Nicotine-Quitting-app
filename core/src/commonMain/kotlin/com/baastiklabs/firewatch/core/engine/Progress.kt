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
) {
    val netMinutesToday: Double get() = stretchMinutesToday - pullMinutesToday
}

data class HeadsUp(val message: String)

object Progress {
    private const val MIN = 60_000L
    const val ROLLING_DAYS = 7

    fun waking(data: FirewatchData, date: LocalDate, tz: TimeZone) = Waking.day(data, date, tz)

    /** Doses in a waking day window (so a 1am dose counts toward the previous day). */
    fun pace(data: FirewatchData, date: LocalDate, tz: TimeZone): DayPace {
        val day = Waking.day(data, date, tz)
        val next = Waking.day(data, date.plus(1, DateTimeUnit.DAY), tz)
        val ref = data.referenceMg
        val pieces = data.doses.filter { it.at >= day.wakeAt && it.at < next.wakeAt }.sumOf { it.pieces(ref) }
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
        val since = data.rungChanges.lastOrNull()?.at ?: return null
        val hold = data.settings.holdDays
        if (now - data.settings.stepDownSnoozedAt < 24 * 60 * MIN) return null
        val today = now.localDate(tz)
        val sinceDate = since.localDate(tz)
        val lastFull = today.minus(1, DateTimeUnit.DAY)
        val firstCounted = lastFull.minus(hold - 1, DateTimeUnit.DAY)
        if (firstCounted <= sinceDate) return null
        val ok = generateSequence(firstCounted) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it <= lastFull }
            .all { pace(data, it, tz).scaled <= target + 0.25 }
        return if (ok) Ladder.nextDown(target) else null
    }

    fun headsUps(data: FirewatchData, now: Long, tz: TimeZone): List<HeadsUp> {
        val out = mutableListOf<HeadsUp>()
        val recent = data.doses.filter { !it.estimated && now - it.at in 0..(3 * 60 * MIN) }.sortedBy { it.at }
        if (recent.size >= 2) {
            val last = recent.last()
            val prev = recent[recent.size - 2]
            if (last.at - prev.at < Kinetics.peakMinutes(prev.speed) * MIN) {
                out += HeadsUp("Double-up: the last dose went in while the one before was still peaking.")
            }
        }
        val today = pace(data, now.localDate(tz), tz).pieces
        if (today >= 20) out += HeadsUp("Today is near the 24-pieces-a-day maximum printed on gum boxes.")
        return out
    }
}
