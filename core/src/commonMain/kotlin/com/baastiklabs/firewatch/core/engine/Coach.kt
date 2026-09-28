package com.baastiklabs.firewatch.core.engine

import com.baastiklabs.firewatch.core.CravingResult
import com.baastiklabs.firewatch.core.Cravings
import com.baastiklabs.firewatch.core.localDate
import com.baastiklabs.firewatch.core.model.CravingOutcome
import com.baastiklabs.firewatch.core.model.Dose
import com.baastiklabs.firewatch.core.model.ProductKind
import com.baastiklabs.firewatch.core.pieces
import com.baastiklabs.firewatch.core.records.FirewatchData
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Personal craving intelligence. Learns which craving strength D can actually ride out, and how
 * strong cravings get as the wait since the last piece grows, then predicts what the next rung
 * down would feel like. Step-down offers wait until that prediction is within D's capacity; when
 * cravings at the current rung keep beating D, it offers a step back up instead. Honest, not harsh.
 */
object Coach {
    /** Ride-out rate needed to count a level as "handled". */
    const val HANDLED_RATE = 0.7
    const val DEFAULT_CAPACITY = 5
    private const val MIN = 60_000L

    data class LevelStat(val level: Int, val total: Int, val rodeOut: Int) {
        val rate: Double get() = (rodeOut + 1.0) / (total + 2.0)
    }

    data class Profile(
        /** Highest craving level D reliably rides out. */
        val capacity: Int,
        /** True once there's enough history for the estimate to mean something. */
        val confident: Boolean,
        val levels: List<LevelStat>,
        /** Fitted intensity ≈ a + b·ln(1 + hours waited). */
        val a: Double,
        val b: Double,
        val cravingsCounted: Int,
    ) {
        fun predictedIntensity(intervalMinutes: Double): Double =
            (a + b * ln(1.0 + intervalMinutes / 60.0)).coerceIn(1.0, 10.0)
    }

    fun profile(data: FirewatchData, now: Long): Profile {
        val resolved = data.cravings.map { it to Cravings.effectiveOutcome(data, it, now) }
            .filter { it.second != CravingOutcome.OPEN }
        val levels = (1..10).map { l ->
            val near = resolved.filter { kotlin.math.abs(it.first.intensity - l) <= 1 }
            LevelStat(l, near.size, near.count { it.second == CravingOutcome.RODE_OUT })
        }
        val confident = resolved.size >= 8
        val capacity = if (!confident) DEFAULT_CAPACITY
        else levels.filter { it.total >= 2 && it.rate >= HANDLED_RATE }.maxOfOrNull { it.level } ?: 2

        // Intensity vs hours since the previous dose.
        val points = data.cravings.mapNotNull { c ->
            val last = data.doses.lastOrNull { !it.estimated && it.at < c.at } ?: return@mapNotNull null
            ln(1.0 + (c.at - last.at) / 3_600_000.0) to c.intensity.toDouble()
        }
        var a = 2.5
        var b = 1.8
        if (points.size >= 6) {
            val mx = points.map { it.first }.average()
            val my = points.map { it.second }.average()
            val sxx = points.sumOf { (it.first - mx) * (it.first - mx) }
            if (sxx > 1e-6) {
                b = (points.sumOf { (it.first - mx) * (it.second - my) } / sxx).coerceIn(0.2, 6.0)
                a = my - b * mx
            }
        }
        return Profile(capacity, confident, levels, a, b, resolved.size)
    }

    data class Readiness(val predictedNext: Double, val capacity: Int, val ready: Boolean, val confident: Boolean)

    /** Would the next rung down be manageable, judging by D's own craving history? */
    fun readiness(data: FirewatchData, targetPieces: Double, now: Long): Readiness {
        val p = profile(data, now)
        val next = Ladder.nextDown(targetPieces)
        val interval = if (next.pieces <= 0) Ladder.WAKING_MINUTES * 3 else next.intervalMinutes
        val predicted = p.predictedIntensity(interval)
        return Readiness(predicted, p.capacity, !p.confident || predicted <= p.capacity + 0.5, p.confident)
    }

    /** A step-up offer and the plain-language reasons behind it. [sameDay]: triggered by today alone. */
    data class StepUp(val rung: Rung, val reasons: List<String>, val sameDay: Boolean) {
        val why: String get() = reasons.joinToString(" and ").replaceFirstChar { it.uppercase() } + "."
    }

    fun stepUpOffer(data: FirewatchData, now: Long, tz: TimeZone): Rung? = stepUp(data, now, tz)?.rung

    /**
     * Offer a step back up when the current rung is too much right now. It aims to catch a rough day
     * the same day, before it turns into a relapse. Any one of these today is enough:
     * - pull reaches one full gap; 3+ cravings above what D usually rides out (or 2+ within 2 hours);
     * - more than 1 piece over today's target; timer checks while refilling at least double the usual
     *   (and 6+, when "Hide next piece timer" is on); a craving answered with a cigarette or vape.
     * Or, over the last 5 days against the 14 before, two or more of: 3+ days over target, more early
     * doses and relapses, more strong cravings, more timer checks while refilling, stronger cravings.
     * "I'm OK" hides a same-day offer until tomorrow and a multi-day one for 3 days.
     */
    fun stepUp(data: FirewatchData, now: Long, tz: TimeZone): StepUp? {
        val target = data.targetPieces ?: return null
        val next = Ladder.nextUp(target)
        if (next.pieces <= target) return null
        val today = now.localDate(tz)
        val snoozed = data.settings.stepUpSnoozedAt
        if (snoozed > 0 && snoozed.localDate(tz) == today) return null
        val p = profile(data, now)
        val strongAt = p.capacity + 1
        val (day, nextWake) = BatteryEngine.currentDay(data, now, tz)
        fun cravingsIn(from: Long, to: Long) = data.cravings.filter { it.at in from until to }

        // ---- Today ----
        val reasons = ArrayList<String>()
        val interval = BatteryEngine.intervalFor(if (target > 0) target else 1.0 / 3.0)
        val pull = BatteryEngine.day(data, day.date, tz, now)?.pullMin ?: 0.0
        if (pull >= interval) reasons += "today's pull has reached ${hm(pull)}"
        val strongToday = cravingsIn(day.wakeAt, minOf(now + 1, nextWake)).filter { it.intensity > strongAt }
        val clustered = strongToday.any { c -> strongToday.count { it.at in c.at until c.at + 2 * 60 * MIN } >= 2 }
        if (strongToday.size >= 3 || clustered) reasons += "you've had ${strongToday.size} strong cravings today"
        val piecesToday = data.doses.filter { it.at >= day.wakeAt && it.at < nextWake }
            .sumOf { it.pieces(data.referenceMg) }
        if (piecesToday > target + 1.0) reasons += "you're already ${com.baastiklabs.firewatch.core.engine.Ladder.piecesText(piecesToday - target)} over today's target"
        if (data.settings.hideTimer) {
            val todayChecks = Checks.day(data, day.date, tz).charging
            val usual = Checks.history(data, day.date, tz, 14).map { it.charging }.average().takeIf { !it.isNaN() } ?: 0.0
            if (todayChecks >= 6 && todayChecks >= 2 * usual) reasons += "you've checked the timer $todayChecks times while it was refilling"
        }
        val relapseToday = cravingsIn(day.wakeAt, minOf(now + 1, nextWake))
            .any { Cravings.result(data, it, now, tz) == CravingResult.RELAPSE }
        if (relapseToday) reasons += "a craving today ended with a cigarette or vape"
        if (reasons.isNotEmpty()) return StepUp(next, reasons, sameDay = true)

        // ---- Last 5 days vs the 14 before ----
        if (now - snoozed < 3 * 24 * 60 * MIN) return null
        // Creeping up: the last 7 days measure a heavier rung than the one D is working at.
        if (!Control.isEarly(data)) {
            val measured = Progress.measuredRung(data, today, tz)
            val sinceChange = data.rungChanges.lastOrNull()?.at ?: 0L
            if (measured != null && measured.pieces > target + 1e-6 && now - sinceChange >= 7 * 24 * 60 * MIN) {
                return StepUp(Ladder.rung(measured.pieces), listOf("your last 7 days measure ${measured.label}"), sameDay = false)
            }
        }
        val since = data.rungChanges.lastOrNull()?.at ?: return null
        if (now - since < 3 * 24 * 60 * MIN) return null
        val recentFrom = day.wakeAt - 5 * 24 * 60 * MIN
        val priorFrom = recentFrom - 14 * 24 * 60 * MIN
        val recent = cravingsIn(recentFrom, day.wakeAt)
        val prior = cravingsIn(priorFrom, recentFrom)
        fun rate(n: Int, days: Int) = n / days.toDouble()
        val multi = ArrayList<String>()
        val overDays = (1..5).count { Progress.pace(data, today.minus(it, DateTimeUnit.DAY), tz).scaled > target + 0.75 }
        if (overDays >= 3) multi += "$overDays of the last 5 days ran over"
        fun misses(cs: List<com.baastiklabs.firewatch.core.model.Craving>) =
            cs.count { Cravings.result(data, it, now, tz).let { r -> r == CravingResult.EARLY || r == CravingResult.RELAPSE } }
        val missR = misses(recent)
        if (missR >= 3 && rate(missR, 5) > 1.5 * rate(misses(prior), 14)) multi += "more cravings have been ending in an early piece"
        val strongR = recent.count { it.intensity > strongAt }
        if (strongR >= 3 && rate(strongR, 5) > 1.5 * rate(prior.count { it.intensity > strongAt }, 14)) multi += "strong cravings are coming more often"
        if (recent.size >= 3 && prior.size >= 3 && recent.map { it.intensity }.average() >= prior.map { it.intensity }.average() + 1) multi += "cravings have been getting stronger"
        if (data.settings.hideTimer) {
            val h = Checks.history(data, today, tz, 19)
            if (h.size >= 10) {
                val r = h.takeLast(5).sumOf { it.charging }
                val b = h.dropLast(5).map { it.charging }.average()
                if (r >= 5 && r / 5.0 >= 1.5 * b) multi += "you've been checking the timer more while it refills"
            }
        }
        return if (multi.size >= 2) StepUp(next, multi, sameDay = false) else null
    }

    private fun hm(min: Double): String { val m = min.toInt(); return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m" }

    /**
     * "Where your body really is": measured pace, plus cravings above capacity that were white-
     * knuckled (each counts as a quarter piece of unmet need).
     */
    fun honestPieces(data: FirewatchData, now: Long, tz: TimeZone): Double? {
        val measured = Progress.rollingAverage(data, now.localDate(tz), tz) ?: return null
        val p = profile(data, now)
        val week = data.cravings.filter { now - it.at < 7 * 24 * 60 * MIN && it.intensity > p.capacity }
        return measured + week.size * 0.25 / 7.0
    }
}

/**
 * Nicotine quality: HOW you use nicotine, on a food scale. Quit aids (gum, lozenge, patch) are
 * broccoli; smoke is burger and fries. Using less of a vape doesn't raise it; switching does.
 * Per dose: a base score by method, minus a dose-size penalty (one big hit is worse than a gentle
 * one) and a stacking penalty (dosing while the last one is still peaking). The day's score is the
 * pieces-weighted average.
 */
object Quality {
    enum class Food(val emoji: String, val title: String, val min: Int) {
        BROCCOLI("🥦", "Broccoli", 90),
        APPLE("🍎", "Apple", 75),
        SANDWICH("🥪", "Sandwich", 55),
        PIZZA("🍕", "Pizza", 35),
        DONUT("🍩", "Donut", 11),
        BURGER("🍔", "Burger & fries", 0),
    }

    /** Base score by delivery method (speed + whether it's a licensed quit aid). */
    fun score(kind: ProductKind): Int = when (kind) {
        ProductKind.GUM, ProductKind.PATCH, ProductKind.LOZENGE -> 100
        ProductKind.POUCH -> 60
        ProductKind.OTHER -> 50
        ProductKind.VAPE -> 25
        ProductKind.CIGARETTE -> 5
    }

    fun isQuitAid(kind: ProductKind) = kind == ProductKind.GUM || kind == ProductKind.PATCH || kind == ProductKind.LOZENGE

    fun why(kind: ProductKind): String = when (kind) {
        ProductKind.GUM, ProductKind.PATCH, ProductKind.LOZENGE -> "Licensed quit aid, slow and steady"
        ProductKind.POUCH -> "Slow like gum, but not a quit aid"
        ProductKind.VAPE -> "Fast spikes are the most habit-forming kind"
        ProductKind.CIGARETTE -> "Fastest spike, plus smoke"
        ProductKind.OTHER -> "Unknown"
    }

    const val SIZE_PENALTY_PER_PIECE = 8.0
    const val STACK_PENALTY = 15.0
    const val SMOKE_CAP = 10.0

    /** Score of one dose. [stacked] = taken while the previous dose was still peaking. */
    fun doseScore(dose: Dose, referenceMg: Double, stacked: Boolean): Double {
        var s = score(dose.kind).toDouble()
        val pieces = dose.pieces(referenceMg)
        if (pieces > 1.5) s -= (pieces - 1.5) * SIZE_PENALTY_PER_PIECE
        if (stacked) s -= STACK_PENALTY
        if (dose.kind == ProductKind.CIGARETTE) s = minOf(s, SMOKE_CAP)
        return s.coerceIn(0.0, 100.0)
    }

    /** Pieces-weighted average score of these doses (null if none). */
    fun of(doses: List<Dose>, referenceMg: Double): Double? {
        val sorted = doses.sortedBy { it.at }
        val total = sorted.sumOf { it.pieces(referenceMg) }
        if (total <= 0) return null
        return sorted.mapIndexed { i, d ->
            val prev = sorted.getOrNull(i - 1)
            val stacked = prev != null && !d.estimated && !prev.estimated &&
                d.at - prev.at < Kinetics.peakMinutes(prev.speed) * 60_000L
            d.pieces(referenceMg) * doseScore(d, referenceMg, stacked)
        }.sum() / total
    }

    fun food(score: Double): Food = Food.entries.first { score.roundToInt() >= it.min }

    fun grade(score: Double): String = food(score).emoji

    fun label(score: Double): String = food(score).let { "${it.emoji} ${it.title} · ${score.roundToInt()}" }

    /** A nudge toward quit aids when today's mix isn't broccoli yet. */
    fun swapTip(doses: List<Dose>, referenceMg: Double): String? {
        val now = of(doses, referenceMg) ?: return null
        if (food(now) == Food.BROCCOLI) return null
        val worst = doses.filter { !isQuitAid(it.kind) }.groupBy { it.kind }.maxByOrNull { (_, v) -> v.sumOf { it.pieces(referenceMg) } }?.key ?: return null
        val swapped = doses.map { if (it.kind == worst) it.copy(kind = ProductKind.GUM, speed = com.baastiklabs.firewatch.core.model.SpeedProfile.BUILD) else it }
        val after = of(swapped, referenceMg) ?: return null
        val name = DefaultProductsNames.plural(worst)
        return "Swapping $name for gum would move today from ${food(now).emoji} to ${food(after).emoji}."
    }
}

private object DefaultProductsNames {
    fun plural(kind: ProductKind) = when (kind) {
        ProductKind.POUCH -> "pouches"
        ProductKind.VAPE -> "vaping"
        ProductKind.CIGARETTE -> "cigarettes"
        else -> "that"
    }
}

/** Unknown doses (a friend's vape): a range instead of a made-up number. */
object FriendVape {
    enum class Amount(val label: String, val puffs: IntRange) {
        FEW("A couple of hits", 2..5),
        SESSION("A proper session", 10..20),
        ALL_NIGHT("On and off all night", 30..80),
    }

    /** Common strengths in mg/mL (5% ≈ 50 mg/mL). null = no idea. */
    val strengths: List<Pair<Double?, String>> = listOf(
        null to "No idea",
        20.0 to "2% · 20 mg",
        30.0 to "3% · 30 mg",
        50.0 to "5% · 50 mg",
    )

    /** Absorbed mg per puff at 50 mg/mL, low and high research estimates. */
    private const val PER_PUFF_LOW = 0.05
    private const val PER_PUFF_HIGH = 0.1

    fun range(strengthMgPerMl: Double?, puffs: IntRange): Pair<Double, Double> {
        val lowStrength = strengthMgPerMl ?: 20.0
        val highStrength = strengthMgPerMl ?: 50.0
        val low = puffs.first * PER_PUFF_LOW * lowStrength / 50.0
        val high = puffs.last * PER_PUFF_HIGH * highStrength / 50.0
        return low to high
    }
}
