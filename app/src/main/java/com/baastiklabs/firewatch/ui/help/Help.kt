package com.baastiklabs.firewatch.ui.help

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.baastiklabs.firewatch.core.Help

/** The first-launch welcome tour. Text is shared with the web app (core Help). */
@Composable
fun TourScreen(onDone: () -> Unit, onWhy: () -> Unit) {
    var i by rememberSaveable { mutableIntStateOf(0) }
    val page = Help.tour[i]
    val last = i == Help.tour.lastIndex
    // Surface sets the theme's text colour (without it, text defaults to black on the dark background).
    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onDone) { Text("Skip") } }
        Spacer(Modifier.weight(1f))
        Text(page.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.size(16.dp))
        Text(page.body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (last) TextButton(onClick = onWhy) { Text("Why Firewatch works this way") }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Help.tour.indices.forEach { j ->
                Box(Modifier.size(8.dp).background(if (j == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape))
            }
        }
        Spacer(Modifier.size(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (i > 0) TextButton(onClick = { i-- }) { Text("Back") }
            Spacer(Modifier.weight(1f))
            Button(onClick = { if (last) onDone() else i++ }) { Text(if (last) "Let's go" else "Next") }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Screen(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
        )
        content()
    }
    }
}

@Composable
fun WhyScreen(onBack: () -> Unit) {
    Screen("Why Firewatch works this way", onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Help.why.forEach { p ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(p.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(p.body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun HelpScreen(onBack: () -> Unit, onTour: () -> Unit, onWhy: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val results = remember(query) { Help.search(query) }
    Screen("Help", onBack) {
        LazyColumn(
            Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(query, { query = it }, label = { Text("Search help") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onTour) { Text("Welcome tour") }
                    OutlinedButton(onClick = onWhy) { Text("Why it works this way") }
                }
            }
            listOf(Help.HOW, Help.MEANS, Help.QUESTIONS).forEach { section ->
                val matches = results.filter { it.section == section }
                if (matches.isNotEmpty()) {
                    item { Text(section, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
                    items(matches, key = { it.id }) { a ->
                        Column(
                            Modifier.fillMaxWidth().clickable { open = if (open == a.id) null else a.id }.padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(a.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            if (open == a.id) Text(a.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (results.isEmpty()) item { Text("Nothing matches \"$query\".", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
