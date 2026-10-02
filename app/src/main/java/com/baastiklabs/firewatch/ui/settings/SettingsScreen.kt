package com.baastiklabs.firewatch.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.baastiklabs.firewatch.BuildConfig
import com.baastiklabs.firewatch.R
import com.baastiklabs.firewatch.core.Branding
import com.baastiklabs.firewatch.core.backup.BackupFile
import com.baastiklabs.firewatch.core.backup.Backups
import com.baastiklabs.firewatch.core.records.FirewatchData
import com.baastiklabs.firewatch.core.update.Changelog
import com.baastiklabs.firewatch.core.update.UpdatePolicy
import com.baastiklabs.firewatch.data.BackupStore
import com.baastiklabs.firewatch.ui.ChangelogDialog
import com.baastiklabs.firewatch.ui.EstimateNote
import com.baastiklabs.firewatch.ui.FirewatchViewModel
import com.baastiklabs.firewatch.ui.Fmt
import com.baastiklabs.firewatch.ui.LabeledRow
import com.baastiklabs.firewatch.ui.SectionTitle
import com.baastiklabs.firewatch.ui.TimePickDialog
import com.baastiklabs.firewatch.ui.readChangelog
import com.baastiklabs.firewatch.update.SelfInstaller
import com.baastiklabs.firewatch.update.UpdateScheduler
import com.baastiklabs.firewatch.update.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun SettingsScreen(
    vm: FirewatchViewModel,
    data: FirewatchData,
    update: UpdateState.Snapshot,
    now: Long,
    snackbar: SnackbarHostState,
    onOpenProducts: () -> Unit,
    onBackfill: () -> Unit = {},
    onOpenAbout: () -> Unit,
    onOpenHelp: () -> Unit = {},
    onOpenThemes: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var canInstall by remember { mutableStateOf(SelfInstaller.canRequestInstalls(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canInstall = SelfInstaller.canRequestInstalls(context) }

    var showChangelog by remember { mutableStateOf(false) }
    var confirmRollback by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<BackupFile?>(null) }
    var showBackups by remember { mutableStateOf(false) }
    var pickReference by remember { mutableStateOf(false) }
    var refChoice by remember { mutableStateOf<String?>(null) }
    var pickWake by remember { mutableStateOf(false) }
    var pickSleep by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    val file = Backups.build(vm.repository.allRecords(), vm.repository.now(), BuildConfig.VERSION_NAME, "export")
                    context.contentResolver.openOutputStream(uri)?.use { it.write(Backups.encode(file).toByteArray()) }
                        ?: error("Couldn't open file")
                }
            }.isSuccess
            if (ok) BackupStore.markOffPhone(context)
            snackbar.showSnackbar(if (ok) "Exported. Keep that file somewhere safe." else "Export didn't work. Try another location.")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                }.getOrNull()?.let { Backups.decode(it) }
            }
            if (file == null) snackbar.showSnackbar("That file isn't a Firewatch backup.") else pendingImport = file
        }
    }

    val rollback = update.manifest?.let { UpdatePolicy.rollbackTarget(BuildConfig.VERSION_CODE, it) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        // --- Updates ---
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Updates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Last checked: " + if (update.lastCheckAt == 0L) "not yet" else Fmt.ago(update.lastCheckAt, now),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(update.status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                update.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Text(
                    "Firewatch finds and installs new versions by itself. Your data is backed up before every update.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        UpdateScheduler.checkNow(context)
                        scope.launch { snackbar.showSnackbar("Checking for updates…") }
                    }) { Text("Check now") }
                    TextButton(onClick = { showChangelog = true }) { Text("What's new") }
                }
                if (update.pendingInstall) {
                    Button(onClick = { UpdateScheduler.checkNow(context, installNow = true) }) {
                        Text("Install the update now")
                    }
                }
                if (rollback != null) {
                    OutlinedButton(onClick = { confirmRollback = true }) {
                        Text("Go back to version ${rollback.versionName}")
                    }
                }
            }
        }

        if (!canInstall || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!canInstall) {
                        Text("Firewatch can't update itself yet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Tap the button, switch on \"Allow from this source\", then come back. You only do this once.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = {
                            context.startActivity(
                                Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
                            )
                        }) { Text("Allow updates") }
                    } else {
                        Text(
                            "This phone runs an older Android, so each update asks for one tap to finish.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        SectionTitle("Appearance")
        OutlinedButton(onClick = onOpenThemes, modifier = Modifier.fillMaxWidth()) {
            Text("Theme: ${com.baastiklabs.firewatch.core.Themes.all.firstOrNull { it.id == data.settings.theme }?.name ?: "Firewatch"} · More themes ›")
        }

        PlanSettings(vm, data)

        // --- Data ---
        SectionTitle("Your data")
        Text(
            "Everything stays on this phone. Export a copy now and then (save it to Google Drive or email it to yourself) so your history survives a lost or new phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val lastBackup = remember(data) { com.baastiklabs.firewatch.data.BackupStore.lastOffPhone(context) }
        Text(
            "Last backup: " + if (lastBackup <= 0) "never" else ((System.currentTimeMillis() - lastBackup) / 86_400_000L).let { if (it == 0L) "today" else "$it days ago" },
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { exportLauncher.launch("firewatch-backup-${LocalDate.now()}.json") }) { Text("Export") }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                Text("Import")
            }
            TextButton(onClick = { showBackups = true }) { Text("Auto backups") }
        }
        var folder by remember { mutableStateOf(BackupStore.folder(context)) }
        val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                runCatching { BackupStore.setFolder(context, uri) }
                folder = BackupStore.folder(context)
                scope.launch {
                    BackupStore.write(context, vm.repository, "first-folder-backup")
                    snackbar.showSnackbar("Backups will now also go to that folder, every day and before updates.")
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Automatic backup off the phone", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (folder == null) "Pick a folder once (for example in Google Drive). A backup goes there every day and before every update."
                else "On: backups go to ${folder?.lastPathSegment?.substringAfterLast(':') ?: "your folder"}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { folderLauncher.launch(null) }) { Text(if (folder == null) "Choose folder" else "Change folder") }
                if (folder != null) TextButton(onClick = { BackupStore.setFolder(context, null); folder = null }) { Text("Stop") }
            }
        }
        if (com.baastiklabs.firewatch.core.Baseline.status(data, java.time.LocalDate.now().let { kotlinx.datetime.LocalDate(it.year, it.monthValue, it.dayOfMonth) }, kotlinx.datetime.TimeZone.currentSystemDefault()) !is com.baastiklabs.firewatch.core.BaselineStatus.Complete) {
            OutlinedButton(onClick = onBackfill) { Text("Back-date my baseline week") }
        }

        // --- Preferences ---
        SectionTitle("Products and pieces")
        LabeledRow("Products", "${data.products.count { !it.archived }}", Modifier.clickable(onClick = onOpenProducts).padding(vertical = 8.dp))
        LabeledRow(
            "One piece is",
            data.productsById[data.settings.referenceProductId]?.name ?: "4 mg gum",
            Modifier.clickable { pickReference = true }.padding(vertical = 8.dp),
        )

        SectionTitle("Usual sleep schedule")
        LabeledRow("Wake up", Fmt.minutesOfDay(data.settings.wakeMinutes), Modifier.clickable { pickWake = true }.padding(vertical = 8.dp))
        LabeledRow("Go to bed", Fmt.minutesOfDay(data.settings.sleepMinutes), Modifier.clickable { pickSleep = true }.padding(vertical = 8.dp))
        Text(
            "Use Good morning / Good night on the home screen to correct early mornings and late nights.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        var unsent by remember { mutableStateOf(com.baastiklabs.firewatch.data.FeedbackSender.unsent(context)) }
        if (unsent.isNotEmpty()) {
            SectionTitle("Unsent suggestions (${unsent.size})")
            unsent.forEach { u ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text((if (u.type.isNotBlank()) "${u.type}: " else "") + u.text.take(80), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        (if (u.at > 0) Fmt.dateTime(u.at) + " · " else "") + "last try: ${u.error}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                val error = com.baastiklabs.firewatch.data.FeedbackSender.sendNow(context, u.id)
                                unsent = com.baastiklabs.firewatch.data.FeedbackSender.unsent(context)
                                snackbar.showSnackbar(error?.let { "$it. Still saved." } ?: "Thanks, sent!")
                            }
                        }) { Text("Send now") }
                        TextButton(onClick = {
                            com.baastiklabs.firewatch.data.FeedbackSender.delete(context, u.id)
                            unsent = com.baastiklabs.firewatch.data.FeedbackSender.unsent(context)
                        }) { Text("Delete") }
                    }
                }
            }
        }

        SectionTitle("Help")
        LabeledRow("Help", "›", Modifier.clickable(onClick = onOpenHelp).padding(vertical = 8.dp))

        SectionTitle("About")
        LabeledRow("About ${Branding.APP_NAME}", "›", Modifier.clickable(onClick = onOpenAbout).padding(vertical = 8.dp))

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(
            "${Branding.FULL_NAME} · ${Branding.COPYRIGHT}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
    }

    if (showChangelog) {
        val sections = remember { Changelog.parse(readChangelog(context)) }
        ChangelogDialog("What's new", null, sections, onDismiss = { showChangelog = false })
    }

    if (confirmRollback && rollback != null) {
        AlertDialog(
            onDismissRequest = { confirmRollback = false },
            title = { Text("Go back to version ${rollback.versionName}?") },
            text = {
                Text(
                    "Firewatch will close for a moment and come back as the previous version. " +
                        "Your logs, products and settings stay exactly as they are, and newer versions still arrive automatically.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRollback = false
                    UpdateScheduler.rollbackNow(context, installNow = true)
                    scope.launch { snackbar.showSnackbar("Going back to ${rollback.versionName}…") }
                }) { Text("Go back") }
            },
            dismissButton = { TextButton(onClick = { confirmRollback = false }) { Text("Cancel") } },
        )
    }

    pendingImport?.let { file ->
        ImportDialog(
            file = file,
            onDismiss = { pendingImport = null },
            onImport = { replace ->
                pendingImport = null
                scope.launch {
                    val count = importBackup(vm, context, file, replace, reason = "before-import")
                    snackbar.showSnackbar(if (replace) "Replaced with the file's data." else "Added $count records from the file.")
                }
            },
        )
    }

    if (showBackups) {
        BackupsDialog(
            files = remember { BackupStore.list(context) },
            onDismiss = { showBackups = false },
            onRestore = { backupFile ->
                showBackups = false
                scope.launch {
                    val parsed = withContext(Dispatchers.IO) { BackupStore.read(backupFile) }
                    if (parsed == null) {
                        snackbar.showSnackbar("That backup couldn't be read.")
                    } else {
                        pendingImport = parsed
                    }
                }
            },
        )
    }

    if (pickReference) {
        val products = data.products.filter { !it.archived }
        var selected by remember { mutableStateOf(data.settings.referenceProductId) }
        AlertDialog(
            onDismissRequest = { pickReference = false },
            title = { Text("What counts as one piece?") },
            text = {
                Column {
                    Text(
                        "Every dose is converted into pieces of this product, so you can switch products and still compare.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    products.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().selectable(selected = selected == p.id, onClick = { selected = p.id }).padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected == p.id, onClick = { selected = p.id })
                            Text(p.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pickReference = false
                    if (selected != data.settings.referenceProductId) refChoice = selected
                }) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = { pickReference = false }) { Text("Cancel") } },
        )
    }

    refChoice?.let { productId ->
        val name = data.productsById[productId]?.name ?: "it"
        var backdating by remember { mutableStateOf(false) }
        if (!backdating) {
            AlertDialog(
                onDismissRequest = { refChoice = null },
                title = { Text("From when?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("From today on, $name counts as one piece. Past days keep the old piece size.")
                        Text("Or back-date the change. This changes your past totals and may change your tier.", fontWeight = FontWeight.SemiBold)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        refChoice = null
                        scope.launch { vm.repository.changeReference(productId, vm.repository.now()) }
                    }) { Text("From today on") }
                },
                dismissButton = { TextButton(onClick = { backdating = true }) { Text("Back-date…") } },
            )
        } else {
            @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
            run {
                val state = androidx.compose.material3.rememberDatePickerState()
                androidx.compose.material3.DatePickerDialog(
                    onDismissRequest = { refChoice = null },
                    confirmButton = {
                        TextButton(onClick = {
                            val utc = state.selectedDateMillis
                            refChoice = null
                            if (utc != null) {
                                val day = java.time.Instant.ofEpochMilli(utc).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                                val from = day.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                                scope.launch { vm.repository.changeReference(productId, from) }
                            }
                        }) { Text("Back-date to this day") }
                    },
                    dismissButton = { TextButton(onClick = { refChoice = null }) { Text("Cancel") } },
                ) { androidx.compose.material3.DatePicker(state) }
            }
        }
    }

    if (pickWake) {
        TimePickDialog(
            title = "Usually wake up at",
            initial = LocalTime.of(data.settings.wakeMinutes / 60, data.settings.wakeMinutes % 60),
            onDismiss = { pickWake = false },
            onPick = { t ->
                pickWake = false
                scope.launch { vm.repository.updateSettings { it.copy(wakeMinutes = t.hour * 60 + t.minute) } }
            },
        )
    }
    if (pickSleep) {
        TimePickDialog(
            title = "Usually go to bed at",
            initial = LocalTime.of(data.settings.sleepMinutes / 60, data.settings.sleepMinutes % 60),
            onDismiss = { pickSleep = false },
            onPick = { t ->
                pickSleep = false
                scope.launch { vm.repository.updateSettings { it.copy(sleepMinutes = t.hour * 60 + t.minute) } }
            },
        )
    }
}

/** Imports a backup. Always writes a safety backup of the current data first. */
suspend fun importBackup(
    vm: FirewatchViewModel,
    context: android.content.Context,
    file: BackupFile,
    replace: Boolean,
    reason: String,
): Int {
    val repo = vm.repository
    BackupStore.write(context, repo, reason)
    val incoming = Backups.toEnvelopes(file)
    return if (replace) {
        val now = repo.now()
        repo.tombstoneAllExcept(incoming.map { it.id }.toSet())
        repo.putRecords(incoming.map { it.copy(updatedAt = now) })
        incoming.size
    } else {
        val toWrite = Backups.mergeIncoming(repo.allRecords(), incoming)
        repo.putRecords(toWrite)
        toWrite.size
    }
}

@Composable
private fun ImportDialog(file: BackupFile, onDismiss: () -> Unit, onImport: (replace: Boolean) -> Unit) {
    val preview = remember(file) { Backups.preview(file) }
    var mode by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import this backup?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${preview.doses} doses, ${preview.cravings} cravings, ${preview.products} products" +
                        (if (preview.firstAt != null && preview.lastAt != null) {
                            ", from ${Fmt.dateTime(preview.firstAt!!)} to ${Fmt.dateTime(preview.lastAt!!)}"
                        } else {
                            ""
                        }) + ".",
                )
                if (file.exportedAt > 0) Text("Saved ${Fmt.dateTime(file.exportedAt)}", style = MaterialTheme.typography.bodySmall)
                listOf(
                    "Add to what's on this phone (recommended)",
                    "Replace what's on this phone with the file",
                ).forEachIndexed { index, label ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = mode == index, onClick = { mode = index }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == index, onClick = { mode = index })
                        Text(label)
                    }
                }
                Text(
                    "Your current data is backed up automatically first, either way.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onImport(mode == 1) }) { Text("Import") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun BackupsDialog(files: List<File>, onDismiss: () -> Unit, onRestore: (File) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Automatic backups") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Made daily and before every update or import. A copy also goes to Downloads/Firewatch. Tap one to restore it.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (files.isEmpty()) Text("No backups yet.")
                files.forEach { f ->
                    val reason = f.name.removeSuffix(".json").split('-').drop(6).joinToString(" ").ifBlank { "backup" }
                    Text(
                        "${Fmt.dateTime(f.lastModified())} · $reason",
                        modifier = Modifier.fillMaxWidth().clickable { onRestore(f) }.padding(vertical = 10.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("About") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(120.dp))
            Text(Branding.APP_NAME, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("by ${Branding.MAKER}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Firewatch tracks the nicotine that actually reaches your blood, counted in pieces: " +
                    "what one 4 mg nicotine gum delivers. That way pouches, gum and vapes all compare fairly.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Firewatch updates itself. Your logs, products and settings are backed up automatically before every update " +
                    "and are never touched by one.",
                style = MaterialTheme.typography.bodyMedium,
            )
            EstimateNote()
            Spacer(Modifier.height(8.dp))
            Text(Branding.COPYRIGHT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
