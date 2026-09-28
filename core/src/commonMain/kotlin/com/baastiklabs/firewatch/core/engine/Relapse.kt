package com.baastiklabs.firewatch.core.engine

import com.baastiklabs.firewatch.core.localDate
import com.baastiklabs.firewatch.core.pieces
import com.baastiklabs.firewatch.core.model.Dose
import com.baastiklabs.firewatch.core.model.ProductKind
import com.baastiklabs.firewatch.core.model.Product
import com.baastiklabs.firewatch.core.records.FirewatchData
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Why the "try Relapse prevention mode" card is showing. */
enum class RelapseReason { SMOKED_OR_VAPED, EARLY_HEAVY_GUM, STRONG_CRAVINGS }

/**
 * Relapse prevention mode: the one opt-in exception to "no next-piece reminders". It reminds D to
 * chew at a steady gap so cravings don't catch them off guard.
 * - The gap follows the tier: the same gap as the battery (16 waking hours ÷ the target's pieces).
 * - With no tier yet (the first week, while the baseline is measured) it's 2 hours.
 * - Each logged piece restarts the timer; no reminders during sleeping hours.
 * Tiers, the 7-day average and "pieces today" are untouched; stretch and pull pause on mode days.
 */
object Relapse {
    private const val MIN = 60_000L
    private const val DAY = 24 * 60 * MIN
    const val DEFAULT_GAP_MINUTES = 120.0
    const val SNOOZE_MS = 14 * DAY
    const val STEADY_MS = 28 * DAY
    const val STRONG_CRAVING = 7

    /** Minutes between reminders: the tier's gap, or 2 hours before there's a tier. */
    fun gapMinutes(data: FirewatchData, now: Long): Double {
        val target = BatteryEngine.targetAt(data, now) ?: return DEFAULT_GAP_MINUTES
        return BatteryEngine.intervalFor(if (target > 0) target else 1.0 / 3.0)
    }

    /** When the mode was last switched on, or null if it's off. */
    fun onSince(data: FirewatchData): Long? {
        val last = data.modeChanges.lastOrNull { it.mode == "relapse" } ?: return null
        return if (last.on) last.at else null
    }

    /** True if the mode was on at any point during that waking day. */
    fun isModeDay(data: FirewatchData, date: LocalDate, tz: TimeZone): Boolean {
        val changes = data.modeChanges.filter { it.mode == "relapse" }
        if (changes.isEmpty()) return false
        val start = Waking.day(data, date, tz).wakeAt
        val end = Waking.day(data, date.plus(1, DateTimeUnit.DAY), tz).wakeAt
        val onAtStart = changes.lastOrNull { it.at <= start }?.on == true
        return onAtStart || changes.any { it.on && it.at in start until end }
    }

    /** The product the reminders are for (falls back to the reference piece). */
    fun product(data: FirewatchData): Product? =
        data.productsById[data.settings.relapseProductId]?.takeIf { !it.archived }
            ?: data.productsById[data.settings.referenceProductId]

    /**
     * When the next scheduled piece is due, or null if the mode is off. Counted from the latest of
     * the last real dose, today's wake-up and switching the mode on. If that lands after bedtime,
     * it moves to the next morning (one gap after waking).
     */
    fun nextAt(data: FirewatchData, now: Long, tz: TimeZone): Long? {
        val since = onSince(data) ?: return null
        val gap = (gapMinutes(data, now) * MIN).toLong()
        val (day, nextWake) = BatteryEngine.currentDay(data, now, tz)
        val lastDose = data.doses.lastOrNull { !it.estimated && it.at <= now }?.at ?: 0L
        val base = maxOf(lastDose, day.wakeAt, since)
        val next = base + gap
        return if (next >= day.sleepAt) nextWake + gap else next
    }

    /** True while D is (assumed) awake, so a reminder may be sent. */
    fun awake(data: FirewatchData, now: Long, tz: TimeZone): Boolean {
        val (day, _) = BatteryEngine.currentDay(data, now, tz)
        return now >= day.wakeAt && now < day.sleepAt
    }

    /**
     * Whether a reminder should go out at [now]: the mode is on, D is awake, a piece is due, and
     * no reminder went out within one gap ([lastNotifiedAt]).
     */
    fun shouldRemind(data: FirewatchData, now: Long, tz: TimeZone, lastNotifiedAt: Long): Boolean {
        val next = nextAt(data, now, tz) ?: return false
        val gap = (gapMinutes(data, now) * MIN).toLong()
        return awake(data, now, tz) && now >= next - MIN && now - lastNotifiedAt >= gap - 5 * MIN
    }

    /** When to check again: the next due time, or one gap after the last reminder if D let it pass. */
    fun nextCheckAt(data: FirewatchData, now: Long, tz: TimeZone, lastNotifiedAt: Long): Long? {
        val next = nextAt(data, now, tz) ?: return null
        val gap = (gapMinutes(data, now) * MIN).toLong()
        var t = maxOf(next, lastNotifiedAt + gap)
        if (t <= now) t = now + gap
        val (day, nextWake) = BatteryEngine.currentDay(data, t, tz)
        if (t >= day.sleepAt) t = nextWake + gap
        return t
    }

    private fun smokedOrVaped(d: Dose) =
        d.kind == ProductKind.CIGARETTE || d.kind == ProductKind.VAPE || d.borrowed

    /** Why the "try Relapse prevention mode" card should show, or null if it shouldn't. */
    fun recommendation(data: FirewatchData, now: Long, tz: TimeZone): RelapseReason? {
        if (data.relapseOn || !data.settings.onboardingDone) return null
        if (now - data.settings.relapseCardDismissedAt < SNOOZE_MS) return null
        val week = now - 7 * DAY
        if (data.doses.any { it.at in week..now && smokedOrVaped(it) }) return RelapseReason.SMOKED_OR_VAPED
        val first = data.doses.firstOrNull()?.at
        if (first != null && now - first < 21 * DAY) {
            val today = now.localDate(tz)
            val ref = data.referenceMg
            val gum = data.doses.filter { it.kind == ProductKind.GUM && it.at in week..now }
            val days = (0 until 7).count { i ->
                val d = today.minus(i, DateTimeUnit.DAY)
                gum.any { it.at.localDate(tz) == d }
            }.coerceAtLeast(1)
            if (gum.sumOf { data.piecesOf(it) } / days >= 6.0) return RelapseReason.EARLY_HEAVY_GUM
        }
        if (data.cravings.count { it.at in week..now && it.intensity >= STRONG_CRAVING } >= 3) return RelapseReason.STRONG_CRAVINGS
        return null
    }

    /** "You've been steady for 4 weeks. Ready to switch to tapering?" */
    fun movingOn(data: FirewatchData, now: Long): Boolean {
        val since = onSince(data) ?: return false
        if (now - since < STEADY_MS) return false
        if (now - data.settings.movingOnDismissedAt < SNOOZE_MS) return false
        // Steady: no cigarettes or vapes in the last 2 weeks.
        return data.doses.none { it.at in (now - 14 * DAY)..now && smokedOrVaped(it) }
    }

    fun reasonText(r: RelapseReason): String = when (r) {
        RelapseReason.SMOKED_OR_VAPED -> "You logged a cigarette or vape this week."
        RelapseReason.EARLY_HEAVY_GUM -> "You're in your first weeks of gum, and using quite a lot of it."
        RelapseReason.STRONG_CRAVINGS -> "You've had several strong cravings this week."
    }
}
