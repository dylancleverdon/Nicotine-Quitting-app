package com.baastiklabs.firewatch.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guard against spin in the app's wording (docs/vision.md: never spin a miss, never console).
 * Scans all app text: the shared core (Help included), the Android app and the web app.
 * A test only catches known phrases, so a wording read-through is also part of every release.
 */
class WordingTest {
    private val phrases = listOf(
        Regex("that['’]s ok", RegexOption.IGNORE_CASE),
        Regex("\\bit happens[.,;!]", RegexOption.IGNORE_CASE),
        Regex("don['’]t worry", RegexOption.IGNORE_CASE),
        Regex("you['’]ll get", RegexOption.IGNORE_CASE),
        Regex("better luck", RegexOption.IGNORE_CASE),
    )

    @Test
    fun `no spin phrases in app text`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
        val dirs = listOf("core/src/commonMain", "core/src/jsMain", "app/src/main/java", "app/src/main/res", "web/src")
        val hits = dirs.map { File(root, it) }.filter { it.exists() }.flatMap { dir ->
            dir.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "ts", "tsx", "xml") }.flatMap { f ->
                f.readLines().mapIndexedNotNull { i, line ->
                    phrases.firstOrNull { it.containsMatchIn(line) }?.let { "${f.relativeTo(root)}:${i + 1}: ${line.trim().take(120)}" }
                }
            }
        }
        assertTrue(hits.isEmpty(), "Spin phrases found:\n" + hits.joinToString("\n"))
    }
}
