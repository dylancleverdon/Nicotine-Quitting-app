package com.baastiklabs.firewatch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baastiklabs.firewatch.BuildConfig
import com.baastiklabs.firewatch.core.update.Changelog
import com.baastiklabs.firewatch.ui.calendar.CalendarScreen
import com.baastiklabs.firewatch.ui.calendar.DayScreen
import com.baastiklabs.firewatch.ui.home.HomeScreen
import com.baastiklabs.firewatch.ui.onboarding.OnboardingScreen
import com.baastiklabs.firewatch.ui.products.ProductsScreen
import com.baastiklabs.firewatch.ui.settings.AboutScreen
import com.baastiklabs.firewatch.ui.settings.SettingsScreen
import java.time.LocalDate

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Log", Icons.Filled.Home),
    Tab("calendar", "Calendar", Icons.Filled.DateRange),
    Tab("settings", "Settings", Icons.Filled.Settings),
)

@Composable
fun FirewatchRoot(vm: FirewatchViewModel) {
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()

    if (!loaded) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    if (!data.settings.onboardingDone) {
        OnboardingScreen(vm, data)
        return
    }

    WhatsNewGate(vm)

    var route by rememberSaveable { mutableStateOf("home") }
    val snackbar = remember { SnackbarHostState() }
    val isTab = tabs.any { it.route == route }
    BackHandler(enabled = !isTab) {
        route = when {
            route.startsWith("day/") -> "calendar"
            else -> "settings"
        }
    }
    BackHandler(enabled = isTab && route != "home") { route = "home" }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (isTab) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { route = tab.route },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                route == "home" -> HomeScreen(vm, data, now, snackbar)
                route == "calendar" -> CalendarScreen(data, now, onOpenDay = { route = "day/$it" })
                route == "settings" -> SettingsScreen(
                    vm = vm,
                    data = data,
                    update = update,
                    now = now,
                    snackbar = snackbar,
                    onOpenProducts = { route = "products" },
                    onOpenAbout = { route = "about" },
                )
                route.startsWith("day/") -> DayScreen(
                    vm = vm,
                    data = data,
                    date = LocalDate.parse(route.removePrefix("day/")),
                    snackbar = snackbar,
                    onBack = { route = "calendar" },
                )
                route == "products" -> ProductsScreen(vm, data, onBack = { route = "settings" })
                route == "about" -> AboutScreen(onBack = { route = "settings" })
            }
        }
    }
}

/** After an update (or a rollback), a short "What's new" appears once. */
@Composable
private fun WhatsNewGate(vm: FirewatchViewModel) {
    val context = LocalContext.current
    val current = BuildConfig.VERSION_NAME
    val lastSeen = remember { vm.updateState.lastSeenVersion }
    var show by remember { mutableStateOf(lastSeen != null && lastSeen != current) }
    LaunchedEffect(Unit) {
        if (lastSeen == null) vm.updateState.lastSeenVersion = current
    }
    if (!show) return

    val sections = remember { Changelog.parse(readChangelog(context)) }
    val base = current.substringBefore(' ')
    val dismiss = {
        vm.updateState.lastSeenVersion = current
        show = false
    }
    if (BuildConfig.IS_ROLLBACK_BUILD) {
        ChangelogDialog(
            title = "Went back to version $base",
            intro = "Firewatch went back to the previous version. Your logs, products and settings are untouched.",
            sections = emptyList(),
            onDismiss = dismiss,
        )
    } else {
        ChangelogDialog(
            title = "What's new",
            intro = "Firewatch updated itself to version $base.",
            sections = Changelog.since(sections, lastSeen?.substringBefore(' ')),
            onDismiss = dismiss,
        )
    }
}
