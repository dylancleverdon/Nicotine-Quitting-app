package com.baastiklabs.firewatch.ui.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.baastiklabs.firewatch.core.Feedback
import com.baastiklabs.firewatch.data.FeedbackSender

/** "Suggest something / report a bug": Idea / Bug / Other, the text, optional details and name. */
@Composable
fun FeedbackDialog(onDismiss: () -> Unit, onSend: (type: String, suggestion: String, details: String, name: String) -> Unit) {
    val context = LocalContext.current
    var type by remember { mutableStateOf("Idea") }
    var text by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(FeedbackSender.savedName(context)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Suggest something") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Feedback.types.forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) }) }
                }
                OutlinedTextField(
                    text, { text = it },
                    label = { Text(if (type == "Bug") "What went wrong?" else "Your suggestion") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                )
                OutlinedTextField(
                    details, { details = it },
                    label = { Text(if (type == "Bug") "What were you doing? (optional)" else "Details (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(name, { name = it }, label = { Text("Your name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(Feedback.PRIVACY_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = {
                FeedbackSender.saveName(context, name)
                onSend(type, text, details, name)
            }) { Text("Send") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
