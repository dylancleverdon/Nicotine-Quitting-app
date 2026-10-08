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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baastiklabs.firewatch.core.Absorption
import com.baastiklabs.firewatch.core.model.DefaultProducts
import com.baastiklabs.firewatch.core.model.Duration
import com.baastiklabs.firewatch.core.model.ProductKind
import com.baastiklabs.firewatch.ui.Fmt
import com.baastiklabs.firewatch.ui.TimePickDialog
import com.baastiklabs.firewatch.ui.toEpochMillis
import com.baastiklabs.firewatch.ui.toLocalDateTime

val DoseTags = listOf("Coffee", "After food", "Driving", "Work", "Stress", "Drinking", "Boredom")

data class DoseDraft(
    val at: Long,
    val multiplier: Double = 1.0,
    val duration: Duration = Duration.FULL,
    val acidicDrink: Boolean = false,
    val tags: List<String> = emptyList(),
    /** Pouches: when it came out ("Took it out at…"); null = use [duration]. */
    val removedAt: Long? = null,
)

private val offsets = listOf(0 to "Now", 5 to "5 min ago", 10 to "10 min ago", 20 to "20 min ago", 30 to "30 min ago", 60 to "1 hour ago", 120 to "2 hours ago", 180 to "3 hours ago")
private val amounts = listOf(0.5 to "½", 1.0 to "1", 1.5 to "1½", 2.0 to "2")

/**
 * Options for one dose: when, how much, how long it stayed in, coffee/soda, what was going on.
 * [relativeTime] shows "now / N min ago" choices (logging from home); otherwise a fixed time.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DoseSheet(
    title: String,
    kind: ProductKind,
    labelMg: Double,
    absorption: Double,
    referenceMg: Double,
    initial: DoseDraft,
    relativeTime: Boolean,
    saveLabel: String,
    onDismiss: () -> Unit,
    onSave: (DoseDraft) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var offsetMinutes by remember { mutableIntStateOf(0) }
    var customAt by remember { mutableStateOf<Long?>(if (relativeTime) null else initial.at) }
    var multiplier by remember { mutableDoubleStateOf(initial.multiplier) }
    var duration by remember { mutableStateOf(initial.duration) }
    var acidic by remember { mutableStateOf(initial.acidicDrink) }
    var tags by remember { mutableStateOf(initial.tags.toSet()) }
    var pickTime by remember { mutableStateOf(false) }
    var removedAt by remember { mutableStateOf(initial.removedAt) }
    var pickOut by remember { mutableStateOf(false) }
    val baseAt = initial.at

    fun resolvedAtNow(): Long = customAt ?: (System.currentTimeMillis() - offsetMinutes * 60_000L)
    val inMouth = removedAt?.takeIf { kind == ProductKind.POUCH }?.let { out -> ((out - resolvedAtNow()) / 60_000.0).takeIf { it > 0 } }
    val absorbed = Absorption.absorbedMg(labelMg, absorption, kind, multiplier, duration, acidic, inMouthMinutes = inMouth)
    val pieces = Absorption.pieces(absorbed, referenceMg)

    fun resolvedAt(): Long = customAt ?: (System.currentTimeMillis() - offsetMinutes * 60_000L)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "${Fmt.piecesLabel(pieces)} · ≈ ${Fmt.mg(absorbed)} absorbed (est.)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Text("When", style = MaterialTheme.typography.titleSmall)
            if (relativeTime) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    offsets.forEach { (minutes, label) ->
                        FilterChip(
                            selected = customAt == null && offsetMinutes == minutes,
                            onClick = {
                                customAt = null
                                offsetMinutes = minutes
                            },
                            label = { Text(label) },
                        )
                    }
                    FilterChip(
                        selected = customAt != null,
                        onClick = { pickTime = true },
                        label = { Text(customAt?.let { "At ${Fmt.time(it)}" } ?: "Other time…") },
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(Fmt.dateTime(customAt ?: baseAt), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = { pickTime = true }) { Text("Change time") }
                }
            }

            Text("How much", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                amounts.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = multiplier == value,
                        onClick = { multiplier = value },
                        shape = SegmentedButtonDefaults.itemShape(index, amounts.size),
                    ) { Text(label) }
                }
            }

            if (DefaultProducts.isOral(kind)) {
                Text("How long it stayed in", style = MaterialTheme.typography.titleSmall)
                val durations = listOf(Duration.FULL to "Full", Duration.HALF to "About half", Duration.QUICK to "Quick")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    durations.forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = duration == value && inMouth == null,
                            onClick = { duration = value; removedAt = null },
                            shape = SegmentedButtonDefaults.itemShape(index, durations.size),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(label)
                                Text(
                                    when (value) { Duration.FULL -> "30+ min"; Duration.HALF -> "~15 min"; Duration.QUICK -> "~5 min" },
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
                if (kind == ProductKind.POUCH) {
                    FilterChip(
                        selected = inMouth != null,
                        onClick = { pickOut = true },
                        label = {
                            Text(inMouth?.let { "Took it out at ${Fmt.time(removedAt!!)} (${it.toInt()} min)" } ?: "Took it out at…")
                        },
                    )
                }
            }

            if (kind == ProductKind.GUM) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Coffee or soda around it", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Acidic drinks cut how much gum absorbs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = acidic, onCheckedChange = { acidic = it })
                }
            }

            Text("What was going on (optional)", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DoseTags.forEach { tag ->
                    FilterChip(
                        selected = tag in tags,
                        onClick = { tags = if (tag in tags) tags - tag else tags + tag },
                        label = { Text(tag) },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (onDelete != null) {
                    OutlinedButton(onClick = onDelete) { Text("Delete") }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    val at = resolvedAt()
                    onSave(DoseDraft(at, multiplier, duration, acidic, DoseTags.filter { it in tags }, removedAt?.takeIf { kind == ProductKind.POUCH && it > at }))
                }) { Text(saveLabel) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (pickOut) {
        val at = resolvedAt()
        val start = (removedAt ?: (at + 30 * 60_000L)).toLocalDateTime()
        TimePickDialog(
            title = "When did it come out?",
            initial = start.toLocalTime(),
            onDismiss = { pickOut = false },
            onPick = { time ->
                var picked = at.toLocalDateTime().toLocalDate().atTime(time)
                // Out before it went in means just after midnight.
                if (picked.toEpochMillis() <= at) picked = picked.plusDays(1)
                removedAt = picked.toEpochMillis()
                pickOut = false
            },
        )
    }

    if (pickTime) {
        val start = (customAt ?: resolvedAt()).toLocalDateTime()
        TimePickDialog(
            title = "When was it?",
            initial = start.toLocalTime(),
            onDismiss = { pickTime = false },
            onPick = { time ->
                var picked = start.toLocalDate().atTime(time)
                // From home, a time later than now means last night.
                if (relativeTime && picked.toEpochMillis() > System.currentTimeMillis()) picked = picked.minusDays(1)
                customAt = picked.toEpochMillis()
                pickTime = false
            },
        )
    }
}
