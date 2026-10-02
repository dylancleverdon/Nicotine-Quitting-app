package com.baastiklabs.firewatch.core

import com.baastiklabs.firewatch.core.engine.BatteryState
import com.baastiklabs.firewatch.core.engine.Coach
import com.baastiklabs.firewatch.core.engine.FriendVape
import com.baastiklabs.firewatch.core.engine.Insights
import com.baastiklabs.firewatch.core.engine.Kinetics
import com.baastiklabs.firewatch.core.engine.Ladder
import com.baastiklabs.firewatch.core.engine.Progress
import com.baastiklabs.firewatch.core.engine.Quality
import com.baastiklabs.firewatch.core.engine.Tier
import com.baastiklabs.firewatch.core.model.Craving
import com.baastiklabs.firewatch.core.model.CravingOutcome
import com.baastiklabs.firewatch.core.model.DefaultProducts
import com.baastiklabs.firewatch.core.model.ProductKind
import com.baastiklabs.firewatch.core.model.RungChange
import com.baastiklabs.firewatch.core.model.SpeedProfile
import com.baastiklabs.firewatch.core.records.FirewatchData
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EngineTest {
    private val tz = TimeZone.UTC
    private fun at(d: Int, h: Int, m: Int = 0) = LocalDateTime(2026, 9, d, h, m).toInstant(tz).toEpochMilliseconds()
    private val gum4 = DefaultProducts.all().first { it.id == DefaultProducts.GUM_4MG }

    @Test
    fun `ladder has one-piece micro rungs and spec tiers`() {
        assertEquals(26, Ladder.rungs.size)
        assertEquals(Tier.WILDFIRE, Ladder.rung(16.0).tier)
        assertEquals(Tier.WILDFIRE, Ladder.rung(12.0).tier)
        assertEquals(Tier.BLAZE, Ladder.rung(8.0).tier)
        assertEquals(Tier.BONFIRE, Ladder.rung(5.0).tier)
        assertEquals(Tier.CAMPFIRE, Ladder.rung(4.0).tier)
        assertEquals(Tier.FLICKER, Ladder.rung(3.0).tier)
        assertEquals(Tier.EMBERS, Ladder.rung(2.0).tier)
        assertEquals(Tier.CINDERS, Ladder.rung(1.0).tier)
        assertEquals(Tier.ASH, Ladder.rung(0.5).tier)
        assertEquals(Tier.LAST_WISP, Ladder.rung(1.0 / 3).tier)
        assertEquals(15.0, Ladder.nextDown(16.0).pieces)
        assertEquals(Tier.CLEAR_AIR, Ladder.nextDown(1.0 / 3).tier)
        assertEquals("Moderate, about 4 pieces a day.", Ladder.rung(4.0).plainLine)
    }

    @Test
    fun `measured rung rounds toward heavier`() {
        assertEquals(5.0, Ladder.measured(4.3).pieces)
        assertEquals(4.0, Ladder.measured(4.02).pieces)
        assertEquals(24.0, Ladder.measured(40.0).pieces)
        assertEquals(Tier.CLEAR_AIR, Ladder.measured(0.1).tier)
    }

    @Test
    fun `kinetics rise then fade with a two hour half life`() {
        val build = Kinetics.peakMinutes(SpeedProfile.BUILD)
        val spike = Kinetics.peakMinutes(SpeedProfile.SPIKE)
        assertTrue(spike < 15 && build in 25.0..50.0, "spike=$spike build=$build")
        val peak = Kinetics.contribution(2.0, SpeedProfile.BUILD, build)
        val later = Kinetics.contribution(2.0, SpeedProfile.BUILD, build + 120)
        assertTrue(kotlin.math.abs(later / peak - 0.5) < 0.1)
    }

    private fun withTarget(doses: List<com.baastiklabs.firewatch.core.model.Dose>, pieces: Double = 4.0, settings: com.baastiklabs.firewatch.core.model.Settings = com.baastiklabs.firewatch.core.model.Settings()) =
        FirewatchData(products = DefaultProducts.all(), doses = doses, settings = settings, rungChanges = listOf(RungChange("r", at(1, 8), pieces, "start")))

    @Test
    fun `a dose restarts the countdown one gap from when it's taken`() {
        // Target 4 a day -> 4 h gap. A piece at 12:00 means the next is due at 16:00.
        val data = withTarget(listOf(gum4.toDose("a", at(10, 12), 0)))
        val b = Progress.battery(data, 4.0, at(10, 13), tz)
        assertEquals(BatteryState.CHARGING, b.state)
        assertTrue(kotlin.math.abs(b.readyAt!! - at(10, 16)) <= 2 * 60_000L, "ready ${b.readyAt}")
        assertEquals(BatteryState.CLEAR, Progress.battery(data, 4.0, at(10, 17), tz).state)
    }

    @Test
    fun `no debt - three quick pieces still only wait one gap`() {
        val data = withTarget(listOf(gum4.toDose("a", at(10, 12), 0), gum4.toDose("b", at(10, 12, 5), 0), gum4.toDose("c", at(10, 12, 10), 0)))
        val b = Progress.battery(data, 4.0, at(10, 12, 15), tz)
        assertTrue(b.charge >= 0.0)
        assertTrue(b.readyAt!! <= at(10, 16, 12), "ready ${b.readyAt}")
    }

    @Test
    fun `fresh start every morning`() {
        val data = withTarget(listOf(gum4.toDose("a", at(10, 21), 0), gum4.toDose("b", at(10, 22), 0)))
        val morning = Progress.battery(data, 4.0, at(11, 7, 5), tz)
        assertEquals(BatteryState.CLEAR, morning.state)
    }

    @Test
    fun `full when you wake up instead of waiting overnight`() {
        val data = withTarget(listOf(gum4.toDose("a", at(10, 21), 0)))
        val b = Progress.battery(data, 4.0, at(10, 21, 30), tz)
        assertEquals(BatteryState.FULL_AT_WAKE, b.state)
        assertEquals(at(11, 7), b.readyAt)
    }

    @Test
    fun `asleep after bedtime until the app is opened`() {
        val data = withTarget(listOf(gum4.toDose("a", at(10, 22), 0)))
        assertEquals(BatteryState.ASLEEP, Progress.battery(data, 4.0, at(11, 1), tz).state)
        // Opened the app at 01:00: up since bedtime, the 22:00 piece is 3 h ago -> 1 h to go.
        val up = Progress.battery(data, 4.0, at(11, 1), tz, lastActivityAt = at(11, 1))
        assertEquals(BatteryState.CHARGING, up.state)
        assertTrue(kotlin.math.abs(up.readyAt!! - at(11, 2)) <= 2 * 60_000L)
    }

    @Test
    fun `wind down is a note and never hides the guidance`() {
        val data = withTarget(emptyList())
        val b = Progress.battery(data, 4.0, at(10, 22, 30), tz)
        assertEquals(BatteryState.CLEAR, b.state)
        assertTrue(b.closeToBed)
        assertTrue(!Progress.battery(data, 4.0, at(10, 20), tz).closeToBed)
        // Off in Settings: no note.
        assertTrue(!Progress.battery(data.copy(settings = com.baastiklabs.firewatch.core.model.Settings(windDown = false)), 4.0, at(10, 22, 30), tz).closeToBed)
    }

    @Test
    fun `stretch pull and net`() {
        // Wake 7:00, target 4 a day (4 h gap). Hold off until 9:00 (2 h stretch), piece; next at 11:00 (2 h early -> 2 h pull).
        val data = withTarget(listOf(gum4.toDose("a", at(10, 9), 0), gum4.toDose("b", at(10, 11), 0)))
        val d = com.baastiklabs.firewatch.core.engine.BatteryEngine.day(data, kotlinx.datetime.LocalDate(2026, 9, 10), tz, at(10, 12))!!
        assertTrue(kotlin.math.abs(d.stretchMin - 120) <= 2, "stretch ${d.stretchMin}")
        assertTrue(kotlin.math.abs(d.pullMin - 120) <= 2, "pull ${d.pullMin}")
        assertTrue(kotlin.math.abs(d.netMin) <= 4)
        // Late-night catch-up never counts as stretch.
        val late = withTarget(listOf(gum4.toDose("x", at(10, 23, 30), 0)))
        val ld = com.baastiklabs.firewatch.core.engine.BatteryEngine.day(late, kotlinx.datetime.LocalDate(2026, 9, 10), tz, at(11, 6))!!
        assertTrue(ld.stretchMin <= 16 * 60 + 1)
    }

    private fun product(id: String) = DefaultProducts.all().first { it.id == id }

    private fun pullFor(vararg doses: com.baastiklabs.firewatch.core.model.Dose, pieces: Double = 5.0) =
        com.baastiklabs.firewatch.core.engine.BatteryEngine.day(withTarget(doses.toList(), pieces), kotlinx.datetime.LocalDate(2026, 9, 10), tz, at(10, 23))!!

    private val BE = com.baastiklabs.firewatch.core.engine.BatteryEngine
    private val d10 = kotlinx.datetime.LocalDate(2026, 9, 10)
    private fun dayUntil(data: FirewatchData, until: Long) = BE.day(data, d10, tz, until)!!

    @Test
    fun `every dose empties the battery and the next piece is one gap later`() {
        // Bonfire: 5 a day, gap 3h 12m. A small Zyn 3 mg with a full battery still empties it.
        val zyn3 = product(DefaultProducts.ZYN_3MG)
        val data = withTarget(listOf(zyn3.toDose("a", at(10, 9), 0)), 5.0)
        val b = Progress.battery(data, 5.0, at(10, 9, 1), tz)
        assertTrue(b.charge < 0.01)
        assertTrue(kotlin.math.abs(b.readyAt!! - (at(10, 9) + 192 * 60_000L)) <= 2 * 60_000L)
        // A double doesn't wait longer than one gap either.
        val dbl = withTarget(listOf(gum4.toDose("a", at(10, 9), 0), gum4.toDose("b", at(10, 9), 0)), 5.0)
        assertTrue(kotlin.math.abs(Progress.battery(dbl, 5.0, at(10, 9, 1), tz).readyAt!! - (at(10, 9) + 192 * 60_000L)) <= 2 * 60_000L)
    }

    @Test
    fun `dose size shows up in stretch and pull`() {
        // Taken at wake-up (7:00) with a full battery, so only the size effect counts.
        fun net(vararg doses: com.baastiklabs.firewatch.core.model.Dose) = dayUntil(withTarget(doses.toList(), 5.0), at(10, 7, 1)).netMin
        assertEquals(96.0, net(product(DefaultProducts.GUM_2MG).toDose("a", at(10, 7), 0)), 1.0)
        assertEquals(76.8, net(product(DefaultProducts.ZYN_3MG).toDose("a", at(10, 7), 0)), 1.0)
        assertEquals(0.0, net(gum4.toDose("a", at(10, 7), 0)), 1.0)
        assertEquals(-38.4, net(product(DefaultProducts.ZYN_6MG).toDose("a", at(10, 7), 0)), 1.0)
        assertEquals(-192.0, net(gum4.toDose("a", at(10, 7), 0), gum4.toDose("b", at(10, 7), 0)), 1.0)
        // The preview says the same before logging.
        val empty = withTarget(emptyList(), 5.0)
        assertEquals(-38.4, BE.preview(empty, product(DefaultProducts.ZYN_6MG), 5.0, at(10, 9), tz)!!, 1.0)
        assertEquals(96.0, BE.preview(empty, product(DefaultProducts.GUM_2MG), 5.0, at(10, 9), tz)!!, 1.0)
        // Half-full battery: timing and size combine (Zyn 6 mg: 96m + 38m ≈ 2h 14m of pull).
        val half = withTarget(listOf(gum4.toDose("a", at(10, 9), 0)), 5.0)
        assertEquals(-134.4, BE.preview(half, product(DefaultProducts.ZYN_6MG), 5.0, at(10, 10, 36), tz)!!, 2.0)
    }

    @Test
    fun `steady pace nets zero whatever the product`() {
        // Net over one full cycle (between the 2nd and 3rd dose) is ~0 at 5 a day.
        fun cycle(p: com.baastiklabs.firewatch.core.model.Product, every: Long): Double {
            val doses = (0 until 6).map { p.toDose("x$it", at(10, 7) + it * every * 60_000L, 0) }
            val data = withTarget(doses, 5.0)
            val a = dayUntil(data, at(10, 7) + every * 60_000L - 60_000L).netMin
            val b = dayUntil(data, at(10, 7) + 2 * every * 60_000L - 60_000L).netMin
            return b - a
        }
        assertEquals(0.0, cycle(product(DefaultProducts.ZYN_6MG), 230), 3.0)
        assertEquals(0.0, cycle(product(DefaultProducts.GUM_2MG), 96), 3.0)
        assertEquals(0.0, cycle(gum4, 192), 3.0)
        // Two gum 2 mg together count exactly like one gum 4 mg.
        val gum2 = product(DefaultProducts.GUM_2MG)
        val split = withTarget(listOf(gum2.toDose("a", at(10, 9), 0), gum2.toDose("b", at(10, 9), 0)), 5.0)
        val whole = withTarget(listOf(gum4.toDose("a", at(10, 9), 0)), 5.0)
        assertEquals(dayUntil(whole, at(10, 20)).netMin, dayUntil(split, at(10, 20)).netMin, 1.0)
        assertEquals(Progress.battery(whole, 5.0, at(10, 9, 5), tz).readyAt, Progress.battery(split, 5.0, at(10, 9, 5), tz).readyAt)
    }

    @Test
    fun `five zyn 6 a day at full battery give a negative net`() {
        val zyn6 = product(DefaultProducts.ZYN_6MG)
        val doses = (0 until 5).map { i -> zyn6.toDose("z$i", at(10, 7) + (i * 192L + 1) * 60_000L, 0) }
        assertTrue(dayUntil(withTarget(doses, 5.0), at(10, 23)).netMin < 0)
    }

    @Test
    fun `range doses use the middle estimate`() {
        val vape = com.baastiklabs.firewatch.core.model.Dose("v", "x", at(10, 7), kind = ProductKind.VAPE, speed = SpeedProfile.SPIKE, rangeLowMg = 1.0, rangeHighMg = 3.0)
        // Middle 2.0 mg = one piece: no size effect.
        assertEquals(0.0, dayUntil(withTarget(listOf(vape), 5.0), at(10, 7, 1)).netMin, 1.0)
    }

    @Test
    fun `fast battery maths matches the minute-by-minute version`() {
        val zyn6 = product(DefaultProducts.ZYN_6MG)
        val gum2 = product(DefaultProducts.GUM_2MG)
        val doses = listOf(gum4.toDose("a", at(10, 8, 13), 0), zyn6.toDose("b", at(10, 10, 2), 0), gum2.toDose("c", at(10, 10, 40), 0),
            gum4.toDose("d", at(10, 15, 7), 0), zyn6.toDose("e", at(10, 22, 50), 0), gum4.toDose("f", at(11, 1, 30), 0))
        val data = withTarget(doses, 5.0)
        val (day, next) = BE.dayOf(data, at(10, 12), tz)
        listOf(at(10, 9), at(10, 12), at(10, 18), at(10, 23, 30), at(11, 2)).forEach { until ->
            val slow = BE.simulateByMinute(data, day, next, 192.0, until, at(11, 1, 30))
            val fast = BE.day(data, d10, tz, until)!!
            assertEquals(slow.second, fast.stretchMin, 3.0, "stretch at $until")
            assertEquals(slow.third, fast.pullMin, 3.0, "pull at $until")
        }
    }

    @Test
    fun `cheer only after waiting for a full battery and not for the first piece`() {
        val first = gum4.toDose("a", at(10, 8), 0)
        val waited = gum4.toDose("b", at(10, 13), 0)
        val early = gum4.toDose("c", at(10, 14), 0)
        val data = withTarget(listOf(first, waited, early))
        val e = com.baastiklabs.firewatch.core.engine.BatteryEngine
        assertTrue(!e.waitedForFull(data, first, tz))
        assertTrue(e.waitedForFull(data, waited, tz))
        assertTrue(!e.waitedForFull(data, early, tz))
    }

    @Test
    fun `custom morning delay at a clock time`() {
        val s = com.baastiklabs.firewatch.core.model.Settings(morningDelayClock = 11 * 60)
        val b = Progress.battery(withTarget(emptyList(), settings = s), 4.0, at(10, 9), tz)
        assertEquals(BatteryState.MORNING_DELAY, b.state)
        assertEquals(at(10, 11), b.readyAt)
    }

    @Test
    fun `step down offered after holding the target`() {
        val doses = (1..20).flatMap { d -> (0 until 3).map { i -> gum4.toDose("d$d-$i", at(d, 9 + i * 4), 0) } }
        val data = FirewatchData(
            products = DefaultProducts.all(),
            doses = doses,
            rungChanges = listOf(RungChange("r", at(5, 8), 3.0, "start")),
        )
        assertEquals(2.0, Progress.stepDownOffer(data, at(15, 10), tz)?.pieces)
        assertNull(Progress.stepDownOffer(data, at(8, 10), tz))
    }

    @Test
    fun `step down progress counts held days and matches the offer`() {
        val doses = (1..20).flatMap { d -> (0 until 3).map { i -> gum4.toDose("d$d-$i", at(d, 9 + i * 4), 0) } }
        val rung = listOf(RungChange("r", at(5, 8), 3.0, "start"))
        val data = FirewatchData(products = DefaultProducts.all(), doses = doses, rungChanges = rung)
        // Days 6 and 7 count (the change day doesn't); today (the 8th) isn't finished.
        val early = Progress.stepDownProgress(data, at(8, 10), tz)!!
        assertEquals(2, early.held)
        assertEquals(7, early.needed)
        assertTrue(!early.ready)
        val ready = Progress.stepDownProgress(data, at(15, 10), tz)!!
        assertEquals(7, ready.held)
        assertTrue(ready.ready)
        assertEquals(2.0, ready.next.pieces)
        // A heavier day before the hold is reached starts the count again from the day after it.
        val heavy = data.copy(doses = doses + (0 until 2).map { gum4.toDose("x$it", at(10, 20 + it), 0) })
        assertEquals(4, Progress.stepDownProgress(heavy, at(15, 10), tz)!!.held)
        assertNull(Progress.stepDownOffer(heavy, at(15, 10), tz))
        // Once unlocked it stays unlocked until the level changes, even after a heavier day.
        val after = data.copy(doses = doses + (0 until 2).map { gum4.toDose("y$it", at(13, 20 + it), 0) })
        val p = Progress.stepDownProgress(after, at(15, 10), tz)!!
        assertTrue(p.unlocked && p.ready)
        assertEquals(7, p.held)
        assertNotNull(Progress.stepDownOffer(after, at(15, 10), tz))
        // ...and after "Stay here" the offer pauses for a day, but stays unlocked.
        val snoozed = after.copy(settings = after.settings.copy(stepDownSnoozedAt = at(15, 9)))
        assertNull(Progress.stepDownOffer(snoozed, at(15, 10), tz))
        assertTrue(Progress.stepDownProgress(snoozed, at(15, 10), tz)!!.unlocked)
    }

    @Test
    fun `coach learns capacity from ride-outs`() {
        val cravings = (1..12).map { i ->
            val level = if (i % 2 == 0) 4 else 7
            Craving("c$i", at(1 + i, 10), level, outcome = if (level == 4) CravingOutcome.RODE_OUT else CravingOutcome.USED)
        }
        val p = Coach.profile(FirewatchData(cravings = cravings), at(20, 0))
        assertTrue(p.confident)
        assertTrue(p.capacity in 3..5, "capacity ${p.capacity}")
    }

    @Test
    fun `quality food scale rewards quit aids not less vaping`() {
        val products = DefaultProducts.all().associateBy { it.id }
        val gum = listOf(gum4.toDose("g1", at(1, 9), 0), gum4.toDose("g2", at(1, 14), 0))
        assertEquals(Quality.Food.BROCCOLI, Quality.food(Quality.of(gum, 2.0)!!))
        val zyn3 = listOf(products.getValue(DefaultProducts.ZYN_3MG).toDose("z", at(1, 9), 0))
        assertEquals(Quality.Food.SANDWICH, Quality.food(Quality.of(zyn3, 2.0)!!))
        val cig = listOf(products.getValue(DefaultProducts.CIGARETTE).toDose("c", at(1, 9), 0))
        assertEquals(Quality.Food.BURGER, Quality.food(Quality.of(cig, 2.0)!!))
        // A big vape session is a donut; a small one isn't better than broccoli-level gum.
        val vape = com.baastiklabs.firewatch.core.model.Dose("v", "x", at(1, 9), kind = ProductKind.VAPE, speed = SpeedProfile.SPIKE, rangeLowMg = 1.0, rangeHighMg = 3.0)
        assertEquals(Quality.Food.DONUT, Quality.food(Quality.of(listOf(vape), 2.0)!!))
        // Stacking costs points.
        val stacked = listOf(gum4.toDose("a", at(1, 9), 0), gum4.toDose("b", at(1, 9, 10), 0))
        assertTrue(Quality.of(stacked, 2.0)!! < 100.0)
        assertNotNull(Quality.swapTip(zyn3, 2.0))
        assertNull(Quality.swapTip(gum, 2.0))
    }

    @Test
    fun `backfill spreads estimated doses across the waking day`() {
        val data = FirewatchData(products = DefaultProducts.all())
        val day = kotlinx.datetime.LocalDate(2026, 9, 20)
        var n = 0
        val doses = com.baastiklabs.firewatch.core.engine.Backfill.doses(
            data, day,
            com.baastiklabs.firewatch.core.engine.Backfill.DayEntry(mapOf(DefaultProducts.ZYN_3MG to 4, DefaultProducts.CIGARETTE to 2)),
            tz, 0L, { "id${n++}" },
        )
        assertEquals(6, doses.size)
        assertTrue(doses.all { it.estimated && it.at in at(20, 7)..at(20, 23) })
        assertEquals(7, com.baastiklabs.firewatch.core.engine.Backfill.days(kotlinx.datetime.LocalDate(2026, 9, 27)).size)
        // A back-filled week completes the baseline straight away.
        val week = com.baastiklabs.firewatch.core.engine.Backfill.days(kotlinx.datetime.LocalDate(2026, 9, 27)).flatMap { d ->
            com.baastiklabs.firewatch.core.engine.Backfill.doses(data, d, com.baastiklabs.firewatch.core.engine.Backfill.DayEntry(mapOf(DefaultProducts.GUM_4MG to 5)), tz, 0L, { "w${n++}" })
        }
        val full = data.copy(doses = week)
        assertTrue(Progress.tiersRevealed(full, kotlinx.datetime.LocalDate(2026, 9, 27), tz))
        assertEquals(5.0, Progress.measuredRung(full, kotlinx.datetime.LocalDate(2026, 9, 27), tz)?.pieces)
    }

    @Test
    fun `friend vape range widens when strength unknown`() {
        val known = FriendVape.range(50.0, FriendVape.Amount.FEW.puffs)
        val unknown = FriendVape.range(null, FriendVape.Amount.FEW.puffs)
        assertTrue(unknown.first < known.first && unknown.second >= known.second - 1e-9)
        assertTrue(known.first < known.second)
    }

    @Test
    fun `insights compute a week of stats`() {
        val doses = (1..14).flatMap { d -> (0 until (10 - d / 2)).map { i -> gum4.toDose("d$d-$i", at(d, 8 + i), 0, tags = listOf("Coffee")) } }
        val data = FirewatchData(products = DefaultProducts.all(), doses = doses)
        val ins = Insights(data, tz, at(15, 12))
        assertEquals(15, ins.days.size)
        assertNotNull(ins.baselineAverage)
        assertTrue(ins.piecesAvoided() > 0)
        assertNotNull(ins.taperPercentPerWeek())
        assertTrue(ins.taperPercentPerWeek()!! > 0)
        assertTrue(ins.insightCards().isNotEmpty())
        assertEquals(48, ins.days.first().barcode.size)
        assertTrue(ins.arrivals().isNotEmpty())
        assertNotNull(ins.monthlyRecap(2026, 9))
        assertTrue(ins.typicalCurve().size == 48)
    }

    // ---- Relapse prevention mode ----

    private val R = com.baastiklabs.firewatch.core.engine.Relapse
    private fun modeOn(at: Long) = com.baastiklabs.firewatch.core.model.ModeChange("m$at", at, true)

    @Test
    fun `relapse gap follows the tier and falls back to two hours`() {
        val noTier = FirewatchData(products = DefaultProducts.all(), modeChanges = listOf(modeOn(at(10, 8))))
        assertEquals(120.0, R.gapMinutes(noTier, at(10, 9)), 0.01)
        val bonfire = withTarget(emptyList(), 5.0).copy(modeChanges = listOf(modeOn(at(10, 8))))
        assertEquals(192.0, R.gapMinutes(bonfire, at(10, 9)), 0.01)
        // The tier changes, the gap follows.
        val stepped = bonfire.copy(rungChanges = bonfire.rungChanges + RungChange("r2", at(10, 12), 4.0, "down"))
        assertEquals(240.0, R.gapMinutes(stepped, at(10, 13)), 0.01)
    }

    @Test
    fun `relapse reminders restart after a piece and sleep at night`() {
        val base = FirewatchData(products = DefaultProducts.all(), settings = com.baastiklabs.firewatch.core.model.Settings(onboardingDone = true), modeChanges = listOf(modeOn(at(10, 6))))
        // Wake 7:00, no tier: first reminder 9:00.
        assertEquals(at(10, 9), R.nextAt(base, at(10, 8), tz))
        assertTrue(!R.shouldRemind(base, at(10, 8), tz, 0L))
        assertTrue(R.shouldRemind(base, at(10, 9), tz, 0L))
        // A piece at 9:30 restarts the timer: next 11:30.
        val logged = base.copy(doses = listOf(gum4.toDose("a", at(10, 9, 30), 0)))
        assertEquals(at(10, 11, 30), R.nextAt(logged, at(10, 10), tz))
        // Late piece: next reminder moves to the morning, nothing while asleep.
        val late = base.copy(doses = listOf(gum4.toDose("a", at(10, 22), 0)))
        assertEquals(at(11, 9), R.nextAt(late, at(10, 22, 30), tz))
        assertTrue(!R.shouldRemind(late, at(11, 1), tz, 0L))
        // Ignored reminder: no repeat within one gap.
        assertTrue(!R.shouldRemind(base, at(10, 10), tz, at(10, 9)))
        assertEquals(at(10, 11), R.nextCheckAt(base, at(10, 9, 1), tz, at(10, 9)))
        // Off: nothing.
        assertNull(R.nextAt(base.copy(modeChanges = base.modeChanges + com.baastiklabs.firewatch.core.model.ModeChange("off", at(10, 7), false)), at(10, 9), tz))
    }

    @Test
    fun `relapse recommendation and moving on`() {
        val s = com.baastiklabs.firewatch.core.model.Settings(onboardingDone = true)
        val cig = DefaultProducts.all().first { it.id == DefaultProducts.CIGARETTE }
        val smoked = FirewatchData(products = DefaultProducts.all(), settings = s, doses = listOf(cig.toDose("c", at(10, 9), 0)))
        assertEquals(com.baastiklabs.firewatch.core.engine.RelapseReason.SMOKED_OR_VAPED, R.recommendation(smoked, at(11, 9), tz))
        // Dismissed: hidden for 2 weeks.
        val dismissed = smoked.copy(settings = s.copy(relapseCardDismissedAt = at(11, 9)))
        assertNull(R.recommendation(dismissed, at(20, 9), tz))
        // Never while on.
        assertNull(R.recommendation(smoked.copy(modeChanges = listOf(modeOn(at(10, 10)))), at(11, 9), tz))
        // Early heavy gum: 7 a day in the first week.
        val gum = (4..10).flatMap { d -> (0 until 7).map { i -> gum4.toDose("g$d-$i", at(d, 8 + i * 2), 0) } }
        assertEquals(com.baastiklabs.firewatch.core.engine.RelapseReason.EARLY_HEAVY_GUM, R.recommendation(FirewatchData(products = DefaultProducts.all(), settings = s, doses = gum), at(10, 23), tz))
        // Strong cravings.
        val cr = (1..3).map { Craving("k$it", at(9, 8 + it), 8) }
        assertEquals(com.baastiklabs.firewatch.core.engine.RelapseReason.STRONG_CRAVINGS, R.recommendation(FirewatchData(products = DefaultProducts.all(), settings = s, cravings = cr), at(10, 9), tz))
        assertNull(R.recommendation(FirewatchData(products = DefaultProducts.all(), settings = s), at(10, 9), tz))
        // Moving on after 4 steady weeks.
        val on = FirewatchData(products = DefaultProducts.all(), settings = s, modeChanges = listOf(modeOn(at(1, 8))))
        assertTrue(!R.movingOn(on, at(20, 8)))
        assertTrue(R.movingOn(on, at(1, 8) + 29L * 24 * 3_600_000))
    }

    @Test
    fun `stretch and pull pause on relapse days, tiers unchanged`() {
        val doses = listOf(gum4.toDose("a", at(10, 9), 0), gum4.toDose("b", at(10, 10), 0))
        val plain = withTarget(doses)
        val mode = plain.copy(modeChanges = listOf(modeOn(at(10, 8))))
        val d = kotlinx.datetime.LocalDate(2026, 9, 10)
        assertTrue(com.baastiklabs.firewatch.core.engine.BatteryEngine.day(plain, d, tz, at(10, 12))!!.pullMin > 0)
        val paused = com.baastiklabs.firewatch.core.engine.BatteryEngine.day(mode, d, tz, at(10, 12))!!
        assertTrue(paused.paused && paused.pullMin == 0.0 && paused.stretchMin == 0.0)
        assertEquals(0.0, Progress.battery(mode, 4.0, at(10, 12), tz).pullMinutesToday)
        assertEquals(Progress.rollingAverage(plain, d, tz), Progress.rollingAverage(mode, d, tz))
        assertTrue(!R.isModeDay(mode, kotlinx.datetime.LocalDate(2026, 9, 9), tz))
        assertTrue(R.isModeDay(mode, kotlinx.datetime.LocalDate(2026, 9, 12), tz))
    }

    @Test
    fun `mode changes round-trip through records`() {
        val env = com.baastiklabs.firewatch.core.records.RecordCodec.mode(modeOn(at(10, 8)), null, 1L)
        val data = FirewatchData.fromRecords(listOf(env))
        assertTrue(data.relapseOn)
        assertTrue(com.baastiklabs.firewatch.core.Help.search("relapse").isNotEmpty())
    }

    // ---- Craving forecast ----

    private val CF = com.baastiklabs.firewatch.core.engine.CravingForecast

    private fun cravingDays(days: IntRange, hour: Int, intensity: Int, doses: Boolean = true): FirewatchData {
        val cr = days.map { d -> Craving("c$d", at(d, hour), intensity, outcome = com.baastiklabs.firewatch.core.model.CravingOutcome.RODE_OUT) }
        val ds = if (doses) days.flatMap { d -> listOf(9, 13, 19).map { h -> gum4.toDose("d$d-$h", at(d, h), 0) } } else emptyList()
        return FirewatchData(products = DefaultProducts.all(), doses = ds, cravings = cr, settings = com.baastiklabs.firewatch.core.model.Settings(onboardingDone = true))
    }

    @Test
    fun `forecast peaks when cravings usually happen`() {
        val data = cravingDays(1..20, 15, 6)
        val o = CF.outlook(data, at(21, 8), tz)
        assertTrue(!o.learning)
        val next = o.next!!
        val h = java.time.Instant.ofEpochMilli(next.peakAt).atZone(java.time.ZoneOffset.UTC).hour
        assertTrue(h in 14..16, "peak hour $h")
        assertTrue(next.strength in 5.0..8.0, "strength ${next.strength}")
        // Asleep: no chance shown.
        assertTrue(o.points.filter { it.asleep }.all { it.likelihood == 0.0 })
        // The back-test finds most 3 PM cravings inside the predicted window.
        assertTrue(o.tested >= 10 && o.hits >= o.tested * 3 / 4, "hits ${o.hits}/${o.tested}")
    }

    @Test
    fun `strong past cravings forecast strong ones`() {
        val mild = CF.outlook(cravingDays(1..20, 15, 3), at(21, 8), tz).next!!.strength
        val strong = CF.outlook(cravingDays(1..20, 15, 8), at(21, 8), tz).next!!.strength
        assertTrue(strong > mild + 3, "mild $mild strong $strong")
    }

    @Test
    fun `falling nicotine raises the chance`() {
        // Same craving pattern; one day D has had nothing since morning.
        val base = cravingDays(1..20, 15, 6)
        val withDose = base.copy(doses = base.doses + gum4.toDose("x", at(21, 11), 0))
        val without = base
        val p = { d: FirewatchData -> CF.outlook(d, at(21, 12, 5), tz).points.first { it.at == at(21, 12) }.likelihood }
        assertTrue(p(without) > p(withDose), "without ${p(without)} with ${p(withDose)}")
    }

    @Test
    fun `forecast is still learning with few cravings`() {
        val data = cravingDays(1..3, 15, 6)
        val o = CF.outlook(data, at(4, 8), tz)
        assertTrue(o.learning)
        assertEquals(3, o.cravingsUsed)
        assertTrue(o.points.isNotEmpty())
        // No cravings, no doses: nothing to go on, so no invented window.
        val empty = CF.outlook(FirewatchData(products = DefaultProducts.all()), at(4, 8), tz)
        assertNull(empty.next)
    }

    // ---- Receptors ----

    private val RC = com.baastiklabs.firewatch.core.engine.Receptors

    @Test
    fun `receptor load heals on the research timescale after stopping`() {
        var load = 1.0
        repeat(42) { load = RC.step(load, 0.0) }
        assertEquals(0.1, load, 0.01)
        repeat(42) { load = RC.step(load, 0.0) }
        assertTrue(load < 0.01)
        // Heavy use heads to full load; more nicotine, more load.
        assertEquals(1.0, RC.targetLoad(RC.HEAVY_MG), 1e-9)
        assertTrue(RC.targetLoad(0.24) < RC.targetLoad(1.2))
        var up = 0.0
        repeat(21) { up = RC.step(up, 1.0) }
        assertTrue(up > 0.9)
    }

    @Test
    fun `following the plan heals sooner than staying`() {
        val doses = (1..14).flatMap { d -> (0 until 6).map { i -> gum4.toDose("d$d-$i", at(d, 8 + i * 2), 0) } }
        val data = withTarget(doses, 6.0).copy(rungChanges = listOf(RungChange("r", at(8, 8), 6.0, "start")))
        val o = RC.outlook(data, at(15, 12), tz)!!
        assertTrue(o.todayLoad in 0.3..1.0, "today ${o.todayLoad}")
        assertNotNull(o.clearAirOnPlan)
        assertNotNull(o.typicalOnPlan)
        assertNull(o.typicalIfStay)
        assertTrue(o.plan.last().load < o.stay.last().load)
        // A step up is reflected: the plan starts from the heavier rung and takes longer.
        val up = data.copy(rungChanges = data.rungChanges + RungChange("u", at(15, 9), 7.0, "up"))
        val o2 = RC.outlook(up, at(15, 12), tz)!!
        assertTrue(o2.typicalOnPlan!! > o.typicalOnPlan!!)
        // Nothing logged: no guess.
        assertNull(RC.outlook(FirewatchData(products = DefaultProducts.all()), at(15, 12), tz))
    }

    // ---- How cravings ended ----

    private fun result(data: FirewatchData, c: Craving, now: Long) = Cravings.result(data, c, now, tz)

    @Test
    fun `cravings end as rode out, waited, early or relapse`() {
        val c = Craving("c", at(10, 12), 7)
        val base = withTarget(listOf(gum4.toDose("a", at(10, 8), 0)))  // 4 a day: 4 h gap, full again at 12:00
        assertEquals(CravingResult.PENDING, result(base.copy(cravings = listOf(c)), c, at(10, 12, 20)))
        assertEquals(CravingResult.RODE_OUT, result(base.copy(cravings = listOf(c)), c, at(10, 12, 50)))
        // A piece after the battery was full: waited = a win.
        val waited = base.copy(doses = base.doses + gum4.toDose("b", at(10, 12, 10), 0), cravings = listOf(c))
        assertEquals(CravingResult.WAITED, result(waited, c, at(10, 13)))
        assertTrue(Cravings.effectiveOutcome(waited, c, at(10, 13), tz) == com.baastiklabs.firewatch.core.model.CravingOutcome.RODE_OUT)
        // Early gum and early pouch: early, not relapse.
        val c2 = Craving("c2", at(10, 9), 7)
        val zyn = product(DefaultProducts.ZYN_3MG)
        assertEquals(CravingResult.EARLY, result(base.copy(doses = base.doses + gum4.toDose("e", at(10, 9, 5), 0)), c2, at(10, 10)))
        assertEquals(CravingResult.EARLY, result(base.copy(doses = base.doses + zyn.toDose("z", at(10, 9, 5), 0)), c2, at(10, 10)))
        // Cigarette: relapse, even with a full battery.
        val cig = product(DefaultProducts.CIGARETTE)
        assertEquals(CravingResult.RELAPSE, result(base.copy(doses = base.doses + cig.toDose("s", at(10, 12, 10), 0)), c, at(10, 13)))
        // A dose more than 45 minutes later isn't linked.
        assertEquals(CravingResult.RODE_OUT, result(base.copy(doses = base.doses + gum4.toDose("l", at(10, 12, 50), 0)), c, at(10, 13)))
        // Old "I used" with no dose logged: early.
        val old = c.copy(outcome = com.baastiklabs.firewatch.core.model.CravingOutcome.USED)
        assertEquals(CravingResult.EARLY, result(base, old, at(10, 14)))
        // Baseline week (no target): neither win nor miss label.
        assertEquals(CravingResult.BASELINE, result(FirewatchData(products = DefaultProducts.all(), doses = listOf(gum4.toDose("x", at(10, 12, 5), 0))), c, at(10, 13)))
    }

    // ---- Step up ----

    private val CO = com.baastiklabs.firewatch.core.engine.Coach

    private fun stepData(doses: List<com.baastiklabs.firewatch.core.model.Dose> = emptyList(), cravings: List<Craving> = emptyList(), settings: com.baastiklabs.firewatch.core.model.Settings = com.baastiklabs.firewatch.core.model.Settings()) =
        FirewatchData(products = DefaultProducts.all(), doses = doses, cravings = cravings, settings = settings,
            rungChanges = listOf(RungChange("r", at(10, 8), 4.0, "down")))

    @Test
    fun `step up catches a rough day the same day`() {
        // A calm day: nothing.
        assertNull(CO.stepUp(stepData(listOf(gum4.toDose("a", at(10, 8), 0))), at(10, 12), tz))
        // Pull reaches one full gap (4 h at 4 a day): pieces at 8, 9, 10 → early pieces.
        val pulled = stepData(listOf(8, 9, 10, 11).map { gum4.toDose("p$it", at(10, it), 0) })
        val s1 = CO.stepUp(pulled, at(10, 11, 30), tz)!!
        assertTrue(s1.sameDay && s1.rung.pieces == 5.0, "$s1")
        // Three strong cravings today.
        val strong = stepData(cravings = listOf(9, 12, 15).map { Craving("s$it", at(10, it), 8) })
        assertTrue(CO.stepUp(strong, at(10, 16), tz)!!.reasons.any { "strong cravings" in it })
        // Two strong within two hours is enough.
        assertNotNull(CO.stepUp(stepData(cravings = listOf(Craving("a", at(10, 9), 8), Craving("b", at(10, 10), 9))), at(10, 11), tz))
        // Over today's target by more than a piece.
        val over = stepData((0 until 6).map { gum4.toDose("o$it", at(10, 8) + it * 245 * 60_000L, 0) })
        assertTrue(CO.stepUp(over, at(10, 22, 30), tz)!!.reasons.any { "over today's target" in it })
        // A craving answered with a cigarette.
        val cig = product(DefaultProducts.CIGARETTE)
        val relapse = stepData(listOf(cig.toDose("c", at(10, 12, 5), 0)), listOf(Craving("k", at(10, 12), 6)))
        assertTrue(CO.stepUp(relapse, at(10, 13), tz)!!.reasons.any { "cigarette or vape" in it })
        // Timer checks: only with the setting on.
        val checks = (0 until 7).map { com.baastiklabs.firewatch.core.model.TimerCheck("t$it", at(10, 9) + it * 10 * 60_000L, charging = true) }
        val off = stepData(listOf(gum4.toDose("a", at(10, 8, 50), 0))).copy(timerChecks = checks)
        assertNull(CO.stepUp(off, at(10, 11), tz))
        val on = off.copy(settings = com.baastiklabs.firewatch.core.model.Settings(hideTimer = true))
        assertTrue(CO.stepUp(on, at(10, 11), tz)!!.reasons.any { "checked the timer" in it })
        // "I'm OK" hides a same-day offer until tomorrow.
        val snoozed = strong.copy(settings = com.baastiklabs.firewatch.core.model.Settings(stepUpSnoozedAt = at(10, 16)))
        assertNull(CO.stepUp(snoozed, at(10, 18), tz))
    }

    @Test
    fun `multi-day step up needs two signals, creeping up needs one`() {
        // On target (4 a day) but strong cravings rising: one signal only.
        val onTarget = (1..9).flatMap { d -> (0 until 4).map { i -> gum4.toDose("d$d-$i", at(d, 8 + i * 4), 0) } }
        val cr = (5..9).map { Craving("s$it", at(it, 10), 8) }
        val one = FirewatchData(products = DefaultProducts.all(), doses = onTarget, cravings = cr,
            rungChanges = listOf(RungChange("r", at(1, 8), 4.0, "start")))
        assertNull(CO.stepUp(one, at(10, 7, 30), tz))
        // Strong cravings now ending in an early piece (the 12:00 piece taken at 11:05), after milder
        // ones before: several signals together.
        val mild = (1..4).map { Craving("m$it", at(it, 11), 3) }
        val strongEarly = (5..9).map { Craving("s$it", at(it, 11), 8) }
        val shifted = onTarget.map { d -> if (d.id.endsWith("-1") && d.at >= at(5, 0)) d.copy(at = d.at - 55 * 60_000L) else d }
        val two = one.copy(doses = shifted, cravings = mild + strongEarly)
        val s = CO.stepUp(two, at(10, 7, 30), tz)!!
        assertTrue(!s.sameDay && s.reasons.size >= 2, "$s")
        // Creeping up: a week measuring a heavier rung is enough on its own.
        val over = (1..9).flatMap { d -> (0 until 5).map { i -> gum4.toDose("o$d-$i", at(d, 8 + i * 3), 0) } }
        val creep = FirewatchData(products = DefaultProducts.all(), doses = over, rungChanges = listOf(RungChange("r", at(1, 8), 4.0, "start")))
        val c = CO.stepUp(creep, at(10, 7, 30), tz)!!
        assertTrue(c.reasons.single().contains("last 7 days measure") && c.rung.pieces == 5.0, "$c")
    }

    // ---- Find, then control ----

    private val CT = com.baastiklabs.firewatch.core.engine.Control

    @Test
    fun `early target starts at 8 and firms up over the first week`() {
        val early = listOf(RungChange("e", at(1, 7), 8.0, CT.EARLY))
        val day1 = FirewatchData(products = DefaultProducts.all(), rungChanges = early, doses = listOf(gum4.toDose("a", at(1, 9), 0)))
        assertNull(CT.earlyTargetUpdate(day1, at(1, 12), tz))  // no full day yet
        // Three days of 3 a day: (8×4 + 3×3)/7 ≈ 5.9 → 6.
        val doses = (1..3).flatMap { d -> (0 until 3).map { i -> gum4.toDose("d$d-$i", at(d, 8 + i * 5), 0) } }
        assertEquals(6.0, CT.earlyTargetUpdate(day1.copy(doses = doses), at(4, 9), tz))
        // Not early any more: nothing.
        assertNull(CT.earlyTargetUpdate(day1.copy(doses = doses, rungChanges = listOf(RungChange("s", at(1, 7), 4.0, "start"))), at(4, 9), tz))
    }

    @Test
    fun `holding steady counts and earns badges`() {
        val doses = (1..40).flatMap { d -> (0 until 4).map { i -> gum4.toDose("d$d-$i", at(1, 8) + (d - 1) * 86_400_000L + i * 4 * 3_600_000L, 0) } }
        val data = FirewatchData(products = DefaultProducts.all(), doses = doses, rungChanges = listOf(RungChange("r", at(2, 8), 4.0, "start")))
        val now = at(1, 12) + 39L * 86_400_000L
        assertTrue(CT.heldDays(data, now, tz) >= 30)
        assertTrue(Insights(data, tz, now).badges().any { it.title.startsWith("Held") && "30 days" in it.title })
    }

    @Test
    fun `welcome back offers the gap days once`() {
        val s = com.baastiklabs.firewatch.core.model.Settings(onboardingDone = true)
        val data = FirewatchData(products = DefaultProducts.all(), settings = s, doses = listOf(gum4.toDose("a", at(10, 9), 0)))
        assertNull(CT.welcomeBackDays(data, at(11, 9), tz))
        val gap = CT.welcomeBackDays(data, at(15, 9), tz)!!
        assertEquals(listOf(11, 12, 13, 14), gap.map { it.dayOfMonth })
        assertNull(CT.welcomeBackDays(data.copy(settings = s.copy(welcomeBackDismissedAt = at(15, 9))), at(15, 10), tz))
    }

    // ---- 0.10: one day everywhere, steady days, practice day, forecasts, reference changes ----

    @Test
    fun `a 1am piece belongs to the previous waking day everywhere`() {
        val late = gum4.toDose("late", at(10, 23, 30) + 90 * 60_000L, 0)   // 1:00 on the 11th
        val data = withTarget(listOf(gum4.toDose("a", at(10, 9), 0), late))
        val d10 = kotlinx.datetime.LocalDate(2026, 9, 10)
        assertEquals(d10, Days.wakingDate(data, late.at, tz))
        val sums = Days.summaries(data, tz, at(11, 12))
        assertEquals(2, sums.getValue(d10).doseCount)
        assertNull(sums[kotlinx.datetime.LocalDate(2026, 9, 11)])
        assertEquals(2.0, Progress.pace(data, d10, tz).pieces, 0.01)
        assertEquals(2, Insights(data, tz, at(11, 12)).days.first { it.date == d10 }.doses.size)
        assertEquals(2, Days.dosesOn(data, d10, tz).size)
    }

    @Test
    fun `steady days only go up`() {
        // 4 a day on a 4-a-day rung, spaced a gap apart: steady. A cigarette day: not steady.
        val cig = product(DefaultProducts.CIGARETTE)
        val doses = (2..9).flatMap { d -> (0 until 4).map { i -> gum4.toDose("d$d-$i", at(d, 7) + i * 240 * 60_000L + 60_000L, 0) } } +
            cig.toDose("c", at(5, 20), 0)
        val data = FirewatchData(products = DefaultProducts.all(), doses = doses, rungChanges = listOf(RungChange("r", at(1, 8), 4.0, "start")))
        val ct = com.baastiklabs.firewatch.core.engine.Control
        val all = ct.steadyDates(data, at(10, 12), tz).map { it.dayOfMonth }
        assertTrue(5 !in all && all.containsAll(listOf(2, 3, 4, 6, 7, 8, 9)), "$all")
        // More data never lowers the count.
        val later = ct.steadyDays(data.copy(doses = data.doses + cig.toDose("c2", at(10, 10), 0)), at(11, 12), tz)
        assertTrue(later >= all.size)
        // Back-dated days use pieces.
        val est = (2..3).flatMap { d -> (0 until 4).map { i -> gum4.toDose("e$d-$i", at(d, 9) + i * 3_600_000L, 0).copy(estimated = true) } }
        val backdated = FirewatchData(products = DefaultProducts.all(), doses = est, rungChanges = listOf(RungChange("r", at(1, 8), 4.0, "start")))
        assertEquals(2, ct.steadyDays(backdated, at(4, 12), tz))
        assertEquals(7, ct.newSteadyMilestone(data, 7))
        assertNull(ct.newSteadyMilestone(data.copy(settings = com.baastiklabs.firewatch.core.model.Settings(steadyMilestoneSeen = 7)), 8))
    }

    @Test
    fun `taper forecast shows a plan from day one`() {
        val ct = com.baastiklabs.firewatch.core.engine.Control
        val data = withTarget(emptyList(), 4.0)
        val plan = ct.taperPlan(data, at(1, 9), tz)!!
        assertEquals("Based on your plan", plan.basis)
        // 4 → 3 → 2 → 1 → ½ → ⅓ → Clear Air, one rung per 7 days.
        assertEquals(com.baastiklabs.firewatch.core.engine.Tier.CLEAR_AIR, plan.steps.last().rung.tier)
        assertEquals(6, plan.steps.size)
        assertEquals(kotlinx.datetime.LocalDate(2026, 9, 8), plan.steps.first().date)
    }

    @Test
    fun `reference piece changes can start today or be back-dated`() {
        val gum2 = product(DefaultProducts.GUM_2MG)
        val old = gum4.toDose("old", at(5, 9), 0)
        val new = gum4.toDose("new", at(12, 9), 0)
        val base = FirewatchData(products = DefaultProducts.all(), doses = listOf(old, new),
            settings = com.baastiklabs.firewatch.core.model.Settings(referenceProductId = DefaultProducts.GUM_2MG))
        // From the 10th on, gum 2 mg is one piece; before that gum 4 mg was.
        val fromToday = base.copy(refChanges = listOf(com.baastiklabs.firewatch.core.model.RefChange("r", at(10, 9), at(10, 9), gum2.id, DefaultProducts.GUM_4MG)))
        assertEquals(1.0, fromToday.piecesOf(old), 0.01)
        assertEquals(2.0, fromToday.piecesOf(new), 0.01)
        // Back-dated to the 1st: both use gum 2 mg.
        val backdated = base.copy(refChanges = listOf(com.baastiklabs.firewatch.core.model.RefChange("r", at(10, 9), at(1, 0), gum2.id, DefaultProducts.GUM_4MG)))
        assertEquals(2.0, backdated.piecesOf(old), 0.01)
        // Stored and read back as a record.
        val env = com.baastiklabs.firewatch.core.records.RecordCodec.refChange(fromToday.refChanges.first(), null, 1L)
        assertEquals(1, FirewatchData.fromRecords(listOf(env)).refChanges.size)
    }

    // ---- 0.11: chew and park, volatility, morning stretch, Yesterday in review, Coaching tips ----

    private val pouch6 = DefaultProducts.all().first { it.id == DefaultProducts.ZYN_6MG }
    private fun vapeDose(id: String, t: Long, mg: Double) =
        com.baastiklabs.firewatch.core.model.Dose(id, "v", t, kind = ProductKind.VAPE, speed = SpeedProfile.SPIKE, labelMg = mg, absorption = 1.0)

    @Test
    fun `chew and park gum peaks later and lower than a pouch and absorbs the same total`() {
        val gum = gum4.toDose("g", at(10, 9), 0)
        assertEquals(SpeedProfile.CHEW, Kinetics.speedOf(gum))
        // Old gum logs (BUILD) are drawn as chew and park too.
        assertEquals(SpeedProfile.CHEW, Kinetics.speedOf(gum.copy(speed = SpeedProfile.BUILD)))
        val mg = gum.absorbedMg()
        val chewPeak = Kinetics.peakMinutes(gum)
        val pouchPeak = Kinetics.peakMinutes(SpeedProfile.BUILD)
        assertTrue(chewPeak in 45.0..65.0 && chewPeak > pouchPeak, "chew=$chewPeak pouch=$pouchPeak")
        val chewTop = Kinetics.contribution(mg, SpeedProfile.CHEW, chewPeak, 30.0)
        val pouchTop = Kinetics.contribution(mg, SpeedProfile.BUILD, pouchPeak)
        assertTrue(chewTop < pouchTop, "chew=$chewTop pouch=$pouchTop")
        // Same total: the area under both curves matches.
        fun area(f: (Double) -> Double) = (0 until 48 * 60).sumOf { f(it.toDouble()) }
        val a1 = area { Kinetics.contribution(mg, SpeedProfile.CHEW, it, 30.0) }
        val a2 = area { Kinetics.contribution(mg, SpeedProfile.BUILD, it) }
        assertTrue(kotlin.math.abs(a1 / a2 - 1) < 0.01, "$a1 vs $a2")
        // Quick and half chews release sooner.
        val quick = Kinetics.peakMinutes(gum.copy(duration = com.baastiklabs.firewatch.core.model.Duration.QUICK))
        val half = Kinetics.peakMinutes(gum.copy(duration = com.baastiklabs.firewatch.core.model.Duration.HALF))
        assertTrue(quick < half && half < chewPeak, "quick=$quick half=$half full=$chewPeak")
    }

    @Test
    fun `the gum profile leaves pieces net and tiers unchanged`() {
        val chew = listOf(gum4.toDose("a", at(10, 9), 0), gum4.toDose("b", at(10, 11), 0))
        val build = chew.map { it.copy(speed = SpeedProfile.BUILD) }
        val a = pullFor(*chew.toTypedArray()); val b = pullFor(*build.toTypedArray())
        assertEquals(a.netMin, b.netMin, 1e-9)
        val da = withTarget(chew); val db = withTarget(build)
        assertEquals(chew.sumOf { da.piecesOf(it) }, build.sumOf { db.piecesOf(it) }, 1e-9)
        assertEquals(Progress.pace(da, d10, tz).pieces, Progress.pace(db, d10, tz).pieces, 1e-9)
    }

    @Test
    fun `volatility is near zero when clear and higher for a vape than gum`() {
        val clear = withTarget(emptyList())
        assertEquals(0.0, Insights(clear, tz, at(10, 20)).dayStat(d10).volatility, 1e-9)
        val gum = gum4.toDose("g", at(10, 12), 0)
        val vape = vapeDose("v", at(10, 12), gum.absorbedMg())
        val vg = Insights(withTarget(listOf(gum)), tz, at(10, 22)).dayStat(d10).volatility
        val vv = Insights(withTarget(listOf(vape)), tz, at(10, 22)).dayStat(d10).volatility
        assertTrue(vv > vg && vg > 0, "vape=$vv gum=$vg")
        // Daily figure = volatility (RMS rate of change, mg/h) over the waking day.
        val data = withTarget(listOf(vape))
        val ins = Insights(data, tz, at(11, 12))
        val w = com.baastiklabs.firewatch.core.engine.Waking.day(data, d10, tz)
        assertEquals(Kinetics.volatility(data.doses, w.wakeAt, w.sleepAt), ins.dayStat(d10).volatility, 1e-9)
        // The running curve for the stepper covers the same day.
        assertTrue(ins.volatilityCurve(d10).maxOf { it.second } > 0)
    }

    @Test
    fun `morning stretch runs from wake-up to the first piece`() {
        val data = withTarget(emptyList())
        assertEquals(120.0, BE.morningStretch(data, at(10, 9), tz)!!, 0.01)
        val withDose = withTarget(listOf(gum4.toDose("a", at(10, 8, 30), 0)))
        assertNull(BE.morningStretch(withDose, at(10, 9), tz))
        assertNull(BE.morningStretch(data, at(10, 23, 30), tz))
    }

    private fun pouchWeek(settings: com.baastiklabs.firewatch.core.model.Settings, extra: List<com.baastiklabs.firewatch.core.model.Dose> = emptyList()) =
        withTarget((3..9).flatMap { d -> (0 until 4).map { i -> pouch6.toDose("p$d-$i", at(d, 8) + i * 240 * 60_000L, 0) } } + extra, 5.0, settings)

    @Test
    fun `bridge with gum only shows when its rules are met`() {
        val on = com.baastiklabs.firewatch.core.model.Settings(coachingTips = true)
        val C = com.baastiklabs.firewatch.core.engine.Coaching
        // Mostly pouches, gum 2 mg on the home screen, battery full at 9:00 on the 10th.
        val tip = C.bridgeTip(pouchWeek(on), 5.0, at(10, 9), tz)
        assertNotNull(tip)
        assertTrue(tip.text.contains("gum 2 mg"), tip.text)
        // Off by default.
        assertNull(C.bridgeTip(pouchWeek(com.baastiklabs.firewatch.core.model.Settings()), 5.0, at(10, 9), tz))
        // Battery under half full: a pouch just taken.
        assertNull(C.bridgeTip(pouchWeek(on, listOf(pouch6.toDose("x", at(10, 8, 50), 0))), 5.0, at(10, 9), tz))
        // Not during a craving.
        val craving = pouchWeek(on).let { it.copy(cravings = listOf(Craving("c", at(10, 8, 50), 6))) }
        assertNull(C.bridgeTip(craving, 5.0, at(10, 9), tz))
        // Dismissed for 2 weeks.
        val dismissed = pouchWeek(on.copy(tipDismissedAt = mapOf(C.BRIDGE to at(9, 9))))
        assertNull(C.bridgeTip(dismissed, 5.0, at(10, 9), tz))
        val expired = pouchWeek(on.copy(tipDismissedAt = mapOf(C.BRIDGE to at(10, 9) - 15 * 24 * 3_600_000L)))
        assertNotNull(C.bridgeTip(expired, 5.0, at(10, 9), tz))
        // Mostly gum: no tip.
        val gumWeek = withTarget((3..9).flatMap { d -> (0 until 4).map { i -> gum4.toDose("g$d-$i", at(d, 8) + i * 240 * 60_000L, 0) } }, 5.0, on)
        assertNull(C.bridgeTip(gumWeek, 5.0, at(10, 9), tz))
    }

    @Test
    fun `yesterday in review shows facts and tips only with coaching on`() {
        val C = com.baastiklabs.firewatch.core.engine.Coaching
        val doses = listOf(gum4.toDose("a", at(9, 9), 0), pouch6.toDose("b", at(9, 14), 0), pouch6.toDose("c", at(9, 14, 20), 0), pouch6.toDose("d", at(9, 16, 30), 0), pouch6.toDose("e", at(9, 16, 50), 0))
        val off = C.yesterday(withTarget(doses, 5.0), at(10, 12), tz)!!
        assertEquals(kotlinx.datetime.LocalDate(2026, 9, 9), off.date)
        assertTrue(off.tips.isEmpty())
        assertEquals(300.0, off.longestGapMin!!, 0.01)
        assertEquals(120.0, off.morningStretchMin!!, 0.01)
        assertTrue(off.stacked >= 2 && off.volatility > 0)
        assertEquals(ProductKind.POUCH, off.mix.first().first)
        val on = C.yesterday(withTarget(doses, 5.0, com.baastiklabs.firewatch.core.model.Settings(coachingTips = true)), at(10, 12), tz)!!
        assertTrue(on.tips.isNotEmpty() && on.tips.size <= 2, "${on.tips}")
        assertTrue(on.tips.contains("Your longest gap started with gum."), "${on.tips}")
    }

    @Test
    fun `taper speed reads plainly`() {
        val data = withTarget((1..28).flatMap { d -> (0 until (4 + d / 7)).map { i -> gum4.toDose("t$d-$i", at(d, 8) + i * 60 * 60_000L, 0) } })
        val text = Insights(data, tz, at(29, 12)).taperSpeedText()!!
        assertTrue(text.startsWith("About") && text.contains("more each week"), text)
    }

    // ---- 0.13: known days, practice pace, level history, volatility in mg/h ----

    private val PR = com.baastiklabs.firewatch.core.engine.Practice
    private fun mark(date: String, state: String) = com.baastiklabs.firewatch.core.model.DayMark("daymark-$date", date, state, 1L)
    private fun session(start: Long, pieces: Double, end: Long? = null, untilBedtime: Boolean = true) =
        com.baastiklabs.firewatch.core.model.PracticeSession("p$start", start, pieces, end, untilBedtime)
    private fun dosesOn(days: IntRange, perDay: Int, product: com.baastiklabs.firewatch.core.model.Product = gum4, from: Int = 8) =
        days.flatMap { d -> (0 until perDay).map { i -> product.toDose("x$d-$i", at(d, from) + i * (14 * 60 / perDay.coerceAtLeast(1)) * 60_000L, 0) } }

    @Test
    fun `an empty day is a question mark once it's over, until marked`() {
        val data = withTarget(listOf(gum4.toDose("a", at(2, 9), 0)))
        val d3 = kotlinx.datetime.LocalDate(2026, 9, 3)
        assertEquals(DayState.OPEN, Days.state(data, d3, tz, at(3, 20)))
        assertEquals(DayState.UNKNOWN, Days.state(data, d3, tz, at(4, 8)))
        // Cravings and Good morning don't make it clear.
        val busy = data.copy(cravings = listOf(Craving("c", at(3, 10), 5)))
        assertEquals(DayState.UNKNOWN, Days.state(busy, d3, tz, at(4, 8)))
        assertEquals(DayState.CLEAR, Days.state(data.copy(dayMarkList = listOf(mark("2026-09-03", "clear"))), d3, tz, at(4, 8)))
        assertEquals(DayState.GHOST, Days.state(data.copy(dayMarkList = listOf(mark("2026-09-03", "ghost"))), d3, tz, at(4, 8)))
        // Before D started: never "?".
        assertEquals(DayState.BEFORE, Days.state(data, kotlinx.datetime.LocalDate(2026, 8, 30), tz, at(4, 8)))
    }

    @Test
    fun `question mark and ghost days are left out everywhere and clear days count as zero`() {
        // 4 a day on the 2nd–8th, except the 5th (empty).
        val doses = dosesOn(2..8, 4).filter { !it.id.startsWith("x5-") }
        val base = withTarget(doses, 4.0)
        val today = kotlinx.datetime.LocalDate(2026, 9, 9)
        assertEquals(4.0, Progress.rollingAverage(base, today, tz)!!, 0.01)
        assertEquals(6, Progress.knownDays(base, today, tz))
        val ghost = base.copy(dayMarkList = listOf(mark("2026-09-05", "ghost")))
        assertEquals(4.0, Progress.rollingAverage(ghost, today, tz)!!, 0.01)
        val clear = base.copy(dayMarkList = listOf(mark("2026-09-05", "clear")))
        assertEquals(24.0 / 7, Progress.rollingAverage(clear, today, tz)!!, 0.01)
        assertEquals(7, Progress.knownDays(clear, today, tz))
        // Insights leave "?" out and keep clear days.
        val now = at(9, 12)
        assertTrue(Insights(base, tz, now).days.none { it.date.dayOfMonth == 5 })
        assertTrue(Insights(clear, tz, now).days.any { it.date.dayOfMonth == 5 && it.pieces == 0.0 })
        // Steady days: a clear day counts, a "?" day doesn't.
        val ct = com.baastiklabs.firewatch.core.engine.Control
        assertTrue(5 !in ct.steadyDates(base, now, tz).map { it.dayOfMonth })
        assertTrue(5 in ct.steadyDates(clear, now, tz).map { it.dayOfMonth })
    }

    @Test
    fun `step-down count skips question mark days without resetting and the plan agrees`() {
        val data = FirewatchData(
            products = DefaultProducts.all(), doses = dosesOn(2..8, 4).filter { !it.id.startsWith("x6-") },
            rungChanges = listOf(RungChange("r", at(1, 8), 4.0, "start")),
            settings = com.baastiklabs.firewatch.core.model.Settings(holdDays = 5),
        )
        val now = at(9, 12)
        val p = Progress.stepDownProgress(data, now, tz)!!
        assertEquals(5, p.held)
        assertTrue(p.ready)
        // Two days short: the plan's first step is two days away.
        val short = data.copy(settings = data.settings.copy(holdDays = 8))
        val sp = Progress.stepDownProgress(short, now, tz)!!
        assertEquals(6, sp.held)
        val plan = com.baastiklabs.firewatch.core.engine.Control.taperPlan(short, now, tz)!!
        assertEquals(kotlinx.datetime.LocalDate(2026, 9, 11), plan.steps.first().date)
    }

    @Test
    fun `practice pace limits, bedtime end and relapse mode`() {
        // Working at 7, measuring 4: can practice 6, 5 or 4.
        val data = withTarget(dosesOn(2..9, 4), 7.0)
        val now = at(10, 9)
        assertEquals(listOf(6.0, 5.0, 4.0), PR.allowedRungs(data, now, tz).map { it.pieces })
        // At 4 measuring 4: only 3.
        assertEquals(listOf(3.0), PR.allowedRungs(withTarget(dosesOn(2..9, 4), 4.0), now, tz).map { it.pieces })
        // Turn off at bedtime (23:00) vs leave it on.
        val bed = data.copy(practices = listOf(session(at(10, 9), 5.0)))
        assertNotNull(PR.active(bed, at(10, 22), tz))
        assertNull(PR.active(bed, at(10, 23, 30), tz))
        val on = data.copy(practices = listOf(session(at(10, 9), 5.0, untilBedtime = false)))
        assertNotNull(PR.active(on, at(12, 10), tz))
        assertNull(PR.active(on.copy(practices = listOf(on.practices[0].copy(end = at(11, 9)))), at(12, 10), tz))
        // Off in Relapse prevention mode.
        assertNull(PR.active(bed.copy(modeChanges = listOf(modeOn(at(10, 8)))), at(10, 12), tz))
        assertTrue(PR.allowedRungs(data.copy(modeChanges = listOf(modeOn(at(10, 8)))), now, tz).isEmpty())
    }

    @Test
    fun `practice pace changes only the battery and counts practice net only while on`() {
        // Level 4 (4h gap), practicing 2 (8h gap) from 13:00. A piece at 9:00.
        val doses = listOf(gum4.toDose("a", at(10, 9), 0))
        val plain = withTarget(doses, 4.0)
        val prac = plain.copy(practices = listOf(session(at(10, 13), 2.0)))
        // At 13:00 the battery is full either way; switching keeps the charge.
        val b0 = Progress.battery(plain, 4.0, at(10, 11), tz)
        val b1 = Progress.battery(prac, 4.0, at(10, 11), tz)
        assertEquals(b0.charge, b1.charge, 1e-9)
        // A piece at 14:00: the next one is 8 hours later under practice pace.
        val more = prac.copy(doses = doses + gum4.toDose("b", at(10, 14), 0))
        val b = Progress.battery(more, 4.0, at(10, 15), tz)
        assertEquals(2.0, b.practicePieces)
        assertTrue(kotlin.math.abs(b.readyAt!! - (at(10, 14) + 480 * 60_000L)) <= 2 * 60_000L, "${b.readyAt}")
        // Normal stretch, pull and net stay against the real level.
        val plainMore = plain.copy(doses = more.doses)
        assertEquals(Progress.battery(plainMore, 4.0, at(10, 15), tz).netMinutesToday, b.netMinutesToday, 1e-9)
        assertEquals(BE.day(plainMore, d10, tz, at(10, 23))!!.netMin, BE.day(more, d10, tz, at(10, 23))!!.netMin, 1e-9)
        // Practice net: only 13:00 onward, against the 8h gap: full at 13:00 → 1h stretch, then the
        // 14:00 piece empties it; no pull (it was full).
        assertEquals(60.0, BE.practiceNetDay(more, d10, tz, at(10, 15))!!, 1.0)
        // Level, steady days and the step-down count are untouched.
        assertEquals(4.0, more.targetPieces)
    }

    @Test
    fun `lighter level offer rules`() {
        val s = com.baastiklabs.firewatch.core.model.Settings(holdDays = 3)
        val data = FirewatchData(products = DefaultProducts.all(), doses = dosesOn(2..9, 4), settings = s,
            rungChanges = listOf(RungChange("r", at(1, 8), 7.0, "start")))
        val now = at(10, 9)
        assertEquals(4.0, PR.lighterOffer(data, now, tz)!!.pieces)
        // Within a hold period of any level change (here a step up).
        val up = data.copy(rungChanges = data.rungChanges + RungChange("u", at(9, 8), 7.0, "up"))
        assertNull(PR.lighterOffer(up, now, tz))
        // Only one rung lighter: covered by the step-down count.
        assertNull(PR.lighterOffer(data.copy(rungChanges = listOf(RungChange("r", at(1, 8), 5.0, "start"))), now, tz))
        // Under 6 of 7 days known.
        val gaps = data.copy(doses = data.doses.filter { !it.id.startsWith("x8-") && !it.id.startsWith("x9-") })
        assertNull(PR.lighterOffer(gaps, now, tz))
        // "Don't ask me again", snoozed, practice on, Relapse prevention mode.
        assertNull(PR.lighterOffer(data.copy(settings = s.copy(lighterOffers = false)), now, tz))
        assertNull(PR.lighterOffer(data.copy(settings = s.copy(lighterSnoozedAt = at(9, 9))), now, tz))
        assertNull(PR.lighterOffer(data.copy(practices = listOf(session(at(10, 8), 4.0))), now, tz))
        assertNull(PR.lighterOffer(data.copy(modeChanges = listOf(modeOn(at(9, 8)))), now, tz))
        // It replaces the step-down offer while it shows.
        assertNull(Progress.stepDownOffer(data, now, tz))
    }

    @Test
    fun `work from measured level needs 75 percent of the hold period at practice pace`() {
        val s = com.baastiklabs.firewatch.core.model.Settings(holdDays = 3)
        // 4 a day, on pace for 4 a day (every 4 hours from wake-up).
        val onPace = (2..12).flatMap { d -> (0 until 4).map { i -> gum4.toDose("x$d-$i", at(d, 7) + i * 240 * 60_000L, 0) } }
        val base = FirewatchData(products = DefaultProducts.all(), doses = onPace, settings = s,
            rungChanges = listOf(RungChange("r", at(1, 8), 7.0, "start")))
        // Hold 3 → 75% of 48 waking hours = 36 h. Left on from 10th 9:00: by the 12th's morning,
        // 14 h + 16 h = 30 h (not enough); by the 13th's morning 46 h.
        val run = base.copy(practices = listOf(session(at(10, 9), 4.0, untilBedtime = false)))
        assertNull(PR.workFromOffer(run, at(12, 9), tz))
        val offer = PR.workFromOffer(run, at(13, 9), tz)
        assertNotNull(offer)
        assertEquals(4.0, offer.rung.pieces)
        assertTrue(offer.coveredMin >= 36 * 60, "${offer.coveredMin}")
        // Several bedtime sessions add up the same way.
        val days = base.copy(practices = (10..12).map { session(at(it, 7), 4.0) })
        assertNotNull(PR.workFromOffer(days, at(13, 9), tz))
        // Practice net below zero: no offer.
        val heavy = run.copy(doses = run.doses + (10..12).flatMap { d -> (0 until 6).map { i -> gum4.toDose("h$d-$i", at(d, 12) + i * 20 * 60_000L, 0) } })
        assertNull(PR.workFromOffer(heavy, at(13, 9), tz))
    }

    @Test
    fun `level history and calendar marks`() {
        val data = withTarget(emptyList(), 5.0).copy(
            rungChanges = listOf(RungChange("r", at(1, 8), 5.0, "start"), RungChange("u", at(5, 10), 7.0, "up", "a craving ended with a vape"), RungChange("d", at(9, 10), 6.0, "down")),
            practices = listOf(session(at(7, 14), 6.0)),
        )
        val h = PR.history(data, at(10, 12), tz) { "${it.dayOfMonth} Sep" }
        assertEquals("9 Sep · Blaze 7 → 6 · stepped down", h[0].text)
        assertTrue(h[1].text.startsWith("7 Sep · Practiced Blaze pace 2 pm – 11 pm: practice net"), h[1].text)
        assertEquals("5 Sep · Bonfire 5 → Blaze 7 · stepped up: a craving ended with a vape", h[2].text)
        assertEquals(mapOf("2026-09-05" to "up", "2026-09-09" to "down"), PR.levelMarks(data, tz))
    }

    @Test
    fun `volatility counts how fast as well as how much`() {
        val from = at(10, 12); val to = at(10, 15)
        val vape = vapeDose("v", from, gum4.toDose("g", from, 0).absorbedMg())
        val zyn = pouch6.toDose("z", from, 0)
        val gum = gum4.toDose("g", from, 0)
        val vv = Kinetics.volatility(listOf(vape), from, to)
        val vz = Kinetics.volatility(listOf(zyn), from, to)
        val vg = Kinetics.volatility(listOf(gum), from, to)
        println("volatility mg/h: vape=$vv zyn=$vz gum=$vg; steepest: vape=${Kinetics.steepestClimb(listOf(vape), from, to)} zyn=${Kinetics.steepestClimb(listOf(zyn), from, to)} gum=${Kinetics.steepestClimb(listOf(gum), from, to)}")
        assertTrue(vv > vz && vz > vg && vg > 0, "vape=$vv zyn=$vz gum=$vg")
        assertEquals(0.0, Kinetics.volatility(emptyList(), from, to), 1e-12)
    }

    @Test
    fun `swap tip only with coaching tips on`() {
        val zyn3 = listOf(DefaultProducts.all().first { it.id == DefaultProducts.ZYN_3MG }.toDose("z", at(10, 9), 0))
        val C = com.baastiklabs.firewatch.core.engine.Coaching
        assertNull(C.swapTip(withTarget(zyn3), zyn3))
        assertNotNull(C.swapTip(withTarget(zyn3, settings = com.baastiklabs.firewatch.core.model.Settings(coachingTips = true)), zyn3))
    }

    // ---- 0.14: Life at Clear Air and new charts ----

    @Test
    fun `clear air days count without marking and only go up`() {
        val CA = com.baastiklabs.firewatch.core.engine.ClearAir
        val data = FirewatchData(products = DefaultProducts.all(), doses = dosesOn(2..4, 1),
            rungChanges = listOf(RungChange("r", at(1, 8), 1.0, "start"), RungChange("c", at(5, 8), 0.0, "down")))
        assertTrue(CA.active(data))
        // The 5th–9th are empty at Clear Air: clear without marking.
        assertEquals(5, CA.daysFree(data, at(10, 12), tz))
        // A dose at Clear Air is counted plainly; nothing resets.
        val slip = data.copy(doses = data.doses + gum4.toDose("s", at(10, 13), 0))
        assertEquals(5, CA.daysFree(slip, at(11, 12), tz))
        assertEquals(6, CA.daysFree(slip, at(12, 12), tz))
        // Steady days count clear days too.
        assertTrue(com.baastiklabs.firewatch.core.engine.Control.steadyDays(data, at(10, 12), tz) >= 5)
    }

    @Test
    fun `clear air is offered after a nicotine-free known week`() {
        val CA = com.baastiklabs.firewatch.core.engine.ClearAir
        val marks = (3..9).map { mark("2026-09-%02d".format(it), "clear") }
        val data = FirewatchData(products = DefaultProducts.all(), doses = listOf(gum4.toDose("a", at(2, 9), 0)),
            rungChanges = listOf(RungChange("r", at(1, 8), 1.0, "start")), dayMarkList = marks)
        assertTrue(CA.offer(data, at(10, 12), tz))
        assertTrue(!CA.offer(data.copy(dayMarkList = marks.drop(1)), at(10, 12), tz))
        assertTrue(!CA.offer(data.copy(settings = com.baastiklabs.firewatch.core.model.Settings(clearAirOfferSnoozedAt = at(9, 12))), at(10, 12), tz))
    }

    @Test
    fun `new charts on a fixed data set`() {
        // Pieces at 8:00 and every 3.5 h (4 a day), 2nd–9th.
        val data = withTarget(dosesOn(2..9, 4), 4.0)
        val ins = Insights(data, tz, at(10, 12))
        val gaps = ins.gapSizes().toMap()
        assertEquals(24, gaps["2–4h"])
        assertEquals(0, gaps["Under 1h"])
        assertEquals(7, ins.weekShape().size)
        assertTrue(ins.longestGaps().all { it.second == 210.0 })
        // Net split: gum 4 mg is one piece, so dose size adds nothing.
        assertTrue(ins.netSplit().all { kotlin.math.abs(it.third) < 1e-6 })
        assertEquals(32.0, ins.kindsByHour()[ProductKind.GUM]!!.sum(), 1e-6)
        assertTrue(ins.steadyByMonth().isNotEmpty() || com.baastiklabs.firewatch.core.engine.Control.steadyDays(data, at(10, 12), tz) == 0)
        // Pace vs plan only after a first step down.
        assertTrue(!ins.paceVsPlan().second)
        val stepped = data.copy(rungChanges = data.rungChanges + RungChange("d", at(6, 8), 3.0, "down"))
        assertTrue(Insights(stepped, tz, at(10, 12)).paceVsPlan().second)
        // "?" days are left out of the charts.
        val gap = withTarget(dosesOn(2..9, 4).filter { !it.id.startsWith("x5-") }, 4.0)
        assertTrue(Insights(gap, tz, at(10, 12)).longestGaps().none { it.first.dayOfMonth == 5 })
    }

    @Test
    fun `days count exactly what was had, with no scaling for short or long days`() {
        // Usual day 8 AM–11 PM (15 h). 7 pieces at level 7 is held; a late start doesn't inflate.
        val s = com.baastiklabs.firewatch.core.model.Settings(wakeMinutes = 8 * 60, sleepMinutes = 23 * 60, holdDays = 3)
        val doses = (10..12).flatMap { d -> (0 until 7).map { i -> gum4.toDose("p$d-$i", at(d, 12) + i * 60 * 60_000L, 0) } }
        val late = listOf(com.baastiklabs.firewatch.core.model.SleepEvent("w", at(10, 11), com.baastiklabs.firewatch.core.model.SleepKind.WAKE))
        val data = FirewatchData(products = DefaultProducts.all(), doses = doses, settings = s, sleepEvents = late,
            rungChanges = listOf(RungChange("r", at(9, 20), 7.0, "start")))
        assertEquals(7.0, Progress.pace(data, d10, tz).scaled, 1e-9)
        val p = Progress.stepDownProgress(data, at(13, 12), tz)!!
        assertEquals(3, p.held)
        assertTrue(p.unlocked)
        assertEquals(7.0, Progress.rollingAverage(data, kotlinx.datetime.LocalDate(2026, 9, 13), tz)!!, 1e-9)
    }
}
