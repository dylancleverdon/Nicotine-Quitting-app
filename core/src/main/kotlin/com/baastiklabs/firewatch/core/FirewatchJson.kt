package com.baastiklabs.firewatch.core

import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * The one JSON configuration used for storage, backups and update manifests.
 *
 * It is deliberately tolerant so that any version of the app can read data written by any
 * other version (newer or older, e.g. after a rollback): unknown keys are ignored and unknown
 * enum values fall back to the property's default.
 */
val FirewatchJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
    isLenient = true
}

object Ids {
    private const val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz"

    /** A random, time-prefixed id. Sortable by creation time; unique enough for one person's data. */
    fun newId(nowMillis: Long): String {
        val time = nowMillis.toString(36).padStart(9, '0')
        val random = (1..10).map { ALPHABET[Random.nextInt(ALPHABET.length)] }.joinToString("")
        return "$time-$random"
    }
}
