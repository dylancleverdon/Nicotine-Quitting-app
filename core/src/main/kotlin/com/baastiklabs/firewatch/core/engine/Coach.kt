package com.baastiklabs.firewatch.core.engine

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
        val resolved = data.cravings.map { it to Cravings.effectiveOutcome(it, data.doses, now) }
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
            val last = data.doses.lastOrNull { it.at < c.at } ?: return@mapNotNull null
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

    /**
     * Offer a step back up when the current rung is too much right now: over the last 5 days,
     * strong cravings beyond capacity keep coming, most urges end in use, or days run over target.
     */
    fun stepUpOffer(data: FirewatchData, now: Long, tz: TimeZone): Rung? {
        val target = data.targetPieces ?: return null
        if (now - data.settings.stepUpSnoozedAt < 3 * 24 * 60 * MIN) return null
        val since = data.rungChanges.lastOrNull()?.at ?: return null
        if (now - since < 3 * 24 * 60 * MIN) return null
        val p = profile(data, now)
        val windowStart = now - 5 * 24 * 60 * MIN
        val recent = data.cravings.filter { it.at >= windowStart }
        val tooStrong = recent.count { it.intensity > p.capacity + 1 }
        val used = recent.count { Cravings.effectiveOutcome(it, data.doses, now) == CravingOutcome.USED }
        val today = now.localDate(tz)
        val overDays = (1..5).count { Progress.pace(data, today.minus(it, DateTimeUnit.DAY), tz).scaled > target + 0.75 }
        val struggling = tooStrong >= 8 || (recent.size >= 5 && used * 2 > recent.size) || overDays >= 3
        return if (struggling) Ladder.nextUp(target) else null
    }

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

/** Rates delivery methods with nicotine gum as the gold standard (100). */
object Quality {
    fun score(kind: ProductKind): Int = when (kind) {
        ProductKind.GUM -> 100
        ProductKind.PATCH -> 100
        ProductKind.LOZENGE -> 95
        ProductKind.POUCH -> 75
        ProductKind.OTHER -> 60
        ProductKind.VAPE -> 40
        ProductKind.CIGARETTE -> 10
    }

    fun why(kind: ProductKind): String = when (kind) {
        ProductKind.GUM, ProductKind.PATCH, ProductKind.LOZENGE -> "Licensed quit aid, slow and steady"
        ProductKind.POUCH -> "Slow like gum, but stronger and not a licensed quit aid"
        ProductKind.VAPE -> "Fast spikes are the most habit-forming kind"
        ProductKind.CIGARETTE -> "Fastest spike, plus smoke"
        ProductKind.OTHER -> "Unknown"
    }

    /** Pieces-weighted average score of these doses (null if none). */
    fun of(doses: List<Dose>, referenceMg: Double): Double? {
        val total = doses.sumOf { it.pieces(referenceMg) }
        if (total <= 0) return null
        return doses.sumOf { it.pieces(referenceMg) * score(it.kind) } / total
    }

    fun grade(score: Double): String = when {
        score >= 90 -> "A"
        score >= 75 -> "B"
        score >= 60 -> "C"
        score >= 40 -> "D"
        else -> "E"
    }

    fun label(score: Double) = "${grade(score)} · ${score.roundToInt()}"
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
