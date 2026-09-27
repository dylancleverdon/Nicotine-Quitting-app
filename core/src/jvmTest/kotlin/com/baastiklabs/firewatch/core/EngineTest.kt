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
    fun `wind down hides clear in the last hour`() {
        val data = withTarget(emptyList())
        assertEquals(BatteryState.WIND_DOWN, Progress.battery(data, 4.0, at(10, 22, 30), tz).state)
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

    @Test
    fun `pull carries dose size and the wait stays one gap`() {
        val gap = 16 * 60 / 5.0
        val gum2 = product(DefaultProducts.GUM_2MG)
        val zyn6 = product(DefaultProducts.ZYN_6MG)
        assertEquals(0.0, pullFor(gum2.toDose("a", at(10, 9), 0)).pullMin, 1.0)
        assertEquals(0.0, pullFor(gum4.toDose("a", at(10, 9), 0)).pullMin, 1.0)
        assertEquals(0.2 * gap, pullFor(zyn6.toDose("a", at(10, 9), 0)).pullMin, 2.0)
        assertEquals(gap, pullFor(gum4.toDose("a", at(10, 9), 0), gum4.toDose("b", at(10, 9), 0)).pullMin, 2.0)
        // Two gum 2 mg together = one gum 4 mg: same pull, same wait.
        val split = withTarget(listOf(gum2.toDose("a", at(10, 9), 0), gum2.toDose("b", at(10, 9, 1), 0)), 5.0)
        val whole = withTarget(listOf(gum4.toDose("a", at(10, 9), 0)), 5.0)
        assertEquals(0.0, pullFor(gum2.toDose("a", at(10, 9), 0), gum2.toDose("b", at(10, 9, 1), 0)).pullMin, 1.0)
        val ws = Progress.battery(split, 5.0, at(10, 9, 5), tz).readyAt!!
        val ww = Progress.battery(whole, 5.0, at(10, 9, 5), tz).readyAt!!
        assertTrue(kotlin.math.abs(ws - ww) <= 2 * 60_000L)
        // A double never waits more than one gap.
        val dbl = withTarget(listOf(gum4.toDose("a", at(10, 9), 0), gum4.toDose("b", at(10, 9), 0)), 5.0)
        assertTrue(Progress.battery(dbl, 5.0, at(10, 9, 1), tz).readyAt!! <= at(10, 9) + (gap * 60_000).toLong() + 2 * 60_000L)
    }

    @Test
    fun `five zyn 6 a day at full battery give a negative net`() {
        val zyn6 = product(DefaultProducts.ZYN_6MG)
        // Each taken the moment the battery is full (gap 3h 12m from 7:00).
        val doses = (0 until 5).map { i -> zyn6.toDose("z$i", at(10, 7) + (i * 192L + 1) * 60_000L, 0) }
        val d = pullFor(*doses.toTypedArray())
        assertTrue(d.netMin < 0, "net ${d.netMin}")
    }

    @Test
    fun `range doses use the middle estimate for the battery`() {
        val vape = com.baastiklabs.firewatch.core.model.Dose("v", "x", at(10, 9), kind = ProductKind.VAPE, speed = SpeedProfile.SPIKE, rangeLowMg = 1.0, rangeHighMg = 3.0)
        val data = withTarget(listOf(vape), 5.0)
        val b = Progress.battery(data, 5.0, at(10, 9), tz)
        // Drains by the middle (2.0 mg), the same figure "pieces today" shows.
        assertEquals((1.0 - Absorption.pieces(2.0, data.referenceMg)).coerceAtLeast(0.0), b.charge, 0.02)
    }

    @Test
    fun `what fits now names the biggest home product that fits`() {
        val e = com.baastiklabs.firewatch.core.engine.BatteryEngine
        val data = withTarget(listOf(gum4.toDose("a", at(10, 9), 0)), 5.0)
        // Half-way through the gap: about half a piece of room.
        val b = Progress.battery(data, 5.0, at(10, 9) + 100 * 60_000L, tz)
        val fit = e.fitsNow(data, b)
        assertNotNull(fit)
        assertTrue(Absorption.pieces(Absorption.absorbedMg(fit), data.referenceMg) <= b.charge + 1e-9)
        assertNull(e.fitsNow(data, Progress.battery(data, 5.0, at(10, 9, 10), tz)))
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
}
