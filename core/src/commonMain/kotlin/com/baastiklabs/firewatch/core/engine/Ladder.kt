package com.baastiklabs.firewatch.core.engine

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The tier ladder. Each tier answers: if all of D's nicotine came from gum, how often would D be
 * chewing? Named tiers come from the spec; between them are micro rungs one piece apart, so a
 * step down is always small.
 */
enum class Tier(val title: String, val pace: String, val plain: String) {
    WILDFIRE("Wildfire", "1 every waking hour", "Very heavy"),
    BLAZE("Blaze", "1 every 2 hours", "Heavy"),
    BONFIRE("Bonfire", "1 every 3 hours", "Moderately heavy"),
    CAMPFIRE("Campfire", "1 every 4 hours", "Moderate"),
    FLICKER("Flicker", "1 every 6 hours", "Light"),
    EMBERS("Embers", "Twice a day", "Very light"),
    CINDERS("Cinders", "Once a day", "Minimal"),
    ASH("Ash", "1 every 2 days", "Occasional"),
    LAST_WISP("Last Wisp", "1 every 3 days", "Almost gone"),
    CLEAR_AIR("Clear Air", "Nicotine-free", "Clear"),
}

/**
 * A rung: [pieces] a day (gum 4 mg equivalents; tiers always come from pieces). With a [unit] (0.22,
 * "Battery and steps in") it's shown in that product first: "Embers · 5 pouches a day".
 */
data class Rung(val pieces: Double, val tier: Tier, val unit: StepUnit? = null) {
    /** Minutes between pieces over a 16-hour waking day. */
    val intervalMinutes: Double get() = if (pieces <= 0) Double.POSITIVE_INFINITY else Ladder.WAKING_MINUTES / pieces

    val label: String get() = if (tier == Tier.CLEAR_AIR) "Clear Air"
        else if (unit != null) "${tier.title} · ${unit.amount(pieces)} a day"
        else "${tier.title} · ${Ladder.piecesText(pieces)} a day"

    /** e.g. "Moderate, about 4 pieces a day." or "Very light, about 5 pouches a day (2 pieces)." */
    val plainLine: String get() = when {
        tier == Tier.CLEAR_AIR -> "No nicotine."
        unit != null -> "${tier.plain}, about ${unit.amount(pieces)} a day (${Ladder.piecesText(pieces)} ${if (pieces == 1.0) "piece" else "pieces"})."
        else -> "${tier.plain}, about ${Ladder.piecesText(pieces)} ${if (pieces == 1.0) "piece" else "pieces"} a day."
    }
}

/**
 * A product D steps in (0.22): the battery holds one and each step down is one. [pieces]: what one
 * is worth in pieces (a 2 mg pouch ≈ 0.4).
 */
data class StepUnit(val productId: String, val pieces: Double, val noun: String, val plural: String) {
    /** How many of this unit [p] pieces are, e.g. 2 pieces → 5 pouches. */
    fun count(p: Double): Double = p / pieces

    /** "5", or "7.5" when it isn't a whole number. */
    fun countText(p: Double): String {
        val c = (count(p) * 10).roundToLong() / 10.0
        return if (c == c.toLong().toDouble()) c.toLong().toString() else c.toString()
    }

    /** "5 pouches", "1 pouch". */
    fun amount(p: Double): String = "${countText(p)} ${if (countText(p) == "1") noun else plural}"

    companion object {
        fun of(product: com.baastiklabs.firewatch.core.model.Product, pieces: Double): StepUnit {
            val (noun, plural) = when (product.kind) {
                com.baastiklabs.firewatch.core.model.ProductKind.POUCH -> "pouch" to "pouches"
                com.baastiklabs.firewatch.core.model.ProductKind.LOZENGE -> "lozenge" to "lozenges"
                com.baastiklabs.firewatch.core.model.ProductKind.GUM -> "gum" to "gums"
                else -> "dose" to "doses"
            }
            return StepUnit(product.id, pieces, noun, plural)
        }
    }
}

/**
 * The ladder D steps along (0.22). Without a unit it's the piece ladder ([Ladder.rungs]). With a
 * unit, rungs are whole numbers of it (5, 4, 3 … pouches), so each step down is one. Levels stay in
 * pieces and tiers come from pieces, so switching never moves the level or the progress.
 */
class Steps(val unit: StepUnit?) {
    val rungs: List<Rung> = if (unit == null) Ladder.rungs else {
        val n = kotlin.math.ceil(Ladder.MAX_PIECES / unit.pieces - 1e-9).toInt()
        (n downTo 1).map { k -> make(k * unit.pieces) }
    }

    private fun make(p: Double): Rung {
        val r = (p * 1e6).roundToLong() / 1e6
        return Rung(r, Ladder.tierFor(r), unit)
    }

    /** The rung for a level. With a unit the level is kept exactly (it may sit between whole units). */
    fun rung(pieces: Double): Rung = when {
        pieces <= 0 -> Ladder.clearAir
        unit == null -> Ladder.rung(pieces)
        else -> make(pieces)
    }

    fun nextDown(pieces: Double): Rung = rungs.firstOrNull { it.pieces < pieces - 1e-6 } ?: Ladder.clearAir

    fun nextUp(pieces: Double): Rung {
        if (pieces <= 0) return rungs.last()
        return rungs.lastOrNull { it.pieces > pieces + 1e-6 } ?: rungs.first()
    }

    /** The rung for a measured pace, rounded toward the heavier rung so it's never flattering. */
    fun measured(piecesPerDay: Double): Rung {
        if (piecesPerDay < 0.2) return Ladder.clearAir
        return rungs.lastOrNull { it.pieces >= piecesPerDay - 0.05 } ?: rungs.first()
    }

    /** Rungs from [heavier] down to [lighter] (exclusive of heavier), lightest last. */
    fun between(heavier: Double, lighter: Double): List<Rung> =
        rungs.filter { it.pieces < heavier - 1e-6 && it.pieces >= lighter - 1e-6 }

    /** How many steps lighter [lighter] is than [heavier] (0 if it isn't lighter). */
    fun stepsLighter(heavier: Double, lighter: Double): Int =
        if (lighter <= 0) rungs.count { it.pieces < heavier - 1e-6 } + 1
        else rungs.count { it.pieces < heavier - 1e-6 && it.pieces >= lighter - 1e-6 }

    /** "5 pouches" or "2 pieces". */
    fun amount(pieces: Double): String = unit?.amount(pieces)
        ?: "${Ladder.piecesText(pieces)} ${if (abs(pieces - 1.0) < 1e-6) "piece" else "pieces"}"

    /** "5" (pouches) or "2" (pieces). */
    fun text(pieces: Double): String = unit?.countText(pieces) ?: Ladder.piecesText(pieces)

    /**
     * A level change in a few words: "Embers 5 → 4 pouches", "Flicker 3 → Embers · 2 a day".
     * Used by the calendar day and the level history.
     */
    fun changeText(from: Double, to: Double): String {
        val a = rung(from); val b = rung(to)
        if (a.tier == Tier.CLEAR_AIR) return b.label
        if (a.tier != b.tier) return "${a.tier.title} ${text(from)} → ${b.label}"
        val word = unit?.let { u -> " " + (if (u.countText(to) == "1") u.noun else u.plural) } ?: ""
        return "${a.tier.title} ${text(from)} → ${text(to)}$word"
    }

    /** For Insights while stepping in a product: charts keep counting pieces. */
    val chartNote: String? get() = unit?.let { "Charts here count pieces: 1 piece = ${it.amount(1.0)}." }

    /** "pouch" or "piece". */
    val noun: String get() = unit?.noun ?: "piece"
    val plural: String get() = unit?.plural ?: "pieces"
}

object Ladder {
    const val WAKING_MINUTES = 16 * 60.0
    const val MAX_PIECES = 24.0

    val clearAir = Rung(0.0, Tier.CLEAR_AIR)

    /** Heaviest first: 24, 23, ..., 1, then ½ and ⅓. */
    val rungs: List<Rung> = (24 downTo 1).map { Rung(it.toDouble(), tierFor(it.toDouble())) } +
        listOf(Rung(0.5, Tier.ASH), Rung(1.0 / 3.0, Tier.LAST_WISP))

    fun tierFor(pieces: Double): Tier = when {
        pieces > 8.0 -> Tier.WILDFIRE
        pieces > 5.0 -> Tier.BLAZE
        pieces > 4.0 -> Tier.BONFIRE
        pieces > 3.0 -> Tier.CAMPFIRE
        pieces > 2.0 -> Tier.FLICKER
        pieces > 1.0 -> Tier.EMBERS
        pieces > 0.5 -> Tier.CINDERS
        pieces > 0.34 -> Tier.ASH
        pieces > 0.0 -> Tier.LAST_WISP
        else -> Tier.CLEAR_AIR
    }

    /** The rung for a measured pace, rounded toward the heavier rung so it's never flattering. */
    fun measured(piecesPerDay: Double): Rung {
        if (piecesPerDay < 0.2) return clearAir
        return rungs.lastOrNull { it.pieces >= piecesPerDay - 0.05 } ?: rungs.first()
    }

    fun rung(pieces: Double): Rung =
        rungs.minByOrNull { abs(it.pieces - pieces) }?.takeIf { pieces > 0 } ?: clearAir

    // Also right for levels between rungs (a pouch level like 1.6 after switching back to pieces).
    fun nextDown(pieces: Double): Rung = rungs.firstOrNull { it.pieces < pieces - 1e-6 } ?: clearAir

    fun nextUp(pieces: Double): Rung {
        if (pieces <= 0) return rungs.last()
        return rungs.lastOrNull { it.pieces > pieces + 1e-6 } ?: rungs.first()
    }

    /**
     * Where [pieces] sits on the piece ladder as a fractional rung index (0 = heaviest rung,
     * [rungs].size = Clear Air), interpolating between rungs so a pouch step moves it a little.
     */
    fun fractionalIndex(pieces: Double): Double {
        if (pieces <= 0) return rungs.size.toDouble()
        if (pieces >= rungs.first().pieces) return 0.0
        for (i in 0 until rungs.lastIndex) {
            val a = rungs[i].pieces; val b = rungs[i + 1].pieces
            if (pieces <= a + 1e-9 && pieces >= b - 1e-9) return i + (a - pieces) / (a - b)
        }
        val last = rungs.last().pieces
        return rungs.lastIndex + (last - pieces) / last
    }

    /** Position 0..1 from the heaviest rung to Clear Air (used for the journey meter). */
    fun position(pieces: Double): Double {
        if (pieces <= 0) return 1.0
        val i = rungs.indexOfFirst { it.pieces <= pieces + 1e-6 }.let { if (it < 0) rungs.lastIndex else it }
        return i.toDouble() / rungs.size
    }

    fun piecesText(p: Double): String = when {
        abs(p - 0.5) < 1e-6 -> "½"
        abs(p - 1.0 / 3.0) < 1e-3 -> "⅓"
        p == p.toLong().toDouble() -> p.toLong().toString()
        else -> ((p * 10).roundToLong() / 10.0).toString()
    }
}
