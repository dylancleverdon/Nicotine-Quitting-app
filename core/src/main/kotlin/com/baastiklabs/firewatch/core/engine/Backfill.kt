package com.baastiklabs.firewatch.core.engine

import com.baastiklabs.firewatch.core.model.Dose
import com.baastiklabs.firewatch.core.model.Product
import com.baastiklabs.firewatch.core.model.ProductKind
import com.baastiklabs.firewatch.core.model.SpeedProfile
import com.baastiklabs.firewatch.core.records.FirewatchData
import com.baastiklabs.firewatch.core.toDose
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus

/**
 * The back-dated baseline week: rough counts per day, turned into estimated doses spread evenly
 * across that day's usual waking hours. No times are asked for.
 */
object Backfill {
    /** The 7 days before [today], oldest first. */
    fun days(today: LocalDate): List<LocalDate> = (7 downTo 1).map { today.minus(it, DateTimeUnit.DAY) }

    /** One day's entry: product counts plus friend's-vape sessions (by amount). */
    data class DayEntry(
        val counts: Map<String, Int> = emptyMap(),
        val vapes: Map<FriendVape.Amount, Int> = emptyMap(),
    ) {
        val isEmpty: Boolean get() = counts.values.all { it <= 0 } && vapes.values.all { it <= 0 }
    }

    fun doses(
        data: FirewatchData,
        date: LocalDate,
        entry: DayEntry,
        tz: TimeZone,
        now: Long,
        newId: () -> String,
    ): List<Dose> {
        val day = Waking.day(data, date, tz)
        val items = mutableListOf<(Long) -> Dose>()
        entry.counts.forEach { (productId, n) ->
            val product: Product = data.productsById[productId] ?: return@forEach
            repeat(n.coerceAtLeast(0)) { items += { at -> product.toDose(newId(), at, now).copy(estimated = true) } }
        }
        entry.vapes.forEach { (amount, n) ->
            val (lo, hi) = FriendVape.range(null, amount.puffs)
            repeat(n.coerceAtLeast(0)) {
                items += { at ->
                    Dose(
                        id = newId(), productId = "friends-vape", at = at, productName = "Friend's vape (${amount.label.lowercase()})",
                        kind = ProductKind.VAPE, speed = SpeedProfile.SPIKE, rangeLowMg = lo, rangeHighMg = hi,
                        borrowed = true, loggedAt = now, estimated = true,
                    )
                }
            }
        }
        if (items.isEmpty()) return emptyList()
        val span = day.sleepAt - day.wakeAt
        return items.mapIndexed { i, make -> make(day.wakeAt + span * (2 * i + 1) / (2L * items.size)) }
    }
}
