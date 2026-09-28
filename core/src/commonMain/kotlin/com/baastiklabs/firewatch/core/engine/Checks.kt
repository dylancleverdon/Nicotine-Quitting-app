package com.baastiklabs.firewatch.core.engine

import com.baastiklabs.firewatch.core.records.FirewatchData
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Taps on the hidden next-piece timer ("Hide next piece timer", opt-in). */
object Checks {
    data class Day(val date: LocalDate, val total: Int, val charging: Int)

    /** Checks during one waking day (wake to next wake). */
    fun day(data: FirewatchData, date: LocalDate, tz: TimeZone): Day {
        val from = Waking.day(data, date, tz).wakeAt
        val to = Waking.day(data, date.plus(1, DateTimeUnit.DAY), tz).wakeAt
        val cs = data.timerChecks.filter { it.at in from until to }
        return Day(date, cs.size, cs.count { it.charging })
    }

    /** The last [n] waking days before [today] that are on or after the first check. */
    fun history(data: FirewatchData, today: LocalDate, tz: TimeZone, n: Int): List<Day> {
        val first = data.timerChecks.firstOrNull()?.at ?: return emptyList()
        val firstDate = BatteryEngine.currentDay(data, first, tz).first.date
        return (1..n).map { today.minus(it, DateTimeUnit.DAY) }.filter { it >= firstDate }.map { day(data, it, tz) }.reversed()
    }

    /** "You've been checking the battery less than before": last 7 days vs the 14 before. */
    fun trendNote(data: FirewatchData, today: LocalDate, tz: TimeZone): String? {
        if (!data.settings.hideTimer) return null
        val all = history(data, today, tz, 21)
        if (all.size < 14) return null
        val recent = all.takeLast(7).map { it.charging }.average()
        val before = all.dropLast(7).map { it.charging }.average()
        return when {
            before > 0 && recent <= before * 0.7 -> "You've been checking the timer less than before, a good sign."
            else -> null
        }
    }
}
