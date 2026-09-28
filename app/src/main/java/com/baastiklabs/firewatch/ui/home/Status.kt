package com.baastiklabs.firewatch.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baastiklabs.firewatch.core.DaySummary
import com.baastiklabs.firewatch.core.engine.Battery
import com.baastiklabs.firewatch.core.engine.BatteryState
import com.baastiklabs.firewatch.core.engine.HeadsUp
import com.baastiklabs.firewatch.core.engine.Ladder
import com.baastiklabs.firewatch.core.engine.Rung
import com.baastiklabs.firewatch.ui.Fmt
import com.baastiklabs.firewatch.ui.Stat
import com.baastiklabs.firewatch.ui.charts.WaveChart

/** The status card once tiers are revealed. */
@Composable
fun TierStatusCard(
    measured: Rung?,
    target: Rung?,
    battery: Battery?,
    today: DaySummary,
    lastDoseAt: Long?,
    now: Long,
    wave: List<Pair<Long, Double>>,
    sleepShade: List<Pair<Long, Long>>,
    quality: Double?,
    fitsNow: String? = null,
    nowDoses: List<com.baastiklabs.firewatch.core.model.Dose> = emptyList(),
    relapseNext: Long? = null,
    timerHidden: Boolean = false,
    onReveal: () -> Unit = {},
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val shown = target ?: measured
            if (shown != null) {
                Text(shown.tier.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(shown.plainLine, style = MaterialTheme.typography.bodyMedium)
                if (target != null && measured != null && measured.pieces != target.pieces) {
                    Text(
                        "Working at ${target.label}. Your last 7 days measure ${measured.label}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Progress toward the next rung down, from the measured pace.
                if (measured != null && shown.pieces > 0) {
                    val next = Ladder.nextDown(shown.pieces)
                    val span = (shown.pieces - next.pieces).coerceAtLeast(0.01)
                    val progress = ((shown.pieces - measured.pieces.coerceAtMost(shown.pieces)) / span).toFloat().coerceIn(0f, 1f)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text("Next rung down: ${next.label}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            battery?.let {
                if (timerHidden) HiddenBatteryRow(it, onReveal) else BatteryRow(it, now, fitsNow, relapseNext)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Stat("≈ ${Fmt.pieces(today.pieces)}", "pieces today", big = true)
                Stat("≈ ${Fmt.mg(today.absorbedMg)}", "absorbed today", big = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Stat("${today.doseCount}", if (today.doseCount == 1) "dose" else "doses")
                Stat(lastDoseAt?.let { Fmt.duration(now - it) } ?: "–", "since last")
                Stat("${today.cravingsRodeOut}/${today.cravings}", "urges beaten")
                quality?.let {
                    val food = com.baastiklabs.firewatch.core.engine.Quality.food(it)
                    Stat("${food.emoji} ${it.toInt()}", "quality: ${food.title.lowercase()}")
                }
            }
            if (wave.size > 2) {
                WaveChart(wave, shaded = sleepShade, now = now, height = 64.dp, compact = true)
                Text("Estimated nicotine in your system today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val inBody = remember(wave, now) { com.baastiklabs.firewatch.core.engine.Kinetics.level(nowDoses, now) }
                Text(
                    "≈ ${Fmt.mg(inBody)} in your system now",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun BatteryRow(b: Battery, now: Long, fitsNow: String?, relapseNext: Long?) {
    val (title, detail) = if (relapseNext != null) "Next scheduled piece at ${Fmt.time(relapseNext)}" to "Relapse prevention mode: staying ahead of cravings" else when (b.state) {
        BatteryState.CLEAR -> "Clear for one if you want it" to "No rush. Every minute you wait counts as stretch."
        BatteryState.CHARGING -> "Next piece around ${b.readyAt?.let { Fmt.time(it) } ?: "later"}" to
            "${b.readyAt?.let { Fmt.duration(it - now) } ?: ""} to go at your target pace"
        BatteryState.FULL_AT_WAKE -> "Full when you wake up" to "No waiting overnight. Fresh start in the morning."
        BatteryState.MORNING_DELAY -> "First piece goal: ${b.readyAt?.let { Fmt.time(it) } ?: ""}" to "Pushing the first piece later is one of the best signs of progress"
        BatteryState.WIND_DOWN -> "Winding down for bed" to "Nicotine is a stimulant; late doses can disrupt sleep"
        BatteryState.ASLEEP -> "Sleeping hours" to "Fresh start when you wake up"
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        LinearProgressIndicator(progress = { b.charge.coerceIn(0.0, 1.0).toFloat() }, modifier = Modifier.fillMaxWidth())
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (b.closeToBed) Text("Close to bedtime: nicotine can make it harder to fall asleep.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        fitsNow?.let { Text("A $it fits now", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (relapseNext == null) StretchLine(b.stretchMinutesToday, b.pullMinutesToday)
    }
}

/** "Hide next piece timer": the bar only, until a tap reveals the time (and counts a check). */
@Composable
private fun HiddenBatteryRow(b: Battery, onReveal: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onReveal).padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Tap to see your next piece time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        LinearProgressIndicator(progress = { b.charge.coerceIn(0.0, 1.0).toFloat() }, modifier = Modifier.fillMaxWidth())
    }
}

/** "Stretch 2h 10m · Pull 45m · Net +1h 25m". A negative net is grey, never red. */
@Composable
fun StretchLine(stretchMin: Double, pullMin: Double) {
    val net = stretchMin - pullMin
    val sign = if (net >= 0) "+" else "−"
    Text(
        "Stretch ${Fmt.duration((stretchMin * 60_000).toLong())} · Pull ${Fmt.duration((pullMin * 60_000).toLong())} · " +
            "Net $sign${Fmt.duration((kotlin.math.abs(net) * 60_000).toLong())}",
        style = MaterialTheme.typography.labelMedium,
        color = if (net >= 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
    )
}

/** A prompt card with a primary and a secondary action. */
@Composable
fun OfferCard(title: String, body: String, primary: String, onPrimary: () -> Unit, secondary: String?, onSecondary: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onPrimary) { Text(primary) }
                if (secondary != null) OutlinedButton(onClick = onSecondary) { Text(secondary) }
            }
        }
    }
}

@Composable
fun HeadsUpCard(items: List<HeadsUp>) {
    if (items.isEmpty()) return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Heads-up", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            items.forEach { Text(it.message, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** Optional daily check-in: craving strength, mood and sleep, three taps. */
@Composable
fun CheckInDialog(onDismiss: () -> Unit, onSave: (craving: Int, mood: Int, sleep: Int) -> Unit) {
    var craving by remember { mutableIntStateOf(3) }
    var mood by remember { mutableIntStateOf(3) }
    var sleep by remember { mutableIntStateOf(3) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily check-in") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ScaleRow("Cravings today", listOf("None", "Mild", "Some", "Strong", "Intense"), craving) { craving = it }
                ScaleRow("Mood", listOf("Awful", "Low", "OK", "Good", "Great"), mood) { mood = it }
                ScaleRow("Last night's sleep", listOf("Awful", "Poor", "OK", "Good", "Great"), sleep) { sleep = it }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(craving, mood, sleep) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Skip") } },
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ScaleRow(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEachIndexed { i, o ->
                FilterChip(selected = selected == i + 1, onClick = { onSelect(i + 1) }, label = { Text(o) })
            }
        }
    }
}
