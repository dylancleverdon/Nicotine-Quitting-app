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
import com.baastiklabs.firewatch.ui.charts.Bar
import com.baastiklabs.firewatch.ui.charts.BarChart
import com.baastiklabs.firewatch.ui.charts.DayBarcode
import com.baastiklabs.firewatch.ui.charts.DoseStrip
import com.baastiklabs.firewatch.ui.charts.Heatmap
import com.baastiklabs.firewatch.ui.charts.StackedBars
import com.baastiklabs.firewatch.ui.charts.TrendLine
import com.baastiklabs.firewatch.ui.charts.WaveChart
import com.baastiklabs.firewatch.ui.charts.kindColor
import com.baastiklabs.firewatch.ui.toLocalDateTime
import kotlinx.datetime.TimeZone
import java.util.Locale
import kotlin.math.roundToInt

private val sections = listOf("Today", "Trends", "Patterns", "Going up", "Going down", "Mix", "Forecasts", "Milestones", "Ladder")

@Composable
fun InsightsScreen(data: FirewatchData, now: Long, watch: @Composable () -> Unit) {
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
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (section) {
                "Today" -> item { TodaySection(ins, data, now) }
                "Trends" -> item { TrendsSection(ins) }
                "Patterns" -> item { PatternsSection(ins) }
                "Going up" -> item { GoingUpSection(ins, data) }
                "Going down" -> item { GoingDownSection(ins, data, now) }
                "Mix" -> item { MixSection(ins) }
                "Forecasts" -> item { ForecastSection(ins) }
                "Milestones" -> item { MilestonesSection(ins, now) }
                "Ladder" -> item { LadderSection(data, now, tz) }
            }
            item { watch() }
            item { EstimateNote() }
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

private fun fmt1(x: Double) = String.format(Locale.getDefault(), "%.1f", x)

@Composable
private fun TodaySection(ins: Insights, data: FirewatchData, now: Long) = Col {
    val curve = ins.todayCurve()
    val w = ins.days.last()
    val typicalSlots = ins.typicalCurve()
    val dayStart = w.date.let { java.time.LocalDate.of(it.year, it.monthNumber, it.dayOfMonth) }
        .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val typical = typicalSlots.mapIndexed { i, v -> dayStart + i * 30 * 60_000L to v }
        .filter { curve.isNotEmpty() && it.first in curve.first().first..curve.last().first }
    ChartCard("Blood-level wave", "Each dose is a hill or spike. Sleep is shaded; the dashed line is your typical day.") {
        WaveChart(
            curve,
            typical = typical,
            shaded = listOf(curve.first().first to w.wakeAt, w.sleepAt to curve.last().first),
            now = now,
        )
        AxisLabels(listOf(Fmt.time(curve.first().first), Fmt.time(curve.last().first)))
    }
    ChartCard("Dose strip", "Today's doses across 24 hours, sized by amount and coloured by type.") {
        DoseStrip(w.doses.map { d ->
            val t = d.at.toLocalDateTime()
            Triple((t.hour * 60 + t.minute) / 1440f, d.pieces(data.referenceMg), kindColor(d.kind))
        })
    }
    ChartCard("Today so far") {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Stat("≈ ${Fmt.pieces(w.pieces)}", "pieces")
            Stat(fmt1(w.clearHours) + " h", "clear hours")
            Stat(Fmt.duration(((w.awakeHours * 60 - w.mouthMinutes).coerceAtLeast(0.0) * 60_000).toLong()), "mouth-free")
        }
    }
}

@Composable
private fun TrendsSection(ins: Insights) = Col {
    val days = ins.days.takeLast(42)
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
        )
        AxisLabels(listOf(Fmt.shortDateK(days.first()), Fmt.shortDateK(days.last())))
    }
    val stairs = ins.tierStaircase()
    if (stairs.size >= 2) {
        ChartCard("Tier staircase", "Your measured tier week by week. Stairs going down.") {
            TrendLine(stairs.map { it.second.pieces }, stepped = true)
            Text(stairs.joinToString(" → ") { it.second.tier.title }.takeLast(120), style = MaterialTheme.typography.bodySmall)
        }
    }
    val gaps = ins.weeklyGaps()
    if (gaps.size >= 2) {
        ChartCard("Gap between pieces", "Average time between doses, week by week. This one should climb.") {
            TrendLine(gaps.map { it.second }, color = MaterialTheme.colorScheme.tertiary)
            Text("Latest: ${Fmt.duration((gaps.last().second * 60_000).toLong())}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PatternsSection(ins: Insights) = Col {
    ChartCard("When it happens", "Hour of day across, Monday to Sunday down. Brighter = more.") { Heatmap(ins.heatmap()) }
    val ttf = ins.days.mapNotNull { it.wakeToFirstMin }
    if (ttf.size >= 2) {
        ChartCard("Wake to first piece", "Minutes from waking to the first dose. Longer is better.") {
            TrendLine(ttf, color = MaterialTheme.colorScheme.tertiary)
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
    val days = ins.fullDays.takeLast(42)
    ChartCard("Clear hours", "Hours each day your estimated level sat near zero while awake. Watch it rise.") {
        TrendLine(days.map { it.clearHours }, color = MaterialTheme.colorScheme.tertiary)
    }
    val overnight = ins.overnightGaps().takeLast(42)
    if (overnight.size >= 2) {
        ChartCard("Overnight gap", "Last dose at night to first the next morning.") {
            TrendLine(overnight.map { it.second / 60 }, color = MaterialTheme.colorScheme.tertiary)
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
            Stat(ins.averageCravingMinutes()?.let { "${it.roundToInt()} min" } ?: "–", "typical craving length")
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
    ChartCard("Mouth-free hours", "Awake time without a pouch or gum in.") {
        TrendLine(days.map { ((it.awakeHours * 60 - it.mouthMinutes) / 60).coerceAtLeast(0.0) }, color = MaterialTheme.colorScheme.tertiary)
    }
    val beaten = ins.beatenTriggers()
    ChartCard("Beaten triggers", "Of each trigger's last 10 appearances, how many passed without nicotine (from tagged cravings).") {
        if (beaten.isEmpty()) Text("Tag a craving (coffee, stress…) while riding it out to fill this in.", style = MaterialTheme.typography.bodySmall)
        beaten.forEach { (tag, won, total) -> BarRow(tag, won.toFloat() / total, "$won of $total without") }
    }
}

@Composable
private fun GoingDownSection(ins: Insights, data: FirewatchData, now: Long) = Col {
    val days = ins.fullDays.takeLast(42)
    ChartCard("Average dose size", "Absorbed mg per dose. Catches moves like 6 mg to 3 mg.") {
        TrendLine(days.map { if (it.doses.isEmpty()) 0.0 else it.absorbedMg / it.doses.size })
    }
    ChartCard("Spike share", "Share of nicotine arriving as fast spikes (vapes). Shrinking is real progress.") {
        TrendLine(days.map { if (it.absorbedMg > 0) it.spikeMg / it.absorbedMg * 100 else 0.0 })
    }
    val quality = days.map { it.quality ?: 100.0 }
    ChartCard("Nicotine quality", "How you use nicotine, on a food scale. Gum, lozenges and patches are broccoli; smoke is burger and fries. Switching method raises it; using a vape less doesn't.") {
        TrendLine(quality, color = MaterialTheme.colorScheme.tertiary)
        ins.days.last().quality?.let { Text("Today: ${Quality.label(it)}", style = MaterialTheme.typography.titleMedium) }
        Quality.swapTip(ins.days.last().doses, data.referenceMg)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Quality.Food.entries.forEach { f -> Text("${f.emoji} ${f.title}: ${f.min}+", style = MaterialTheme.typography.bodySmall) }
        Text("Per dose: gum, lozenge, patch 100 · pouch 60 · vape 25 · cigarette 5 (max 10). Minus points for a big single dose and for stacking doses.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    ChartCard("Heaviness score", "From how soon after waking you use and how much per day, the two things dependence tests weight most (0–6).") {
        val weekly = ins.fullDays.chunked(7).mapNotNull { ins.heaviness(it) }
        TrendLine(weekly)
        ins.heaviness()?.let { Text("Last 7 days: ${fmt1(it)} of 6", style = MaterialTheme.typography.bodyMedium) }
    }
    ChartCard("Background level", "A slow line modelled on cotinine, nicotine's breakdown product. It drifts down even through messy days.") {
        TrendLine(ins.backgroundSeries().takeLast(42).map { it.second })
    }
    ChartCard("Double-ups", "Doses stacked while the last one was still peaking, per week.") {
        TrendLine(ins.doubleUpsPerWeek().map { it.second.toDouble() })
    }
    ChartCard("Cravings vs doses", "Urges (teal) and doses (orange) per day. Ideally urges fade as doses drop.") {
        TrendLine(days.map { it.doses.size.toDouble() }, second = days.map { it.cravings.toDouble() }, secondColor = MaterialTheme.colorScheme.tertiary)
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
            val last = data.checkIns.takeLast(42)
            TrendLine(last.map { it.craving.toDouble() }, second = last.map { it.mood.toDouble() }, secondColor = MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
private fun MixSection(ins: Insights) = Col {
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
        TrendLine(ins.days.takeLast(42).map { it.labelMg }, second = ins.days.takeLast(42).map { it.absorbedMg })
    }
    val borrowed = ins.days.sumOf { it.borrowedPieces }
    val total = ins.days.sumOf { it.pieces }.coerceAtLeast(0.001)
    ChartCard("Borrowed share", "Nicotine from other people's products.") {
        Text("${(borrowed / total * 100).roundToInt()}% (≈ ${Fmt.pieces(borrowed)} pieces)", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ForecastSection(ins: Insights) = Col {
    ChartCard("Journey to Clear Air", "From your baseline to nicotine-free.") {
        val j = ins.journey()
        if (j == null) Text("Starts after your baseline week.", style = MaterialTheme.typography.bodySmall)
        else {
            Text("${(j * 100).roundToInt()}%", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            LinearProgressIndicator(progress = { j.toFloat() }, modifier = Modifier.fillMaxWidth())
        }
    }
    ChartCard("Taper speed", "Average drop per week over the last 4 weeks.") {
        Text(ins.taperPercentPerWeek()?.let { if (it >= 0) "${fmt1(it)}% lighter each week" else "${fmt1(-it)}% heavier each week lately; that's OK, it happens" } ?: "Needs a week or two more data.",
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
            TrendLine(nowC, second = then)
            AxisLabels(listOf("12am", "6am", "12pm", "6pm", "12am"))
        }
    }
}

@Composable
private fun MilestonesSection(ins: Insights, now: Long) = Col {
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

