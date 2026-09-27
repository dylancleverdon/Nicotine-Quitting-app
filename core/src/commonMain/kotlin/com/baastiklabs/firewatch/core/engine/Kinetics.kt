package com.baastiklabs.firewatch.core.engine

import com.baastiklabs.firewatch.core.absorbedMg
import com.baastiklabs.firewatch.core.model.Dose
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

    /** Absorption rate per minute: spike peaks ~8 min, build ~35 min, flat over many hours. */
    fun ka(speed: SpeedProfile): Double = when (speed) {
        SpeedProfile.SPIKE -> 0.5
        SpeedProfile.BUILD -> 0.08
        SpeedProfile.FLAT -> 0.004
    }

    /** Minutes from dosing to peak. */
    fun peakMinutes(speed: SpeedProfile): Double {
        val ka = ka(speed)
        return ln(ka / ke) / (ka - ke)
    }

    fun contribution(mg: Double, speed: SpeedProfile, minutesSince: Double): Double {
        if (minutesSince < 0) return 0.0
        val ka = ka(speed)
        return mg * ka / (ka - ke) * (exp(-ke * minutesSince) - exp(-ka * minutesSince))
    }

    fun level(doses: List<Dose>, atMs: Long): Double = doses.sumOf { d ->
        if (d.at > atMs || atMs - d.at > 48 * 3_600_000L) 0.0
        else contribution(d.absorbedMg(), d.speed, (atMs - d.at) / 60_000.0)
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
