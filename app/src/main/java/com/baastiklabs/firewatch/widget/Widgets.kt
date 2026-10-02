package com.baastiklabs.firewatch.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.baastiklabs.firewatch.FirewatchApp
import com.baastiklabs.firewatch.MainActivity
import com.baastiklabs.firewatch.core.Cravings
import com.baastiklabs.firewatch.core.engine.BatteryState
import com.baastiklabs.firewatch.core.engine.FriendVape
import com.baastiklabs.firewatch.core.engine.Progress
import com.baastiklabs.firewatch.core.model.CravingOutcome
import com.baastiklabs.firewatch.core.model.Dose
import com.baastiklabs.firewatch.core.model.ProductKind
import com.baastiklabs.firewatch.core.model.SpeedProfile
import com.baastiklabs.firewatch.core.toDose
import com.baastiklabs.firewatch.ui.Fmt
import kotlinx.datetime.TimeZone

/** Widget colours follow the chosen theme (and the phone's light/dark setting). */
private class WidgetColors(val p: com.baastiklabs.firewatch.core.Themes.Palette) {
    private fun c(h: String) = ColorProvider(com.baastiklabs.firewatch.ui.theme.hexColor(h))
    val bg = c(p.surface); val ember = c(p.primary); val onDark = c(p.text); val muted = c(p.muted)
    val track = c(p.surface3); val onEmber = c(p.onPrimary)
    fun craving(level: Int): Color = androidx.compose.ui.graphics.lerp(
        com.baastiklabs.firewatch.ui.theme.hexColor(p.cravingLow), com.baastiklabs.firewatch.ui.theme.hexColor(p.cravingHigh), (level - 1) / 9f)

    companion object {
        fun of(context: Context, s: com.baastiklabs.firewatch.core.model.Settings): WidgetColors {
            val dark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            return WidgetColors(com.baastiklabs.firewatch.core.Themes.palette(s.theme, s.themeMode, dark, s.trueBlack, s.calmColours, s.colourBlindCharts))
        }
    }
}

private val productKey = ActionParameters.Key<String>("product")
private val levelKey = ActionParameters.Key<Int>("level")

private fun app(context: Context) = context.applicationContext as FirewatchApp

/** Refreshes every Firewatch widget (called after any data change). */
suspend fun refreshWidgets(context: Context) {
    runCatching { QuickLogWidget().updateAll(context) }
    runCatching { CravingWidget().updateAll(context) }
}

private object Puffs {
    fun get(context: Context) = context.getSharedPreferences("fw_widget", Context.MODE_PRIVATE).getInt("puffs", 0)
    fun set(context: Context, n: Int) = context.getSharedPreferences("fw_widget", Context.MODE_PRIVATE).edit().putInt("puffs", n).apply()
}

// ---------------- Quick log ----------------

class QuickLogWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = app(context).graph.repository
        repo.ensureLoaded()
        val data = repo.data.value
        val now = System.currentTimeMillis()
        val tz = TimeZone.currentSystemDefault()
        Fmt.systemUse24h = android.text.format.DateFormat.is24HourFormat(context)
        Fmt.applyTimeFormat(data.settings.timeFormat)
        val relapseNext = com.baastiklabs.firewatch.core.engine.Relapse.nextAt(data, now, tz)
        val hideTimer = data.settings.hideTimer
        val status = if (hideTimer) data.targetPieces?.let { t ->
            "Open Firewatch to see your next piece time" to Progress.battery(data, if (t > 0) t else 1.0 / 3.0, now, tz).charge.coerceIn(0.0, 1.0)
        } else if (relapseNext != null) "Next scheduled piece ${Fmt.time(relapseNext)}" to 1.0 else data.targetPieces?.let { target ->
            val b = Progress.battery(data, if (target > 0) target else 1.0 / 3.0, now, tz, com.baastiklabs.firewatch.data.AppActivity.last(context))
            when (b.state) {
                BatteryState.CLEAR -> "Clear for one if you want it"
                BatteryState.CHARGING -> "Next piece ~${b.readyAt?.let { Fmt.time(it) } ?: "later"}"
                BatteryState.FULL_AT_WAKE -> "Full when you wake up"
                BatteryState.MORNING_DELAY -> "First piece goal ${b.readyAt?.let { Fmt.time(it) } ?: ""}"
                BatteryState.WIND_DOWN -> "Winding down"
                BatteryState.ASLEEP -> "Sleeping hours · fresh start at wake-up"
            } to b.charge.coerceIn(0.0, 1.0)
        }
        val products = data.homeProducts.filter { !(it.kind == ProductKind.VAPE && it.borrowedFrom != null) }.take(4)
        val puffs = Puffs.get(context)
        val wc = WidgetColors.of(context, app(context).graph.repository.data.value.settings)
        provideContent {
            Column(
                GlanceModifier.fillMaxSize().background(wc.bg).cornerRadius(20.dp).padding(10.dp),
            ) {
                Text(
                    status?.first ?: "Firewatch",
                    style = TextStyle(color = wc.onDark, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
                )
                status?.second?.let { charge ->
                    Row(GlanceModifier.fillMaxWidth().height(6.dp).background(wc.track).cornerRadius(3.dp)) {
                        Spacer(GlanceModifier.height(6.dp).width((charge * 160).toInt().dp).background(wc.ember).cornerRadius(3.dp))
                    }
                }
                Spacer(GlanceModifier.height(6.dp))
                products.chunked(2).forEach { row ->
                    Row(GlanceModifier.fillMaxWidth()) {
                        row.forEach { p ->
                            Button(
                                text = p.name,
                                onClick = actionRunCallback<LogProductAction>(actionParametersOf(productKey to p.id)),
                                modifier = GlanceModifier.defaultWeight().padding(2.dp),
                                colors = ButtonDefaults.buttonColors(backgroundColor = wc.ember, contentColor = wc.onEmber),
                            )
                        }
                    }
                }
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Button(text = "+1 puff", onClick = actionRunCallback<PuffAction>(), modifier = GlanceModifier.defaultWeight().padding(2.dp))
                    if (puffs > 0) {
                        Button(text = "Log $puffs", onClick = actionRunCallback<LogPuffsAction>(), modifier = GlanceModifier.defaultWeight().padding(2.dp))
                    }
                }
            }
        }
    }
}

class QuickLogWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickLogWidget()
}

class LogProductAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val repo = app(context).graph.repository
        repo.ensureLoaded()
        val product = repo.data.value.productsById[parameters[productKey]] ?: return
        val now = repo.now()
        repo.logDose(product.toDose(repo.newId(), now, now))
        refreshWidgets(context)
    }
}

class PuffAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Puffs.set(context, Puffs.get(context) + 1)
        QuickLogWidget().updateAll(context)
    }
}

class LogPuffsAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val n = Puffs.get(context)
        if (n <= 0) return
        val repo = app(context).graph.repository
        repo.ensureLoaded()
        val (lo, hi) = FriendVape.range(null, n..n)
        val now = repo.now()
        repo.logDose(
            Dose(
                id = repo.newId(), productId = "friends-vape", at = now, productName = "Friend's vape ($n puffs)",
                kind = ProductKind.VAPE, speed = SpeedProfile.SPIKE, rangeLowMg = lo, rangeHighMg = hi, borrowed = true, loggedAt = now,
            ),
        )
        Puffs.set(context, 0)
        refreshWidgets(context)
    }
}

// ---------------- Cravings ----------------

class CravingWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = app(context).graph.repository
        repo.ensureLoaded()
        val active = Cravings.active(repo.data.value, System.currentTimeMillis())
        val wc = WidgetColors.of(context, app(context).graph.repository.data.value.settings)
        provideContent {
            Column(GlanceModifier.fillMaxSize().background(wc.bg).cornerRadius(20.dp).padding(10.dp)) {
                if (active != null) {
                    // No buttons: thinking about the app mid-craving can feed the craving.
                    Text("Craving logged at ${Fmt.time(active.at)}.", style = TextStyle(color = wc.onDark, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                    Spacer(GlanceModifier.height(4.dp))
                    Text("You've got this.", style = TextStyle(color = wc.muted, fontSize = 12.sp))
                } else {
                    Text("Craving? How strong (1–10)", style = TextStyle(color = wc.onDark, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                    Text("1 passing · 5 distracting · 10 worst", style = TextStyle(color = wc.muted, fontSize = 11.sp))
                    listOf(1..5, 6..10).forEach { range ->
                        Row(GlanceModifier.fillMaxWidth()) {
                            range.forEach { level ->
                                Button(
                                    text = "$level",
                                    onClick = actionRunCallback<LogCravingAction>(actionParametersOf(levelKey to level)),
                                    modifier = GlanceModifier.defaultWeight().padding(2.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = ColorProvider(wc.craving(level)),
                                        contentColor = ColorProvider(Color.White),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

}

class CravingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CravingWidget()
}

class LogCravingAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val repo = app(context).graph.repository
        repo.ensureLoaded()
        repo.startCraving(parameters[levelKey] ?: 5)
        refreshWidgets(context)
    }
}


