package com.baastiklabs.firewatch.core

import com.baastiklabs.firewatch.core.model.DefaultProducts
import com.baastiklabs.firewatch.core.model.Dose
import com.baastiklabs.firewatch.core.model.Duration
import com.baastiklabs.firewatch.core.model.Product
import com.baastiklabs.firewatch.core.model.ProductKind

/**
 * Estimated nicotine absorbed into the blood. Every figure is an estimate; the value is in
 * comparing D's own numbers over time.
 */
object Absorption {
    /** Acidic drinks (coffee, soda) around chewing noticeably cut absorption from gum. */
    const val ACIDIC_FACTOR = 0.6

    fun durationFactor(duration: Duration): Double = when (duration) {
        Duration.FULL -> 1.0
        Duration.HALF -> 0.6
        Duration.QUICK -> 0.3
    }

    /** Minutes each choice stands for: Full 30+ min, About half ~15 min, Quick ~5 min. */
    fun durationMinutes(duration: Duration): Int = when (duration) {
        Duration.FULL -> 30
        Duration.HALF -> 15
        Duration.QUICK -> 5
    }

    /** "Full (30+ min)", "About half (~15 min)", "Quick (~5 min)". */
    fun durationLabel(duration: Duration): String = when (duration) {
        Duration.FULL -> "Full (30+ min)"
        Duration.HALF -> "About half (~15 min)"
        Duration.QUICK -> "Quick (~5 min)"
    }

    /**
     * Share absorbed for a pouch kept in [minutes], matching the choices above (5 min ≈ 0.3,
     * 15 ≈ 0.6, 30+ = all), straight lines between them.
     */
    fun minutesFactor(minutes: Double): Double {
        val m = minutes.coerceAtLeast(0.0)
        return when {
            m >= 30 -> 1.0
            m >= 15 -> 0.6 + (m - 15) / 15 * 0.4
            m >= 5 -> 0.3 + (m - 5) / 10 * 0.3
            else -> m / 5 * 0.3
        }
    }

    fun absorbedMg(
        labelMg: Double,
        absorption: Double,
        kind: ProductKind,
        multiplier: Double = 1.0,
        duration: Duration = Duration.FULL,
        acidicDrink: Boolean = false,
        /** Minutes in the mouth, when known ("Took it out"): replaces [duration]. */
        inMouthMinutes: Double? = null,
    ): Double {
        var mg = labelMg * absorption * multiplier
        if (DefaultProducts.isOral(kind)) mg *= inMouthMinutes?.let { minutesFactor(it) } ?: durationFactor(duration)
        if (acidicDrink && kind == ProductKind.GUM) mg *= ACIDIC_FACTOR
        return mg.coerceAtLeast(0.0)
    }

    /** Absorbed mg of one full, normal use of a product. */
    fun absorbedMg(product: Product): Double =
        absorbedMg(product.labelMg, product.absorption, product.kind)

    fun pieces(absorbedMg: Double, referenceMg: Double): Double =
        if (referenceMg <= 0.0) 0.0 else absorbedMg / referenceMg
}

/** The best single estimate: the dose itself, or the middle of its range for unknown doses. */
fun Dose.absorbedMg(): Double {
    val low = rangeLowMg
    val high = rangeHighMg
    if (low != null && high != null) return (low + high) / 2.0
    return Absorption.absorbedMg(labelMg, absorption, kind, multiplier, duration, acidicDrink,
        inMouthMinutes = removedAt?.takeIf { kind == ProductKind.POUCH && it > at }?.let { (it - at) / 60_000.0 })
}

/** The cautious (top of range) estimate, used for timing. */
fun Dose.absorbedMgHigh(): Double = rangeHighMg ?: absorbedMg()

fun Dose.pieces(referenceMg: Double): Double = Absorption.pieces(absorbedMg(), referenceMg)

/** Builds a dose from a product with the product's details snapshotted. */
fun Product.toDose(
    id: String,
    at: Long,
    loggedAt: Long,
    multiplier: Double = 1.0,
    duration: Duration = Duration.FULL,
    acidicDrink: Boolean = false,
    tags: List<String> = emptyList(),
): Dose = Dose(
    id = id,
    productId = this.id,
    at = at,
    productName = name,
    kind = kind,
    labelMg = labelMg,
    absorption = absorption,
    speed = speed,
    multiplier = multiplier,
    duration = duration,
    acidicDrink = acidicDrink,
    tags = tags,
    borrowed = borrowedFrom != null,
    loggedAt = loggedAt,
)
