package com.baastiklabs.firewatch.ui.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baastiklabs.firewatch.core.engine.Backfill
import com.baastiklabs.firewatch.core.engine.FriendVape
import com.baastiklabs.firewatch.core.localDate
import com.baastiklabs.firewatch.core.records.FirewatchData
import com.baastiklabs.firewatch.ui.FirewatchViewModel
import com.baastiklabs.firewatch.ui.Fmt
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDate

/**
 * The back-dated baseline week. A page per day (oldest first): tap each product as many times as
 * you used it that day, then Next. Rough is fine. Days that already have real logs are skipped.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BackfillScreen(vm: FirewatchViewModel, data: FirewatchData, onDone: () -> Unit, onCancel: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val tz = TimeZone.currentSystemDefault()
    val today = remember { System.currentTimeMillis().localDate(tz) }
    val firstReal = remember { data.doses.filter { !it.estimated }.minOfOrNull { it.at }?.localDate(tz) }
    val days = remember { Backfill.days(today).filter { firstReal == null || it < firstReal } }
    val products = remember(data.products) {
        data.products.filter { !it.archived }.sortedWith(compareBy({ !it.onHome }, { it.order }, { it.name }))
    }
    val entries = remember { mutableStateOf(days.associateWith { Backfill.DayEntry() }) }
    var index by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }

    if (days.isEmpty()) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onDone() }
        return
    }
    val day = days[index]
    val entry = entries.value.getValue(day)
    fun update(e: Backfill.DayEntry) {
        entries.value = entries.value + (day to e)
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp)) {
            Text("Estimate your last week", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(Fmt.dayTitle(day.toJavaLocalDate()), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(progress = { (index + 1f) / days.size }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            Text(
                "Tap each thing you used that day, once per use. Rough is fine. Hold to take one off.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                products.forEach { p ->
                    val n = entry.counts[p.id] ?: 0
                    CounterTile(
                        title = p.name,
                        count = n,
                        onTap = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            update(entry.copy(counts = entry.counts + (p.id to n + 1)))
                        },
                        onMinus = { update(entry.copy(counts = entry.counts + (p.id to (n - 1).coerceAtLeast(0)))) },
                    )
                }
                FriendVape.Amount.entries.forEach { a ->
                    val n = entry.vapes[a] ?: 0
                    CounterTile(
                        title = "Friend's vape · ${a.label.lowercase()}",
                        count = n,
                        onTap = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            update(entry.copy(vapes = entry.vapes + (a to n + 1)))
                        },
                        onMinus = { update(entry.copy(vapes = entry.vapes + (a to (n - 1).coerceAtLeast(0)))) },
                    )
                }
                if (index > 0) {
                    TextButton(onClick = { update(entries.value.getValue(days[index - 1])) }) { Text("Same as the day before") }
                }
                Text(
                    "Other products can be added later in Settings → Products.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { if (index == 0) onCancel() else index-- }) { Text(if (index == 0) "Cancel" else "Back") }
                Spacer(Modifier.weight(1f))
                Text("${index + 1} of ${days.size}", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Button(
                    enabled = !saving,
                    onClick = {
                        if (index < days.lastIndex) {
                            index++
                        } else {
                            saving = true
                            scope.launch {
                                val repo = vm.repository
                                val now = repo.now()
                                val doses = days.flatMap { d ->
                                    Backfill.doses(repo.data.value, d, entries.value.getValue(d), tz, now) { repo.newId() }
                                }
                                if (doses.isNotEmpty()) repo.logDoses(doses)
                                onDone()
                            }
                        }
                    },
                ) { Text(if (index < days.lastIndex) "Next" else "Finish") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CounterTile(title: String, count: Int, onTap: () -> Unit, onMinus: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = if (count > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().height(60.dp).clip(shape).combinedClickable(onClick = onTap, onLongClick = onMinus),
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (count > 0) OutlinedButton(onClick = onMinus) { Text("−") }
            Box(Modifier.padding(start = 12.dp)) {
                Text(if (count > 0) "× $count" else "+", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}
