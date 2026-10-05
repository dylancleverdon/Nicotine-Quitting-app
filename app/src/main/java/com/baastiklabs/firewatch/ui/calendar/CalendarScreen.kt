package com.baastiklabs.firewatch.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.baastiklabs.firewatch.core.Baseline
import com.baastiklabs.firewatch.core.CalendarScale
import com.baastiklabs.firewatch.core.DaySummary
import com.baastiklabs.firewatch.core.Days
import com.baastiklabs.firewatch.core.localDate
import com.baastiklabs.firewatch.core.records.FirewatchData
import com.baastiklabs.firewatch.ui.EstimateNote
import com.baastiklabs.firewatch.ui.Fmt
import com.baastiklabs.firewatch.ui.Stat
import com.baastiklabs.firewatch.ui.theme.Heat
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDate
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * The month D was looking at, kept for 10 minutes after they last used the calendar, so opening a
 * day and coming back doesn't jump to the current month.
 */
private object CalendarMemory {
    const val KEEP_MS = 10 * 60_000L
    var month: String? = null
    var at: Long = 0L
}

/** Each day coloured by how much was used: the light spreads as use drops. */
@Composable
fun CalendarScreen(data: FirewatchData, now: Long, onOpenDay: (LocalDate) -> Unit) {
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { CalendarMemory.at = System.currentTimeMillis() } }
    val tz = TimeZone.currentSystemDefault()
    val today = LocalDate.now()
    var monthText by rememberSaveable {
        val kept = CalendarMemory.month?.takeIf { System.currentTimeMillis() - CalendarMemory.at < CalendarMemory.KEEP_MS }
        mutableStateOf(kept ?: YearMonth.from(today).toString())
    }
    CalendarMemory.month = monthText
    CalendarMemory.at = System.currentTimeMillis()
    val month = YearMonth.parse(monthText)
    val minute = now / 60_000
    val summaries: Map<LocalDate, DaySummary> = remember(data, minute) {
        Days.summaries(data, tz, now).mapKeys { it.key.toJavaLocalDate() }
    }
    val firstLog: LocalDate? = remember(data) { data.doses.minOfOrNull { it.at }?.localDate(tz)?.toJavaLocalDate() }
    // Logged / clear 🌿 / "?" / ghost 👻 for every day shown, and ▲ / ▼ on level changes.
    val states: Map<LocalDate, com.baastiklabs.firewatch.core.DayState> = remember(data, minute, monthText) {
        val first = month.atDay(1).minusDays(7)
        (0 until 45).map { first.plusDays(it.toLong()) }.associateWith { d ->
            Days.state(data, kotlinx.datetime.LocalDate(d.year, d.monthValue, d.dayOfMonth), tz, now)
        }
    }
    val marks = remember(data) { com.baastiklabs.firewatch.core.engine.Practice.levelMarks(data, tz) }
    val dark = isSystemInDarkTheme()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { monthText = month.minusMonths(1).toString() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            Text(
                Fmt.monthTitle(month),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { monthText = month.plusMonths(1).toString() },
                enabled = month < YearMonth.from(today),
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
            }
        }

        val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        val weekdays = (0 until 7).map { firstDayOfWeek.plus(it.toLong()) }
        Row(Modifier.fillMaxWidth()) {
            weekdays.forEach { day ->
                Text(
                    day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val first = month.atDay(1)
        val lead = ((first.dayOfWeek.value - firstDayOfWeek.value) + 7) % 7
        val gridStart = first.minusDays(lead.toLong())
        val weeks = ((lead + month.lengthOfMonth()) + 6) / 7
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (w in 0 until weeks) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (d in 0 until 7) {
                        val date = gridStart.plusDays((w * 7 + d).toLong())
                        DayCell(
                            date = date,
                            inMonth = YearMonth.from(date) == month,
                            isToday = date == today,
                            isFuture = date.isAfter(today),
                            beforeFirstLog = firstLog == null || date.isBefore(firstLog),
                            isModeDay = !date.isAfter(today) && com.baastiklabs.firewatch.core.engine.Relapse.isModeDay(data, kotlinx.datetime.LocalDate(date.year, date.monthValue, date.dayOfMonth), kotlinx.datetime.TimeZone.currentSystemDefault()),
                            isBaseline = firstLog != null && !date.isBefore(firstLog) && date.isBefore(firstLog.plusDays(Baseline.DAYS.toLong())),
                            pieces = summaries[date]?.pieces ?: 0.0,
                            state = states[date],
                            levelMark = marks[date.toString()],
                            dark = dark,
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenDay(date) },
                        )
                    }
                }
            }
        }

        if (states.any { (d, s) -> YearMonth.from(d) == month && s == com.baastiklabs.firewatch.core.DayState.UNKNOWN }) {
            Text(
                com.baastiklabs.firewatch.core.Help.UNKNOWN_DAYS_NOTE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Legend(dark)
        MonthSummary(month, summaries, states, firstLog, today)
        EstimateNote()
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isFuture: Boolean,
    beforeFirstLog: Boolean,
    isBaseline: Boolean,
    pieces: Double,
    state: com.baastiklabs.firewatch.core.DayState?,
    levelMark: String?,
    isModeDay: Boolean = false,
    dark: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val level = CalendarScale.level(pieces)
    val shape = RoundedCornerShape(10.dp)
    val leftOut = state == com.baastiklabs.firewatch.core.DayState.UNKNOWN || state == com.baastiklabs.firewatch.core.DayState.GHOST
    val tracked = !isFuture && !beforeFirstLog && !leftOut
    val background = when {
        !inMonth -> MaterialTheme.colorScheme.background
        !tracked -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> Heat.color(level, dark)
    }
    var cell = modifier.aspectRatio(1f).clip(shape).background(background)
        .semantics {
            contentDescription = "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())}" + when (state) {
                com.baastiklabs.firewatch.core.DayState.UNKNOWN -> ", nothing logged"
                com.baastiklabs.firewatch.core.DayState.CLEAR -> ", clear day"
                com.baastiklabs.firewatch.core.DayState.GHOST -> ", left out"
                else -> if (tracked && pieces > 0) ", about ${Fmt.pieces(pieces)} pieces" else ""
            }
        }
    if (isToday) cell = cell.border(2.dp, MaterialTheme.colorScheme.primary, shape)
    else if (isModeDay && inMonth) cell = cell.border(2.dp, MaterialTheme.colorScheme.tertiary, shape)
    if (inMonth && !isFuture) cell = cell.clickable(onClick = onClick)
    Box(cell, contentAlignment = Alignment.Center) {
        if (inMonth) {
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    isFuture -> MaterialTheme.colorScheme.outline
                    tracked -> Heat.onColor(level, dark)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            val badge = when (state) {
                com.baastiklabs.firewatch.core.DayState.UNKNOWN -> "?"
                com.baastiklabs.firewatch.core.DayState.GHOST -> "👻"
                com.baastiklabs.firewatch.core.DayState.CLEAR -> "🌿"
                else -> null
            }
            badge?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 3.dp, bottom = 1.dp)
                        .then(if (state == com.baastiklabs.firewatch.core.DayState.GHOST) Modifier.alpha(0.6f) else Modifier),
                )
            }
            levelMark?.let {
                Text(
                    if (it == "down") "▼" else "▲",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 3.dp, top = 1.dp),
                )
            }
            if (isBaseline) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary),
                )
            }
        }
    }
}

@Composable
private fun Legend(dark: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Clear", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 4.dp))
            CalendarScale.bands.forEach { band ->
                Box(Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(Heat.color(band.level, dark)))
            }
            Text("Heavy", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp))
        }
        Text(
            "Colour = pieces that day: none, up to 1, 3, 5, 8, 12, more. 🌿 a clear day, ? nothing logged, 👻 left out. ▲ ▼ your level changed. A dot marks your baseline week; a ring marks Relapse prevention mode days.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MonthSummary(month: YearMonth, summaries: Map<LocalDate, DaySummary>, states: Map<LocalDate, com.baastiklabs.firewatch.core.DayState>, firstLog: LocalDate?, today: LocalDate) {
    if (firstLog == null) return
    val start = maxOf(month.atDay(1), firstLog)
    val end = minOf(month.atEndOfMonth(), today)
    if (end.isBefore(start)) return
    // "?" and ghost days are left out; clear days count as 0.
    val days = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }
        .filter { states[it]?.leftOut != true }.toList()
    if (days.isEmpty()) return
    val pieces = days.map { summaries[it]?.pieces ?: 0.0 }
    val total = pieces.sum()
    val clearDays = days.count { states[it] == com.baastiklabs.firewatch.core.DayState.CLEAR }
    val lightest = days.zip(pieces).minByOrNull { it.second }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("This month", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Stat("≈ ${Fmt.pieces(total)}", "pieces")
                Stat("≈ ${Fmt.pieces(total / days.size)}", "a day on average")
                Stat("$clearDays", if (clearDays == 1) "clear day" else "clear days")
            }
            lightest?.let { (date, p) ->
                Text(
                    "Lightest day: ${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} (≈ ${Fmt.pieces(p)} pieces)",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

