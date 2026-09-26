package com.baastiklabs.firewatch.ui

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Display formatting. Every nicotine figure is an estimate and is shown with "≈". */
object Fmt {
    /** Follows the phone's 12/24-hour setting; refreshed by MainActivity. */
    @Volatile
    var use24h: Boolean = false

    fun pieces(x: Double): String = when {
        abs(x) < 0.05 -> "0"
        x < 10 -> String.format(Locale.getDefault(), "%.1f", x)
        else -> String.format(Locale.getDefault(), "%.0f", x)
    }

    fun piecesLabel(x: Double): String = "≈ ${pieces(x)} ${if (abs(x - 1.0) < 0.05) "piece" else "pieces"}"

    fun mg(x: Double): String = String.format(Locale.getDefault(), "%.1f mg", x)

    fun time(ms: Long): String = time(ms.toLocalDateTime().toLocalTime())

    fun time(t: LocalTime): String =
        t.format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a", Locale.getDefault()))

    fun minutesOfDay(minutes: Int): String = time(LocalTime.of((minutes / 60) % 24, minutes % 60))

    fun duration(ms: Long): String {
        val totalMinutes = (ms / 60_000).coerceAtLeast(0)
        val days = totalMinutes / (60 * 24)
        val hours = (totalMinutes / 60) % 24
        val minutes = totalMinutes % 60
        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }

    fun ago(ms: Long, now: Long): String {
        val diff = now - ms
        return if (diff < 60_000) "just now" else "${duration(diff)} ago"
    }

    fun dayTitle(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))

    fun shortDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))

    fun shortDateK(date: kotlinx.datetime.LocalDate): String =
        shortDate(LocalDate.of(date.year, date.monthNumber, date.dayOfMonth))

    fun shortDateK(day: com.baastiklabs.firewatch.core.engine.DayStat): String = shortDateK(day.date)

    fun shortDateK(ms: Long): String = shortDate(ms.toLocalDateTime().toLocalDate())

    fun monthTitle(month: YearMonth): String =
        month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))

    fun dateTime(ms: Long): String {
        val dt = ms.toLocalDateTime()
        return "${shortDate(dt.toLocalDate())}, ${time(dt.toLocalTime())}"
    }
}

fun Long.toLocalDateTime(): LocalDateTime = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()

fun LocalDateTime.toEpochMillis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
