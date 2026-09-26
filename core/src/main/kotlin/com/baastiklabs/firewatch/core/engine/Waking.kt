package com.baastiklabs.firewatch.core.engine

import com.baastiklabs.firewatch.core.model.SleepKind
import com.baastiklabs.firewatch.core.records.FirewatchData
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

/** A waking day: from getting up to going to sleep (which may be after midnight). */
data class WakingDay(val date: LocalDate, val wakeAt: Long, val sleepAt: Long) {
    val awakeMinutes: Double get() = (sleepAt - wakeAt) / 60_000.0
    operator fun contains(t: Long) = t in wakeAt until sleepAt
}

/**
 * Waking hours from the default schedule, corrected by Good morning / Good night taps.
 * A tap within a few hours of the usual time replaces it for that day.
 */
object Waking {
    private const val H = 3_600_000L

    fun day(data: FirewatchData, date: LocalDate, tz: TimeZone): WakingDay {
        val s = data.settings
        val defaultWake = date.atTime(LocalTime(s.wakeMinutes / 60, s.wakeMinutes % 60)).toInstant(tz).toEpochMilliseconds()
        val sleepDate = if (s.sleepMinutes <= s.wakeMinutes) date.plus(1, DateTimeUnit.DAY) else date
        val defaultSleep = sleepDate.atTime(LocalTime((s.sleepMinutes / 60) % 24, s.sleepMinutes % 60)).toInstant(tz).toEpochMilliseconds()

        val wake = data.sleepEvents
            .filter { it.kind == SleepKind.WAKE && it.at in (defaultWake - 6 * H)..(defaultWake + 8 * H) }
            .minByOrNull { kotlin.math.abs(it.at - defaultWake) }?.at ?: defaultWake
        val sleep = data.sleepEvents
            .filter { it.kind == SleepKind.SLEEP && it.at in (wake + 4 * H)..(defaultSleep + 8 * H) }
            .minByOrNull { kotlin.math.abs(it.at - defaultSleep) }?.at ?: defaultSleep
        return WakingDay(date, wake, maxOf(sleep, wake + H))
    }

    fun isAwake(days: List<WakingDay>, t: Long): Boolean = days.any { t in it }
}
