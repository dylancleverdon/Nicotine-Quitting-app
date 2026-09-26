package com.baastiklabs.firewatch.health

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.baastiklabs.firewatch.core.records.FirewatchData
import com.baastiklabs.firewatch.ui.FirewatchViewModel
import com.baastiklabs.firewatch.ui.charts.TrendLine
import com.baastiklabs.firewatch.ui.insights.ChartCard
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Reads resting heart rate and sleep from Health Connect (where fitness watches write them). */
object Watch {
    val permissions = setOf(
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    fun available(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun granted(context: Context): Boolean = runCatching {
        HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions().containsAll(permissions)
    }.getOrDefault(false)

    data class Daily(val date: LocalDate, val restingHr: Double?, val sleepHours: Double?)

    suspend fun last30Days(context: Context): List<Daily> {
        val client = HealthConnectClient.getOrCreate(context)
        val zone = ZoneId.systemDefault()
        val end = Instant.now()
        val start = end.minus(30, ChronoUnit.DAYS)
        val hr = client.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, TimeRangeFilter.between(start, end))).records
        val sleep = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(start, end))).records
        val hrByDay = hr.groupBy { it.time.atZone(zone).toLocalDate() }.mapValues { (_, v) -> v.map { it.beatsPerMinute.toDouble() }.average() }
        val sleepByDay = sleep.groupBy { it.endTime.atZone(zone).toLocalDate() }
            .mapValues { (_, v) -> v.sumOf { ChronoUnit.MINUTES.between(it.startTime, it.endTime) } / 60.0 }
        return (hrByDay.keys + sleepByDay.keys).sorted().map { Daily(it, hrByDay[it], sleepByDay[it]) }
    }
}

/** Insights card: the watch's resting heart rate and sleep next to the nicotine line. */
@Composable
fun WatchOverlayCard(vm: FirewatchViewModel, data: FirewatchData) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(false) }
    var days by remember { mutableStateOf<List<Watch.Daily>>(emptyList()) }
    val available = remember { runCatching { Watch.available(context) }.getOrDefault(false) }
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { result ->
        granted = result.containsAll(Watch.permissions)
    }
    LaunchedEffect(data.settings.watchOverlay, granted) {
        if (available && data.settings.watchOverlay) {
            granted = Watch.granted(context)
            if (granted) days = runCatching { Watch.last30Days(context) }.getOrDefault(emptyList())
        }
    }
    ChartCard("Watch overlay", "If you wear a fitness watch, resting heart rate and sleep plotted next to your nicotine.") {
        when {
            !available -> Text("Needs Health Connect (built into Android 14+, or from the Play Store) and a watch app that writes to it.", style = MaterialTheme.typography.bodySmall)
            !data.settings.watchOverlay -> Button(onClick = { scope.launch { vm.repository.updateSettings { it.copy(watchOverlay = true) } } }) { Text("Connect my watch") }
            !granted -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Allow Firewatch to read resting heart rate and sleep. Nothing leaves your phone.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { launcher.launch(Watch.permissions) }) { Text("Allow") }
                OutlinedButton(onClick = { scope.launch { vm.repository.updateSettings { it.copy(watchOverlay = false) } } }) { Text("Not now") }
            }
            days.isEmpty() -> Text("No heart rate or sleep data from the last 30 days yet.", style = MaterialTheme.typography.bodySmall)
            else -> {
                val ref = data.referenceMg
                val zone = ZoneId.systemDefault()
                val pieces = days.map { d ->
                    data.doses.filter { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() == d.date }
                        .sumOf { com.baastiklabs.firewatch.core.Absorption.pieces(com.baastiklabs.firewatch.core.Absorption.absorbedMg(it.labelMg, it.absorption, it.kind, it.multiplier, it.duration, it.acidicDrink), ref) }
                }
                val hr = days.map { it.restingHr ?: 0.0 }
                if (hr.any { it > 0 }) {
                    Text("Resting heart rate (teal) vs pieces (orange)", style = MaterialTheme.typography.labelMedium)
                    TrendLine(norm(pieces), second = norm(hr), secondColor = MaterialTheme.colorScheme.tertiary)
                }
                val sl = days.map { it.sleepHours ?: 0.0 }
                if (sl.any { it > 0 }) {
                    Text("Sleep hours (teal) vs pieces (orange)", style = MaterialTheme.typography.labelMedium)
                    TrendLine(norm(pieces), second = norm(sl), secondColor = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

/** Scales a series to 0..1 so two different units can share one chart. */
private fun norm(values: List<Double>): List<Double> {
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: return values
    return values.map { it / max }
}
