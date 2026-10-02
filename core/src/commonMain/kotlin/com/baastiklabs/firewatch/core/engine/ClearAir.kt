package com.baastiklabs.firewatch.core.engine

import com.baastiklabs.firewatch.core.DayState
import com.baastiklabs.firewatch.core.Days
import com.baastiklabs.firewatch.core.localDate
import com.baastiklabs.firewatch.core.records.FirewatchData
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

/**
 * Life at Clear Air: the finish line. Days nicotine-free only ever go up (never a streak); a dose
 * is counted plainly and nothing resets. No battery, stretch or pull (there's no gap).
 */
object ClearAir {
    private const val DAY = 24 * 3_600_000L

    /** Working at Clear Air right now. */
    fun active(data: FirewatchData): Boolean = data.targetPieces == 0.0

    /** Every clear 🌿 day so far (marked, or an empty day at Clear Air). Only ever goes up. */
    fun daysFree(data: FirewatchData, now: Long, tz: TimeZone): Int = freeDates(data, now, tz).size

    fun freeDates(data: FirewatchData, now: Long, tz: TimeZone): List<LocalDate> {
        val start = Days.startDate(data, tz) ?: return emptyList()
        val today = BatteryEngine.currentDay(data, now, tz).first.date
        val out = ArrayList<LocalDate>()
        var d = start
        while (d < today) {
            if (Days.state(data, d, tz, now) == DayState.CLEAR) out += d
            d = d.plus(1, DateTimeUnit.DAY)
        }
        return out
    }

    /** Cumulative days nicotine-free by date (the chart). */
    fun daysFreeSeries(data: FirewatchData, now: Long, tz: TimeZone): List<Pair<LocalDate, Int>> =
        freeDates(data, now, tz).mapIndexed { i, d -> d to i + 1 }

    /**
     * "Your last 7 days were nicotine-free. Switch to Clear Air?": 7 known days measuring zero while
     * working above Clear Air. Offered, never automatic; "Not now" hides it for a week.
     */
    fun offer(data: FirewatchData, now: Long, tz: TimeZone): Boolean {
        val target = data.targetPieces ?: return false
        if (target <= 0 || data.relapseOn) return false
        if (now - data.settings.clearAirOfferSnoozedAt < 7 * DAY) return false
        val today = now.localDate(tz)
        if (Progress.knownDays(data, today, tz) < 7) return false
        return (Progress.rollingAverage(data, today, tz) ?: return false) <= 0.0
    }

    /** Receptor healing toward the typical range, 0..1, from the model's peak load. */
    fun receptorHealing(data: FirewatchData, now: Long, tz: TimeZone): Double? {
        val o = Receptors.outlook(data, now, tz) ?: return null
        val peak = o.history.maxOfOrNull { it.load } ?: return null
        if (peak <= Receptors.TYPICAL) return 1.0
        return ((peak - o.todayLoad) / (peak - Receptors.TYPICAL)).coerceIn(0.0, 1.0)
    }
}
