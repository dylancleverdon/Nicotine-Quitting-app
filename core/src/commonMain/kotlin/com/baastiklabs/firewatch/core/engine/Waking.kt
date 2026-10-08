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
import kotlinx.datetime.toLocalDateTime

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
        @Suppress("NAME_SHADOWING")
        val tz = zoneFor(data, date, tz)
        val defaultWake = date.atTime(LocalTime(s.wakeMinutes / 60, s.wakeMinutes % 60)).toInstant(tz).toEpochMilliseconds()
        val sleepDate = if (s.sleepMinutes <= s.wakeMinutes) date.plus(1, DateTimeUnit.DAY) else date
        val defaultSleep = sleepDate.atTime(LocalTime((s.sleepMinutes / 60) % 24, s.sleepMinutes % 60)).toInstant(tz).toEpochMilliseconds()

        val tapWake = data.sleepEvents
            .filter { it.kind == SleepKind.WAKE && it.at in (defaultWake - 6 * H)..(defaultWake + 8 * H) }
            .minByOrNull { kotlin.math.abs(it.at - defaultWake) }?.at ?: defaultWake
        val wake = morningStart(data, defaultWake, tapWake) ?: tapWake
        val sleep = data.sleepEvents
            .filter { it.kind == SleepKind.SLEEP && it.at in (wake + 4 * H)..(defaultSleep + 8 * H) }
            .minByOrNull { kotlin.math.abs(it.at - defaultSleep) }?.at ?: defaultSleep
        return WakingDay(date, wake, maxOf(sleep, wake + H))
    }

    /**
     * Morning logs: a piece logged in the morning before Good morning starts the day (the day begins
     * at that piece or at Good morning, whichever is earlier). "Morning" is within 3 hours before
     * the usual wake time, or after a Good night tap (up to 6 hours before it). Pieces before a Good
     * night tap, and back-dated pieces, stay with the night before.
     */
    private fun morningStart(data: FirewatchData, defaultWake: Long, tapWake: Long): Long? {
        val goodNight = data.sleepEvents.lastOrNull { it.kind == SleepKind.SLEEP && it.at in (defaultWake - 10 * H) until tapWake }?.at
        val from = goodNight?.let { maxOf(it, defaultWake - 6 * H) } ?: (defaultWake - 3 * H)
        val doses = data.doses
        // Doses are sorted by time: binary search for the first at or after [from].
        var lo = 0
        var hi = doses.size
        while (lo < hi) { val mid = (lo + hi) ushr 1; if (doses[mid].at < from) lo = mid + 1 else hi = mid }
        var i = lo
        while (i < doses.size && doses[i].at < tapWake) {
            if (!doses[i].estimated) return doses[i].at
            i++
        }
        return null
    }

    /**
     * The time zone the usual day is in on [date] (travel): the latest switch made before that
     * day, else [fallback] (the phone's zone).
     */
    fun zoneFor(data: FirewatchData, date: LocalDate, fallback: TimeZone): TimeZone {
        val h = data.settings.zoneHistory
        if (h.isEmpty()) return fallback
        var zone: TimeZone? = null
        for (e in h) {
            val parts = e.split("|")
            val from = parts.getOrNull(0)?.toLongOrNull() ?: continue
            val z = runCatching { TimeZone.of(parts.getOrNull(1) ?: "") }.getOrNull() ?: continue
            val fromDate = kotlinx.datetime.Instant.fromEpochMilliseconds(from).toLocalDateTime(z).date
            // The first entry covers everything before it; later ones apply from the next day.
            if (zone == null || date > fromDate) zone = z
        }
        return zone ?: fallback
    }

    /** "<now>|<zone>" to append when D chooses local times (or the first zone ever seen). */
    fun zoneEntry(now: Long, zone: String) = "$now|$zone"

    /** Hours between two zones right now (+ = [to] is ahead), for "now 3 hours ahead". */
    fun offsetHours(from: String, to: String, now: Long): Double = runCatching {
        val i = kotlinx.datetime.Instant.fromEpochMilliseconds(now)
        fun off(z: String) = with(TimeZone.of(z)) { i.toLocalDateTime(this) }.let { lt -> lt.toInstant(TimeZone.UTC).toEpochMilliseconds() - now }
        (off(to) - off(from)) / 3_600_000.0
    }.getOrDefault(0.0)

    fun isAwake(days: List<WakingDay>, t: Long): Boolean = days.any { t in it }
}
