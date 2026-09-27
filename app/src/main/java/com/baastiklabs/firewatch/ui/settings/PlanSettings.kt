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
                "Stepping back up after a rough stretch is normal, not failure. Step-downs are offered on the home screen when you're ready; the target never changes on its own.",
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
        Text("Morning delay goal (first piece after waking)", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "Off", 15 to "15 min", 30 to "30 min", 60 to "1 hour", 90 to "1½ hours").forEach { (m, label) ->
                FilterChip(selected = s.morningDelayMinutes == m, onClick = { update { it.copy(morningDelayMinutes = m) } }, label = { Text(label) })
            }
        }
        ToggleRow("Wind down before bed", "No \"clear for one\" in the last hour before bed", s.windDown) { v -> update { it.copy(windDown = v) } }
        ToggleRow("Daily check-in", "Three quick taps: craving strength, mood and sleep", s.dailyCheckIn) { v -> update { it.copy(dailyCheckIn = v) } }
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
        "Neutral and off by default. Firewatch never sends \"time for your next piece\".",
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
