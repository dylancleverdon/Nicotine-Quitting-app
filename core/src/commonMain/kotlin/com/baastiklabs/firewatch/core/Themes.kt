package com.baastiklabs.firewatch.core

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Colour themes, written once here for both apps. Every theme has a light and a dark palette;
 * colours are "#rrggbb". Only colours change: tier names and the fire branding stay.
 * Negative net is grey (muted), never red, in every theme. Each palette is checked for contrast
 * in the tests (text and muted text ≥ 4.5:1 on the background and surfaces).
 */
object Themes {
    @Serializable
    data class Palette(
        val bg: String, val surface: String, val surface2: String, val surface3: String,
        val text: String, val muted: String, val line: String,
        val primary: String, val onPrimary: String, val primarySoft: String, val onPrimarySoft: String,
        val secondary: String, val tertiary: String,
        /** Calendar heat, level 0 (clear) to 6 (heaviest). */
        val heat: List<String>,
        val onHeat: List<String>,
        /** Product colours (chart and dose strip), by ProductKind name. */
        val kinds: Map<String, String>,
        /** Craving strength: calm (1) to strong (10). */
        val cravingLow: String, val cravingHigh: String,
        val dark: Boolean,
    )

    @Serializable
    data class Theme(val id: String, val name: String, val feel: String)

    const val DEFAULT = "firewatch"

    /** Seeds: hues in degrees, saturations 0..1. */
    private data class Seed(
        val id: String, val name: String, val feel: String,
        val nHue: Double, val nSat: Double,
        val pHue: Double, val pSat: Double,
        val sHue: Double, val tHue: Double,
        val lightOnly: Boolean = false,
        val darkOnly: Boolean = false,
        val special: String = "",
    )

    private val seeds = listOf(
        Seed(DEFAULT, "Firewatch", "Today's warm orange", 20.0, 0.25, 22.0, 1.0, 34.0, 165.0),
        Seed("ember-night", "Ember Night", "Near-black with glowing coal-orange accents", 15.0, 0.12, 18.0, 0.95, 30.0, 40.0),
        Seed("forest", "Forest", "Deep greens with a moss accent", 140.0, 0.22, 140.0, 0.45, 80.0, 95.0),
        Seed("moss-stone", "Moss & Stone", "Grey stone with lichen green", 90.0, 0.06, 85.0, 0.4, 60.0, 150.0),
        Seed("ink", "Ink", "Black and white only", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, special = "mono"),
        Seed("graphite", "Graphite", "Mid-greys with one cool blue accent", 220.0, 0.06, 215.0, 0.6, 215.0, 200.0),
        Seed("paper", "Paper", "Off-white with ink-blue text, like a notebook", 45.0, 0.25, 225.0, 0.55, 225.0, 200.0, special = "paper"),
        Seed("ocean", "Ocean", "Slate and teal", 205.0, 0.2, 180.0, 0.6, 200.0, 160.0),
        Seed("glacier", "Glacier", "Icy white, pale blue and steel grey", 205.0, 0.15, 205.0, 0.5, 215.0, 190.0),
        Seed("clear-sky", "Clear Sky", "Bright sky blue and white", 205.0, 0.3, 200.0, 0.85, 45.0, 170.0),
        Seed("aurora", "Aurora", "Midnight blue with green and violet highlights", 230.0, 0.35, 150.0, 0.6, 270.0, 280.0),
        Seed("dusk", "Dusk", "Muted plum and lavender", 290.0, 0.18, 285.0, 0.35, 260.0, 250.0),
        Seed("lavender", "Lavender Fields", "Lilac and sage", 270.0, 0.15, 268.0, 0.4, 110.0, 110.0),
        Seed("sakura", "Sakura", "Soft blush pink with charcoal text", 345.0, 0.2, 340.0, 0.5, 20.0, 160.0),
        Seed("sand", "Sand", "Soft beige and clay", 35.0, 0.25, 20.0, 0.45, 35.0, 120.0),
        Seed("desert", "Desert", "Terracotta, sage and cream", 30.0, 0.3, 15.0, 0.55, 40.0, 100.0),
        Seed("sunrise", "Sunrise", "Peach-to-gold accents on warm light", 30.0, 0.45, 28.0, 0.85, 45.0, 15.0),
        Seed("terminal", "Retro Terminal", "Black with amber text and mono-style numbers", 35.0, 0.0, 38.0, 1.0, 38.0, 90.0, special = "terminal"),
    )

    val all: List<Theme> = seeds.map { Theme(it.id, it.name, it.feel) }

    // ---- The original Firewatch palettes, kept exactly ----

    private val firewatchDark = base(
        "#140e0c", "#1f1714", "#2a201b", "#352924", "#f3e7df", "#c9b5a8", "#4a3d36",
        "#ff7a2f", "#2a1206", "#4a2412", "#ffdbc8", "#ffb35c", "#8fc7b8", true,
        heat = listOf("#241b17", "#4a3526", "#6e3f1f", "#9a4a1c", "#c8581c", "#e86a24", "#ff8f3a"),
    )
    private val firewatchLight = base(
        "#fff8f4", "#fbebe2", "#f5e4da", "#efded4", "#221a16", "#52443c", "#d8c2b7",
        "#b8480f", "#ffffff", "#ffdbc8", "#3a1402", "#8a5100", "#2e6b5e", false,
        heat = listOf("#f1e6df", "#ffe0c2", "#ffc08f", "#ff9e5e", "#f27a36", "#d9591b", "#b23f0b"),
    )

    /**
     * The palette to draw with. [mode]: "system", "light" or "dark" ([systemDark] is the phone's
     * setting). [calm]: lower saturation and no red anywhere. [colourBlind]: Okabe–Ito product
     * colours. [trueBlack]: pure black backgrounds in dark mode.
     */
    fun palette(
        id: String, mode: String, systemDark: Boolean,
        trueBlack: Boolean = false, calm: Boolean = true, colourBlind: Boolean = false,
    ): Palette {
        val seed = seeds.firstOrNull { it.id == id } ?: seeds.first()
        var dark = when (mode) { "light" -> false; "dark" -> true; else -> systemDark }
        if (seed.special == "terminal") dark = true
        var p = when {
            seed.id == DEFAULT -> if (dark) firewatchDark else firewatchLight
            else -> generate(seed, dark)
        }
        if (dark && trueBlack) p = p.copy(bg = "#000000", surface = "#0b0b0b", surface2 = "#161616", surface3 = "#202020", heat = listOf("#121212") + p.heat.drop(1))
        if (calm) p = calmed(p)
        p = p.copy(kinds = kindColours(p.dark, calm, colourBlind, seed.special == "mono"))
        if (calm) p = p.copy(cravingHigh = if (p.dark) "#b48ead" else "#7d5a86")
        return p
    }

    private fun base(
        bg: String, surface: String, surface2: String, surface3: String, text: String, muted: String, line: String,
        primary: String, onPrimary: String, primarySoft: String, onPrimarySoft: String, secondary: String, tertiary: String,
        dark: Boolean, heat: List<String>,
    ) = Palette(
        bg, surface, surface2, surface3, text, muted, line, primary, onPrimary, primarySoft, onPrimarySoft, secondary, tertiary,
        heat, heat.mapIndexed { i, h -> if (contrast(h, text) >= contrast(h, if (dark) "#000000" else "#ffffff")) text else (if (dark) "#000000" else "#ffffff") },
        emptyMap(), if (dark) "#5fa893" else "#2e7d6b", if (dark) "#e0443a" else "#b3261e", dark,
    )

    private fun generate(s: Seed, dark: Boolean): Palette {
        val mono = s.special == "mono"
        val terminal = s.special == "terminal"
        val paper = s.special == "paper"
        fun n(l: Double, satScale: Double = 1.0) = hsl(s.nHue, s.nSat * satScale, l)
        return if (dark) {
            val bg = if (terminal) "#050403" else n(0.07)
            val text = when { terminal -> hsl(38.0, 1.0, 0.62); mono -> "#f2f2f2"; else -> n(0.93, 0.6) }
            val muted = when { terminal -> hsl(38.0, 0.6, 0.55); mono -> "#bdbdbd"; else -> n(0.74, 0.5) }
            val primary = when { mono -> "#f2f2f2"; terminal -> hsl(38.0, 1.0, 0.58); else -> hsl(s.pHue, s.pSat, 0.64) }
            base(
                bg, if (terminal) "#0e0b07" else n(0.10), if (terminal) "#16110a" else n(0.14), if (terminal) "#1f170d" else n(0.18),
                text, muted, if (terminal) "#3a2c14" else n(0.28),
                primary, if (mono) "#0a0a0a" else hsl(s.pHue, s.pSat * 0.6, 0.10),
                if (mono) "#333333" else hsl(s.pHue, s.pSat * 0.45, 0.22), if (mono) "#f2f2f2" else hsl(s.pHue, s.pSat * 0.5, 0.88),
                if (mono) "#d0d0d0" else hsl(s.sHue, max(0.35, s.pSat * 0.7), 0.70),
                if (mono) "#a8a8a8" else hsl(s.tHue, 0.38, 0.68), true,
                heat = ramp(if (terminal) "#16110a" else n(0.14), primary),
            )
        } else {
            val bg = if (paper) hsl(45.0, 0.45, 0.96) else n(0.96, 1.6)
            val text = when { mono -> "#111111"; paper -> hsl(225.0, 0.55, 0.20); else -> n(0.12, 0.6) }
            val muted = when { mono -> "#4d4d4d"; paper -> hsl(225.0, 0.25, 0.36); else -> n(0.34, 0.5) }
            // Darken until white text on it passes (greens and teals look light at the same lightness).
            var pl = 0.36
            while (!mono && contrast("#ffffff", hsl(s.pHue, s.pSat, pl)) < 4.6 && pl > 0.15) pl -= 0.02
            val primary = if (mono) "#111111" else hsl(s.pHue, s.pSat, pl)
            base(
                bg, n(0.93, 1.6), n(0.90, 1.5), n(0.87, 1.4),
                text, muted, n(0.78),
                primary, if (mono) "#ffffff" else "#ffffff",
                if (mono) "#e0e0e0" else hsl(s.pHue, s.pSat * 0.6, 0.89), if (mono) "#111111" else hsl(s.pHue, s.pSat * 0.7, 0.16),
                if (mono) "#333333" else hsl(s.sHue, max(0.35, s.pSat * 0.7), 0.33),
                if (mono) "#555555" else hsl(s.tHue, 0.45, 0.30), false,
                heat = ramp(n(0.90, 1.5), if (mono) "#111111" else hsl(s.pHue, s.pSat, 0.38)),
            )
        }
    }

    /** Seven steps from [from] (clear) to [to] (heaviest). */
    private fun ramp(from: String, to: String): List<String> = (0..6).map { mix(from, to, it / 6.0) }

    // ---- Product colours ----

    private val baseKinds = mapOf(
        "GUM" to "#8fc7b8", "POUCH" to "#ffb35c", "LOZENGE" to "#b8d98f", "PATCH" to "#9fb3e0",
        "VAPE" to "#e0443a", "CIGARETTE" to "#8a6a5a", "OTHER" to "#b0a49c",
    )
    /** Okabe–Ito: tells apart for the common kinds of colour blindness. */
    private val colourBlindKinds = mapOf(
        "GUM" to "#009e73", "POUCH" to "#e69f00", "LOZENGE" to "#f0e442", "PATCH" to "#56b4e9",
        "VAPE" to "#cc79a7", "CIGARETTE" to "#0072b2", "OTHER" to "#999999",
    )
    private val monoKinds = mapOf(
        "GUM" to "#e0e0e0", "POUCH" to "#bdbdbd", "LOZENGE" to "#9e9e9e", "PATCH" to "#7a7a7a",
        "VAPE" to "#5c5c5c", "CIGARETTE" to "#3d3d3d", "OTHER" to "#8c8c8c",
    )

    private fun kindColours(dark: Boolean, calm: Boolean, colourBlind: Boolean, mono: Boolean): Map<String, String> {
        val k = when { colourBlind -> colourBlindKinds; mono -> monoKinds; else -> baseKinds }
        // Calmer colours: no red at all (vapes get a muted plum, cigarettes a stone grey).
        return if (calm && !colourBlind && !mono) k + mapOf("VAPE" to "#a07aa8", "CIGARETTE" to "#8c8478") else k
    }

    /** Lower saturation (×0.8) everywhere, and no red. */
    private fun calmed(p: Palette): Palette {
        fun c(x: String) = desaturate(x, 0.8)
        return p.copy(primary = c(p.primary), secondary = c(p.secondary), tertiary = c(p.tertiary),
            primarySoft = c(p.primarySoft), heat = p.heat.map(::c))
    }

    // ---- Colour maths ----

    fun hsl(h: Double, s: Double, l: Double): String {
        val c = (1 - abs(2 * l - 1)) * s
        val hh = ((h % 360) + 360) % 360 / 60
        val x = c * (1 - abs(hh % 2 - 1))
        val (r, g, b) = when (hh.toInt()) {
            0 -> Triple(c, x, 0.0); 1 -> Triple(x, c, 0.0); 2 -> Triple(0.0, c, x)
            3 -> Triple(0.0, x, c); 4 -> Triple(x, 0.0, c); else -> Triple(c, 0.0, x)
        }
        val m = l - c / 2
        return hex(r + m, g + m, b + m)
    }

    private fun rgb(h: String): Triple<Double, Double, Double> {
        val v = h.removePrefix("#").toInt(16)
        return Triple((v shr 16 and 255) / 255.0, (v shr 8 and 255) / 255.0, (v and 255) / 255.0)
    }

    private fun hex(r: Double, g: Double, b: Double): String {
        fun p(x: Double) = (x.coerceIn(0.0, 1.0) * 255).roundToInt().toString(16).padStart(2, '0')
        return "#" + p(r) + p(g) + p(b)
    }

    fun mix(a: String, b: String, t: Double): String {
        val (r1, g1, b1) = rgb(a); val (r2, g2, b2) = rgb(b)
        return hex(r1 + (r2 - r1) * t, g1 + (g2 - g1) * t, b1 + (b2 - b1) * t)
    }

    private fun desaturate(h: String, f: Double): String {
        val (r, g, b) = rgb(h)
        val grey = 0.299 * r + 0.587 * g + 0.114 * b
        return hex(grey + (r - grey) * f, grey + (g - grey) * f, grey + (b - grey) * f)
    }

    private fun lum(h: String): Double {
        fun ch(c: Double) = if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        val (r, g, b) = rgb(h)
        return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)
    }

    /** WCAG contrast ratio between two colours. */
    fun contrast(a: String, b: String): Double {
        val la = lum(a); val lb = lum(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }
}
