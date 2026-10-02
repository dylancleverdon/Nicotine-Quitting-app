package com.baastiklabs.firewatch.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baastiklabs.firewatch.core.Themes
import com.baastiklabs.firewatch.core.model.Settings
import com.baastiklabs.firewatch.core.records.FirewatchData
import com.baastiklabs.firewatch.ui.FirewatchViewModel
import com.baastiklabs.firewatch.ui.theme.hexColor
import kotlinx.coroutines.launch

/** Settings → Appearance → More themes: on its own screen so Settings stays uncluttered. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ThemesScreen(vm: FirewatchViewModel, data: FirewatchData, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val s = data.settings
    fun update(t: (Settings) -> Settings) = scope.launch { vm.repository.updateSettings(t) }
    val systemDark = isSystemInDarkTheme()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("More themes") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Light or dark", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "Follow phone", "light" to "Always light", "dark" to "Always dark").forEach { (v, l) ->
                    FilterChip(selected = s.themeMode == v, onClick = { update { it.copy(themeMode = v) } }, label = { Text(l) })
                }
            }
            Themes.all.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { t ->
                        val p = Themes.palette(t.id, s.themeMode, systemDark, s.trueBlack, s.calmColours, s.colourBlindCharts)
                        val on = s.theme == t.id
                        Column(
                            Modifier.weight(1f).heightIn(min = 96.dp).clip(RoundedCornerShape(14.dp))
                                .background(hexColor(p.bg))
                                .border(if (on) 3.dp else 1.dp, hexColor(if (on) p.primary else p.line), RoundedCornerShape(14.dp))
                                .clickable { update { it.copy(theme = t.id) } }
                                .semantics { role = Role.RadioButton; selected = on; contentDescription = "${t.name} theme: ${t.feel}" }
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(p.primary, p.secondary, p.tertiary, p.heat[3]).forEach { c ->
                                    Box(Modifier.size(16.dp).clip(CircleShape).background(hexColor(c)))
                                }
                            }
                            Text(t.name, color = hexColor(p.text), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Text(t.feel, color = hexColor(p.muted), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (row.size == 1) Box(Modifier.weight(1f))
                }
            }
            Text("Colour options", style = MaterialTheme.typography.titleSmall)
            Switchy("Calmer colours, no red", "Softer colours everywhere, and no red at all.", s.calmColours) { v -> update { it.copy(calmColours = v) } }
            Switchy("Colour-blind-safe charts", "Product colours that stay distinct for the common kinds of colour blindness.", s.colourBlindCharts) { v -> update { it.copy(colourBlindCharts = v) } }
            Switchy("True black", "Pure black backgrounds in dark mode (saves battery on OLED screens).", s.trueBlack) { v -> update { it.copy(trueBlack = v) } }
            Text(
                "Only colours change: tier names stay the same. Every theme is checked for readable contrast. Widgets follow the theme too.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Switchy(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
