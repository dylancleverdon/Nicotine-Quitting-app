package com.baastiklabs.firewatch.core.engine

import com.baastiklabs.firewatch.core.absorbedMg
import com.baastiklabs.firewatch.core.model.Dose
import com.baastiklabs.firewatch.core.model.Duration
import com.baastiklabs.firewatch.core.model.ProductKind
import com.baastiklabs.firewatch.core.model.SpeedProfile
import kotlin.math.exp
import kotlin.math.ln

/**
 * Estimated nicotine in the blood over time. Each dose rises and fades (a one-compartment
 * Bateman curve) with nicotine's ~2 hour half-life; the speed profile sets how fast it rises.
 * Values are "mg in the system" estimates, useful for shape and comparison, not lab numbers.
 */
object Kinetics {
    const val HALF_LIFE_MIN = 120.0
    private val ke = ln(2.0) / HALF_LIFE_MIN

    /** Cotinine (the longer-lived breakdown product) half-life ~16 hours. */
    const val COTININE_HALF_LIFE_MIN = 16 * 60.0
    private val kc = ln(2.0) / COTININE_HALF_LIFE_MIN

    /** Absorption rate per minute: spike peaks ~8 min, build ~35 min, chew (per burst) slower, flat over many hours. */
    fun ka(speed: SpeedProfile): Double = when (speed) {
        SpeedProfile.SPIKE -> 0.5
        SpeedProfile.BUILD -> 0.08
        SpeedProfile.CHEW -> 0.05
        SpeedProfile.FLAT -> 0.004
    }

    /** Chew and park: nicotine is released in this many bursts across the chewing time. */
    private const val CHEW_BURSTS = 6

    /** Chewing time for a gum: full ≈ 30 min, about half ≈ 15, quick ≈ 5. */
    fun chewMinutes(duration: Duration): Double = when (duration) {
        Duration.FULL -> 30.0
        Duration.HALF -> 15.0
        Duration.QUICK -> 5.0
    }

    /** Gum logged with the old default (BUILD) is drawn as chew and park too. */
    fun speedOf(d: Dose): SpeedProfile =
        if (d.kind == ProductKind.GUM && d.speed == SpeedProfile.BUILD) SpeedProfile.CHEW else d.speed

    /** Minutes from dosing to peak. */
    fun peakMinutes(speed: SpeedProfile): Double {
        if (speed == SpeedProfile.CHEW) return peakMinutes(SpeedProfile.CHEW, chewMinutes(Duration.FULL))
        val ka = ka(speed)
        return ln(ka / ke) / (ka - ke)
    }

    fun peakMinutes(d: Dose): Double {
        val speed = speedOf(d)
        return if (speed == SpeedProfile.CHEW) peakMinutes(speed, chewMinutes(d.duration)) else peakMinutes(speed)
    }

    private val chewPeaks = mutableMapOf<Double, Double>()

    private fun peakMinutes(speed: SpeedProfile, releaseMin: Double): Double = chewPeaks.getOrPut(releaseMin) {
        (0..600).maxBy { contribution(1.0, speed, it.toDouble(), releaseMin) }.toDouble()
    }

    /** One dose's level [minutesSince] after it; chew spreads it over [releaseMin] in bursts. */
    fun contribution(mg: Double, speed: SpeedProfile, minutesSince: Double, releaseMin: Double = 0.0): Double {
        if (minutesSince < 0) return 0.0
        if (speed == SpeedProfile.CHEW && releaseMin > 0) {
            val step = releaseMin / CHEW_BURSTS
            return (0 until CHEW_BURSTS).sumOf { i -> bateman(mg / CHEW_BURSTS, ka(speed), minutesSince - i * step) }
        }
        return bateman(mg, ka(speed), minutesSince)
    }

    private fun bateman(mg: Double, ka: Double, t: Double): Double =
        if (t < 0) 0.0 else mg * ka / (ka - ke) * (exp(-ke * t) - exp(-ka * t))

    fun contribution(d: Dose, minutesSince: Double): Double =
        contribution(d.absorbedMg(), speedOf(d), minutesSince, chewMinutes(d.duration))

    fun level(doses: List<Dose>, atMs: Long): Double = doses.sumOf { d ->
        if (d.at > atMs || atMs - d.at > 48 * 3_600_000L) 0.0
        else contribution(d, (atMs - d.at) / 60_000.0)
    }

    /** Level at every minute from [from] to [to] (inclusive). */
    private fun minuteLevels(doses: List<Dose>, from: Long, to: Long): DoubleArray {
        val relevant = doses.filter { it.at <= to && it.at >= from - 48 * 3_600_000L }
        val n = ((to - from) / 60_000L).toInt() + 1
        return DoubleArray(n.coerceAtLeast(0)) { level(relevant, from + it * 60_000L) }
    }

    /**
     * Nicotine volatility: how fast and how much the level changes. The root-mean-square of the
     * rate of change (mg per hour) over [from, to): steep stretches count extra, steady or clear
     * time is near zero.
     */
    fun volatility(doses: List<Dose>, from: Long, to: Long): Double {
        if (to - from < 2 * 60_000L) return 0.0
        val lv = minuteLevels(doses, from, to)
        var sum = 0.0
        for (i in 1 until lv.size) { val r = (lv[i] - lv[i - 1]) * 60.0; sum += r * r }
        return kotlin.math.sqrt(sum / (lv.size - 1))
    }

    /** Running volatility (mg/h) over the [windowMin] minutes before each point, every [stepMin]. */
    fun volatilityCurve(doses: List<Dose>, from: Long, to: Long, stepMin: Int = 5, windowMin: Int = 30): List<Pair<Long, Double>> {
        if (to <= from) return emptyList()
        val start = from - windowMin * 60_000L
        val lv = minuteLevels(doses, start, to)
        val sq = DoubleArray(lv.size)
        for (i in 1 until lv.size) { val r = (lv[i] - lv[i - 1]) * 60.0; sq[i] = sq[i - 1] + r * r }
        val out = ArrayList<Pair<Long, Double>>()
        var t = from
        while (t <= to) {
            val i = ((t - start) / 60_000L).toInt()
            out += t to kotlin.math.sqrt((sq[i] - sq[i - windowMin]) / windowMin)
            t += stepMin * 60_000L
        }
        return out
    }

    /** Steepest climb (mg/h) over [from, to). */
    fun steepestClimb(doses: List<Dose>, from: Long, to: Long): Double {
        val lv = minuteLevels(doses, from, to)
        return (1 until lv.size).maxOfOrNull { (lv[it] - lv[it - 1]) * 60.0 } ?: 0.0
    }

    /** Level at evenly spaced points from [fromMs] to [toMs]. */
    fun curve(doses: List<Dose>, fromMs: Long, toMs: Long, stepMin: Int = 5): List<Pair<Long, Double>> {
        val relevant = doses.filter { it.at <= toMs && it.at >= fromMs - 48 * 3_600_000L }
        val step = stepMin * 60_000L
        return generateSequence(fromMs) { it + step }.takeWhile { it <= toMs }.map { it to level(relevant, it) }.toList()
    }

    /**
     * Slow "background level" modelled on cotinine: builds and clears over days, so it drifts down
     * through messy days. Arbitrary units proportional to mg.
     */
    fun background(doses: List<Dose>, atMs: Long): Double = doses.sumOf { d ->
        val mins = (atMs - d.at) / 60_000.0
        if (mins < 0 || mins > 14 * 24 * 60) 0.0 else d.absorbedMg() * exp(-kc * mins)
    }

    /** Level below which D counts as "clear" (about a tenth of one piece). */
    const val CLEAR_THRESHOLD_MG = 0.2
}
