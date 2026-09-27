package com.baastiklabs.firewatch.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.baastiklabs.firewatch.BuildConfig
import com.baastiklabs.firewatch.R
import com.baastiklabs.firewatch.core.Branding
import com.baastiklabs.firewatch.core.records.FirewatchData
import com.baastiklabs.firewatch.ui.FirewatchViewModel
import com.baastiklabs.firewatch.ui.Fmt
import com.baastiklabs.firewatch.ui.LabeledRow
import com.baastiklabs.firewatch.ui.TimePickDialog
import com.baastiklabs.firewatch.update.SelfInstaller
import kotlinx.coroutines.launch
import java.time.LocalTime

private const val STEPS = 5

/** One-time setup. Each step is plain and skippable except the essentials. */
@Composable
fun OnboardingScreen(vm: FirewatchViewModel, data: FirewatchData) {
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var backfill by rememberSaveable { mutableStateOf(false) }
    var backfilling by rememberSaveable { mutableStateOf(false) }

    fun finish() {
        vm.updateState.lastSeenVersion = BuildConfig.VERSION_NAME
        scope.launch { vm.repository.updateSettings { it.copy(onboardingDone = true) } }
    }

    if (backfilling) {
        BackfillScreen(vm, data, onDone = { finish() }, onCancel = { backfilling = false })
        return
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
        ) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (step) {
                    0 -> WelcomeStep()
                    1 -> UpdatesStep()
                    2 -> ScheduleStep(vm, data)
                    3 -> ProductsStep(vm, data)
                    else -> StartStep(backfill) { backfill = it }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) TextButton(onClick = { step-- }) { Text("Back") }
                Spacer(Modifier.weight(1f))
                Text("${step + 1} of $STEPS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    if (step < STEPS - 1) {
                        step++
                    } else if (backfill) {
                        backfilling = true
                    } else {
                        finish()
                    }
                }) { Text(if (step < STEPS - 1) "Next" else if (backfill) "Estimate my week" else "Start") }
            }
        }
    }
}

@Composable
private fun StartStep(backfill: Boolean, onChoose: (Boolean) -> Unit) {
    Text("How do you want to start?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text(
        "Firewatch needs about a week of history to work out your starting tier.",
        style = MaterialTheme.typography.bodyLarge,
    )
    ChoiceCard(
        selected = !backfill,
        title = "Establish a baseline",
        body = "Just log as usual for 7 days. Most accurate; your tier appears on day 8.",
        onClick = { onChoose(false) },
    )
    ChoiceCard(
        selected = backfill,
        title = "Estimate my last week",
        body = "Go through the last 7 days one at a time and tap roughly what you used each day. No times needed. Your starting tier shows up straight away.",
        onClick = { onChoose(true) },
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceCard(selected: Boolean, title: String, body: String, onClick: () -> Unit) {
    androidx.compose.material3.Card(
        onClick = onClick,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.RadioButton(selected = selected, onClick = onClick)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(
        Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(140.dp))
        Text(Branding.APP_NAME, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text("by ${Branding.MAKER}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text(
            "Log a dose in two seconds. Watch the numbers come down.",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Everything is counted in pieces: what one 4 mg nicotine gum delivers into your blood. " +
                "Start with a baseline week, or estimate your last week and see your starting tier straight away.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UpdatesStep() {
    val context = LocalContext.current
    var canInstall by remember { mutableStateOf(SelfInstaller.canRequestInstalls(context)) }
    var notificationsOk by remember { mutableStateOf(notificationsAllowed(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canInstall = SelfInstaller.canRequestInstalls(context)
        notificationsOk = notificationsAllowed(context)
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsOk = notificationsAllowed(context)
    }

    Text("Let Firewatch keep itself up to date", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text(
        "New versions will download and install by themselves, with nothing for you to do. Android needs your OK for that, once.",
        style = MaterialTheme.typography.bodyLarge,
    )
    if (canInstall) {
        Text("✓ Done. Firewatch can update itself.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
    } else {
        Text(
            "1. Tap the button below.\n2. Switch on \"Allow from this source\".\n3. Press back to return here.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = {
            context.startActivity(
                Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
            )
        }) { Text("Allow updates") }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Spacer(Modifier.height(8.dp))
        if (notificationsOk) {
            Text("✓ Notifications allowed.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
        } else {
            Text(
                "Optional: allow notifications. Firewatch only uses them if an update ever needs a tap to finish. It never nudges you to use nicotine.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                Text("Allow notifications")
            }
        }
    }
}

@Composable
private fun ScheduleStep(vm: FirewatchViewModel, data: FirewatchData) {
    val scope = rememberCoroutineScope()
    var pickWake by remember { mutableStateOf(false) }
    var pickSleep by remember { mutableStateOf(false) }
    Text("Your usual day", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text(
        "Firewatch works out your pace from the hours you're awake. Set your usual times; the Good morning and Good night buttons correct them on unusual days.",
        style = MaterialTheme.typography.bodyLarge,
    )
    LabeledRow("I usually wake up at", Fmt.minutesOfDay(data.settings.wakeMinutes), Modifier.padding(vertical = 8.dp))
    OutlinedButton(onClick = { pickWake = true }) { Text("Change wake-up time") }
    LabeledRow("I usually go to bed at", Fmt.minutesOfDay(data.settings.sleepMinutes), Modifier.padding(vertical = 8.dp))
    OutlinedButton(onClick = { pickSleep = true }) { Text("Change bedtime") }

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

@Composable
private fun ProductsStep(vm: FirewatchViewModel, data: FirewatchData) {
    val scope = rememberCoroutineScope()
    Text("What do you use?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text(
        "Ticked products get a big one-tap button on the home screen. You can add others (lozenges, patches, vapes, other brands) later in Settings → Products.",
        style = MaterialTheme.typography.bodyLarge,
    )
    data.products.filter { !it.archived }.sortedBy { it.order }.forEach { product ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = product.onHome,
                onCheckedChange = { on -> scope.launch { vm.repository.saveProduct(product.copy(onHome = on)) } },
            )
            Text(product.name, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun notificationsAllowed(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
