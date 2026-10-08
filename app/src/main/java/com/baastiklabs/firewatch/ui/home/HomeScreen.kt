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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
fun HomeScreen(
    vm: FirewatchViewModel,
    data: FirewatchData,
    now: Long,
    snackbar: SnackbarHostState,
    onBackfill: () -> Unit = {},
    onHelp: () -> Unit = {},
    onFill: (List<kotlinx.datetime.LocalDate>) -> Unit = {},
) {
    val repo = vm.repository
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val tz = TimeZone.currentSystemDefault()
    val today = now.localDate(tz)
    val minute = now / 60_000
    val summaries = remember(data, minute) { Days.summaries(data, tz, now) }
    // "Today" = the waking day: a 1 AM piece counts toward the night before.
    val wakingToday = remember(data, minute) { Days.wakingDate(data, now, tz) }
    val todaySummary = summaries[wakingToday] ?: DaySummary(wakingToday)
    val baseline = remember(data, minute) { Baseline.status(data, today, tz) }
    val activeCraving = remember(data, minute) { Cravings.active(data, now) }
    val todayDoses = remember(data, minute) { data.doses.filter { Days.wakingDate(data, it.at, tz) == wakingToday }.sortedByDescending { it.at } }
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
    val early = com.baastiklabs.firewatch.core.engine.Control.isEarly(data)
    val showTier = revealed || early
    val stepUpFull = remember(data, minute) { if (showTier && stepDown == null) Coach.stepUp(data, now, tz) else null }
    // First week: the early target (8 a day) firms up as days are logged.
    androidx.compose.runtime.LaunchedEffect(data, minute) {
        com.baastiklabs.firewatch.core.engine.Control.earlyTargetUpdate(data, now, tz)?.let {
            repo.setTarget(it, com.baastiklabs.firewatch.core.engine.Control.EARLY)
        }
    }
    val lighter = remember(data, minute / 60) { com.baastiklabs.firewatch.core.engine.Control.lighterThanStart(data, now, tz) }
    val daysOff = remember(data, minute / 60) { com.baastiklabs.firewatch.core.engine.Control.daysOffSmokeAndVape(data, now, tz) }
    val journey = remember(data, minute / 60) { if (revealed) Insights(data, tz, now).journey() else null }
    val steadyDays = remember(data, minute / 30) { com.baastiklabs.firewatch.core.engine.Control.steadyDays(data, now, tz) }
    val steadyMilestone = com.baastiklabs.firewatch.core.engine.Control.newSteadyMilestone(data, steadyDays)
    val practiceStatus = remember(data, minute) { com.baastiklabs.firewatch.core.engine.Practice.status(data, now, tz) }
    val followUpSession = remember(data, minute) { com.baastiklabs.firewatch.core.engine.Practice.followUp(data, now, tz) }
    val practiceFollowUp = followUpSession?.let { Ladder.rung(it.pieces) }
    val lighterOffer = remember(data, minute) { if (revealed) com.baastiklabs.firewatch.core.engine.Practice.lighterOffer(data, now, tz) else null }
    val workFrom = remember(data, minute) { if (revealed) com.baastiklabs.firewatch.core.engine.Practice.workFromOffer(data, now, tz) else null }
    var practiceAsk by remember { mutableStateOf<Double?>(null) }
    // Life at Clear Air: days nicotine-free, receptor healing, craving logging up front.
    val clearAir = com.baastiklabs.firewatch.core.engine.ClearAir.active(data)
    val daysFree = remember(data, minute / 30) { com.baastiklabs.firewatch.core.engine.ClearAir.daysFree(data, now, tz) }
    val healing = remember(data, minute / 30) { if (clearAir) com.baastiklabs.firewatch.core.engine.ClearAir.receptorHealing(data, now, tz) else null }
    val clearAirOffer = remember(data, minute) { revealed && com.baastiklabs.firewatch.core.engine.ClearAir.offer(data, now, tz) }
    var hadSome by remember { mutableStateOf(false) }
    val stepProgressNow = remember(data, minute) {
        if (target == null || target.pieces <= 0 || early) null else Progress.stepDownProgress(data, now, tz)
    }
    // Travel: the time zone changed since it was last answered.
    val zoneId = java.util.TimeZone.getDefault().id
    val travel = remember(data, zoneId) { com.baastiklabs.firewatch.core.engine.Travel.prompt(data, zoneId, now) }
    LaunchedEffect(data.settings.lastZone, data.settings.onboardingDone) {
        if (data.settings.onboardingDone) com.baastiklabs.firewatch.core.engine.Travel.firstSeen(data.settings, zoneId, repo.now())?.let { f -> repo.updateSettings { f } }
    }
    val heldTotal = remember(data, minute) {
        target?.let { com.baastiklabs.firewatch.core.engine.Control.heldByRung(data, now, tz)[it.pieces]?.first } ?: 0
    }
    val previews = remember(data, minute) {
        val t = target?.pieces
        if (t == null || data.settings.hideDosePreview) emptyMap()
        else data.homeProducts.mapNotNull { p ->
            com.baastiklabs.firewatch.core.engine.BatteryEngine.preview(data, p, if (t > 0) t else 1.0 / 3.0, now, tz, com.baastiklabs.firewatch.data.AppActivity.last(context))?.let { p.id to it }
        }.toMap()
    }
    val morningStretch = remember(data, minute) {
        if (target == null || data.relapseOn) null else com.baastiklabs.firewatch.core.engine.BatteryEngine.morningStretch(data, now, tz)
    }
    val tip = remember(data, minute) {
        com.baastiklabs.firewatch.core.engine.Coaching.bridgeTip(data, target?.pieces?.let { if (it > 0) it else 1.0 / 3.0 }, now, tz, com.baastiklabs.firewatch.data.AppActivity.last(context))
    }
    var steadyInfo by remember { mutableStateOf(false) }
    val welcomeBack = remember(data, minute) { com.baastiklabs.firewatch.core.engine.Control.welcomeBackDays(data, now, tz) }
    val headsUps = remember(data, minute) { Progress.headsUps(data, now, tz) }
    val wave = remember(data, minute) { Insights(data, tz, now).todayCurve(10) }
    val todayWake = remember(data, minute) { Waking.day(data, today, tz) }
    val quality = remember(data, minute) { Quality.of(todayDoses, data.referenceMg) }
    val checkedInToday = data.checkIns.any { it.at.localDate(tz) == today }

    var celebrate by remember { mutableStateOf<com.baastiklabs.firewatch.core.engine.Rung?>(null) }
    var relapseDialog by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf(false) }
    // "Hide next piece timer": the time shows for 30 seconds after a tap, and each tap is counted.
    var revealed30 by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(revealed30) { if (revealed30) { kotlinx.coroutines.delay(30_000); revealed30 = false } }
    val timerHidden = data.settings.hideTimer && !revealed30
    val relapse = com.baastiklabs.firewatch.core.engine.Relapse
    val relapseNext = remember(data, minute) { relapse.nextAt(data, now, tz) }
    val relapseProduct = remember(data) { relapse.product(data)?.name }
    val relapseReason = remember(data, minute) { relapse.recommendation(data, now, tz) }
    val movingOn = remember(data, minute) { relapse.movingOn(data, now) }
    val switchRelapse = com.baastiklabs.firewatch.ui.relapse.rememberRelapseSwitch(vm) { on ->
        scope.launch { snackbar.showSnackbar(if (on) "Relapse prevention mode turned on" else "Relapse prevention mode turned off") }
    }

    fun moveTarget(pieces: Double, reason: String, detail: String = "") {
        scope.launch {
            repo.setTarget(pieces, reason, detail)
            if (reason == "down") {
                celebrate = Ladder.rung(pieces)
                return@launch
            }
            val r = Ladder.rung(pieces)
            snackbar.showSnackbar(
                when (reason) {
                    "down" -> "New rung: ${r.label}. That's real progress."
                    "up" -> "Stepped up to ${r.label}. Your level is more accurate now."
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
                ).copy(removedAt = draft?.removedAt),
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
        if (data.relapseOn) item { com.baastiklabs.firewatch.ui.relapse.RelapseIndicator(if (timerHidden) null else relapseNext, relapseProduct) }
        if (clearAir) item {
            ClearAirCard(daysFree, healing, remember(data) { Insights(data, tz, now).clearAirTimeline().second.firstOrNull { it.second > now } })
        } else item {
            if (showTier) {
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
                    timerHidden = timerHidden,
                    onReveal = {
                        revealed30 = true
                        scope.launch { repo.logTimerCheck(charging = (battery?.charge ?: 1.0) < 0.999) }
                    },
                    stepProgress = stepProgressNow,
                    onStepDown = { p -> moveTarget(p, "down") },
                )
            } else {
                StatusCard(todaySummary, baseline, summaries, lastDoseAt, now, onBackfill)
            }
        }
        travel?.let { tp ->
            item {
                OfferCard(
                    title = "Your time zone changed",
                    body = "You're ${com.baastiklabs.firewatch.core.engine.Travel.describe(tp)}. Use your usual day (${Fmt.minutesOfDay(data.settings.wakeMinutes)}–${Fmt.minutesOfDay(data.settings.sleepMinutes)}) in local time here? It starts from your next wake-up; past days don't move.",
                    primary = "Yes, use local time",
                    onPrimary = { scope.launch { repo.updateSettings { com.baastiklabs.firewatch.core.engine.Travel.useLocal(it, zoneId, repo.now()) } } },
                    secondary = "Keep my home times",
                    onSecondary = { scope.launch { repo.updateSettings { com.baastiklabs.firewatch.core.engine.Travel.keepHome(it, zoneId) } } },
                )
            }
        }
        if (clearAirOffer) item {
            OfferCard(
                title = "Your last 7 days were nicotine-free",
                body = "Switch to Clear Air? The Log tab becomes your days nicotine-free, with craving logging up front. You can step back up any time.",
                primary = "Switch to Clear Air",
                onPrimary = { moveTarget(0.0, "down") },
                secondary = "Not now",
                onSecondary = { scope.launch { repo.updateSettings { it.copy(clearAirOfferSnoozedAt = repo.now()) } } },
            )
        }
        if (showTier && battery != null && !data.relapseOn && !clearAir) {
            morningStretch?.takeIf { it >= 1 }?.let { m ->
                item {
                    Text(
                        "Morning stretch: ${Fmt.duration((m * 60_000).toLong())}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            if (!data.settings.netExplained) item {
                OfferCard(
                    title = "Your net",
                    body = com.baastiklabs.firewatch.core.Help.NET_EXPLAINER,
                    primary = "Got it",
                    onPrimary = { scope.launch { repo.updateSettings { it.copy(netExplained = true) } } },
                    secondary = "Learn more",
                    onSecondary = onHelp,
                )
            }
        }
        tip?.let { t ->
            item {
                Column {
                    Text("💡 ${t.text}", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = {
                        scope.launch { repo.updateSettings { it.copy(tipDismissedAt = it.tipDismissedAt + (t.id to repo.now())) } }
                    }) { Text("Hide for 2 weeks") }
                }
            }
        }
        if (showTier) {
            if (data.settings.showSteadyDays && steadyDays > 0) item {
                Column(Modifier.clickable { steadyInfo = !steadyInfo }) {
                    Text(
                        "✓ $steadyDays steady ${if (steadyDays == 1) "day" else "days"} ⓘ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    if (steadyInfo) Text(
                        com.baastiklabs.firewatch.core.Help.STEADY_EXPLAINER,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val wins = listOfNotNull(
                // A running total that never resets (not the step-down count).
                if (heldTotal > 0 && target != null && !early) "✓ $heldTotal ${if (heldTotal == 1) "day" else "days"} held at ${target.tier.title} in total" else null,
                lighter?.let { "✓ About ${(it * 100).toInt()}% lighter than when you started" },
                daysOff?.takeIf { it > 0 }?.let { "✓ $it days off cigarettes and vapes" },
                journey?.takeIf { it > 0 }?.let { "Journey to Clear Air: ${(it * 100).toInt()}%" },
            )
            practiceStatus?.let { st -> item { com.baastiklabs.firewatch.ui.practice.PracticeStatusRow(st) } }
            if (early) item {
                Text(
                    "Early estimate · firming up as you log your first week" +
                        ((baseline as? com.baastiklabs.firewatch.core.BaselineStatus.InProgress)?.let { " (day ${it.dayNumber} of 7)" } ?: "") + ".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (wins.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    wins.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary) }
                }
            }
        }
        steadyMilestone?.takeIf { data.settings.showSteadyDays }?.let { m ->
            item {
                OfferCard(
                    title = "$m steady days",
                    body = "$m days at or under your pace with no cigarettes or vapes. That's real control.",
                    primary = "Nice",
                    onPrimary = { scope.launch { repo.updateSettings { it.copy(steadyMilestoneSeen = m) } } },
                    secondary = null,
                    onSecondary = {},
                )
            }
        }
        practiceFollowUp?.let { r ->
            item {
                OfferCard(
                    title = "How was ${r.label} pace?",
                    body = "Step down to it, or stay where you are. Either is fine.",
                    primary = "Step down",
                    onPrimary = {
                        scope.launch { repo.updateSettings { it.copy(practiceAnswered = followUpSession?.id ?: "") } }
                        moveTarget(r.pieces, "down")
                    },
                    secondary = "Stay here",
                    onSecondary = { scope.launch { repo.updateSettings { it.copy(practiceAnswered = followUpSession?.id ?: "", stepDownSnoozedAt = repo.now()) } } },
                )
            }
        }
        lighterOffer?.takeIf { target != null }?.let { m ->
            item {
                LighterOfferCard(
                    title = com.baastiklabs.firewatch.core.Help.lighterOfferTitle(m.label),
                    body = com.baastiklabs.firewatch.core.Help.lighterOfferBody(target!!.label, m.tier.title),
                    tryLabel = "Try ${m.tier.title} pace",
                    onTry = { practiceAsk = m.pieces },
                    onStay = { dontAsk ->
                        scope.launch { repo.updateSettings { it.copy(lighterSnoozedAt = repo.now(), lighterOffers = if (dontAsk) false else it.lighterOffers) } }
                    },
                )
            }
        }
        workFrom?.takeIf { target != null }?.let { w ->
            item {
                val hours = (w.coveredMin / 60).toInt()
                OfferCard(
                    title = "${w.rung.tier.title} pace held",
                    body = "You practiced ${w.rung.tier.title} pace for $hours hours, practice net ${Fmt.signedMinutes(w.netMin)}. Work from ${w.rung.tier.title} from now on?",
                    primary = "Work from ${w.rung.tier.title}",
                    onPrimary = {
                        scope.launch {
                            repo.stopPractice()
                            repo.updateSettings { it.copy(practiceAnswered = data.practices.lastOrNull()?.id ?: "") }
                            repo.setTarget(w.rung.pieces, "measured", "from measured level")
                            snackbar.showSnackbar("Working from ${w.rung.label}.")
                        }
                    },
                    secondary = "Keep practicing",
                    onSecondary = { scope.launch { repo.updateSettings { it.copy(workFromSnoozedAt = repo.now()) } } },
                    tertiary = "Back to ${target!!.tier.title} pace",
                    onTertiary = {
                        scope.launch {
                            repo.stopPractice()
                            repo.updateSettings { it.copy(practiceAnswered = data.practices.lastOrNull()?.id ?: "", lighterSnoozedAt = repo.now()) }
                        }
                    },
                )
            }
        }
        welcomeBack?.let { gap ->
            item {
                OfferCard(
                    title = "Welcome back",
                    body = "Want to add what you had while you were away? Rough counts per day, no times needed.",
                    primary = "Add those days",
                    onPrimary = { onFill(gap) },
                    secondary = "Not now",
                    onSecondary = { scope.launch { repo.updateSettings { it.copy(welcomeBackDismissedAt = repo.now()) } } },
                )
            }
        }
        if (revealed && (target == null || early) && measured != null) {
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
                    body = "You've held ${target.label} for ${data.settings.holdDays} days." + (if (readiness?.confident == true) {
                        if (ready) " From your cravings, the next rung should feel like about a ${readiness.predictedNext.toInt()} out of 10, and you ride out ${readiness.capacity}s."
                        else " Heads-up: your cravings suggest the next rung may feel like a ${readiness.predictedNext.toInt()}, above the ${readiness.capacity} you usually ride out. Holding a bit longer is fine too."
                    } else "") + (com.baastiklabs.firewatch.core.engine.Checks.trendNote(data, today, tz)?.let { " $it" } ?: "") + " Or stay here, that's fine too.",
                    primary = "Step down",
                    onPrimary = { moveTarget(stepDown.pieces, "down") },
                    secondary = "Stay here",
                    onSecondary = { scope.launch { repo.updateSettings { it.copy(stepDownSnoozedAt = repo.now()) } } },
                    tertiary = "Try it for a day",
                    onTertiary = { practiceAsk = stepDown.pieces },
                )
            }
        }
        if (stepUpFull != null && target != null) {
            val stepUp = stepUpFull.rung
            item {
                OfferCard(
                    title = "This rung is tough right now",
                    body = "${stepUpFull.why} Stepping up to ${stepUp.label} makes your level more accurate. Step-downs are offered when you're ready.",
                    primary = "Step up",
                    onPrimary = { moveTarget(stepUp.pieces, "up", stepUpFull.why.trimEnd('.').replaceFirstChar { it.lowercase() }) },
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
        com.baastiklabs.firewatch.core.engine.Coaching.swapTip(data, todayDoses)?.let { tip ->
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
          Column {
            if (activeCraving != null) {
                // No buttons: thinking about the app mid-craving can feed the craving.
                Text(
                    "Craving logged at ${Fmt.time(activeCraving.at)}. You've got this.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            if (clearAir) {
                Button(
                    onClick = { cravingSheet = true },
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                    shape = RoundedCornerShape(20.dp),
                ) { Text("Craving? Log it", style = MaterialTheme.typography.titleMedium) }
                if (!hadSome) TextButton(onClick = { hadSome = true }) { Text("I had some") }
                else OutlinedButton(onClick = { friendVape = true }, modifier = Modifier.padding(top = 4.dp)) { Text("Friend's vape") }
            } else run {
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
        }
        if (!clearAir || hadSome) item {
            Column {
                Text("Log a dose", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Tap to log it now · hold for time, amount and more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(if (!clearAir || hadSome) data.homeProducts.chunked(2) else emptyList()) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { product ->
                    ProductButton(
                        preview = previews[product.id],
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
            val canTakeOut = dose.kind == com.baastiklabs.firewatch.core.model.ProductKind.POUCH && dose.removedAt == null &&
                !dose.estimated && now - dose.at in 0..(60 * 60_000L)
            DoseRow(
                dose, data.refMgAt(dose.at), onClick = { editing = dose },
                onTookOut = if (canTakeOut) ({ scope.launch { repo.updateDose(dose.copy(removedAt = repo.now())) } }) else null,
            )
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
                val error = com.baastiklabs.firewatch.data.FeedbackSender.send(context, type, text, details, name)
                snackbar.showSnackbar(error?.let { "$it. Saved: send it later from Settings → Unsent suggestions." } ?: "Thanks, sent!")
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
        EditDoseSheet(vm, dose, data.referenceMg, snackbar, scope, onDone = { editing = null })
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
    practiceAsk?.let { pieces ->
        com.baastiklabs.firewatch.ui.practice.PracticeDurationDialog(
            rungTitle = Ladder.rung(pieces).tier.title,
            initialUntilBedtime = data.settings.practiceUntilBedtime,
            onStart = { untilBedtime ->
                practiceAsk = null
                scope.launch {
                    repo.startPractice(pieces, untilBedtime)
                    snackbar.showSnackbar("Practice pace on: ${Ladder.rung(pieces).tier.title}. ${com.baastiklabs.firewatch.core.Help.PRACTICE_STOP_NOTE}")
                }
            },
            onDismiss = { practiceAsk = null },
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
    // The screen's scope, not the sheet's: the sheet leaves as it saves, which would cancel it.
    scope: kotlinx.coroutines.CoroutineScope,
    onDone: () -> Unit,
) {
    DoseSheet(
        title = dose.productName.ifBlank { "Dose" },
        kind = dose.kind,
        labelMg = dose.labelMg,
        absorption = dose.absorption,
        referenceMg = referenceMg,
        initial = DoseDraft(dose.at, dose.multiplier, dose.duration, dose.acidicDrink, dose.tags, dose.removedAt),
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
                        removedAt = draft.removedAt,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductButton(
    preview: Double?,
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
            .heightIn(min = if (preview != null) 112.dp else 96.dp)
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
            // What logging it now would do to net (timing and size): neutral, never "earn it".
            preview?.takeIf { kotlin.math.abs(it) >= 1 }?.let {
                Text(
                    if (it > 0) "+${Fmt.duration((it * 60_000).toLong())} stretch" else "+${Fmt.duration((-it * 60_000).toLong())} pull",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
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
fun DoseRow(dose: Dose, referenceMg: Double, onClick: () -> Unit, onTookOut: (() -> Unit)? = null) {
    val extras = buildList {
        if (dose.estimated) add("estimated")
        if (dose.multiplier != 1.0) add("×${Fmt.pieces(dose.multiplier)}")
        val out = dose.removedAt?.takeIf { it > dose.at }
        if (out != null) add("in for ${((out - dose.at) / 60_000).toInt()} min")
        else if (dose.duration != com.baastiklabs.firewatch.core.model.Duration.FULL) add(dose.duration.name.lowercase())
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
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onTookOut != null) androidx.compose.material3.TextButton(onClick = onTookOut) { Text("Took it out") }
                Text(Fmt.piecesLabel(dose.pieces(referenceMg)), style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}


/** The lighter-level practice offer: only ever offers to practice, with "Don't ask me again". */
@Composable
private fun LighterOfferCard(title: String, body: String, tryLabel: String, onTry: () -> Unit, onStay: (dontAsk: Boolean) -> Unit) {
    var dontAsk by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onTry) { Text(tryLabel) }
                OutlinedButton(onClick = { onStay(dontAsk) }) { Text("Stay here") }
            }
            Row(Modifier.clickable { dontAsk = !dontAsk }, verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(checked = dontAsk, onCheckedChange = { dontAsk = it })
                Text("Don't ask me again", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Life at Clear Air: a total that only goes up, receptor healing and the next recovery step. */
@Composable
private fun ClearAirCard(daysFree: Int, healing: Double?, next: Pair<String, Long>?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Clear Air", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$daysFree", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(if (daysFree == 1) "day nicotine-free" else "days nicotine-free", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
            }
            healing?.let {
                Text("Receptors heading back to typical: ≈ ${(it * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(progress = { it.toFloat() }, modifier = Modifier.fillMaxWidth())
            }
            next?.let { (label, at) ->
                Text("Next on the recovery timeline: $label (in about ${Fmt.duration(at - System.currentTimeMillis())})", style = MaterialTheme.typography.bodySmall)
            }
            Text("A total that only goes up. Logging something just counts it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
