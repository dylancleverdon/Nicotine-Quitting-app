package com.baastiklabs.firewatch.ui.practice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baastiklabs.firewatch.core.Help
import com.baastiklabs.firewatch.core.engine.Practice
import com.baastiklabs.firewatch.ui.Fmt

/** "Turn off at bedtime" or "Leave it on until I turn it off". */
@Composable
fun DurationChoice(untilBedtime: Boolean, onChange: (Boolean) -> Unit) {
    Column {
        listOf(true to "Turn off at bedtime (just today)", false to "Leave it on until I turn it off").forEach { (v, label) ->
            Row(
                Modifier.fillMaxWidth().clickable { onChange(v) }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = untilBedtime == v, onClick = { onChange(v) })
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * The second screen after accepting a practice offer (kept separate so the offer card isn't
 * crowded): how long practice pace should run.
 */
@Composable
fun PracticeDurationDialog(rungTitle: String, initialUntilBedtime: Boolean, onStart: (Boolean) -> Unit, onDismiss: () -> Unit) {
    var untilBedtime by remember { mutableStateOf(initialUntilBedtime) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How long should practice pace run?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$rungTitle pace", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                DurationChoice(untilBedtime) { untilBedtime = it }
                Text(Help.PRACTICE_STOP_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onStart(untilBedtime) }) { Text("Start") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** "Practice pace · Campfire 4" with a "?", and practice net for the run. */
@Composable
fun PracticeStatusRow(status: Practice.Status) {
    var help by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Practice pace · ${status.rung.tier.title} ${com.baastiklabs.firewatch.core.engine.Ladder.piecesText(status.rung.pieces)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f, fill = false),
            )
            OutlinedButton(onClick = { help = !help }, modifier = Modifier.padding(start = 8.dp)) { Text("?") }
        }
        Text(
            "Practice net ${Fmt.signedMinutes(status.netMin)}" + if (status.day > 1) " · day ${status.day}" else "",
            style = MaterialTheme.typography.labelMedium,
            color = if (status.netMin >= 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
        )
        if (help) Text(Help.PRACTICE_EXPLAINER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
