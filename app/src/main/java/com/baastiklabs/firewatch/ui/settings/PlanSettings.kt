package com.baastiklabs.firewatch.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.baastiklabs.firewatch.core.engine.Ladder
import com.baastiklabs.firewatch.core.model.Settings
import com.baastiklabs.firewatch.core.records.FirewatchData
import com.baastiklabs.firewatch.ui.FirewatchViewModel
import com.baastiklabs.firewatch.ui.SectionTitle
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanSettings(vm: FirewatchViewModel, data: FirewatchData) {
    val scope = rememberCoroutineScope()
    val s = data.settings
    fun update(t: (Settings) -> Settings) = scope.launch { vm.repository.updateSettings(t) }

    SectionTitle("Your plan")
    val target = data.targetPieces
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            target?.let { "Working at ${Ladder.rung(it).label}" } ?: "Your target appears after the baseline week.",
            style = MaterialTheme.typography.bodyLarge,
        )
        if (target != null) {
            OutlinedButton(onClick = { scope.launch { vm.repository.setTarget(Ladder.nextUp(target).pieces, "up") } }) {
                Text("Step back up a rung (${Ladder.nextUp(target).label})")
            }
            Text(
                "Stepping up makes your level more accurate. Step-downs are offered when you're ready; the target never changes on its own.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("Hold each rung for", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(3 to "3 days", 7 to "1 week", 14 to "2 weeks", 21 to "3 weeks").forEach { (d, label) ->
                FilterChip(selected = s.holdDays == d, onClick = { update { it.copy(holdDays = d) } }, label = { Text(label) })
            }
        }
        if (s.holdDays < 7) Text(
            com.baastiklabs.firewatch.core.Help.HOLD_SHORT_NOTE,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (target != null && target > 0) PracticePaceSettings(vm, data)
        Text("First-piece goal", style = MaterialTheme.typography.titleSmall)
        var customDelay by remember { mutableStateOf(false) }
        val isPreset = s.morningDelayClock < 0 && s.morningDelayMinutes in listOf(0, 15, 30, 60, 90)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "Off", 15 to "15 min", 30 to "30 min", 60 to "1 hour", 90 to "1½ hours").forEach { (m, label) ->
                FilterChip(
                    selected = s.morningDelayClock < 0 && s.morningDelayMinutes == m,
                    onClick = { update { it.copy(morningDelayMinutes = m, morningDelayClock = -1) } },
                    label = { Text(if (m == 0) label else "$label after waking") },
                )
            }
            FilterChip(
                selected = !isPreset,
                onClick = { customDelay = true },
                label = {
                    Text(
                        when {
                            s.morningDelayClock >= 0 -> "At ${com.baastiklabs.firewatch.ui.Fmt.minutesOfDay(s.morningDelayClock)}"
                            !isPreset -> "${s.morningDelayMinutes / 60}h ${s.morningDelayMinutes % 60}m after waking"
                            else -> "Custom…"
                        },
                    )
                },
            )
        }
        if (customDelay) CustomDelayDialog(s, onDismiss = { customDelay = false }) { minutes, clock ->
            customDelay = false
            update { it.copy(morningDelayMinutes = minutes, morningDelayClock = clock) }
        }
        Text("Time format", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("system" to "Phone setting", "12h" to "12-hour (AM/PM)", "24h" to "24-hour").forEach { (v, label) ->
                FilterChip(selected = s.timeFormat == v, onClick = { update { it.copy(timeFormat = v) } }, label = { Text(label) })
            }
        }
        ToggleRow(
            "Hide next piece timer",
            "The time shows only when you tap it, so Firewatch can learn how often you check",
            s.hideTimer,
        ) { v -> update { it.copy(hideTimer = v) } }
        ToggleRow("Wind down before bed", "A bedtime note in the last hour before bed", s.windDown) { v -> update { it.copy(windDown = v) } }
        ToggleRow("Show steady days", "\"✓ N steady days\" on the home card", s.showSteadyDays) { v -> update { it.copy(showSteadyDays = v) } }
        ToggleRow("Coaching tips", "Practical tips based on your own logs. Off means Firewatch just measures.", s.coachingTips) { v -> update { it.copy(coachingTips = v) } }
        ToggleRow("Hide stretch and pull on doses", "The \"+38m pull\" line on each product button", s.hideDosePreview) { v -> update { it.copy(hideDosePreview = v) } }
        ToggleRow("Detailed charts", "Range choices and earlier/later on Insights charts", s.detailedCharts) { v -> update { it.copy(detailedCharts = v) } }
        ToggleRow("Daily check-in", "Three quick taps: craving strength, mood and sleep", s.dailyCheckIn) { v -> update { it.copy(dailyCheckIn = v) } }
    }

    SectionTitle("Relapse prevention mode")
    val switchRelapse = com.baastiklabs.firewatch.ui.relapse.rememberRelapseSwitch(vm)
    Text(
        "A reminder to chew at your tier's gap (every 2 hours before you have a tier), to stay ahead of cravings. Never during sleeping hours. Off unless you turn it on.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val gap = com.baastiklabs.firewatch.core.engine.Relapse.gapMinutes(data, System.currentTimeMillis())
    ToggleRow(
        "Relapse prevention mode",
        if (data.relapseOn) "On · every ${com.baastiklabs.firewatch.ui.Fmt.duration((gap * 60_000).toLong())}" else "Off",
        data.relapseOn,
    ) { v -> switchRelapse(v) }
    Text("Reminds me about", style = MaterialTheme.typography.titleSmall)
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val current = com.baastiklabs.firewatch.core.engine.Relapse.product(data)?.id
        data.products.filter { !it.archived && it.borrowedFrom == null }.sortedWith(compareBy({ it.order }, { it.name })).forEach { p ->
            FilterChip(selected = current == p.id, onClick = { update { it.copy(relapseProductId = p.id) } }, label = { Text(p.name) })
        }
    }

    SectionTitle("Reminders (optional)")
    val context = androidx.compose.ui.platform.LocalContext.current
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }
    fun remind(t: (Settings) -> Settings) {
        if (android.os.Build.VERSION.SDK_INT >= 33) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        scope.launch {
            vm.repository.updateSettings(t)
            com.baastiklabs.firewatch.reminders.Reminders.sync(context, vm.repository.data.value.settings)
        }
    }
    Text(
        "Neutral and off by default. Apart from Relapse prevention mode, Firewatch never sends \"time for your next piece\".",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ToggleRow("Check-in reminder", "An evening nudge if you haven't checked in", s.remindCheckIn) { v -> remind { it.copy(remindCheckIn = v, dailyCheckIn = it.dailyCheckIn || v) } }
    ToggleRow("Backup reminder", "Weekly, only if no copy of your data has left the phone", s.remindBackup) { v -> remind { it.copy(remindBackup = v) } }

    SectionTitle("Money")
    var currency by remember(s.currency) { mutableStateOf(s.currency) }
    var reward by remember(s.rewardName) { mutableStateOf(s.rewardName) }
    var cost by remember(s.rewardCost) { mutableStateOf(if (s.rewardCost > 0) s.rewardCost.toString() else "") }
    Text(
        "Set what each product costs in Products. Money saved compares with your baseline week.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(currency, { currency = it.take(3); update { s2 -> s2.copy(currency = it.take(3)) } }, label = { Text("Currency") }, singleLine = true, modifier = Modifier.weight(0.3f))
        OutlinedTextField(reward, { reward = it; update { s2 -> s2.copy(rewardName = it) } }, label = { Text("Saving toward") }, singleLine = true, modifier = Modifier.weight(0.7f))
    }
    OutlinedTextField(
        cost,
        { v -> cost = v; v.replace(',', '.').toDoubleOrNull()?.let { c -> update { it.copy(rewardCost = c) } } },
        label = { Text("It costs") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ToggleRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Custom first-piece goal: a set time after waking, or a time of day. */
@Composable
private fun CustomDelayDialog(s: Settings, onDismiss: () -> Unit, onSave: (minutes: Int, clock: Int) -> Unit) {
    var byClock by remember { mutableStateOf(s.morningDelayClock >= 0) }
    var hours by remember { mutableStateOf(if (s.morningDelayClock < 0 && s.morningDelayMinutes > 0) s.morningDelayMinutes / 60.0 else 3.0) }
    var clock by remember { mutableStateOf(if (s.morningDelayClock >= 0) s.morningDelayClock else 11 * 60) }
    var pickTime by remember { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("First-piece goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !byClock, onClick = { byClock = false }, label = { Text("Time after waking") })
                    FilterChip(selected = byClock, onClick = { byClock = true }, label = { Text("Time of day") })
                }
                if (byClock) {
                    OutlinedButton(onClick = { pickTime = true }) { Text("At ${com.baastiklabs.firewatch.ui.Fmt.minutesOfDay(clock)}") }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { hours = (hours - 0.5).coerceAtLeast(0.5) }) { Text("−") }
                        Text("${if (hours % 1.0 == 0.0) hours.toInt().toString() else hours.toString()} h after waking", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = { hours = (hours + 0.5).coerceAtMost(12.0) }) { Text("+") }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                if (byClock) onSave(0, clock) else onSave((hours * 60).toInt(), -1)
            }) { Text("Save") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (pickTime) {
        com.baastiklabs.firewatch.ui.TimePickDialog(
            title = "First piece at",
            initial = java.time.LocalTime.of(clock / 60, clock % 60),
            onDismiss = { pickTime = false },
            onPick = { t -> clock = t.hour * 60 + t.minute; pickTime = false },
        )
    }
}

/**
 * Settings → Your plan → Practice pace: choose a rung and start, "Back to my pace", the duration
 * choice (changeable any time, even while it's on), and the lighter-level offers switch.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PracticePaceSettings(vm: FirewatchViewModel, data: FirewatchData) {
    val scope = rememberCoroutineScope()
    val repo = vm.repository
    val tz = kotlinx.datetime.TimeZone.currentSystemDefault()
    val now = System.currentTimeMillis()
    val practice = com.baastiklabs.firewatch.core.engine.Practice
    val active = practice.active(data, now, tz)
    val allowed = remember(data) { practice.allowedRungs(data, now, tz) }
    var choice by remember(data) { mutableStateOf(active?.pieces ?: allowed.firstOrNull()?.pieces) }
    val untilBedtime = active?.untilBedtime ?: data.settings.practiceUntilBedtime
    Text("Practice pace", style = MaterialTheme.typography.titleSmall)
    Text(
        "Try a lighter pace without changing your level. The battery and dose preview use its gap; your level, net and steady days don't change.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (data.relapseOn) {
        Text(com.baastiklabs.firewatch.core.Help.PRACTICE_RELAPSE_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else if (active != null) {
        Text("On · ${Ladder.rung(active.pieces).label}", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { scope.launch { repo.stopPractice() } }) { Text("Back to my pace") }
    } else if (allowed.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            allowed.forEach { r -> FilterChip(selected = choice == r.pieces, onClick = { choice = r.pieces }, label = { Text(r.label) }) }
        }
        OutlinedButton(onClick = { choice?.let { p -> scope.launch { repo.startPractice(p, untilBedtime) } } }, enabled = choice != null) { Text("Start") }
    }
    if (!data.relapseOn) com.baastiklabs.firewatch.ui.practice.DurationChoice(untilBedtime) { v -> scope.launch { repo.setPracticeUntilBedtime(v) } }
    ToggleRow(
        "Lighter-level practice offers",
        "When your logs measure 2 or more rungs lighter, offer to practice that pace",
        data.settings.lighterOffers,
    ) { v -> scope.launch { repo.updateSettings { it.copy(lighterOffers = v) } } }
}
