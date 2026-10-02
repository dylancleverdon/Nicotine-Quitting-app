package com.baastiklabs.firewatch.core.engine

import com.baastiklabs.firewatch.core.localDate
import com.baastiklabs.firewatch.core.model.PracticeSession
import com.baastiklabs.firewatch.core.records.FirewatchData
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Practice pace: try a lighter pace without changing D's level. The battery and dose preview use
 * the practice gap; level, tier, measured level, normal net, steady days, the step-down count and
 * the taper plan never change. Practice net is counted only while it's on, against its gap.
 * Never in Relapse prevention mode (its reminders follow the level's gap).
 */
object Practice {
    private const val MIN = 60_000L
    private const val DAY = 24 * 60 * MIN

    /** Share of the hold period's waking hours practice pace must cover before "Work from". */
    const val COVERAGE = 0.75

    data class Window(val start: Long, val end: Long, val pieces: Double, val sessionId: String)

    /** When a session stops: its end, bedtime of the waking day it's in (if "turn off at bedtime"), or never. */
    fun effectiveEnd(data: FirewatchData, s: PracticeSession, tz: TimeZone): Long? {
        val bed = if (s.untilBedtime) {
            val (day, nextWake) = BatteryEngine.currentDay(data, s.start, tz)
            if (s.start < day.sleepAt) day.sleepAt else nextWake
        } else null
        return listOfNotNull(s.end, bed).minOrNull()
    }

    /** The session on right now, if any. */
    fun active(data: FirewatchData, now: Long, tz: TimeZone): PracticeSession? {
        if (data.relapseOn) return null
        return data.practices.lastOrNull { it.start <= now && (effectiveEnd(data, it, tz)?.let { e -> e > now } ?: true) }
    }

    /** Practice windows overlapping [from, to), clipped to it. */
    fun windows(data: FirewatchData, from: Long, to: Long, tz: TimeZone): List<Window> =
        data.practices.mapNotNull { s ->
            val a = maxOf(s.start, from)
            val b = minOf(effectiveEnd(data, s, tz) ?: Long.MAX_VALUE, to)
            if (b > a) Window(a, b, s.pieces, s.id) else null
        }

    private fun index(pieces: Double) = Ladder.rungs.indexOfFirst { kotlin.math.abs(it.pieces - pieces) < 1e-6 }

    /** How many rungs lighter [lighter] is than [heavier] (rungs run heaviest first). */
    fun rungsLighter(heavier: Double, lighter: Double): Int {
        val a = index(heavier); val b = index(lighter)
        if (a < 0) return 0
        if (lighter <= 0) return Ladder.rungs.size - a
        return if (b < 0) 0 else b - a
    }

    /**
     * Rungs D can practice: one below the working level, or down to the measured level if that's
     * lighter (the logs show D already lives there). Never further, never Clear Air.
     */
    fun allowedRungs(data: FirewatchData, now: Long, tz: TimeZone): List<Rung> {
        val target = data.targetPieces ?: return emptyList()
        if (target <= 0 || data.relapseOn) return emptyList()
        val one = Ladder.nextDown(target)
        if (one.pieces <= 0) return emptyList()
        val measured = Progress.measuredRung(data, now.localDate(tz), tz)?.pieces?.takeIf { it > 0 }
        val lightest = minOf(one.pieces, measured ?: one.pieces)
        return Ladder.rungs.filter { it.pieces < target - 1e-6 && it.pieces >= lightest - 1e-6 }
    }

    // ---- Coverage and practice net for a run ----

    /** Sessions at [pieces] since the last level change (the run "Work from" looks at). */
    fun run(data: FirewatchData, pieces: Double): List<PracticeSession> {
        val since = data.rungChanges.lastOrNull()?.at ?: 0L
        return data.practices.filter { it.start >= since && kotlin.math.abs(it.pieces - pieces) < 1e-6 }
    }

    /** Waking minutes (wake-up to bedtime) practice pace covered in [sessions], up to [until]. */
    fun coverageMinutes(data: FirewatchData, sessions: List<PracticeSession>, until: Long, tz: TimeZone): Double {
        var total = 0.0
        for (s in sessions) {
            val end = minOf(effectiveEnd(data, s, tz) ?: until, until)
            if (end <= s.start) continue
            var date = BatteryEngine.currentDay(data, s.start, tz).first.date
            var guard = 0
            while (guard++ < 400) {
                val w = Waking.day(data, date, tz)
                if (w.wakeAt >= end) break
                val a = maxOf(s.start, w.wakeAt); val b = minOf(end, w.sleepAt)
                if (b > a) total += (b - a) / MIN.toDouble()
                date = date.plus(1, DateTimeUnit.DAY)
            }
        }
        return total
    }

    /** Practice net (minutes) over every waking day the run touched, up to [now]. */
    fun runNet(data: FirewatchData, sessions: List<PracticeSession>, now: Long, tz: TimeZone): Double {
        if (sessions.isEmpty()) return 0.0
        val first = BatteryEngine.currentDay(data, sessions.first().start, tz).first.date
        val last = BatteryEngine.currentDay(data, now, tz).first.date
        var d = first
        var net = 0.0
        while (d <= last) {
            net += BatteryEngine.practiceNetDay(data, d, tz, now) ?: 0.0
            d = d.plus(1, DateTimeUnit.DAY)
        }
        return net
    }

    /** Live status while practice pace is on: the rung, practice net for the run, and which day of it. */
    data class Status(val session: PracticeSession, val rung: Rung, val netMin: Double, val day: Int)

    fun status(data: FirewatchData, now: Long, tz: TimeZone): Status? {
        val s = active(data, now, tz) ?: return null
        // A run is this session plus any back-to-back sessions at the same pace before it.
        val chain = ArrayList<PracticeSession>()
        chain += s
        var head = s
        while (true) {
            val prev = data.practices.lastOrNull { it.start < head.start && kotlin.math.abs(it.pieces - s.pieces) < 1e-6 } ?: break
            val prevEnd = effectiveEnd(data, prev, tz) ?: break
            if (head.start - prevEnd > 12 * 60 * MIN) break
            chain.add(0, prev); head = prev
        }
        val startDate = BatteryEngine.currentDay(data, chain.first().start, tz).first.date
        val today = BatteryEngine.currentDay(data, now, tz).first.date
        return Status(s, Ladder.rung(s.pieces), runNet(data, chain, now, tz), startDate.daysUntil(today) + 1)
    }

    // ---- Offers ----

    /**
     * "How was Campfire pace?" the morning after practicing the next rung down (from the step-down
     * offer's "Try it for a day"). Null once answered.
     */
    fun followUp(data: FirewatchData, now: Long, tz: TimeZone): PracticeSession? {
        val target = data.targetPieces ?: return null
        if (active(data, now, tz) != null) return null
        val s = data.practices.lastOrNull() ?: return null
        if (s.id == data.settings.practiceAnswered) return null
        if (kotlin.math.abs(s.pieces - Ladder.nextDown(target).pieces) > 1e-6) return null
        if (s.start < (data.rungChanges.lastOrNull()?.at ?: 0L)) return null
        val end = effectiveEnd(data, s, tz) ?: return null
        val today = BatteryEngine.currentDay(data, now, tz).first.date
        val practiced = BatteryEngine.currentDay(data, end - 1, tz).first.date
        return if (practiced < today && practiced.daysUntil(today) <= 2) s else null
    }

    /** §7 rules 1–3 and the parts of 4 that apply to both offers. */
    private fun lighterEligible(data: FirewatchData, now: Long, tz: TimeZone): Rung? {
        val target = data.targetPieces ?: return null
        if (target <= 0 || Control.isEarly(data) || data.relapseOn) return null
        val last = data.rungChanges.lastOrNull() ?: return null
        val hold = data.settings.holdDays.coerceAtLeast(1)
        val today = now.localDate(tz)
        if (last.at.localDate(tz).daysUntil(today) < hold) return null
        val measured = Progress.measuredRung(data, today, tz) ?: return null
        if (measured.pieces <= 0) return null
        if (rungsLighter(target, measured.pieces) < 2) return null
        if (Progress.knownDays(data, today, tz) < 6) return null
        return measured
    }

    /**
     * "Your logs measure Campfire · 4 a day. Try Campfire pace?" Only when the measured level is 2+
     * rungs lighter, a hold period after any level change, 6 of the last 7 days known, outside the
     * first week and Relapse prevention mode, practice pace not on. It only ever offers to practice.
     */
    fun lighterOffer(data: FirewatchData, now: Long, tz: TimeZone): Rung? {
        val s = data.settings
        if (!s.lighterOffers) return null
        if (active(data, now, tz) != null) return null
        val hold = s.holdDays.coerceAtLeast(1)
        if (now - s.lighterSnoozedAt < hold * DAY) return null
        val measured = lighterEligible(data, now, tz) ?: return null
        if (workFromOffer(data, now, tz) != null) return null
        return measured
    }

    data class WorkFrom(val rung: Rung, val coveredMin: Double, val netMin: Double)

    /**
     * "Campfire pace held. Work from Campfire from now on?" once practice pace at the measured pace
     * has covered at least 75% of the hold period's waking hours (counted up to this morning), with
     * practice net ≥ 0. A correction to the real level, not a taper step.
     */
    fun workFromOffer(data: FirewatchData, now: Long, tz: TimeZone): WorkFrom? {
        val measured = lighterEligible(data, now, tz) ?: return null
        val s = data.settings
        if (now - s.workFromSnoozedAt < DAY) return null
        val sessions = run(data, measured.pieces)
        if (sessions.isEmpty() || sessions.last().id == s.practiceAnswered) return null
        val morning = BatteryEngine.currentDay(data, now, tz).first.wakeAt
        val covered = coverageMinutes(data, sessions, morning, tz)
        val hold = s.holdDays.coerceAtLeast(1)
        if (covered < COVERAGE * hold * Ladder.WAKING_MINUTES) return null
        val net = runNet(data, sessions, morning, tz)
        if (net < 0) return null
        return WorkFrom(measured, covered, net)
    }

    // ---- Level history ----

    data class HistoryEntry(val at: Long, val text: String, val kind: String)

    /**
     * Level history, newest first: "1 Oct · Blaze 7 → 6 · stepped down", step ups with their reason,
     * and practice sessions with their practice net. [dateText] formats a date for display.
     */
    fun history(data: FirewatchData, now: Long, tz: TimeZone, dateText: (LocalDate) -> String): List<HistoryEntry> {
        val out = ArrayList<HistoryEntry>()
        data.rungChanges.forEachIndexed { i, rc ->
            val to = Ladder.rung(rc.pieces)
            val prev = data.rungChanges.getOrNull(i - 1)?.let { Ladder.rung(it.pieces) }
            val move = when {
                rc.reason == Control.EARLY && prev == null -> "started at an early estimate"
                prev == null -> "started"
                rc.reason == "measured" -> "working from measured level"
                rc.pieces < prev.pieces -> "stepped down"
                rc.pieces > prev.pieces -> "stepped up"
                else -> "level set"
            }
            fun short(r: Rung) = if (r.tier == Tier.CLEAR_AIR) "Clear Air" else "${r.tier.title} ${Ladder.piecesText(r.pieces)}"
            val label = when {
                prev == null -> short(to)
                prev.tier == to.tier -> "${short(prev)} → ${Ladder.piecesText(to.pieces)}"
                else -> "${short(prev)} → ${short(to)}"
            }
            val detail = rc.detail.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
            out += HistoryEntry(rc.at, "${dateText(rc.at.localDate(tz))} · $label · $move$detail",
                if (prev != null && rc.pieces > prev.pieces) "up" else if (prev != null && rc.pieces < prev.pieces) "down" else "set")
        }
        data.practices.forEach { s ->
            val end = effectiveEnd(data, s, tz)?.let { minOf(it, now) } ?: now
            val net = runNet(data, listOf(s), end, tz)
            val sign = if (net >= 0) "+" else "−"
            val m = kotlin.math.abs(net).toInt()
            val netText = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
            val days = BatteryEngine.currentDay(data, s.start, tz).first.date.daysUntil(BatteryEngine.currentDay(data, end - 1, tz).first.date) + 1
            val span = if (days > 1) "$days days" else "${clock(s.start, tz)} – ${clock(end, tz)}"
            out += HistoryEntry(s.start, "${dateText(s.start.localDate(tz))} · Practiced ${Ladder.rung(s.pieces).tier.title} pace $span: practice net $sign$netText", "practice")
        }
        return out.sortedByDescending { it.at }
    }

    /** Days the level changed: ISO date → "up" / "down" (for ▲ / ▼ on the calendar). */
    fun levelMarks(data: FirewatchData, tz: TimeZone): Map<String, String> {
        val out = HashMap<String, String>()
        data.rungChanges.forEachIndexed { i, rc ->
            val prev = data.rungChanges.getOrNull(i - 1) ?: return@forEachIndexed
            val date = BatteryEngine.currentDay(data, rc.at, tz).first.date.toString()
            if (rc.pieces < prev.pieces) out[date] = "down" else if (rc.pieces > prev.pieces) out[date] = "up"
        }
        return out
    }

    private fun clock(t: Long, tz: TimeZone): String {
        val lt = kotlinx.datetime.Instant.fromEpochMilliseconds(t).toLocalDateTime(tz)
        val h = lt.hour % 12
        return "${if (h == 0) 12 else h}${if (lt.minute > 0) ":" + lt.minute.toString().padStart(2, '0') else ""} ${if (lt.hour < 12) "am" else "pm"}"
    }
}

