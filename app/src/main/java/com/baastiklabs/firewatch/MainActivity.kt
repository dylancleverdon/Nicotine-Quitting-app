package com.baastiklabs.firewatch

import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.baastiklabs.firewatch.ui.FirewatchRoot
import com.baastiklabs.firewatch.ui.FirewatchViewModel
import com.baastiklabs.firewatch.ui.Fmt
import com.baastiklabs.firewatch.ui.theme.FirewatchTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
    private val vm: FirewatchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !vm.loaded.value }
        Fmt.systemUse24h = DateFormat.is24HourFormat(this)
        Fmt.use24h = Fmt.systemUse24h
        enableEdgeToEdge()
        setContent {
            val data by vm.data.collectAsState()
            FirewatchTheme(data.settings) {
                FirewatchRoot(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Fmt.systemUse24h = DateFormat.is24HourFormat(this)
        Fmt.applyTimeFormat(vm.data.value.settings.timeFormat)
        com.baastiklabs.firewatch.data.AppActivity.mark(this)
    }
}
