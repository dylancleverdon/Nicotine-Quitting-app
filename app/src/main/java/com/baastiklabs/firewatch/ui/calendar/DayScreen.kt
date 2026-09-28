package com.baastiklabs.firewatch.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.baastiklabs.firewatch.core.CravingScale
import com.baastiklabs.firewatch.core.Cravings
import com.baastiklabs.firewatch.core.DaySummary
import com.baastiklabs.firewatch.core.Days
import com.baastiklabs.firewatch.core.model.Craving
import com.baastiklabs.firewatch.core.model.CravingOutcome
import com.baastiklabs.firewatch.core.model.Dose
import com.baastiklabs.firewatch.core.model.Product
import com.baastiklabs.firewatch.core.model.SleepEvent
import com.baastiklabs.firewatch.core.model.SleepKind
import com.baastiklabs.firewatch.core.records.FirewatchData
import com.baastiklabs.firewatch.core.toDose
import com.baastiklabs.firewatch.ui.FirewatchViewModel
import com.baastiklabs.firewatch.ui.Fmt
import com.baastiklabs.firewatch.ui.Stat
import com.baastiklabs.firewatch.ui.home.DoseDraft
import com.baastiklabs.firewatch.ui.home.DoseRow
import com.baastiklabs.firewatch.ui.home.DoseSheet
import com.baastiklabs.firewatch.ui.home.EditDoseSheet
import com.baastiklabs.firewatch.ui.toEpochMillis
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toKotlinLocalDate
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private sealed interface Entry {
    val at: Long

    data class DoseEntry(val dose: Dose) : Entry {
        override val at: Long get() = dose.at
    }

    data class CravingEntry(val craving: Craving) : Entry {
        override val at: Long get() = craving.at
    }

    data class SleepEntry(val event: SleepEvent) : Entry {
        override val at: Long get() = event.at
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(
    vm: FirewatchViewModel,
    data: FirewatchData,
    date: LocalDate,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    val tz = TimeZone.currentSystemDefault()
    val scope = rememberCoroutineScope()
    val kDate = date.toKotlinLocalDate()
    val now = System.currentTimeMillis()
    val summary = remember(data) { Days.summaries(data, tz, now)[kDate] ?: DaySummary(kDate) }
    val entries = remember(data) {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        (Days.dosesOn(data, kDate, tz).map { Entry.DoseEntry(it) } +
            Days.cravingsOn(data, kDate, tz).map { Entry.CravingEntry(it) } +
            data.sleepEvents.filter { it.at in start until end }.map { Entry.SleepEntry(it) })
            .sortedBy { it.at }
    }

    var editingDose by remember { mutableStateOf<Dose?>(null) }
    var editingCraving by remember { mutableStateOf<Craving?>(null) }
    var deletingSleep by remember { mutableStateOf<SleepEvent?>(null) }
    var pickingProduct by remember { mutableStateOf(false) }
    var addingFor by remember { mutableStateOf<Product?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(Fmt.dayTitle(date)) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    Stat("≈ ${Fmt.pieces(summary.pieces)}", "pieces", big = true)
                    Stat("≈ ${Fmt.mg(summary.absorbedMg)}", "absorbed", big = true)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    Stat("${summary.doseCount}", "doses")
                    Stat("${summary.cravingsRodeOut} of ${summary.cravings}", "cravings ridden out")
                }
            }
            if (entries.isEmpty()) {
                item { Text("Nothing logged this day.", style = MaterialTheme.typography.bodyMedium) }
            }
            items(entries, key = { entry ->
                when (entry) {
                    is Entry.DoseEntry -> "d-" + entry.dose.id
                    is Entry.CravingEntry -> "c-" + entry.craving.id
                    is Entry.SleepEntry -> "s-" + entry.event.id
                }
            }) { entry ->
                when (entry) {
                    is Entry.DoseEntry -> DoseRow(entry.dose, data.referenceMg, onClick = { editingDose = entry.dose })
                    is Entry.CravingEntry -> CravingRow(entry.craving, data, onClick = { editingCraving = entry.craving })
                    is Entry.SleepEntry -> ListItem(
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                        modifier = Modifier.clickable { deletingSleep = entry.event },
                        headlineContent = { Text(if (entry.event.kind == SleepKind.WAKE) "Woke up" else "Went to sleep") },
                        supportingContent = { Text(Fmt.time(entry.event.at)) },
                    )
                }
            }
            item {
                OutlinedButton(onClick = { pickingProduct = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("Add a dose I forgot to log")
                }
            }
        }
    }

    editingDose?.let { dose ->
        EditDoseSheet(vm, dose, data.referenceMg, snackbar, onDone = { editingDose = null })
    }

    if (pickingProduct) {
        val products = data.products.filter { !it.archived }.sortedWith(compareBy({ !it.onHome }, { it.order }, { it.name }))
        AlertDialog(
            onDismissRequest = { pickingProduct = false },
            title = { Text("Which product?") },
            text = {
                Column {
                    products.forEach { product ->
                        Text(
                            product.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    pickingProduct = false
                                    addingFor = product
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickingProduct = false }) { Text("Cancel") } },
        )
    }

    addingFor?.let { product ->
        val defaultTime = if (date == LocalDate.now()) LocalTime.now() else LocalTime.NOON
        DoseSheet(
            title = product.name,
            kind = product.kind,
            labelMg = product.labelMg,
            absorption = product.absorption,
            referenceMg = data.referenceMg,
            initial = DoseDraft(at = date.atTime(defaultTime).toEpochMillis()),
            relativeTime = false,
            saveLabel = "Add",
            onDismiss = { addingFor = null },
            onSave = { draft ->
                addingFor = null
                scope.launch {
                    val repo = vm.repository
                    repo.logDose(
                        product.toDose(
                            id = repo.newId(),
                            at = draft.at,
                            loggedAt = repo.now(),
                            multiplier = draft.multiplier,
                            duration = draft.duration,
                            acidicDrink = draft.acidicDrink,
                            tags = draft.tags,
                        ),
                    )
                }
            },
        )
    }

    editingCraving?.let { craving ->
        AlertDialog(
            onDismissRequest = { editingCraving = null },
            title = { Text("Craving · ${craving.intensity} ${CravingScale.level(craving.intensity).name}") },
            text = {
                Text(
                    "Started ${Fmt.time(craving.at)} · ${com.baastiklabs.firewatch.core.Cravings.result(data, craving, System.currentTimeMillis()).title.lowercase()}.\n\n" +
                        "Worked out from your logs: a piece within 45 minutes is linked to the craving.",
                )
            },
            confirmButton = { TextButton(onClick = { editingCraving = null }) { Text("OK") } },
            dismissButton = {
                TextButton(onClick = {
                    editingCraving = null
                    scope.launch {
                        vm.repository.deleteCraving(craving.id)
                        val result = snackbar.showSnackbar("Craving deleted", actionLabel = "Undo", duration = SnackbarDuration.Short)
                        if (result == SnackbarResult.ActionPerformed) vm.repository.saveCraving(craving)
                    }
                }) { Text("Delete") }
            },
        )
    }

    deletingSleep?.let { event ->
        AlertDialog(
            onDismissRequest = { deletingSleep = null },
            title = { Text("Remove this entry?") },
            text = { Text("${if (event.kind == SleepKind.WAKE) "Woke up" else "Went to sleep"} at ${Fmt.time(event.at)}") },
            confirmButton = {
                TextButton(onClick = {
                    deletingSleep = null
                    scope.launch { vm.repository.deleteSleep(event.id) }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { deletingSleep = null }) { Text("Keep") } },
        )
    }
}

@Composable
private fun CravingRow(craving: Craving, data: FirewatchData, onClick: () -> Unit) {
    val level = CravingScale.level(craving.intensity)
    val result = com.baastiklabs.firewatch.core.Cravings.result(data, craving, System.currentTimeMillis()).title.lowercase()
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text("Craving · ${craving.intensity} ${level.name}") },
        supportingContent = { Text("${Fmt.time(craving.at)} · $result") },
    )
}
