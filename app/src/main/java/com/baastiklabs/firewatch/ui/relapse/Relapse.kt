package com.baastiklabs.firewatch.ui.relapse

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baastiklabs.firewatch.ui.FirewatchViewModel
import com.baastiklabs.firewatch.ui.Fmt
import kotlinx.coroutines.launch

/**
 * Turns Relapse prevention mode on or off. Turning it on asks for notification permission once
 * (Android 13+); the mode turns on either way, and the home screen still shows the next time.
 */
@Composable
fun rememberRelapseSwitch(vm: FirewatchViewModel, onChanged: (Boolean) -> Unit = {}): (Boolean) -> Unit {
    val scope = rememberCoroutineScope()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        scope.launch { vm.repository.setRelapse(true); onChanged(true) }
    }
    return { on ->
        if (on && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else scope.launch { vm.repository.setRelapse(on); onChanged(on) }
    }
}

/** The explanation shown before turning the mode on (or off). */
@Composable
fun RelapseDialog(on: Boolean, onDismiss: () -> Unit, onSwitch: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Relapse prevention mode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("What it is: a reminder to chew a piece at a steady gap: your tier's gap, or every 2 hours before you have a tier. Never during sleeping hours.")
                Text("Why: early on, staying ahead of cravings makes going back to smoking or vaping much less likely. It sounds backwards for an app about cutting down, and that's intentional for now. Your tiers and figures stay just as honest.")
                Text("Turning it off: this same button, any time.")
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onSwitch(!on) }) { Text(if (on) "Turn off" else "Turn on") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (on) "Keep it on" else "Not now") } },
    )
}

/** The small "mode is on" indicator on the home screen. */
@Composable
fun RelapseIndicator(nextAt: Long?, productName: String?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Relapse prevention mode is on", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            val line = listOfNotNull(nextAt?.let { "Next scheduled piece at ${Fmt.time(it)}" }, productName).joinToString(" · ")
            if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodySmall)
        }
    }
}
