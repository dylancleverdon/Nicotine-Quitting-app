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

/** Each day coloured by how much was used: the light spreads as use drops. */
@Composable
fun CalendarScreen(data: FirewatchData, now: Long, onOpenDay: (LocalDate) -> Unit) {
    val tz = TimeZone.currentSystemDefault()
    val today = LocalDate.now()
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val month = YearMonth.parse(monthText)
    val minute = now / 60_000
    val summaries: Map<LocalDate, DaySummary> = remember(data, minute) {
        Days.summaries(data, tz, now).mapKeys { it.key.toJavaLocalDate() }
    }
    val firstLog: LocalDate? = remember(data) { data.doses.minOfOrNull { it.at }?.localDate(tz)?.toJavaLocalDate() }
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
                            dark = dark,
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenDay(date) },
                        )
                    }
                }
            }
        }

        Legend(dark)
        MonthSummary(month, summaries, firstLog, today)
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
    isModeDay: Boolean = false,
    dark: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val level = CalendarScale.level(pieces)
    val shape = RoundedCornerShape(10.dp)
    val tracked = !isFuture && !beforeFirstLog
    val background = when {
        !inMonth -> MaterialTheme.colorScheme.background
        !tracked -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> Heat.color(level, dark)
    }
    var cell = modifier.aspectRatio(1f).clip(shape).background(background)
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
            "Colour = pieces that day: none, up to 1, 3, 5, 8, 12, more. A dot marks your baseline week; a ring marks Relapse prevention mode days.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MonthSummary(month: YearMonth, summaries: Map<LocalDate, DaySummary>, firstLog: LocalDate?, today: LocalDate) {
    if (firstLog == null) return
    val start = maxOf(month.atDay(1), firstLog)
    val end = minOf(month.atEndOfMonth(), today)
    if (end.isBefore(start)) return
    val days = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
    val pieces = days.map { summaries[it]?.pieces ?: 0.0 }
    val total = pieces.sum()
    val clearDays = pieces.count { it < 0.05 }
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

