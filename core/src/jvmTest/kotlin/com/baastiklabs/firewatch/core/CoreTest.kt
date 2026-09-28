package com.baastiklabs.firewatch.core

import com.baastiklabs.firewatch.core.backup.Backups
import com.baastiklabs.firewatch.core.model.Craving
import com.baastiklabs.firewatch.core.model.CravingOutcome
import com.baastiklabs.firewatch.core.model.DefaultProducts
import com.baastiklabs.firewatch.core.model.Dose
import com.baastiklabs.firewatch.core.model.Duration
import com.baastiklabs.firewatch.core.model.ProductKind
import com.baastiklabs.firewatch.core.model.Settings
import com.baastiklabs.firewatch.core.records.FirewatchData
import com.baastiklabs.firewatch.core.records.RecordCodec
import com.baastiklabs.firewatch.core.records.RecordEnvelope
import com.baastiklabs.firewatch.core.records.RecordTypes
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreTest {
    private val tz = TimeZone.UTC
    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long =
        LocalDateTime(y, m, d, h, min).toInstant(tz).toEpochMilliseconds()

    private fun near(expected: Double, actual: Double) =
        assertTrue(abs(expected - actual) < 1e-9, "expected $expected but was $actual")

    private val products = DefaultProducts.all().associateBy { it.id }

    @Test
    fun `reference piece is 4mg gum and 2mg gum is half a piece`() {
        val data = FirewatchData(products = DefaultProducts.all())
        near(2.0, data.referenceMg)
        val gum2 = products.getValue(DefaultProducts.GUM_2MG).toDose("a", 0, 0)
        near(0.5, gum2.pieces(data.referenceMg))
        val gum4 = products.getValue(DefaultProducts.GUM_4MG).toDose("b", 0, 0)
        near(1.0, gum4.pieces(data.referenceMg))
    }

    @Test
    fun `6mg zyn logs as its absorbed amount`() {
        val zyn6 = products.getValue(DefaultProducts.ZYN_6MG).toDose("a", 0, 0)
        near(2.4, zyn6.absorbedMg())
        near(1.2, zyn6.pieces(DefaultProducts.REFERENCE_MG))
    }

    @Test
    fun `log time tweaks reduce absorption`() {
        val gum4 = products.getValue(DefaultProducts.GUM_4MG)
        near(2.0 * 0.6, gum4.toDose("a", 0, 0, duration = Duration.HALF).absorbedMg())
        near(2.0 * Absorption.ACIDIC_FACTOR, gum4.toDose("b", 0, 0, acidicDrink = true).absorbedMg())
        near(1.0, gum4.toDose("c", 0, 0, multiplier = 0.5).absorbedMg())
        // Acidic drinks only matter for gum.
        val zyn = products.getValue(DefaultProducts.ZYN_3MG)
        near(zyn.toDose("d", 0, 0).absorbedMg(), zyn.toDose("e", 0, 0, acidicDrink = true).absorbedMg())
    }

    @Test
    fun `range doses use the middle for stats and the top for timing`() {
        val d = Dose("x", "vape", at = 0, kind = ProductKind.VAPE, rangeLowMg = 2.0, rangeHighMg = 6.0)
        near(4.0, d.absorbedMg())
        near(6.0, d.absorbedMgHigh())
    }

    @Test
    fun `records round trip and keep fields this version does not know`() {
        val dose = products.getValue(DefaultProducts.GUM_4MG).toDose("d1", at(2026, 9, 1), at(2026, 9, 1))
        // A future version wrote an extra field.
        val futureJson = RecordCodec.dose(dose, null, 1).json.dropLast(1) + ""","futureField":"keep me"}"""
        val decoded = RecordCodec.decode(futureJson, Dose.serializer())
        assertNotNull(decoded)
        assertEquals(dose, decoded)
        // This version edits the dose; the unknown field must survive.
        val edited = RecordCodec.dose(decoded.copy(multiplier = 2.0), futureJson, 2)
        val obj = RecordCodec.parseObject(edited.json)!!
        assertEquals("keep me", obj["futureField"]!!.jsonPrimitive.content)
        assertEquals("2.0", obj["multiplier"]!!.jsonPrimitive.content)
    }

    @Test
    fun `unknown enum values fall back to defaults`() {
        val json = """{"id":"p","name":"Spray","kind":"NASAL_SPRAY","labelMg":1.0}"""
        val product = RecordCodec.decode(json, com.baastiklabs.firewatch.core.model.Product.serializer())
        assertEquals(ProductKind.OTHER, product?.kind)
    }

    @Test
    fun `data ignores deleted records and unknown types`() {
        val now = 10L
        val d1 = products.getValue(DefaultProducts.GUM_4MG).toDose("d1", at(2026, 9, 1), now)
        val d2 = products.getValue(DefaultProducts.GUM_4MG).toDose("d2", at(2026, 9, 2), now)
        val records = listOf(
            RecordCodec.dose(d1, null, now),
            RecordCodec.dose(d2, null, now, deleted = true),
            RecordEnvelope("z", "something-new", null, now, false, "{}"),
            RecordCodec.settings(Settings(onboardingDone = true), null, now),
        )
        val data = FirewatchData.fromRecords(records)
        assertEquals(listOf("d1"), data.doses.map { it.id })
        assertTrue(data.settings.onboardingDone)
    }

    @Test
    fun `backup round trip and merge keeps the newest version of each record`() {
        val gum = products.getValue(DefaultProducts.GUM_4MG)
        val old = RecordCodec.dose(gum.toDose("d1", at(2026, 9, 1), 1), null, 1)
        val newer = RecordCodec.dose(gum.toDose("d1", at(2026, 9, 1), 1, multiplier = 2.0), old.json, 5)
        val other = RecordCodec.dose(gum.toDose("d2", at(2026, 9, 2), 1), null, 1)

        val file = Backups.build(listOf(newer, other), now = 100, appVersion = "0.1.0", reason = "test")
        val text = Backups.encode(file)
        assertTrue(text.contains("Firewatch by Baastik Labs"))
        val decoded = Backups.decode(text)
        assertNotNull(decoded)
        val incoming = Backups.toEnvelopes(decoded)
        assertEquals(2, Backups.preview(decoded).doses)

        val toWrite = Backups.mergeIncoming(existing = listOf(old), incoming = incoming)
        assertEquals(setOf("d1", "d2"), toWrite.map { it.id }.toSet())
        val nothingNew = Backups.mergeIncoming(existing = listOf(newer, other), incoming = listOf(old))
        assertTrue(nothingNew.isEmpty())
        assertNull(Backups.decode("not json"))
    }

    @Test
    fun `day summaries total pieces and cravings per day`() {
        val gum4 = products.getValue(DefaultProducts.GUM_4MG)
        val zyn6 = products.getValue(DefaultProducts.ZYN_6MG)
        val data = FirewatchData(
            products = DefaultProducts.all(),
            doses = listOf(
                gum4.toDose("a", at(2026, 9, 1, 8), 0),
                zyn6.toDose("b", at(2026, 9, 1, 14), 0),
                gum4.toDose("c", at(2026, 9, 2, 9), 0),
            ),
            cravings = listOf(
                Craving("c1", at(2026, 9, 1, 13, 50), intensity = 6), // used: zyn 10 min later
                Craving("c2", at(2026, 9, 1, 18), intensity = 4, outcome = CravingOutcome.RODE_OUT, endedAt = at(2026, 9, 1, 18, 7)),
            ),
        )
        val days = Days.summaries(data, tz, now = at(2026, 9, 3))
        val first = days.getValue(LocalDate(2026, 9, 1))
        near(2.2, first.pieces)
        assertEquals(2, first.doseCount)
        assertEquals(2, first.cravings)
        assertEquals(1, first.cravingsRodeOut)
        near(1.0, days.getValue(LocalDate(2026, 9, 2)).pieces)
    }

    @Test
    fun `open cravings auto resolve`() {
        val c = Craving("c", at = 0, intensity = 5)
        val empty = FirewatchData()
        assertEquals(CravingOutcome.OPEN, Cravings.effectiveOutcome(empty, c, now = 10 * 60_000L))
        assertEquals(CravingOutcome.RODE_OUT, Cravings.effectiveOutcome(empty, c, now = Cravings.WINDOW_MS))
        // A dose during the baseline (no target yet) isn't a win.
        val dose = Dose("d", "p", at = 5 * 60_000L)
        assertEquals(CravingOutcome.USED, Cravings.effectiveOutcome(FirewatchData(doses = listOf(dose)), c, now = 10 * 60_000L))
    }

    @Test
    fun `baseline week runs seven days from the first log`() {
        val gum4 = products.getValue(DefaultProducts.GUM_4MG)
        assertEquals(BaselineStatus.NotStarted, Baseline.status(FirewatchData(), LocalDate(2026, 9, 1), tz))
        val data = FirewatchData(doses = listOf(gum4.toDose("a", at(2026, 9, 1), 0)))
        assertEquals(BaselineStatus.InProgress(1, 6), Baseline.status(data, LocalDate(2026, 9, 1), tz))
        assertEquals(BaselineStatus.InProgress(7, 0), Baseline.status(data, LocalDate(2026, 9, 7), tz))
        assertIs<BaselineStatus.Complete>(Baseline.status(data, LocalDate(2026, 9, 8), tz))

        val week = FirewatchData(
            products = DefaultProducts.all(),
            doses = (1..7).map { d -> gum4.toDose("d$d", at(2026, 9, d), 0) } + gum4.toDose("late", at(2026, 9, 9), 0),
        )
        val summaries = Days.summaries(week, tz, now = at(2026, 9, 10))
        near(1.0, Baseline.averagePiecesPerDay(summaries, LocalDate(2026, 9, 1)))
    }

    @Test
    fun `calendar levels`() {
        assertEquals(0, CalendarScale.level(0.0))
        assertEquals(1, CalendarScale.level(0.5))
        assertEquals(2, CalendarScale.level(2.0))
        assertEquals(4, CalendarScale.level(8.0))
        assertEquals(6, CalendarScale.level(30.0))
    }

    @Test
    fun `craving scale has ten described levels`() {
        assertEquals((1..10).toList(), CravingScale.levels.map { it.value })
        assertEquals(10, CravingScale.level(99).value)
        assertTrue(CravingScale.levels.all { it.feels.isNotBlank() })
    }

    @Test
    fun `ids are unique`() {
        val ids = (1..1000).map { Ids.newId(123) }.toSet()
        assertEquals(1000, ids.size)
    }

    @Test
    fun `settings record uses a fixed id`() {
        val env = RecordCodec.settings(Settings(), null, 1)
        assertEquals(RecordTypes.SETTINGS_ID, env.id)
        assertTrue(RecordCodec.parseObject(env.json)!!.jsonObject.containsKey("referenceProductId"))
    }

    @Test
    fun `feedback form body encodes every field`() {
        val body = Feedback.encode(Feedback.fields("Bug", "Crash on 3 & 4 mg ✓", "", "Sam", "Android 0.7.0 · Pixel"))
        assertTrue(body.startsWith("entry.2010880696=Bug&entry.1095248491=Crash+on+3+%26+4+mg+%E2%9C%93"), body)
        assertTrue("entry.610873672=Sam" in body && "entry.904750360=Android+0.7.0+%C2%B7+Pixel" in body)
        assertTrue(Feedback.encode(Feedback.fields("Weird", "x", "", "", "")).startsWith("entry.2010880696=Other"))
    }
}
