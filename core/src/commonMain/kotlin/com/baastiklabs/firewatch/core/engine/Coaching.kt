package com.baastiklabs.firewatch.core.engine

import com.baastiklabs.firewatch.core.Absorption
import com.baastiklabs.firewatch.core.localDate
import com.baastiklabs.firewatch.core.model.Product
import com.baastiklabs.firewatch.core.model.ProductKind
import com.baastiklabs.firewatch.core.records.FirewatchData
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/** Yesterday's waking day in facts only (no comparison with other days). */
data class DayReview(
    val date: LocalDate,
    val pieces: Double,
    /** Null when there was no target yet, or a Relapse prevention mode day. */
    val netMin: Double?,
    val volatility: Double,
    /** Share of pieces per product kind, biggest first (0..1). */
    val mix: List<Pair<ProductKind, Double>>,
    val longestGapMin: Double?,
    /** Doses taken while the one before was still peaking. */
    val stacked: Int,
    val morningStretchMin: Double?,
    /** Up to two tips, only with Coaching tips on. */
    val tips: List<String>,
)

/** A tip shown on the Log tab (Coaching tips only); [id] is what dismissing hides for 2 weeks. */
data class Tip(val id: String, val text: String)

/**
 * Opt-in Coaching tips: practical, factual tips from D's own logs. Off means Firewatch just
 * measures. Never notifications, never in the craving flow, never "you should".
 */
object Coaching {
    private const val MIN = 60_000L
    const val DISMISS_MS = 14 * 24 * 60 * MIN
    const val BRIDGE = "bridge"

    fun dismissed(data: FirewatchData, id: String, now: Long): Boolean =
        data.settings.tipDismissedAt[id]?.let { now - it < DISMISS_MS } ?: false

    fun kindName(k: ProductKind): String = when (k) {
        ProductKind.GUM -> "gum"; ProductKind.POUCH -> "pouches"; ProductKind.LOZENGE -> "lozenges"
        ProductKind.PATCH -> "patches"; ProductKind.VAPE -> "vapes"; ProductKind.CIGARETTE -> "cigarettes"
        ProductKind.OTHER -> "other"
    }

    fun yesterday(data: FirewatchData, now: Long, tz: TimeZone): DayReview? {
        val (day, _) = BatteryEngine.currentDay(data, now, tz)
        val date = day.date.minus(1, DateTimeUnit.DAY)
        if (data.doses.none { it.at < day.wakeAt }) return null
        val stat = Insights(data, tz, now).dayStat(date)
        if (stat.doses.isEmpty()) return null
        val total = stat.kindPieces.values.sum()
        val mix = if (total <= 0) emptyList() else stat.kindPieces.entries.map { it.key to it.value / total }.sortedByDescending { it.second }
        val battery = BatteryEngine.day(data, date, tz, now)
        val timed = stat.doses.filter { !it.estimated }
        val tips = if (!data.settings.coachingTips) emptyList() else buildList {
            if (stat.longestGapStart?.kind == ProductKind.GUM && (stat.longestGapMin ?: 0.0) >= 60) add("Your longest gap started with gum.")
            if (stat.doubleUps >= 2) {
                val late = timed.zipWithNext().count { (a, b) ->
                    b.at - a.at < Kinetics.peakMinutes(a) * MIN && b.at.toHour(tz) >= 16
                }
                add(if (late >= 2) "$late doses were stacked after 4pm." else "${stat.doubleUps} doses were stacked while the last one was still peaking.")
            }
            if ((stat.wakeToFirstMin ?: 0.0) >= 90) add("A later first piece gave you ${hm(stat.wakeToFirstMin!!)} of morning stretch.")
        }.take(2)
        return DayReview(
            date = date,
            pieces = stat.pieces,
            netMin = battery?.takeIf { !it.paused }?.netMin,
            volatility = stat.volatility,
            mix = mix,
            longestGapMin = stat.longestGapMin,
            stacked = stat.doubleUps,
            morningStretchMin = if (battery != null && !battery.paused) stat.wakeToFirstMin else null,
            tips = tips,
        )
    }

    /**
     * "Bridge with gum": when pouches are most of D's use and a smaller gum is on the home screen,
     * a gum now can take the edge off at no net cost (battery at least half full).
     */
    fun bridgeTip(data: FirewatchData, targetPieces: Double?, now: Long, tz: TimeZone, lastActivityAt: Long = 0L): Tip? {
        if (!data.settings.coachingTips || targetPieces == null || dismissed(data, BRIDGE, now)) return null
        val (day, _) = BatteryEngine.currentDay(data, now, tz)
        if (Relapse.isModeDay(data, day.date, tz)) return null
        if (data.cravings.any { now - it.at in 0..(45 * MIN) }) return null
        val recent = data.doses.filter { now - it.at in 0..(7 * 24 * 60 * MIN) }
        val total = recent.sumOf { data.piecesOf(it) }
        val pouch = recent.filter { it.kind == ProductKind.POUCH }.sumOf { data.piecesOf(it) }
        if (total <= 0 || pouch / total < 0.5) return null
        val gum: Product = data.homeProducts.filter { it.kind == ProductKind.GUM }
            .map { it to Absorption.pieces(Absorption.absorbedMg(it), data.refMgAt(now)) }
            .filter { it.second < 0.9 }.minByOrNull { it.second }?.first ?: return null
        val b = BatteryEngine.now(data, targetPieces, now, tz, lastActivityAt)
        if (b.state == BatteryState.ASLEEP || b.charge < 0.5) return null
        val preview = BatteryEngine.preview(data, gum, targetPieces, now, tz, lastActivityAt) ?: return null
        if (preview < -1) return null
        return Tip(BRIDGE, "A ${gum.name} now is net-neutral and can take the edge off before your next piece.")
    }

    /** The pouch → gum quality swap tip: only with Coaching tips on. */
    fun swapTip(data: FirewatchData, doses: List<com.baastiklabs.firewatch.core.model.Dose>): String? =
        if (data.settings.coachingTips) Quality.swapTip(doses, data.referenceMg) else null

    private fun Long.toHour(tz: TimeZone): Int =
        kotlinx.datetime.Instant.fromEpochMilliseconds(this).toLocalDateTime(tz).hour

    fun hm(m: Double): String { val t = m.toInt(); return if (t >= 60) "${t / 60}h ${t % 60}m" else "${t}m" }
}
