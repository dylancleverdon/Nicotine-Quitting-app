package com.baastiklabs.firewatch.core

import com.baastiklabs.firewatch.core.update.Changelog
import com.baastiklabs.firewatch.core.update.CrashPolicy
import com.baastiklabs.firewatch.core.update.UpdateAsset
import com.baastiklabs.firewatch.core.update.UpdateManifest
import com.baastiklabs.firewatch.core.update.UpdatePolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateTest {
    private fun asset(code: Int, name: String) = UpdateAsset(code, name, "https://example/$name.apk", "00")

    private val manifest = UpdateManifest(latest = asset(30, "0.3.0"), rollback = asset(35, "0.2.0"))

    @Test
    fun `newer latest is installed automatically`() {
        assertEquals(30, UpdatePolicy.updateTarget(20, manifest)?.versionCode)
        assertNull(UpdatePolicy.updateTarget(30, manifest))
    }

    @Test
    fun `rollback is offered on the latest and never auto installed`() {
        assertEquals(35, UpdatePolicy.rollbackTarget(30, manifest)?.versionCode)
        // Once rolled back, the latest (lower code) is not re-installed, and no further rollback.
        assertNull(UpdatePolicy.updateTarget(35, manifest))
        assertNull(UpdatePolicy.rollbackTarget(35, manifest))
        // The next release installs over the rollback.
        val next = manifest.copy(latest = asset(40, "0.4.0"), rollback = asset(45, "0.3.0"))
        assertEquals(40, UpdatePolicy.updateTarget(35, next)?.versionCode)
    }

    @Test
    fun `manifest parses tolerantly`() {
        val text = """{"format":1,"latest":{"versionCode":10,"versionName":"0.1.0","url":"u","sha256":"ab","extra":1},"movedTo":null,"newThing":true}"""
        val parsed = UpdateManifest.parse(text)
        assertNotNull(parsed)
        assertEquals(10, parsed.latest?.versionCode)
        assertNull(parsed.rollback)
        assertNull(UpdateManifest.parse("<html>"))
    }

    @Test
    fun `crash loop right after an update triggers rollback`() {
        val installedAt = 1_000_000L
        val crashes = listOf(installedAt + 1000, installedAt + 2000, installedAt + 3000)
        assertTrue(CrashPolicy.shouldRollback(crashes, installedAt + 4000, installedAt))
        assertFalse(CrashPolicy.shouldRollback(crashes.take(2), installedAt + 4000, installedAt))
        // Crashes from before this install don't count.
        assertFalse(CrashPolicy.shouldRollback(crashes.map { it - 10_000 }, installedAt + 4000, installedAt))
        // Old installs don't auto-rollback.
        val later = installedAt + CrashPolicy.FRESH_INSTALL_MS + 1
        assertFalse(CrashPolicy.shouldRollback(listOf(later - 3, later - 2, later - 1), later, installedAt))
    }

    @Test
    fun `changelog sections since last seen`() {
        val md = """
            # Firewatch changelog

            ## 0.3.0 · 2026-10-10
            - Three

            ## 0.2.0 · 2026-10-01
            - Two

            ## 0.1.0 · 2026-09-26
            - One
        """.trimIndent()
        val sections = Changelog.parse(md)
        assertEquals(listOf("0.3.0", "0.2.0", "0.1.0"), sections.map { it.version })
        assertEquals("- Two", sections[1].body)
        assertEquals(listOf("0.3.0", "0.2.0"), Changelog.since(sections, "0.1.0").map { it.version })
        assertEquals(emptyList(), Changelog.since(sections, "0.3.0"))
        assertEquals(listOf("0.3.0"), Changelog.since(sections, null).map { it.version })
    }
}
