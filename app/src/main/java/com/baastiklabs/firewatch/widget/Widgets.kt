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

private val Bg = ColorProvider(Color(0xFF1E1613))
private val Ember = ColorProvider(Color(0xFFFF7A2F))
private val OnDark = ColorProvider(Color(0xFFF3E7DF))
private val Muted = ColorProvider(Color(0xFFC9B5A8))

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
        val status = data.targetPieces?.let { target ->
            val b = Progress.battery(data, if (target > 0) target else 1.0 / 3.0, now, tz)
            when (b.state) {
                BatteryState.CLEAR -> "Clear for one if you want it"
                BatteryState.CHARGING -> "Next piece ~${b.readyAt?.let { Fmt.time(it) } ?: "later"}"
                BatteryState.MORNING_DELAY -> "Morning delay"
                BatteryState.WIND_DOWN -> "Winding down"
                BatteryState.ASLEEP -> "Sleeping hours"
            } to b.charge.coerceIn(0.0, 1.0)
        }
        val products = data.homeProducts.filter { !(it.kind == ProductKind.VAPE && it.borrowedFrom != null) }.take(4)
        val puffs = Puffs.get(context)
        provideContent {
            Column(
                GlanceModifier.fillMaxSize().background(Bg).cornerRadius(20.dp).padding(10.dp),
            ) {
                Text(
                    status?.first ?: "Firewatch",
                    style = TextStyle(color = OnDark, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
                )
                status?.second?.let { charge ->
                    Row(GlanceModifier.fillMaxWidth().height(6.dp).background(ColorProvider(Color(0xFF3A2C25))).cornerRadius(3.dp)) {
                        Spacer(GlanceModifier.height(6.dp).width((charge * 160).toInt().dp).background(Ember).cornerRadius(3.dp))
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
                                colors = ButtonDefaults.buttonColors(backgroundColor = Ember, contentColor = ColorProvider(Color(0xFF2A1206))),
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
        provideContent {
            Column(GlanceModifier.fillMaxSize().background(Bg).cornerRadius(20.dp).padding(10.dp)) {
                if (active != null) {
                    Text("Riding out a ${active.intensity}…", style = TextStyle(color = OnDark, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                    Spacer(GlanceModifier.height(6.dp))
                    Row(GlanceModifier.fillMaxWidth()) {
                        Button(text = "It passed", onClick = actionRunCallback<FinishCravingAction>(actionParametersOf(levelKey to 1)), modifier = GlanceModifier.defaultWeight().padding(2.dp),
                            colors = ButtonDefaults.buttonColors(backgroundColor = Ember, contentColor = ColorProvider(Color(0xFF2A1206))))
                        Button(text = "I used", onClick = actionRunCallback<FinishCravingAction>(actionParametersOf(levelKey to 0)), modifier = GlanceModifier.defaultWeight().padding(2.dp))
                    }
                } else {
                    Text("Craving? How strong (1–10)", style = TextStyle(color = OnDark, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                    Text("1 passing · 5 distracting · 10 worst", style = TextStyle(color = Muted, fontSize = 11.sp))
                    listOf(1..5, 6..10).forEach { range ->
                        Row(GlanceModifier.fillMaxWidth()) {
                            range.forEach { level ->
                                Button(
                                    text = "$level",
                                    onClick = actionRunCallback<LogCravingAction>(actionParametersOf(levelKey to level)),
                                    modifier = GlanceModifier.defaultWeight().padding(2.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = ColorProvider(craving(level)),
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

    private fun craving(level: Int): Color {
        val f = (level - 1) / 9f
        return Color(0xFF5FA893).let { a ->
            val b = Color(0xFFE0443A)
            Color(a.red + (b.red - a.red) * f, a.green + (b.green - a.green) * f, a.blue + (b.blue - a.blue) * f)
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

class FinishCravingAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val repo = app(context).graph.repository
        repo.ensureLoaded()
        val active = Cravings.active(repo.data.value, System.currentTimeMillis()) ?: return
        repo.finishCraving(active.id, if ((parameters[levelKey] ?: 1) == 1) CravingOutcome.RODE_OUT else CravingOutcome.USED)
        refreshWidgets(context)
    }
}

