package com.baastiklabs.firewatch.core.engine

import kotlin.math.abs

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

data class Rung(val pieces: Double, val tier: Tier) {
    /** Minutes between pieces over a 16-hour waking day. */
    val intervalMinutes: Double get() = if (pieces <= 0) Double.POSITIVE_INFINITY else Ladder.WAKING_MINUTES / pieces

    val label: String get() = if (tier == Tier.CLEAR_AIR) "Clear Air" else "${tier.title} · ${Ladder.piecesText(pieces)} a day"

    /** e.g. "Moderate, about 4 pieces a day." */
    val plainLine: String get() = when (tier) {
        Tier.CLEAR_AIR -> "No nicotine."
        else -> "${tier.plain}, about ${Ladder.piecesText(pieces)} ${if (pieces == 1.0) "piece" else "pieces"} a day."
    }
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

    fun nextDown(pieces: Double): Rung {
        val i = rungs.indexOfFirst { abs(it.pieces - pieces) < 1e-6 }
        return if (i < 0 || i == rungs.lastIndex) clearAir else rungs[i + 1]
    }

    fun nextUp(pieces: Double): Rung {
        if (pieces <= 0) return rungs.last()
        val i = rungs.indexOfFirst { abs(it.pieces - pieces) < 1e-6 }
        return if (i <= 0) rungs.first() else rungs[i - 1]
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
        else -> String.format("%.1f", p)
    }
}
