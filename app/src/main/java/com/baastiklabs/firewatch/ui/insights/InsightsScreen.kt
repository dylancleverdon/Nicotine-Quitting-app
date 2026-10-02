package com.baastiklabs.firewatch.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import kotlinx.datetime.minus
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baastiklabs.firewatch.core.engine.Coach
import com.baastiklabs.firewatch.core.engine.Insights
import com.baastiklabs.firewatch.core.engine.Ladder
import com.baastiklabs.firewatch.core.engine.Progress
import com.baastiklabs.firewatch.core.engine.Quality
import com.baastiklabs.firewatch.core.engine.Tier
import com.baastiklabs.firewatch.core.localDate
import com.baastiklabs.firewatch.core.model.ProductKind
import com.baastiklabs.firewatch.core.pieces
import com.baastiklabs.firewatch.core.records.FirewatchData
import com.baastiklabs.firewatch.ui.EstimateNote
import com.baastiklabs.firewatch.ui.Fmt
import com.baastiklabs.firewatch.ui.Stat
import com.baastiklabs.firewatch.ui.charts.AxisLabels
import com.baastiklabs.firewatch.ui.charts.DivergingBars
import com.baastiklabs.firewatch.ui.charts.Bar
import com.baastiklabs.firewatch.ui.charts.BarChart
import com.baastiklabs.firewatch.ui.charts.CravingForecastChart
import com.baastiklabs.firewatch.ui.charts.ReceptorChart
import com.baastiklabs.firewatch.ui.charts.DayBarcode
import com.baastiklabs.firewatch.ui.charts.DoseStrip
import com.baastiklabs.firewatch.ui.charts.Heatmap
import com.baastiklabs.firewatch.ui.charts.StackedBars
import com.baastiklabs.firewatch.ui.charts.TrendLine
import com.baastiklabs.firewatch.ui.charts.WaveChart
import com.baastiklabs.firewatch.ui.charts.kindColor
import com.baastiklabs.firewatch.ui.charts.fmtNum
import com.baastiklabs.firewatch.ui.toLocalDateTime
import kotlinx.datetime.TimeZone
import java.util.Locale
import kotlin.math.roundToInt

private val sections = listOf("Today", "Cravings ahead", "Receptors", "Stretch & pull", "Trends", "Patterns", "Going up", "Going down", "Mix", "Forecasts", "Milestones", "Ladder")

@Composable
fun InsightsScreen(
    data: FirewatchData,
    now: Long,
    onSettings: (transform: (com.baastiklabs.firewatch.core.model.Settings) -> com.baastiklabs.firewatch.core.model.Settings) -> Unit,
    watch: @Composable () -> Unit,
) {
    val tz = TimeZone.currentSystemDefault()
    val minute = now / 60_000 / 5
    val ins = remember(data, minute) { Insights(data, tz, now) }
    var section by rememberSaveable { mutableStateOf(sections.first()) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            "Insights",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sections.forEach { s -> FilterChip(selected = section == s, onClick = { section = s }, label = { Text(s) }) }
        }
        if (ins.days.isEmpty()) {
            Text("Log a few doses and your graphs appear here.", modifier = Modifier.padding(16.dp))
            return@Column
        }
        var range by rememberSaveable { mutableStateOf(42) }
        var endOff by rememberSaveable { mutableStateOf(0) }
        val detailed = data.settings.detailedCharts && section !in listOf("Today", "Cravings ahead", "Receptors", "Ladder")
        if (detailed) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                listOf(7 to "7 days", 30 to "30 days", 90 to "90 days", 100_000 to "All").forEach { (v, l) ->
                    FilterChip(selected = range == v, onClick = { range = v; endOff = 0 }, label = { Text(l) })
                }
                if (range < 100_000) {
                    androidx.compose.material3.TextButton(onClick = { endOff += range }) { Text("‹ Earlier") }
                    androidx.compose.material3.TextButton(onClick = { endOff = (endOff - range).coerceAtLeast(0) }, enabled = endOff > 0) { Text("Later ›") }
                }
            }
        }
        androidx.compose.runtime.CompositionLocalProvider(LocalWindow provides (if (detailed) range to endOff else 42 to 0)) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (section) {
                "Today" -> item { TodaySection(ins, data, now, onSettings) }
                "Cravings ahead" -> item { CravingsAheadSection(data, now, tz) }
                "Receptors" -> item { ReceptorSection(data, now, tz) }
                "Stretch & pull" -> item { StretchSection(ins) }
                "Trends" -> item { TrendsSection(ins, data, now, tz) }
                "Patterns" -> item { PatternsSection(ins, data, tz) }
                "Going up" -> item { GoingUpSection(ins, data) }
                "Going down" -> item { GoingDownSection(ins, data, now) }
                "Mix" -> item { MixSection(ins) }
                "Forecasts" -> item { ForecastSection(ins, data, now) }
                "Milestones" -> item { MilestonesSection(ins, now) }
                "Ladder" -> item { LadderSection(data, now, tz) }
            }
            item { watch() }
            item { EstimateNote() }
        }
        }
    }
}

@Composable
fun ChartCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            content()
        }
    }
}

@Composable
private fun Col(content: @Composable () -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }

/** Opt-in "Detailed charts": how many days multi-day charts show, and how far back they end. */
private val LocalWindow = androidx.compose.runtime.compositionLocalOf { 42 to 0 }

@Composable
private fun <T> List<T>.win(): List<T> {
    val (range, endOff) = LocalWindow.current
    return dropLast(endOff.coerceAtMost((size - 1).coerceAtLeast(0))).takeLast(range)
}

private fun fmt1(x: Double) = String.format(Locale.getDefault(), "%.1f", x)

// Axis formatters and date labels.
private val hrs: (Double) -> String = { "${com.baastiklabs.firewatch.ui.charts.fmtNum(it)}h" }
private val num: (Double) -> String = { com.baastiklabs.firewatch.ui.charts.fmtNum(it) }
private val mgs: (Double) -> String = { "${com.baastiklabs.firewatch.ui.charts.fmtNum(it)} mg" }
private val pct: (Double) -> String = { "${com.baastiklabs.firewatch.ui.charts.fmtNum(it)}%" }
private val mins: (Double) -> String = { "${com.baastiklabs.firewatch.ui.charts.fmtNum(it)}m" }
private fun dates(ds: List<com.baastiklabs.firewatch.core.engine.DayStat>) = ds.map { Fmt.dayMonth(it.date) }
private fun kdates(ds: List<kotlinx.datetime.LocalDate>) = ds.map { Fmt.dayMonth(it) }

@Composable
private fun TodaySection(
    ins: Insights,
    data: FirewatchData,
    now: Long,
    onSettings: (transform: (com.baastiklabs.firewatch.core.model.Settings) -> com.baastiklabs.firewatch.core.model.Settings) -> Unit,
) = Col {
    YesterdayCard(data, now)
    // Day charts: choose which day to view (no overlay, no comparison).
    var back by rememberSaveable { mutableStateOf(0) }
    val today = ins.days.last().date
    val date = today.minus(back, kotlinx.datetime.DateTimeUnit.DAY)
    val w = remember(ins, date) { ins.days.firstOrNull { it.date == date } ?: ins.dayStat(date) }
    val oldest = ins.days.first().date
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        androidx.compose.material3.TextButton(onClick = { back++ }, enabled = date > oldest) { Text("‹") }
        Text(
            when (back) { 0 -> "Today"; 1 -> "Yesterday"; else -> Fmt.dayTitle(java.time.LocalDate.of(date.year, date.monthNumber, date.dayOfMonth)) },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.TextButton(onClick = { back-- }, enabled = back > 0) { Text("›") }
    }
    val curve = remember(ins, date) { ins.dayCurve(date) }
    val typicalSlots = ins.typicalCurve()
    val dayStart = w.date.let { java.time.LocalDate.of(it.year, it.monthNumber, it.dayOfMonth) }
        .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val typical = if (back > 0) emptyList() else typicalSlots.mapIndexed { i, v -> dayStart + i * 30 * 60_000L to v }
        .filter { curve.isNotEmpty() && it.first in curve.first().first..curve.last().first }
    ChartCard("Blood-level wave", if (back == 0) "Each dose is a hill or spike. Sleep is shaded; the dashed line is your typical day." else "Each dose is a hill or spike. Sleep is shaded.") {
        if (curve.size < 2) Text("Nothing logged that day.", style = MaterialTheme.typography.bodySmall)
        else WaveChart(
            curve,
            typical = typical,
            shaded = listOf(curve.first().first to w.wakeAt, w.sleepAt to curve.last().first),
            now = if (back == 0) now else null,
            overlay = if (data.settings.showVolatility) remember(ins, date) { ins.volatilityCurve(date) } else emptyList(),
        )
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.material3.Checkbox(
                checked = data.settings.showVolatility,
                onCheckedChange = { v -> onSettings { it.copy(showVolatility = v) } },
            )
            Text("Show volatility", style = MaterialTheme.typography.bodySmall)
        }
    }
    val vol = ins.fullDays.win()
    if (vol.isNotEmpty()) {
        ChartCard("Nicotine volatility", "How fast and how much your nicotine level changes, each day (mg per hour), with a 7-day average line. Lower means steadier nicotine through the day.") {
            val vs = vol.map { it.volatility }
            BarChart(
                vs.map { Bar(it) },
                line = vs.indices.map { i -> vs.subList(maxOf(0, i - 6), i + 1).average() },
                yFmt = { "${fmtNum(it)} mg/h" },
                xLabels = dates(vol),
            )
        }
    }
    ChartCard("Dose strip", "That day's doses across 24 hours, sized by amount and coloured by type.") {
        DoseStrip(w.doses.map { d ->
            val t = d.at.toLocalDateTime()
            Triple((t.hour * 60 + t.minute) / 1440f, data.piecesOf(d), kindColor(d.kind))
        })
    }
    ChartCard(if (back == 0) "Today so far" else "That day") {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Stat("≈ ${Fmt.pieces(w.pieces)}", "pieces")
            Stat(fmt1(w.clearHours) + " h", "clear hours")
            Stat("${w.doses.size}", if (w.doses.size == 1) "dose" else "doses")
        }
    }
}

/** Yesterday's waking day in facts only; up to two tips with Coaching tips on. */
@Composable
private fun YesterdayCard(data: FirewatchData, now: Long) {
    val tz = TimeZone.currentSystemDefault()
    val r = remember(data, now / 600_000) { com.baastiklabs.firewatch.core.engine.Coaching.yesterday(data, now, tz) } ?: return
    ChartCard("Yesterday in review", "Facts only · every figure is an estimate") {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Stat("≈ ${Fmt.pieces(r.pieces)}", "pieces")
            r.netMin?.let { Stat(Fmt.signedMinutes(it), "net") }
            Stat("≈ ${fmt1(r.volatility)} mg/h", "volatility")
        }
        val lines = listOfNotNull(
            r.mix.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "Mix: ") { "${(it.second * 100).roundToInt()}% ${com.baastiklabs.firewatch.core.engine.Coaching.kindName(it.first)}" },
            r.longestGapMin?.let { "Longest gap between pieces: ${Fmt.duration((it * 60_000).toLong())}" },
            "Doses stacked while the last one was still peaking: ${r.stacked}",
            r.morningStretchMin?.let { "Morning stretch: ${Fmt.duration((it * 60_000).toLong())}" },
        )
        lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        r.tips.forEach { Text("💡 $it", style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun CravingsAheadSection(data: FirewatchData, now: Long, tz: TimeZone) = Col {
    val minute = now / 60_000 / 5
    val o = remember(data, minute) { com.baastiklabs.firewatch.core.engine.CravingForecast.outlook(data, now, tz) }
    ChartCard(
        "Cravings ahead",
        "Chance of a craving over the next 24 hours, from your own logs and your estimated nicotine level. " +
            "Dots show how strong one would probably be. Sleep is shaded. An estimate, not a promise.",
    ) {
        CravingForecastChart(o.points, now)
        Text(
            o.next?.let { "Next craving likely around ${Fmt.time(it.peakAt)} (strength about ${it.strength.roundToInt()})" }
                ?: "No clear craving peak ahead right now.",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (o.windows.size > 1) {
            Text(
                "Most likely: " + o.windows.joinToString(", ") { "${Fmt.time(it.peakAt)} (about ${it.strength.roundToInt()})" } +
                    (o.quietestAt?.let { ". Quietest: around ${Fmt.time(it)}." } ?: "."),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (o.learning) {
            Text(
                "Still learning: based on ${o.cravingsUsed} logged ${if (o.cravingsUsed == 1) "craving" else "cravings"} so far, plus your nicotine curve. " +
                    "Log cravings with \"Craving? Log it\" and this sharpens up.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (o.tested >= 3) {
            Text(
                "Last 2 weeks: ${o.hits} of ${o.tested} cravings came during a predicted high window (the likeliest quarter of waking time).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReceptorSection(data: FirewatchData, now: Long, tz: TimeZone) = Col {
    val day = now / 86_400_000L
    val o = remember(data, day) { com.baastiklabs.firewatch.core.engine.Receptors.outlook(data, now, tz) }
    if (o == null) {
        ChartCard("Receptors", "Appears after your first full day of logging.") {}
        return@Col
    }
    val history = o.history.takeLast(90)
    ChartCard(
        "Receptors",
        "Estimated nicotine receptor load. 100% is typical of heavy regular use; the shaded band is the typical non-user range. " +
            "Solid: your past. Dashed: if you keep following the program. Faint: if you stayed on your current rung.",
    ) {
        ReceptorChart(
            history.map { it.load }, o.plan.map { it.load }, o.stay.map { it.load }, com.baastiklabs.firewatch.core.engine.Receptors.TYPICAL,
            dates = (history.map { it.date } + o.plan.map { it.date }).map { Fmt.dayMonth(it) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Stat("≈ ${(o.todayLoad * 100).roundToInt()}%", "load today")
            Stat(o.clearAirOnPlan?.let { Fmt.shortDateK(it) } ?: "over a year", "Clear Air on plan")
            Stat(o.typicalOnPlan?.let { Fmt.shortDateK(it) } ?: "over a year", "typical range on plan")
        }
        if (o.typicalIfStay == null) {
            Text(
                "Staying on your current rung keeps the load around ${(o.stay.last().load * 100).roundToInt()}%. Each step down lets it fall further.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (data.relapseOn) {
            Text(
                "Relapse prevention mode is on. The dashed line shows what tapering looks like once you're ready.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "An estimate from brain-imaging research averages and your logs, not a medical measurement. Everyone heals at their own pace.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StretchSection(ins: Insights) = Col {
    val split = ins.netSplit().win()
    if (split.isNotEmpty()) ChartCard("Where your net comes from", "Each day's net, split into timing (orange: waiting for a full battery) and dose size (teal: pieces smaller or bigger than one).") {
        DivergingBars(split.map { it.second }, split.map { it.third }, kdates(split.map { it.first }), { Fmt.signedMinutes(it) })
    }
    val days = ins.stretchPull.win()
    if (days.isEmpty()) {
        ChartCard("Stretch & pull", "Starts once you're working at a target rung.") {}
        return@Col
    }
    ChartCard(
        "Stretch & pull",
        "Stretch: time you held off after the battery was full. Pull: nicotine that came before the battery had room for it. " +
            "Net = stretch − pull; positive means you're living below your target pace. Every day starts clean.",
    ) {
        Text("Stretch (teal) and pull (grey), hours a day", style = MaterialTheme.typography.labelMedium)
        TrendLine(days.map { it.stretchMin / 60 }, second = days.map { it.pullMin / 60 },
            color = MaterialTheme.colorScheme.tertiary, secondColor = MaterialTheme.colorScheme.outline, yFmt = hrs, xLabels = kdates(days.map { it.date }))
        Text("Net, hours a day", style = MaterialTheme.typography.labelMedium)
        BarChart(days.map { Bar(kotlin.math.max(0.0, it.netMin / 60), color = if (it.netMin >= 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline) },
            height = 90.dp, yFmt = hrs, xLabels = kdates(days.map { it.date }))
        val week = days.filter { it.date < ins.today }.takeLast(7).filter { !it.paused }
        if (week.isNotEmpty()) {
            fun hm(m: Double) = Fmt.duration((kotlin.math.abs(m) * 60_000).toLong())
            val net = week.map { it.netMin }.average()
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Stat(hm(week.map { it.stretchMin }.average()), "stretch, 7-day avg")
                Stat(hm(week.map { it.pullMin }.average()), "pull, 7-day avg")
                Stat((if (net >= 0) "+" else "−") + hm(net), "net, 7-day avg")
            }
        }
        ins.stretchSummary()?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        val paused = days.count { it.paused }
        if (paused > 0) Text(
            "$paused ${if (paused == 1) "day" else "days"} in Relapse prevention mode: stretch and pull paused.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrendsSection(ins: Insights, data: FirewatchData, now: Long, tz: TimeZone) = Col {
    val states = remember(data, now / 600_000) { Progress.recentStates(data, now, tz, 30) }
    if (states.isNotEmpty()) {
        val known = states.count { it.second.known }
        val measured = Progress.measuredRung(data, now.localDate(tz), tz)
        val known7 = Progress.knownDays(data, now.localDate(tz), tz)
        ChartCard("$known of ${states.size} days known", "One dot per day: filled = logged, 🌿 = clear, ? = nothing logged, 👻 = left out. Only known days count in your figures.") {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                states.forEach { (_, st) ->
                    Text(
                        when (st) {
                            com.baastiklabs.firewatch.core.DayState.LOGGED -> "●"
                            com.baastiklabs.firewatch.core.DayState.CLEAR -> "🌿"
                            com.baastiklabs.firewatch.core.DayState.GHOST -> "👻"
                            else -> "?"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (st.known) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            measured?.let {
                Text(
                    "Your last 7 days measure ${it.label}" + if (known7 < 7) " ($known7 of 7 days known)" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
    val days = ins.days.win()
    val offset = ins.days.size - days.size
    val bands = listOf(
        1.0 to Color(0x2250A890), 3.0 to Color(0x1A8FC7B8), 5.0 to Color(0x14FFB35C), 8.0 to Color(0x14FF7A2F), 99.0 to Color(0x14E0443A),
    )
    ChartCard("Daily totals", "Pieces a day, tier bands behind and the 7-day average on top. Hatched = unknown doses (range); faded = estimated (back-dated) days.") {
        BarChart(
            days.map { d ->
                Bar(
                    d.pieces,
                    if (d.hasRange) d.lowPieces else null,
                    if (d.hasRange) d.highPieces else null,
                    color = if (d.doses.any { it.estimated }) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else null,
                )
            },
            line = days.indices.map { ins.sevenDayAverage(it + offset) },
            bands = bands,
            yFmt = num,
            xLabels = dates(days),
        )
    }
    val stairs = ins.tierStaircase()
    if (stairs.size >= 2) {
        ChartCard("Tier staircase", "Your measured tier week by week. Stairs going down.") {
            TrendLine(stairs.map { it.second.pieces }, stepped = true, yFmt = num, xLabels = kdates(stairs.map { it.first }))
            Text(stairs.joinToString(" → ") { it.second.tier.title }.takeLast(120), style = MaterialTheme.typography.bodySmall)
        }
    }
    val gaps = ins.weeklyGaps()
    if (gaps.size >= 2) {
        ChartCard("Gap between pieces", "Average time between doses, week by week. This one should climb.") {
            TrendLine(gaps.map { it.second / 60 }, color = MaterialTheme.colorScheme.tertiary, yFmt = hrs, xLabels = kdates(gaps.map { it.first }))
            Text("Latest: ${Fmt.duration((gaps.last().second * 60_000).toLong())}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PatternsSection(ins: Insights, data: FirewatchData, tz: TimeZone) = Col {
    val gaps = ins.gapSizes()
    if (gaps.any { it.second > 0 }) ChartCard("Gap sizes", "Time between pieces over your last 30 known days. Longer gaps are spacing at work.") {
        BarChart(gaps.map { Bar(it.second.toDouble()) }, yFmt = num, xLabels = gaps.map { it.first })
    }
    val shape = ins.weekShape()
    if (shape.any { it.second > 0 }) ChartCard("Week shape", "Average pieces by weekday, last 8 weeks.") {
        BarChart(shape.map { Bar(it.second) }, yFmt = num, xLabels = shape.map { it.first })
    }
    val fp = ins.firstPieceWeekdaysVsWeekends()
    if (fp.size > 1) ChartCard("First piece: weekdays vs weekends", "Minutes from waking to the first piece, week by week. Weekdays orange, weekends teal.") {
        TrendLine(fp.map { it.second ?: 0.0 }, second = fp.map { it.third ?: 0.0 }, secondColor = MaterialTheme.colorScheme.tertiary, yFmt = { "${fmtNum(it)}m" }, xLabels = kdates(fp.map { it.first }))
    }
    ChartCard("When it happens", "Hour of day across, Monday to Sunday down. Brighter = more.") { Heatmap(ins.heatmap()) }
    val endings = ins.cravingEndings()
    ChartCard(
        "How cravings ended",
        "Last 2 weeks. Riding it out and waiting for the right time both count as wins. Worked out from your logs: a piece within 45 minutes is linked to the craving.",
    ) {
        if (endings.isEmpty()) Text("Log cravings with \"Craving? Log it\" and this fills in.", style = MaterialTheme.typography.bodySmall)
        else {
            val max = endings.values.max()
            endings.entries.sortedBy { it.key.ordinal }.forEach { (r, n) ->
                BarRow((if (r.win) "✓ " else "") + r.title, n.toFloat() / max, "$n")
            }
        }
    }
    if (data.settings.hideTimer || data.timerChecks.isNotEmpty()) {
        val today = ins.today
        val h = com.baastiklabs.firewatch.core.engine.Checks.history(data, today, tz, 42)
        ChartCard("Checking", "Taps on the hidden next-piece timer. Checks while it's still refilling are the \"wanting it\" signal; fewer over time is progress.") {
            if (h.size >= 2) {
                TrendLine(h.map { it.total.toDouble() }, second = h.map { it.charging.toDouble() }, color = MaterialTheme.colorScheme.outline,
                    secondColor = MaterialTheme.colorScheme.tertiary, yFmt = num, xLabels = kdates(h.map { it.date }))
                Text("All checks (grey) and while refilling (teal), per day.", style = MaterialTheme.typography.bodySmall)
            } else Text("Your first days of checks appear here.", style = MaterialTheme.typography.bodySmall)
            val todayChecks = com.baastiklabs.firewatch.core.engine.Checks.day(data, com.baastiklabs.firewatch.core.engine.BatteryEngine.currentDay(data, System.currentTimeMillis(), tz).first.date, tz)
            val pieces = ins.lastDays(14).sumOf { it.pieces }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Stat("${todayChecks.total}", "checks today")
                if (pieces > 0) Stat(fmt1(h.takeLast(14).sumOf { it.total } / pieces), "checks per piece (2 weeks)")
            }
        }
    }
    val ttf = ins.days.mapNotNull { it.wakeToFirstMin }
    if (ttf.size >= 2) {
        ChartCard("Wake to first piece", "Minutes from waking to the first dose. Longer is better.") {
            TrendLine(ttf, color = MaterialTheme.colorScheme.tertiary, yFmt = mins)
            Text("Latest: ${Fmt.duration((ttf.last() * 60_000).toLong())}", style = MaterialTheme.typography.bodySmall)
        }
    }
    val triggers = ins.triggerCounts()
    ChartCard("Triggers", "Tags that show up most around doses.") {
        if (triggers.isEmpty()) Text("Hold a product button to tag what was going on.", style = MaterialTheme.typography.bodySmall)
        val max = triggers.maxOfOrNull { it.second } ?: 1
        triggers.forEach { (tag, n) -> BarRow(tag, n.toFloat() / max, "$n") }
    }
    ChartCard("Comparisons") {
        val cs = ins.comparisons()
        if (cs.isEmpty()) Text("Needs two weeks of logs.", style = MaterialTheme.typography.bodySmall)
        cs.forEach { c -> Text("${c.label}: ≈ ${Fmt.pieces(c.a)} vs ${Fmt.pieces(c.b)}", style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun GoingUpSection(ins: Insights, data: FirewatchData) = Col {
    val lg = ins.longestGaps().win()
    if (lg.size > 1) ChartCard("Longest gap each day", "The day's biggest stretch between pieces.") {
        BarChart(lg.map { Bar((it.second ?: 0.0) / 60) }, yFmt = hrs, xLabels = kdates(lg.map { it.first }))
    }
    val cw = ins.cravingStrengthWeekly()
    if (cw.size > 1) ChartCard("Craving strength over time", "Average strength of the cravings you logged, week by week (1–10). It usually fades as receptors settle.") {
        TrendLine(cw.map { it.second }, color = MaterialTheme.colorScheme.tertiary, yFmt = num, top = 10.0, xLabels = kdates(cw.map { it.first }))
    }
    val days = ins.fullDays.win()
    val tz = TimeZone.currentSystemDefault()
    val held = remember(data, ins.today) { com.baastiklabs.firewatch.core.engine.Control.heldSeries(data, System.currentTimeMillis(), tz).filter { it.third != null } }
    if (held.isNotEmpty()) {
        ChartCard("Holding steady", "Pieces a day against the rung you were working at (line). Every day at or under it is a win, whether or not you're tapering.") {
            BarChart(
                held.map { (_, p, t) -> Bar(p, color = if (p <= t!! + 0.25) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline) },
                line = held.map { it.third!! },
                lineColor = MaterialTheme.colorScheme.primary,
                yFmt = num,
                xLabels = kdates(held.map { it.first }),
            )
            val n = held.count { (_, p, t) -> p <= t!! + 0.25 }
            val now = System.currentTimeMillis()
            val atRung = com.baastiklabs.firewatch.core.engine.Control.heldDays(data, now, tz)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Stat("$n", "of the last ${held.size} days held")
                data.targetPieces?.takeIf { atRung > 0 }?.let { Stat("$atRung", "days held at ${com.baastiklabs.firewatch.core.engine.Ladder.rung(it).label}") }
            }
        }
    }
    ChartCard("Clear hours", "Hours each day your estimated level sat near zero while awake. Watch it rise.") {
        TrendLine(days.map { it.clearHours }, color = MaterialTheme.colorScheme.tertiary, yFmt = hrs, xLabels = dates(days))
    }
    val overnight = ins.overnightGaps().win()
    if (overnight.size >= 2) {
        ChartCard("Overnight gap", "Last dose at night to first the next morning.") {
            TrendLine(overnight.map { it.second / 60 }, color = MaterialTheme.colorScheme.tertiary, yFmt = hrs, xLabels = kdates(overnight.map { it.first }))
            Text("Latest: ${Fmt.duration((overnight.last().second * 60_000).toLong())}", style = MaterialTheme.typography.bodySmall)
        }
    }
    ChartCard("Wins") {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Stat("≈ ${Fmt.pieces(ins.piecesAvoided())}", "pieces avoided")
            Stat("≈ ${fmt1(ins.mgAvoided())} mg", "nicotine avoided")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Stat(ins.cravingWinRate()?.let { "${(it * 100).roundToInt()}%" } ?: "–", "craving win rate")
        }
        if (!ins.baselineComplete) Text("Avoided figures start after your baseline week.", style = MaterialTheme.typography.bodySmall)
    }
    val saved = ins.moneySaved()
    val s = data.settings
    ChartCard("Money saved", "Compared with your baseline spending. Set prices in Settings → Products.") {
        Text("${s.currency}${String.format(Locale.getDefault(), "%.2f", saved)}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        ins.packsNotBought()?.let { (packs, name) -> Text("≈ ${fmt1(packs)} packs of $name not bought", style = MaterialTheme.typography.bodySmall) }
        if (s.rewardCost > 0) {
            LinearProgressIndicator(progress = { (saved / s.rewardCost).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Text("Toward ${s.rewardName.ifBlank { "your reward" }}: ${(saved / s.rewardCost * 100).roundToInt().coerceAtMost(100)}%", style = MaterialTheme.typography.bodySmall)
        }
    }
    val beaten = ins.beatenTriggers()
    ChartCard("Beaten triggers", "Of each trigger's last 10 appearances, how many passed without nicotine (from tagged cravings).") {
        if (beaten.isEmpty()) Text("Tag a craving (coffee, stress…) while riding it out to fill this in.", style = MaterialTheme.typography.bodySmall)
        beaten.forEach { (tag, won, total) -> BarRow(tag, won.toFloat() / total, "$won of $total without") }
    }
}

@Composable
private fun GoingDownSection(ins: Insights, data: FirewatchData, now: Long) = Col {
    val days = ins.fullDays.win()
    ChartCard("Average dose size", "Absorbed mg per dose. Catches moves like 6 mg to 3 mg.") {
        TrendLine(days.map { if (it.doses.isEmpty()) 0.0 else it.absorbedMg / it.doses.size }, yFmt = mgs, xLabels = dates(days))
    }
    ChartCard("Spike share", "Share of nicotine arriving as fast spikes (vapes). Shrinking is real progress.") {
        TrendLine(days.map { if (it.absorbedMg > 0) it.spikeMg / it.absorbedMg * 100 else 0.0 }, yFmt = pct, top = 100.0, xLabels = dates(days))
    }
    val quality = days.map { it.quality ?: 100.0 }
    ChartCard("Nicotine quality", "How you use nicotine, on a food scale. Gum, lozenges and patches are broccoli; smoke is burger and fries. Switching method raises it; using a vape less doesn't.") {
        TrendLine(quality, color = MaterialTheme.colorScheme.tertiary, yFmt = num, top = 100.0, xLabels = dates(days))
        ins.days.last().quality?.let { Text("Today: ${Quality.label(it)}", style = MaterialTheme.typography.titleMedium) }
        com.baastiklabs.firewatch.core.engine.Coaching.swapTip(data, ins.days.last().doses)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Quality.Food.entries.forEach { f -> Text("${f.emoji} ${f.title}: ${f.min}+", style = MaterialTheme.typography.bodySmall) }
        Text("Per dose: gum, lozenge, patch 100 · pouch 60 · vape 25 · cigarette 5 (max 10). Minus points for a big single dose and for stacking doses.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    ChartCard("Heaviness score", "From how soon after waking you use and how much per day, the two things dependence tests weight most (0–6).") {
        val weekly = ins.fullDays.chunked(7).mapNotNull { ins.heaviness(it) }
        TrendLine(weekly, yFmt = num, top = 6.0, xLabels = weekly.indices.map { "Wk ${it + 1}" })
        ins.heaviness()?.let { Text("Last 7 days: ${fmt1(it)} of 6", style = MaterialTheme.typography.bodyMedium) }
    }
    ChartCard("Background level", "A slow line modelled on cotinine, nicotine's breakdown product. It drifts down even through messy days.") {
        val bg = ins.backgroundSeries().win()
        TrendLine(bg.map { it.second }, yFmt = mgs, xLabels = kdates(bg.map { it.first }))
    }
    ChartCard("Double-ups", "Doses stacked while the last one was still peaking, per week.") {
        val du = ins.doubleUpsPerWeek()
        TrendLine(du.map { it.second.toDouble() }, yFmt = num, xLabels = kdates(du.map { it.first }))
    }
    ChartCard("Cravings vs doses", "Urges (teal) and doses (orange) per day. Ideally urges fade as doses drop.") {
        TrendLine(days.map { it.doses.size.toDouble() }, second = days.map { it.cravings.toDouble() }, secondColor = MaterialTheme.colorScheme.tertiary, yFmt = num, xLabels = dates(days))
    }
    val coach = remember(data) { Coach.profile(data, now) }
    ChartCard("What you can ride out", "Personal: the strongest craving level you usually beat, learned from your logs.") {
        Text(
            if (coach.confident) "You reliably ride out cravings up to about ${coach.capacity} out of 10."
            else "Log about ${8 - coach.cravingsCounted.coerceAtMost(8)} more cravings (and whether they passed) to personalise this.",
            style = MaterialTheme.typography.bodyMedium,
        )
        coach.levels.filter { it.total > 0 }.forEach { BarRow("Level ${it.level}", it.rate.toFloat(), "${it.rodeOut} of ${it.total} beaten") }
        Coach.honestPieces(data, now, TimeZone.currentSystemDefault())?.let {
            Text("Honest level (use + strong unmet cravings): ${Ladder.measured(it).label}", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (data.checkIns.size >= 2) {
        ChartCard("Daily check-in", "Craving strength (orange) and mood (teal), 1–5.") {
            val last = data.checkIns.win()
            TrendLine(last.map { it.craving.toDouble() }, second = last.map { it.mood.toDouble() }, secondColor = MaterialTheme.colorScheme.tertiary, yFmt = num, top = 5.0)
        }
    }
}

@Composable
private fun MixSection(ins: Insights) = Col {
    val byHour = ins.kindsByHour()
    if (byHour.isNotEmpty()) ChartCard("Doses by product over the day", "Pieces by hour of day, last 30 known days, coloured by type.") {
        StackedBars((0 until 24).map { h -> byHour.entries.mapNotNull { (k, v) -> v[h].takeIf { it > 0 }?.let { it to kindColor(k) } } })
        AxisLabels(listOf("12 AM", "6 AM", "12 PM", "6 PM", "11 PM"))
    }
    val weeks = ins.days.chunked(7)
    ChartCard("Product mix", "Nicotine by delivery method, week by week.") {
        StackedBars(weeks.map { w ->
            ProductKind.entries.mapNotNull { k -> w.sumOf { it.kindPieces[k] ?: 0.0 }.takeIf { it > 0 }?.let { it to kindColor(k) } }
        })
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProductKind.entries.forEach { k ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(kindColor(k)))
                    Text(" ${k.name.lowercase()}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    val label = ins.days.sumOf { it.labelMg }
    val absorbed = ins.days.sumOf { it.absorbedMg }
    ChartCard("Label vs absorbed", "What the packaging says versus what reached your blood (est.).") {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Stat("${label.roundToInt()} mg", "on the labels")
            Stat("≈ ${absorbed.roundToInt()} mg", "absorbed")
        }
        TrendLine(ins.days.win().map { it.labelMg }, second = ins.days.win().map { it.absorbedMg }, yFmt = mgs, xLabels = dates(ins.days.win()))
    }
    val borrowed = ins.days.sumOf { it.borrowedPieces }
    val total = ins.days.sumOf { it.pieces }.coerceAtLeast(0.001)
    ChartCard("Borrowed share", "Nicotine from other people's products.") {
        Text("${(borrowed / total * 100).roundToInt()}% (≈ ${Fmt.pieces(borrowed)} pieces)", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ForecastSection(ins: Insights, data: FirewatchData, now: Long) = Col {
    val (pvp, show) = remember(data, now / 3_600_000) { ins.paceVsPlan() }
    if (show && pvp.isNotEmpty()) ChartCard("Pace vs your plan", "Your pieces each day (bars) against your level, then the plan if you take each step (line). Optional: staying steady is a win too.") {
        BarChart(pvp.map { Bar(it.second ?: 0.0) }, line = pvp.map { it.third ?: 0.0 }, lineColor = MaterialTheme.colorScheme.tertiary, yFmt = num, xLabels = kdates(pvp.map { it.first }))
    }
    val free = remember(data, now / 3_600_000) { com.baastiklabs.firewatch.core.engine.ClearAir.daysFreeSeries(data, now, TimeZone.currentSystemDefault()) }
    if (free.isNotEmpty()) ChartCard("Days nicotine-free", "A total that only goes up.") {
        TrendLine(free.map { it.second.toDouble() }, stepped = true, color = MaterialTheme.colorScheme.tertiary, yFmt = num, xLabels = kdates(free.map { it.first }))
    }
    val stepDown = remember(data, now / 3_600_000) {
        if (com.baastiklabs.firewatch.core.engine.Control.isEarly(data)) null
        else com.baastiklabs.firewatch.core.engine.Progress.stepDownProgress(data, now, TimeZone.currentSystemDefault())
    }
    if (stepDown != null) {
        ChartCard(
            "Next step down",
            "Full days in a row at or under your level, since your last change. Today counts once it's over. Staying where you are is a win too.",
        ) {
            val offered = stepDown.ready &&
                com.baastiklabs.firewatch.core.engine.Progress.stepDownOffer(data, now, TimeZone.currentSystemDefault()) != null
            Text(
                if (offered) "${stepDown.needed} of ${stepDown.needed} days held: ${stepDown.next.label} is offered on the Log tab"
                else "${stepDown.held} of ${stepDown.needed} days held",
                style = MaterialTheme.typography.titleMedium,
            )
            LinearProgressIndicator(
                progress = { (stepDown.held.toFloat() / stepDown.needed).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    val plan = remember(data, now / 3_600_000) { com.baastiklabs.firewatch.core.engine.Control.taperPlan(data, now, TimeZone.currentSystemDefault()) }
    if (plan != null && plan.steps.isNotEmpty()) {
        ChartCard(
            "If you take each step",
            "Stepping down each time it's offered (every ${plan.holdDays} days). Optional: staying steady is a win too. ${plan.basis}.",
        ) {
            plan.steps.forEach { st ->
                Row(Modifier.fillMaxWidth()) {
                    Text(st.rung.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("around ${Fmt.shortDateK(st.date)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    ChartCard("Journey to Clear Air", "From your baseline to nicotine-free.") {
        val j = ins.journey()
        if (j == null) Text("Starts after your baseline week.", style = MaterialTheme.typography.bodySmall)
        else {
            Text("${(j * 100).roundToInt()}%", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            LinearProgressIndicator(progress = { j.toFloat() }, modifier = Modifier.fillMaxWidth())
        }
    }
    ChartCard("Taper speed", "Average drop per week over the last 4 weeks.") {
        Text(ins.taperSpeedText() ?: "Needs a week or two more data.",
            style = MaterialTheme.typography.titleMedium)
    }
    ChartCard("Arrival dates", "At your current pace, when you'd reach each tier. They get closer as you go.") {
        val arrivals = ins.arrivals()
        if (arrivals.isEmpty()) Text("Needs a steady trend first.", style = MaterialTheme.typography.bodySmall)
        arrivals.forEach { f ->
            Text("${f.rung.tier.title}: ${f.date?.let { if (it <= ins.today) "reached" else Fmt.shortDateK(it) } ?: "not at this pace yet"}", style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (ins.baselineComplete && ins.fullDays.size > ins.baselineDays.size + 3) {
        val then = ins.typicalCurve(ins.baselineDays)
        val nowC = ins.typicalCurve(ins.lastDays(7))
        ChartCard("Then vs now", "Your average baseline day (grey) over your average day now.") {
            TrendLine(nowC, second = then, yFmt = mgs, xLabels = (0 until 48).map { Fmt.hourLabel(it / 2) })
        }
    }
}

@Composable
private fun MilestonesSection(ins: Insights, now: Long) = Col {
    val sm = ins.steadyByMonth()
    if (sm.isNotEmpty()) ChartCard("Steady days by month", "A total for each month, never a streak.") {
        BarChart(sm.map { Bar(it.second.toDouble()) }, yFmt = num, xLabels = sm.map { m ->
            java.time.YearMonth.parse(m.first).month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
        })
    }
    val r = ins.records()
    ChartCard("Records") {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Stat(Fmt.duration((r.longestGapMin * 60_000).toLong()), "longest gap")
            Stat(r.lightestDay?.let { "≈ ${Fmt.pieces(it.pieces)}" } ?: "–", "lightest day")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Stat(Fmt.duration((r.totalStretchMin * 60_000).toLong()), "total stretch time")
            Stat("${r.daysAtCurrentRung}", "days at this rung")
        }
    }
    ChartCard("Insights") {
        ins.insightCards().forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
    }
    ChartCard("Badges", "One for each rung reached, plus firsts. Badges are never taken away.") {
        val badges = ins.badges()
        if (badges.isEmpty()) Text("Your first badges come with your first step down.", style = MaterialTheme.typography.bodySmall)
        badges.reversed().forEach { b -> Text("🔥 ${b.title} · ${b.detail} · ${Fmt.shortDateK(b.earnedOn)}", style = MaterialTheme.typography.bodyMedium) }
    }
    var recapKey by rememberSaveable { mutableStateOf("") }
    val months = ins.months()
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        months.forEachIndexed { i, (y, m) ->
            val key = "$y-$m"
            FilterChip(selected = recapKey == key || (recapKey.isEmpty() && i == 0), onClick = { recapKey = key },
                label = { Text(java.time.Month.of(m).getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()) + " $y") })
        }
        months.map { it.first }.distinct().forEach { y ->
            FilterChip(selected = recapKey == "year-$y", onClick = { recapKey = "year-$y" }, label = { Text("$y in review") })
        }
    }
    val recap = when {
        recapKey.startsWith("year-") -> ins.yearRecap(recapKey.removePrefix("year-").toInt())
        recapKey.isNotEmpty() -> recapKey.split("-").let { ins.monthlyRecap(it[0].toInt(), it[1].toInt()) }
        else -> months.firstOrNull()?.let { ins.monthlyRecap(it.first, it.second) }
    }
    recap?.let { rc ->
        ChartCard(if (rc.month.contains("review")) rc.month else "${rc.month} recap") {
            Text("≈ ${Fmt.pieces(rc.pieces)} pieces", style = MaterialTheme.typography.bodyMedium)
            rc.biggestWeeklyDropPct?.let { Text("Biggest weekly drop: ${it.roundToInt()}%", style = MaterialTheme.typography.bodyMedium) }
            Text("Longest gap: ${Fmt.duration((rc.longestGapMin * 60_000).toLong())}", style = MaterialTheme.typography.bodyMedium)
            rc.mostBeatenTrigger?.let { Text("Most-beaten trigger: $it", style = MaterialTheme.typography.bodyMedium) }
            Text("Cravings ridden out: ${rc.cravingsRidden}", style = MaterialTheme.typography.bodyMedium)
            if (rc.rungsReached.isNotEmpty()) Text("Rungs reached: ${rc.rungsReached.joinToString()}", style = MaterialTheme.typography.bodyMedium)
        }
    }
    ChartCard("Day barcode", "Each day a strip, stacked by month: dark where nicotine was in your system, light where clear. Watch the light spread.") {
        ins.days.groupBy { it.date.year to it.date.monthNumber }.entries.toList().takeLast(4).forEach { (ym, ds) ->
            Text(java.time.Month.of(ym.second).getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()) + " ${ym.first}",
                style = MaterialTheme.typography.labelMedium)
            DayBarcode(ds.map { it.barcode })
        }
    }
    val silly = ins.silly()
    ChartCard("Silly conversions") {
        Text("≈ ${silly.pouchesSkipped.roundToInt()} pouches skipped, ${fmt1(silly.pouchLengthMetres)} m laid end to end", style = MaterialTheme.typography.bodyMedium)
        Text("≈ ${fmt1(silly.chewingHoursAvoided)} hours of chewing avoided", style = MaterialTheme.typography.bodyMedium)
        Text("≈ ${silly.cigarettesNotSmoked.roundToInt()} cigarettes' worth of nicotine not taken in", style = MaterialTheme.typography.bodyMedium)
    }
    val (last, steps) = ins.clearAirTimeline()
    if (last != null && now - last > 6 * 3_600_000L) {
        ChartCard("Clear Air countdown", "Since your last dose ${Fmt.ago(last, now)}. A research-based recovery timeline (approximate).") {
            steps.forEach { (label, at) ->
                Text((if (now >= at) "✓ " else "○ ") + label + if (now < at) " · ${Fmt.shortDateK(at)}" else "", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun LadderSection(data: FirewatchData, now: Long, tz: TimeZone) = Col {
    val runs = remember(data) { com.baastiklabs.firewatch.core.engine.Practice.runs(data, now, tz) }
    if (runs.isNotEmpty()) ChartCard("Practice pace runs", "Each run's practice net (bars) and the waking hours it covered.") {
        BarChart(runs.map { Bar((it.netMin / 60).coerceAtLeast(0.0)) }, yFmt = hrs, xLabels = runs.map { it.rung.tier.title })
        runs.forEach { r -> Text("${r.rung.tier.title} pace · ${r.hours.toInt()} h · practice net ${Fmt.signedMinutes(r.netMin)}", style = MaterialTheme.typography.bodySmall) }
    }
    val history = remember(data) {
        com.baastiklabs.firewatch.core.engine.Practice.history(data, now, tz) { Fmt.dayMonth(it) }
    }
    if (history.isNotEmpty()) {
        ChartCard("Level history", "Newest first. Step ups keep their reason; practice pace shows its practice net.") {
            history.forEach { h ->
                Text(
                    (when (h.kind) { "down" -> "▼ "; "up" -> "▲ "; "practice" -> "◇ "; else -> "• " }) + h.text,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    val measured = Progress.measuredRung(data, now.localDate(tz), tz)
    val target = data.targetPieces
    ChartCard("The ladder", "Each rung is one piece a day lighter. Named tiers are the big landmarks.") {
        (Ladder.rungs + Ladder.clearAir).forEach { r ->
            val isTarget = target != null && kotlin.math.abs(r.pieces - target) < 1e-6
            val isMeasured = measured != null && kotlin.math.abs(r.pieces - measured.pieces) < 1e-6
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    r.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isTarget || isMeasured) FontWeight.Bold else FontWeight.Normal,
                    color = if (isTarget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (isTarget) Text("target  ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                if (isMeasured) Text("you", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        Text(Tier.entries.joinToString("\n") { "${it.title}: ${it.pace}" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "The top rungs mirror the gum box schedule (a piece every 1–2 hours for weeks 1–6, every 2–4 hours for weeks 7–9, every 4–8 hours for weeks 10–12). This ladder keeps going with smaller steps.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BarRow(label: String, fraction: Float, value: String) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodySmall)
        }
        LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
    }
}

