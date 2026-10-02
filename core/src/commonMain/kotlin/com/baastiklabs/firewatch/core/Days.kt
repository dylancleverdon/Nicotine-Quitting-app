package com.baastiklabs.firewatch.core

import com.baastiklabs.firewatch.core.model.Craving
import com.baastiklabs.firewatch.core.model.CravingOutcome
import com.baastiklabs.firewatch.core.model.Dose
import com.baastiklabs.firewatch.core.records.FirewatchData
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

fun Long.localDate(tz: TimeZone): LocalDate = Instant.fromEpochMilliseconds(this).toLocalDateTime(tz).date

data class DaySummary(
    val date: LocalDate,
    val absorbedMg: Double = 0.0,
    val pieces: Double = 0.0,
    val doseCount: Int = 0,
    val borrowedPieces: Double = 0.0,
    val cravings: Int = 0,
    val cravingsRodeOut: Int = 0,
    val firstDoseAt: Long? = null,
    val lastDoseAt: Long? = null,
)

/**
 * What a full waking day is, for counting. LOGGED and CLEAR are "known" and count everywhere;
 * UNKNOWN ("?": nothing logged, not explained) and GHOST (D chose not to log it) are left out of
 * every figure. OPEN = the day isn't over yet; BEFORE = before D started.
 */
enum class DayState { LOGGED, CLEAR, UNKNOWN, GHOST, OPEN, BEFORE;
    val known: Boolean get() = this == LOGGED || this == CLEAR
    val leftOut: Boolean get() = this == UNKNOWN || this == GHOST
}

object Days {
    /** The first waking day that counts: D's first level or first dose, whichever came first. */
    fun startDate(data: FirewatchData, tz: TimeZone): LocalDate? =
        listOfNotNull(data.rungChanges.firstOrNull()?.at, data.doses.firstOrNull()?.at).minOrNull()?.let { wakingDate(data, it, tz) }

    /** Any dose in [from, to) (doses are sorted by time). */
    fun anyDose(data: FirewatchData, from: Long, to: Long): Boolean {
        val ds = data.doses
        var lo = 0; var hi = ds.size
        while (lo < hi) { val m = (lo + hi) / 2; if (ds[m].at < from) lo = m + 1 else hi = m }
        return lo < ds.size && ds[lo].at < to
    }

    /**
     * The one rule for which days count. A day becomes "?" only once its waking day is over (at the
     * next wake-up); opening the app, cravings or Good morning / night never make it clear.
     */
    fun state(data: FirewatchData, date: LocalDate, tz: TimeZone, now: Long): DayState {
        val start = startDate(data, tz) ?: return DayState.BEFORE
        if (date < start) return DayState.BEFORE
        val w = com.baastiklabs.firewatch.core.engine.Waking.day(data, date, tz)
        val next = com.baastiklabs.firewatch.core.engine.Waking.day(data, date.plus(1, kotlinx.datetime.DateTimeUnit.DAY), tz).wakeAt
        if (anyDose(data, w.wakeAt, next)) return if (next > now) DayState.OPEN else DayState.LOGGED
        if (next > now) return DayState.OPEN
        return when (data.dayMarks[date.toString()]) {
            com.baastiklabs.firewatch.core.model.DayMark.CLEAR -> DayState.CLEAR
            com.baastiklabs.firewatch.core.model.DayMark.GHOST -> DayState.GHOST
            // At Clear Air an empty day is the expected, nicotine-free day: D logs only when they
            // had some, so it's clear without marking.
            else -> if (data.rungChanges.lastOrNull { it.at < next }?.pieces == 0.0) DayState.CLEAR else DayState.UNKNOWN
        }
    }

    fun known(data: FirewatchData, date: LocalDate, tz: TimeZone, now: Long): Boolean = state(data, date, tz, now).known

    /**
     * The one "which day" rule: the waking day (wake-up to the next wake-up) that [t] belongs to.
     * A 1 AM piece counts toward the night before. Daily counts everywhere use this; continuous
     * things (the blood-level wave, receptors, the hour heatmap) keep clock time.
     */
    fun wakingDate(data: FirewatchData, t: Long, tz: TimeZone): LocalDate =
        com.baastiklabs.firewatch.core.engine.BatteryEngine.currentDay(data, t, tz).first.date

    /** Per-waking-day totals. Days with no doses or cravings are absent. */
    fun summaries(data: FirewatchData, tz: TimeZone, now: Long): Map<LocalDate, DaySummary> {
        val result = HashMap<LocalDate, DaySummary>()
        for (dose in data.doses) {
            val date = wakingDate(data, dose.at, tz)
            val s = result[date] ?: DaySummary(date)
            val mg = dose.absorbedMg()
            val pieces = data.piecesOf(dose)
            result[date] = s.copy(
                absorbedMg = s.absorbedMg + mg,
                pieces = s.pieces + pieces,
                doseCount = s.doseCount + 1,
                borrowedPieces = s.borrowedPieces + if (dose.borrowed) pieces else 0.0,
                firstDoseAt = minOf(s.firstDoseAt ?: dose.at, dose.at),
                lastDoseAt = maxOf(s.lastDoseAt ?: dose.at, dose.at),
            )
        }
        for (craving in data.cravings) {
            val date = wakingDate(data, craving.at, tz)
            val s = result[date] ?: DaySummary(date)
            val outcome = Cravings.effectiveOutcome(data, craving, now, tz)
            result[date] = s.copy(
                cravings = s.cravings + 1,
                cravingsRodeOut = s.cravingsRodeOut + if (outcome == CravingOutcome.RODE_OUT) 1 else 0,
            )
        }
        return result
    }

    fun dosesOn(data: FirewatchData, date: LocalDate, tz: TimeZone): List<Dose> =
        data.doses.filter { wakingDate(data, it.at, tz) == date }

    fun cravingsOn(data: FirewatchData, date: LocalDate, tz: TimeZone): List<Craving> =
        data.cravings.filter { wakingDate(data, it.at, tz) == date }
}

/** How a craving ended, worked out from the logs (never stored, so older versions are unaffected). */
enum class CravingResult(val title: String, val win: Boolean) {
    /** Still within the 45-minute window, nothing logged yet. */
    PENDING("In progress", false),
    RODE_OUT("Rode it out", true),
    /** A piece came, but only once the battery was full (or on schedule). */
    WAITED("Waited for the right time", true),
    /** An early gum, lozenge, patch or pouch. A miss, not a relapse. */
    EARLY("Early", false),
    /** A cigarette or vape (including a friend's). */
    RELAPSE("Relapse", false),
    /** A piece during the baseline week, before there's a target to judge against. */
    BASELINE("Had a piece", false),
}

object Cravings {
    /** A dose within this long after a craving started is linked to it. */
    const val WINDOW_MS = 45 * 60_000L
    @Deprecated("Use WINDOW_MS") const val USED_WINDOW_MS = WINDOW_MS
    const val AUTO_CLOSE_MS = WINDOW_MS

    fun isRelapseDose(d: Dose): Boolean =
        d.kind == com.baastiklabs.firewatch.core.model.ProductKind.CIGARETTE ||
            d.kind == com.baastiklabs.firewatch.core.model.ProductKind.VAPE || d.borrowed

    /**
     * How [craving] ended: no dose within 45 minutes = rode it out; a dose once the battery was full
     * (or on schedule in Relapse prevention mode) = waited; a cigarette or vape = relapse; anything
     * else early = early. Old "I used" / "It passed" taps are re-read the same way from the logs.
     */
    fun result(data: FirewatchData, craving: Craving, now: Long, tz: TimeZone = TimeZone.currentSystemDefault()): CravingResult {
        val dose = data.doses.firstOrNull { !it.estimated && it.at >= craving.at && it.at - craving.at <= WINDOW_MS }
        if (dose == null) {
            return when {
                craving.outcome == CravingOutcome.USED -> CravingResult.EARLY
                craving.outcome == CravingOutcome.RODE_OUT || now - craving.at >= WINDOW_MS -> CravingResult.RODE_OUT
                else -> CravingResult.PENDING
            }
        }
        if (isRelapseDose(dose)) return CravingResult.RELAPSE
        val onTime = com.baastiklabs.firewatch.core.engine.BatteryEngine.onTime(data, dose, tz) ?: return CravingResult.BASELINE
        return if (onTime) CravingResult.WAITED else CravingResult.EARLY
    }

    /**
     * The older two-way view many figures use: beaten (rode out or waited) = RODE_OUT, a miss = USED,
     * still going = OPEN.
     */
    fun effectiveOutcome(data: FirewatchData, craving: Craving, now: Long, tz: TimeZone = TimeZone.currentSystemDefault()): CravingOutcome =
        when (val r = result(data, craving, now, tz)) {
            CravingResult.PENDING -> CravingOutcome.OPEN
            else -> if (r.win) CravingOutcome.RODE_OUT else CravingOutcome.USED
        }

    /** The most recent craving still inside its 45-minute window with nothing logged. */
    fun active(data: FirewatchData, now: Long): Craving? =
        data.cravings.lastOrNull { now - it.at < WINDOW_MS && result(data, it, now) == CravingResult.PENDING }
}

sealed interface BaselineStatus {
    /** Nothing logged yet; the baseline week starts with the first log. */
    data object NotStarted : BaselineStatus

    data class InProgress(val dayNumber: Int, val daysLeft: Int) : BaselineStatus

    data class Complete(val startedOn: LocalDate) : BaselineStatus
}

/** For the first 7 days the app only logs and graphs; then it reveals the starting tier. */
object Baseline {
    const val DAYS = 7

    fun status(data: FirewatchData, today: LocalDate, tz: TimeZone): BaselineStatus {
        val first = data.doses.minOfOrNull { it.at }?.localDate(tz) ?: return BaselineStatus.NotStarted
        val dayNumber = first.daysUntil(today) + 1
        return if (dayNumber <= DAYS) {
            BaselineStatus.InProgress(dayNumber.coerceAtLeast(1), DAYS - dayNumber)
        } else {
            BaselineStatus.Complete(first)
        }
    }

    /** Average pieces a day across the baseline week (days with nothing logged count as zero). */
    fun averagePiecesPerDay(summaries: Map<LocalDate, DaySummary>, startedOn: LocalDate): Double {
        val total = summaries.values
            .filter { it.date >= startedOn && startedOn.daysUntil(it.date) < DAYS }
            .sumOf { it.pieces }
        return total / DAYS
    }
}

/** Colour levels for the calendar grid, aligned loosely with the tier ladder. */
object CalendarScale {
    data class Band(val level: Int, val label: String, val maxPieces: Double)

    val bands: List<Band> = listOf(
        Band(0, "None", 0.0),
        Band(1, "Up to 1", 1.0),
        Band(2, "Up to 3", 3.0),
        Band(3, "Up to 5", 5.0),
        Band(4, "Up to 8", 8.0),
        Band(5, "Up to 12", 12.0),
        Band(6, "Over 12", Double.MAX_VALUE),
    )

    fun level(pieces: Double): Int {
        if (pieces <= 0.0001) return 0
        return bands.first { it.level > 0 && pieces <= it.maxPieces }.level
    }
}
