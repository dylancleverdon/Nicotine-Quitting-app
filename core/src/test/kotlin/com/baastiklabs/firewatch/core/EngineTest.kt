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

    @Test
    fun `battery refills per interval and never banks more than one`() {
        val dose = gum4.toDose("a", at(10, 12), 0)
        val data = FirewatchData(products = DefaultProducts.all(), doses = listOf(dose))
        // Target 4 a day -> 240 min interval. One hour after a piece, not clear yet.
        val b = Progress.battery(data, 4.0, at(10, 13), tz)
        assertEquals(BatteryState.CHARGING, b.state)
        assertNotNull(b.readyAt)
        assertTrue(kotlin.math.abs(b.readyAt!! - at(10, 16)) < 3 * 60_000L, "ready ${b.readyAt}")
        val later = Progress.battery(data, 4.0, at(10, 18), tz)
        assertEquals(BatteryState.CLEAR, later.state)
        assertTrue(later.charge <= 1.0)
    }

    @Test
    fun `wind down hides clear in the last hour`() {
        val data = FirewatchData(products = DefaultProducts.all())
        assertEquals(BatteryState.WIND_DOWN, Progress.battery(data, 4.0, at(10, 22, 30), tz).state)
        assertEquals(BatteryState.ASLEEP, Progress.battery(data, 4.0, at(11, 3), tz).state)
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
    fun `quality ranks gum above vapes`() {
        assertTrue(Quality.score(ProductKind.GUM) > Quality.score(ProductKind.POUCH))
        assertTrue(Quality.score(ProductKind.POUCH) > Quality.score(ProductKind.VAPE))
        assertEquals("A", Quality.grade(100.0))
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
}
