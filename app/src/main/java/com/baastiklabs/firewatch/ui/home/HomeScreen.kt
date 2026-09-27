package com.baastiklabs.firewatch.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.baastiklabs.firewatch.core.Baseline
import com.baastiklabs.firewatch.core.BaselineStatus
import com.baastiklabs.firewatch.core.CravingScale
import com.baastiklabs.firewatch.core.Cravings
import com.baastiklabs.firewatch.core.DaySummary
import com.baastiklabs.firewatch.core.Days
import com.baastiklabs.firewatch.core.localDate
import com.baastiklabs.firewatch.core.engine.Coach
import com.baastiklabs.firewatch.core.engine.Insights
import com.baastiklabs.firewatch.core.engine.Ladder
import com.baastiklabs.firewatch.core.engine.Progress
import com.baastiklabs.firewatch.core.engine.Quality
import com.baastiklabs.firewatch.core.engine.Waking
import com.baastiklabs.firewatch.core.model.CheckIn
import com.baastiklabs.firewatch.core.model.ProductKind
import com.baastiklabs.firewatch.core.model.SpeedProfile
import com.baastiklabs.firewatch.core.model.Craving
import com.baastiklabs.firewatch.core.model.CravingOutcome
import com.baastiklabs.firewatch.core.model.Dose
import com.baastiklabs.firewatch.core.model.Product
import com.baastiklabs.firewatch.core.model.SleepKind
import com.baastiklabs.firewatch.core.pieces
import com.baastiklabs.firewatch.core.records.FirewatchData
import com.baastiklabs.firewatch.core.toDose
import com.baastiklabs.firewatch.ui.EstimateNote
import com.baastiklabs.firewatch.ui.FirewatchViewModel
import com.baastiklabs.firewatch.ui.Fmt
import com.baastiklabs.firewatch.ui.Stat
import com.baastiklabs.firewatch.ui.TimePickDialog
import com.baastiklabs.firewatch.ui.theme.cravingColor
import com.baastiklabs.firewatch.ui.toEpochMillis
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun HomeScreen(vm: FirewatchViewModel, data: FirewatchData, now: Long, snackbar: SnackbarHostState, onBackfill: () -> Unit = {}, onHelp: () -> Unit = {}) {
    val repo = vm.repository
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val tz = TimeZone.currentSystemDefault()
    val today = now.localDate(tz)
    val minute = now / 60_000
    val summaries = remember(data, minute) { Days.summaries(data, tz, now) }
    val todaySummary = summaries[today] ?: DaySummary(today)
    val baseline = remember(data, minute) { Baseline.status(data, today, tz) }
    val activeCraving = remember(data, minute) { Cravings.active(data, now) }
    val todayDoses = remember(data, minute) { data.doses.filter { it.at.localDate(tz) == today }.sortedByDescending { it.at } }
    val lastDoseAt = data.doses.maxOfOrNull { it.at }

    var optionsFor by remember { mutableStateOf<Product?>(null) }
    var editing by remember { mutableStateOf<Dose?>(null) }
    var cravingSheet by remember { mutableStateOf(false) }
    var sleepPicker by remember { mutableStateOf<SleepKind?>(null) }
    var vapeFor by remember { mutableStateOf<Product?>(null) }
    var friendVape by remember { mutableStateOf(false) }
    var checkIn by remember { mutableStateOf(false) }

    val revealed = baseline is BaselineStatus.Complete
    val measured = remember(data, minute) { if (revealed) Progress.measuredRung(data, today, tz) else null }
    val target = data.targetPieces?.let { Ladder.rung(it) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val battery = remember(data, minute) { target?.let { Progress.battery(data, if (it.pieces > 0) it.pieces else 1.0 / 3.0, now, tz, com.baastiklabs.firewatch.data.AppActivity.last(context)) } }
    val stepDown = remember(data, minute) { if (revealed) Progress.stepDownOffer(data, now, tz) else null }
    val readiness = remember(data, minute) { data.targetPieces?.let { Coach.readiness(data, it, now) } }
    val stepUp = remember(data, minute) { if (revealed && stepDown == null) Coach.stepUpOffer(data, now, tz) else null }
    val headsUps = remember(data, minute) { Progress.headsUps(data, now, tz) }
    val wave = remember(data, minute) { Insights(data, tz, now).todayCurve(10) }
    val todayWake = remember(data, minute) { Waking.day(data, today, tz) }
    val quality = remember(data, minute) { Quality.of(todayDoses, data.referenceMg) }
    val checkedInToday = data.checkIns.any { it.at.localDate(tz) == today }

    var celebrate by remember { mutableStateOf<com.baastiklabs.firewatch.core.engine.Rung?>(null) }
    var relapseDialog by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf(false) }
    val relapse = com.baastiklabs.firewatch.core.engine.Relapse
    val relapseNext = remember(data, minute) { relapse.nextAt(data, now, tz) }
    val relapseProduct = remember(data) { relapse.product(data)?.name }
    val relapseReason = remember(data, minute) { relapse.recommendation(data, now, tz) }
    val movingOn = remember(data, minute) { relapse.movingOn(data, now) }
    val switchRelapse = com.baastiklabs.firewatch.ui.relapse.rememberRelapseSwitch(vm) { on ->
        scope.launch { snackbar.showSnackbar(if (on) "Relapse prevention mode turned on" else "Relapse prevention mode turned off") }
    }

    fun moveTarget(pieces: Double, reason: String) {
        scope.launch {
            repo.setTarget(pieces, reason)
            if (reason == "down") {
                celebrate = Ladder.rung(pieces)
                return@launch
            }
            val r = Ladder.rung(pieces)
            snackbar.showSnackbar(
                when (reason) {
                    "down" -> "New rung: ${r.label}. That's real progress."
                    "up" -> "Stepped back to ${r.label}. That's normal; it's how the climb down works."
                    else -> "Starting at ${r.label}."
                },
            )
        }
    }

    fun logNow(product: Product, draft: DoseDraft? = null) {
        scope.launch {
            val at = draft?.at ?: repo.now()
            val dose = repo.logDose(
                product.toDose(
                    id = repo.newId(),
                    at = at,
                    loggedAt = repo.now(),
                    multiplier = draft?.multiplier ?: 1.0,
                    duration = draft?.duration ?: com.baastiklabs.firewatch.core.model.Duration.FULL,
                    acidicDrink = draft?.acidicDrink ?: false,
                    tags = draft?.tags ?: emptyList(),
                ),
            )
            val waited = com.baastiklabs.firewatch.core.engine.BatteryEngine.waitedForFull(repo.data.value, dose, tz)
            val result = snackbar.showSnackbar(
                message = if (waited) "Logged ${product.name}. You waited for a full battery. Nice work!"
                else "Logged ${product.name} · ${Fmt.piecesLabel(dose.pieces(data.referenceMg))}",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) repo.deleteDose(dose.id)
        }
    }

    fun logSleep(kind: SleepKind, at: Long) {
        scope.launch {
            val event = repo.logSleep(kind, at)
            val label = if (kind == SleepKind.WAKE) "Good morning" else "Good night"
            val result = snackbar.showSnackbar("$label · ${Fmt.time(at)}", actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) repo.deleteSleep(event.id)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Firewatch", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "by Baastik Labs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onHelp, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(36.dp).padding(0.dp)) { Text("?", fontWeight = FontWeight.Bold) }
            }
        }
        if (data.relapseOn) item { com.baastiklabs.firewatch.ui.relapse.RelapseIndicator(relapseNext, relapseProduct) }
        item {
            if (revealed) {
                TierStatusCard(
                    measured = measured,
                    target = target,
                    battery = battery,
                    today = todaySummary,
                    lastDoseAt = lastDoseAt,
                    now = now,
                    wave = wave,
                    sleepShade = listOf(wave.firstOrNull()?.first.let { (it ?: 0L) to todayWake.wakeAt }, todayWake.sleepAt to (wave.lastOrNull()?.first ?: 0L)),
                    quality = quality,
                    nowDoses = data.doses,
                    relapseNext = if (data.relapseOn) relapseNext else null,
                    fitsNow = battery?.let { com.baastiklabs.firewatch.core.engine.BatteryEngine.fitsNow(data, it)?.name },
                )
            } else {
                StatusCard(todaySummary, baseline, summaries, lastDoseAt, now, onBackfill)
            }
        }
        if (revealed && target == null && measured != null) {
            item {
                OfferCard(
                    title = "Your starting point: ${measured.tier.title}",
                    body = "${measured.plainLine} Work from here? Firewatch will suggest your next piece at this pace, and offer a small step down once you've held it.",
                    primary = "Start here",
                    onPrimary = { moveTarget(measured.pieces, "start") },
                    secondary = null,
                    onSecondary = {},
                )
            }
        }
        if (stepDown != null && target != null) {
            item {
                val ready = readiness?.ready ?: true
                OfferCard(
                    title = "Ready for ${stepDown.label}?",
                    body = "You've held ${target.label} for ${data.settings.holdDays} days." + if (readiness?.confident == true) {
                        if (ready) " From your cravings, the next rung should feel like about a ${readiness.predictedNext.toInt()} out of 10, and you ride out ${readiness.capacity}s."
                        else " Heads-up: your cravings suggest the next rung may feel like a ${readiness.predictedNext.toInt()}, above the ${readiness.capacity} you usually ride out. Holding a bit longer is fine too."
                    } else "",
                    primary = "Step down",
                    onPrimary = { moveTarget(stepDown.pieces, "down") },
                    secondary = "Not yet",
                    onSecondary = { scope.launch { repo.updateSettings { it.copy(stepDownSnoozedAt = repo.now()) } } },
                )
            }
        }
        if (stepUp != null && target != null) {
            item {
                OfferCard(
                    title = "This rung is tough right now",
                    body = "Your cravings have been beating you at ${target.label}. Stepping up to ${stepUp.label} for a while is normal and keeps you on gum rather than something worse. Come back down when it's ready.",
                    primary = "Step up",
                    onPrimary = { moveTarget(stepUp.pieces, "up") },
                    secondary = "I'm OK",
                    onSecondary = { scope.launch { repo.updateSettings { it.copy(stepUpSnoozedAt = repo.now()) } } },
                )
            }
        }
        relapseReason?.let { reason ->
            item {
                OfferCard(
                    title = "Try Relapse prevention mode?",
                    body = "${relapse.reasonText(reason)} Chewing on a steady schedule early on keeps you ahead of cravings.",
                    primary = "Tell me more",
                    onPrimary = { relapseDialog = true },
                    secondary = "Not now",
                    onSecondary = { scope.launch { repo.updateSettings { it.copy(relapseCardDismissedAt = repo.now()) } } },
                )
            }
        }
        if (movingOn) {
            item {
                OfferCard(
                    title = "You've been steady for 4 weeks",
                    body = "Ready to switch to tapering? Relapse prevention mode turns off, and Firewatch helps you step down at your own pace.",
                    primary = "Switch to tapering",
                    onPrimary = { switchRelapse(false) },
                    secondary = "Not yet",
                    onSecondary = { scope.launch { repo.updateSettings { it.copy(movingOnDismissedAt = repo.now()) } } },
                )
            }
        }
        if (headsUps.isNotEmpty()) item { HeadsUpCard(headsUps) }
        Quality.swapTip(todayDoses, data.referenceMg)?.let { tip ->
            item {
                Text(tip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (data.settings.dailyCheckIn && !checkedInToday) {
            item {
                OutlinedButton(onClick = { checkIn = true }, modifier = Modifier.fillMaxWidth()) { Text("Daily check-in (3 taps)") }
            }
        }
        item {
            if (activeCraving != null) {
                ActiveCravingCard(
                    craving = activeCraving,
                    now = now,
                    onPassed = { scope.launch { repo.finishCraving(activeCraving.id, CravingOutcome.RODE_OUT) } },
                    onUsed = { scope.launch { repo.finishCraving(activeCraving.id, CravingOutcome.USED) } },
                    onTag = { tag ->
                        scope.launch {
                            val tags = if (tag in activeCraving.tags) activeCraving.tags - tag else activeCraving.tags + tag
                            repo.saveCraving(activeCraving.copy(tags = tags))
                        }
                    },
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { cravingSheet = true },
                        modifier = Modifier.weight(1.4f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("Craving? Log it", style = MaterialTheme.typography.titleSmall)
                    }
                    OutlinedButton(
                        onClick = { friendVape = true },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("Friend's vape", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
        item {
            Column {
                Text("Log a dose", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Tap to log it now · hold for time, amount and more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(data.homeProducts.chunked(2)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { product ->
                    ProductButton(
                        product = product,
                        referenceMg = data.referenceMg,
                        modifier = Modifier.weight(1f),
                        onTap = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (product.kind == ProductKind.VAPE && product.borrowedFrom != null) vapeFor = product else logNow(product)
                        },
                        onLongPress = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            optionsFor = product
                        },
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (data.homeProducts.isEmpty()) {
            item {
                Text(
                    "No products on the home screen. Add them in Settings → Products.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            SleepRow(
                data = data,
                today = LocalDate.now(),
                onTap = { kind -> logSleep(kind, repo.now()) },
                onLongPress = { kind -> sleepPicker = kind },
            )
        }
        item {
            Text("Today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        if (todayDoses.isEmpty()) {
            item {
                Text(
                    "Nothing logged yet today.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(todayDoses, key = { it.id }) { dose ->
            DoseRow(dose, data.referenceMg, onClick = { editing = dose })
        }
        item {
            OutlinedButton(onClick = { relapseDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (data.relapseOn) "Relapse prevention mode: on" else "Relapse prevention mode")
            }
        }
        item { EstimateNote(Modifier.padding(top = 8.dp)) }
        item {
            androidx.compose.material3.TextButton(onClick = { feedback = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Suggest something / report a bug")
            }
        }
    }

    if (feedback) {
        com.baastiklabs.firewatch.ui.feedback.FeedbackDialog(onDismiss = { feedback = false }) { type, text, details, name ->
            feedback = false
            scope.launch {
                val sent = com.baastiklabs.firewatch.data.FeedbackSender.send(context, type, text, details, name)
                snackbar.showSnackbar(if (sent) "Thanks, sent!" else "No connection. Saved, and it'll send next time Firewatch opens.")
            }
        }
    }

    if (relapseDialog) {
        com.baastiklabs.firewatch.ui.relapse.RelapseDialog(
            on = data.relapseOn,
            onDismiss = {
                relapseDialog = false
                if (!data.relapseOn && relapseReason != null) scope.launch { repo.updateSettings { it.copy(relapseCardDismissedAt = repo.now()) } }
            },
            onSwitch = switchRelapse,
        )
    }

    optionsFor?.let { product ->
        DoseSheet(
            title = product.name,
            kind = product.kind,
            labelMg = product.labelMg,
            absorption = product.absorption,
            referenceMg = data.referenceMg,
            initial = DoseDraft(at = repo.now()),
            relativeTime = true,
            saveLabel = "Log it",
            onDismiss = { optionsFor = null },
            onSave = { draft ->
                optionsFor = null
                logNow(product, draft)
            },
        )
    }

    editing?.let { dose ->
        EditDoseSheet(vm, dose, data.referenceMg, snackbar, onDone = { editing = null })
    }

    fun logVape(v: VapeLog, name: String, savedId: String?) {
        scope.launch {
            val now2 = repo.now()
            var productId = savedId ?: "friends-vape"
            if (v.saveAs != null && v.strength != null) {
                val p = Product(
                    id = repo.newId(),
                    name = "${v.saveAs}'s vape",
                    kind = ProductKind.VAPE,
                    labelMg = v.strength,
                    absorption = 1.0,
                    speed = SpeedProfile.SPIKE,
                    onHome = true,
                    order = 100,
                    borrowedFrom = v.saveAs,
                    createdAt = now2,
                )
                repo.saveProduct(p)
                productId = p.id
            }
            val dose = Dose(
                id = repo.newId(),
                productId = productId,
                at = now2,
                productName = name + if (v.what.isNotBlank()) " (${v.what})" else "",
                kind = ProductKind.VAPE,
                speed = SpeedProfile.SPIKE,
                rangeLowMg = v.lowMg,
                rangeHighMg = v.highMg,
                borrowed = true,
                loggedAt = now2,
            )
            repo.logDose(dose)
            val result = snackbar.showSnackbar("Logged ${dose.productName} as a range", actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) repo.deleteDose(dose.id)
        }
    }

    if (friendVape) {
        FriendVapeSheet(
            title = "Friend's vape",
            presetStrength = null,
            referenceMg = data.referenceMg,
            onDismiss = { friendVape = false },
            onLog = { v ->
                friendVape = false
                logVape(v, v.saveAs?.let { "$it's vape" } ?: "Friend's vape", null)
            },
        )
    }
    vapeFor?.let { p ->
        FriendVapeSheet(
            title = p.name,
            presetStrength = p.labelMg,
            referenceMg = data.referenceMg,
            onDismiss = { vapeFor = null },
            onLog = { v ->
                vapeFor = null
                logVape(v, p.name, p.id)
            },
        )
    }
    celebrate?.let { r ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { celebrate = null },
            icon = { Text("🔥", style = MaterialTheme.typography.displaySmall) },
            title = { Text("New rung: ${r.tier.title}") },
            text = {
                Text(
                    "${r.plainLine} You earned the ${r.label} badge. Every step down is one piece a day lighter, and badges are never taken away.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { celebrate = null }) { Text("Onward") } },
        )
    }

    if (checkIn) {
        CheckInDialog(onDismiss = { checkIn = false }, onSave = { c, m, sl ->
            checkIn = false
            scope.launch { repo.saveCheckIn(CheckIn(repo.newId(), repo.now(), c, m, sl)) }
        })
    }

    if (cravingSheet) {
        CravingSheet(
            onDismiss = { cravingSheet = false },
            onPick = { level ->
                cravingSheet = false
                scope.launch {
                    val craving = repo.startCraving(level)
                    val result = snackbar.showSnackbar(
                        "Craving logged · ${level} ${CravingScale.level(level).name}. You've got this.",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) repo.deleteCraving(craving.id)
                }
            },
        )
    }

    sleepPicker?.let { kind ->
        TimePickDialog(
            title = if (kind == SleepKind.WAKE) "When did you wake up?" else "When did you go to sleep?",
            initial = LocalTime.now(),
            onDismiss = { sleepPicker = null },
            onPick = { time ->
                sleepPicker = null
                var at = LocalDate.now().atTime(time)
                if (at.toEpochMillis() > System.currentTimeMillis()) at = at.minusDays(1)
                logSleep(kind, at.toEpochMillis())
            },
        )
    }
}

/** Edit or delete an existing dose (shared with the day view). */
@Composable
fun EditDoseSheet(
    vm: FirewatchViewModel,
    dose: Dose,
    referenceMg: Double,
    snackbar: SnackbarHostState,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    DoseSheet(
        title = dose.productName.ifBlank { "Dose" },
        kind = dose.kind,
        labelMg = dose.labelMg,
        absorption = dose.absorption,
        referenceMg = referenceMg,
        initial = DoseDraft(dose.at, dose.multiplier, dose.duration, dose.acidicDrink, dose.tags),
        relativeTime = false,
        saveLabel = "Save",
        onDismiss = onDone,
        onSave = { draft ->
            onDone()
            scope.launch {
                vm.repository.updateDose(
                    dose.copy(
                        at = draft.at,
                        multiplier = draft.multiplier,
                        duration = draft.duration,
                        acidicDrink = draft.acidicDrink,
                        tags = draft.tags,
                    ),
                )
            }
        },
        onDelete = {
            onDone()
            scope.launch {
                vm.repository.deleteDose(dose.id)
                val result = snackbar.showSnackbar("Dose deleted", actionLabel = "Undo", duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) vm.repository.updateDose(dose)
            }
        },
    )
}

@Composable
private fun StatusCard(
    today: DaySummary,
    baseline: BaselineStatus,
    summaries: Map<kotlinx.datetime.LocalDate, DaySummary>,
    lastDoseAt: Long?,
    now: Long,
    onBackfill: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (baseline) {
                BaselineStatus.NotStarted -> {
                    Text("Baseline week", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "Starts with your first log. For 7 days Firewatch just watches, then it shows your starting tier.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = onBackfill) { Text("Estimate my last week instead") }
                }
                is BaselineStatus.InProgress -> {
                    Text(
                        "Baseline week · day ${baseline.dayNumber} of ${Baseline.DAYS}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    LinearProgressIndicator(
                        progress = { baseline.dayNumber / Baseline.DAYS.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Just log as usual. Your starting tier appears after day ${Baseline.DAYS}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onBackfill) { Text("Estimate the days before instead") }
                }
                is BaselineStatus.Complete -> {
                    val avg = Baseline.averagePiecesPerDay(summaries, baseline.startedOn)
                    Text("Baseline week complete", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "Your baseline: about ${Fmt.pieces(avg)} pieces a day. Tiers and the next-piece battery arrive in an upcoming update.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Stat("≈ ${Fmt.pieces(today.pieces)}", "pieces today", big = true)
                Stat("≈ ${Fmt.mg(today.absorbedMg)}", "absorbed today", big = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Stat("${today.doseCount}", if (today.doseCount == 1) "dose" else "doses")
                Stat(lastDoseAt?.let { Fmt.duration(now - it) } ?: "–", "since last")
                Stat("${today.cravingsRodeOut} of ${today.cravings}", "cravings ridden out")
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ActiveCravingCardTags(craving: Craving, onTag: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DoseTags.forEach { tag ->
            androidx.compose.material3.FilterChip(selected = tag in craving.tags, onClick = { onTag(tag) }, label = { Text(tag) })
        }
    }
}

@Composable
private fun ActiveCravingCard(craving: Craving, now: Long, onPassed: () -> Unit, onUsed: () -> Unit, onTag: (String) -> Unit) {
    val level = CravingScale.level(craving.intensity)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(50), color = cravingColor(craving.intensity)) {
                    Text(
                        "${craving.intensity}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.White,
                    )
                }
                Column {
                    Text("Riding out a craving · ${level.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("Started ${Fmt.ago(craving.at, now)}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                "Most cravings pass within a few minutes. Tap when it does. What's going on? (optional)",
                style = MaterialTheme.typography.bodySmall,
            )
            ActiveCravingCardTags(craving, onTag)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onPassed, modifier = Modifier.weight(1f)) { Text("It passed") }
                OutlinedButton(onClick = onUsed, modifier = Modifier.weight(1f)) { Text("I used") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductButton(
    product: Product,
    referenceMg: Double,
    modifier: Modifier,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val pieces = com.baastiklabs.firewatch.core.Absorption.pieces(
        com.baastiklabs.firewatch.core.Absorption.absorbedMg(product),
        referenceMg,
    )
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = shape,
        modifier = modifier
            .height(96.dp)
            .clip(shape)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(
                product.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(Fmt.piecesLabel(pieces), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SleepRow(
    data: FirewatchData,
    today: LocalDate,
    onTap: (SleepKind) -> Unit,
    onLongPress: (SleepKind) -> Unit,
) {
    val zone = java.time.ZoneId.systemDefault()
    val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val wokeToday = data.sleepEvents.lastOrNull { it.kind == SleepKind.WAKE && it.at >= startOfToday }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(SleepKind.WAKE to "Good morning", SleepKind.SLEEP to "Good night").forEach { (kind, label) ->
                val shape = RoundedCornerShape(16.dp)
                Surface(
                    shape = shape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(shape)
                        .combinedClickable(onClick = { onTap(kind) }, onLongClick = { onLongPress(kind) }),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        val schedule = "Usual day ${Fmt.minutesOfDay(data.settings.wakeMinutes)}–${Fmt.minutesOfDay(data.settings.sleepMinutes)}"
        Text(
            (wokeToday?.let { "Up since ${Fmt.time(it.at)} · " } ?: "") + "$schedule · hold to set a time",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun DoseRow(dose: Dose, referenceMg: Double, onClick: () -> Unit) {
    val extras = buildList {
        if (dose.estimated) add("estimated")
        if (dose.multiplier != 1.0) add("×${Fmt.pieces(dose.multiplier)}")
        if (dose.duration != com.baastiklabs.firewatch.core.model.Duration.FULL) add(dose.duration.name.lowercase())
        if (dose.acidicDrink) add("with coffee/soda")
        addAll(dose.tags.map { it.lowercase() })
    }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        headlineContent = { Text(dose.productName.ifBlank { "Dose" }) },
        supportingContent = {
            Text(
                Fmt.time(dose.at) + if (extras.isEmpty()) "" else " · " + extras.joinToString(", "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = { Text(Fmt.piecesLabel(dose.pieces(referenceMg)), style = MaterialTheme.typography.labelLarge) },
    )
}

