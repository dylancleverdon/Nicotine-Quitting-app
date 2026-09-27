package com.baastiklabs.firewatch.core

import com.baastiklabs.firewatch.core.model.Craving
import com.baastiklabs.firewatch.core.model.CravingOutcome
import com.baastiklabs.firewatch.core.model.Dose
import com.baastiklabs.firewatch.core.records.FirewatchData
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
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

object Days {
    /** Per-calendar-day totals. Days with no doses or cravings are absent. */
    fun summaries(data: FirewatchData, tz: TimeZone, now: Long): Map<LocalDate, DaySummary> {
        val ref = data.referenceMg
        val result = HashMap<LocalDate, DaySummary>()
        for (dose in data.doses) {
            val date = dose.at.localDate(tz)
            val s = result[date] ?: DaySummary(date)
            val mg = dose.absorbedMg()
            val pieces = Absorption.pieces(mg, ref)
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
            val date = craving.at.localDate(tz)
            val s = result[date] ?: DaySummary(date)
            val outcome = Cravings.effectiveOutcome(craving, data.doses, now)
            result[date] = s.copy(
                cravings = s.cravings + 1,
                cravingsRodeOut = s.cravingsRodeOut + if (outcome == CravingOutcome.RODE_OUT) 1 else 0,
            )
        }
        return result
    }

    fun dosesOn(data: FirewatchData, date: LocalDate, tz: TimeZone): List<Dose> =
        data.doses.filter { it.at.localDate(tz) == date }

    fun cravingsOn(data: FirewatchData, date: LocalDate, tz: TimeZone): List<Craving> =
        data.cravings.filter { it.at.localDate(tz) == date }
}

object Cravings {
    /** A dose within this long after a craving started means D used. */
    const val USED_WINDOW_MS = 30 * 60_000L

    /** An unanswered craving is assumed ridden out after this long. */
    const val AUTO_CLOSE_MS = 60 * 60_000L

    fun effectiveOutcome(craving: Craving, doses: List<Dose>, now: Long): CravingOutcome {
        if (craving.outcome != CravingOutcome.OPEN) return craving.outcome
        val usedSoonAfter = doses.any { it.at >= craving.at && it.at - craving.at <= USED_WINDOW_MS }
        return when {
            usedSoonAfter -> CravingOutcome.USED
            now - craving.at >= AUTO_CLOSE_MS -> CravingOutcome.RODE_OUT
            else -> CravingOutcome.OPEN
        }
    }

    /** The most recent craving still in progress, if any. */
    fun active(data: FirewatchData, now: Long): Craving? =
        data.cravings.lastOrNull { Cravings.effectiveOutcome(it, data.doses, now) == CravingOutcome.OPEN }
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
