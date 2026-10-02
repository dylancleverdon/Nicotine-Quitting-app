package com.baastiklabs.firewatch.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemesTest {
    @Test
    fun `every theme is readable in light and dark with every switch`() {
        val problems = ArrayList<String>()
        for (t in Themes.all) for (dark in listOf(false, true)) for (calm in listOf(true, false)) for (black in listOf(false, true)) for (cb in listOf(false, true)) {
            val p = Themes.palette(t.id, if (dark) "dark" else "light", false, black, calm, cb)
            fun need(what: String, a: String, b: String, min: Double) {
                val c = Themes.contrast(a, b)
                if (c < min) problems += "${t.id} ${if (p.dark) "dark" else "light"} calm=$calm black=$black: $what ${"%.2f".format(c)} < $min ($a on $b)"
            }
            for ((sn, s) in listOf("bg" to p.bg, "surface" to p.surface, "surface2" to p.surface2)) {
                need("text on $sn", p.text, s, 4.5)
                need("muted on $sn", p.muted, s, 4.5)
            }
            need("primary on bg", p.primary, p.bg, 3.0)
            need("tertiary on bg", p.tertiary, p.bg, 3.0)
            need("onPrimary on primary", p.onPrimary, p.primary, 4.5)
            need("onPrimarySoft on primarySoft", p.onPrimarySoft, p.primarySoft, 4.5)
            need("text on primarySoft", p.text, p.primarySoft, 4.5)
            p.heat.forEachIndexed { i, h -> need("day number on heat $i", p.onHeat[i], h, 3.0) }
        }
        assertTrue(problems.isEmpty(), problems.distinct().joinToString("\n"))
    }

    @Test
    fun `calmer colours have no red and are the default`() {
        val p = Themes.palette(Themes.DEFAULT, "dark", true)
        assertEquals("#a07aa8", p.kinds["VAPE"])
        assertEquals(18, Themes.all.size)
        assertEquals("#000000", Themes.palette("forest", "dark", false, trueBlack = true).bg)
        assertTrue(Themes.palette("terminal", "light", false).dark)
    }
}
