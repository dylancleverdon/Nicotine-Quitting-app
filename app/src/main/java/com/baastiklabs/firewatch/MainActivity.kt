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

class MainActivity : ComponentActivity() {
    private val vm: FirewatchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !vm.loaded.value }
        Fmt.use24h = DateFormat.is24HourFormat(this)
        enableEdgeToEdge()
        setContent {
            FirewatchTheme {
                FirewatchRoot(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Fmt.use24h = DateFormat.is24HourFormat(this)
    }
}
