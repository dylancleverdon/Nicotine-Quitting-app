package com.baastiklabs.firewatch.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baastiklabs.firewatch.core.engine.FriendVape
import com.baastiklabs.firewatch.ui.Fmt

/** What gets logged for an unknown dose. */
data class VapeLog(val lowMg: Double, val highMg: Double, val strength: Double?, val what: String, val saveAs: String?)

/**
 * A friend's vape in three taps: strength (or "no idea"), then how much. Logged as a range.
 * [presetStrength] is set when logging a saved friend's vape (strength already known).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FriendVapeSheet(
    title: String,
    presetStrength: Double?,
    referenceMg: Double,
    initialPuffs: Int = 0,
    onDismiss: () -> Unit,
    onLog: (VapeLog) -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var strength by remember { mutableStateOf(presetStrength) }
    var strengthChosen by remember { mutableStateOf(presetStrength != null || initialPuffs > 0) }
    var amount by remember { mutableStateOf<FriendVape.Amount?>(null) }
    var puffs by remember { mutableIntStateOf(initialPuffs) }
    var name by remember { mutableStateOf("") }
    var save by remember { mutableStateOf(false) }

    val range = when {
        puffs > 0 -> FriendVape.range(strength, puffs..puffs)
        amount != null -> FriendVape.range(strength, amount!!.puffs)
        else -> null
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "Unknown doses are logged as a range. Timing uses the top of the range, so it never says you're clear too soon.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (presetStrength == null) {
                Text("1. Strength (on the device or box)", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FriendVape.strengths.forEach { (value, label) ->
                        FilterChip(
                            selected = strengthChosen && strength == value,
                            onClick = { strength = value; strengthChosen = true },
                            label = { Text(label) },
                        )
                    }
                }
            }
            Text("2. How much", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FriendVape.Amount.entries.forEach { a ->
                    FilterChip(selected = puffs == 0 && amount == a, onClick = { amount = a; puffs = 0 }, label = { Text(a.label) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Or count puffs:", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { puffs = (puffs - 1).coerceAtLeast(0) }) { Text("−") }
                Text("$puffs", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { puffs++; amount = null }) { Text("+1 puff") }
            }
            range?.let { (lo, hi) ->
                Text(
                    "Between ${Fmt.pieces(lo / referenceMg)} and ${Fmt.pieces(hi / referenceMg)} pieces (est.)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (presetStrength == null && strength != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = save, onCheckedChange = { save = it })
                    Text("Save it for next time", style = MaterialTheme.typography.bodyMedium)
                }
                if (save) OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Friend's name") }, singleLine = true)
            }
            Button(
                enabled = range != null && (strengthChosen || presetStrength != null),
                onClick = {
                    val (lo, hi) = range ?: return@Button
                    val what = if (puffs > 0) "$puffs puffs" else amount?.label ?: ""
                    onLog(VapeLog(lo, hi, strength, what, if (save && name.isNotBlank()) name.trim() else null))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Log it") }
            Spacer(Modifier.height(16.dp))
        }
    }
}
