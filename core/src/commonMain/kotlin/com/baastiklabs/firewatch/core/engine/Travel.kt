package com.baastiklabs.firewatch.core.engine

import com.baastiklabs.firewatch.core.model.Settings
import com.baastiklabs.firewatch.core.records.FirewatchData

/**
 * Travel: when the phone's time zone changes, ask once "Use your usual times here?" (Yes is
 * recommended) or keep home times. The switch applies from the next waking day, so past days
 * never move. The first zone ever seen is recorded silently.
 */
object Travel {
    data class Prompt(val from: String, val to: String, val hoursAhead: Double)

    /** Settings to save the first time a zone is seen (no question), or null. */
    fun firstSeen(s: Settings, zone: String, now: Long): Settings? =
        if (s.lastZone.isEmpty()) s.copy(lastZone = zone, zoneHistory = s.zoneHistory.ifEmpty { listOf(Waking.zoneEntry(now, zone)) }) else null

    /** The question to ask, when the zone changed since it was last answered. */
    fun prompt(data: FirewatchData, zone: String, now: Long): Prompt? {
        val last = data.settings.lastZone
        if (last.isEmpty() || last == zone) return null
        return Prompt(last, zone, Waking.offsetHours(last, zone, now))
    }

    /** "Yes, use my usual times here": local times from the next waking day. */
    fun useLocal(s: Settings, zone: String, now: Long): Settings =
        s.copy(lastZone = zone, zoneHistory = s.zoneHistory + Waking.zoneEntry(now, zone))

    /** "Keep my home times": nothing moves; don't ask again for this zone. */
    fun keepHome(s: Settings, zone: String): Settings = s.copy(lastZone = zone)

    /** "now 3 hours ahead" / "now 2 hours behind" / "same time". */
    fun describe(p: Prompt): String {
        val h = p.hoursAhead
        if (kotlin.math.abs(h) < 0.01) return "same clock time"
        val n = kotlin.math.abs(h)
        val txt = if (n == n.toLong().toDouble()) "${n.toLong()}" else "${(n * 10).toLong() / 10.0}"
        return "now $txt ${if (n == 1.0) "hour" else "hours"} ${if (h > 0) "ahead" else "behind"}"
    }
}
