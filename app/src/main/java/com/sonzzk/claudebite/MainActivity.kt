package com.sonzzk.claudebite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.sonzzk.claudebite.ui.navigation.ClaudeBiteRoot
import com.sonzzk.claudebite.ui.theme.ClaudeBiteTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.sonzzk.claudebite.data.prefs.AppSettings
import com.sonzzk.claudebite.ui.components.LocalAppSettings
import com.sonzzk.claudebite.ui.components.LocalMascotStyle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ClaudeBiteApp).container
        setContent {
            val settings by container.profiles.settings.collectAsState(initial = AppSettings())
            val dark = when (settings.theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            ClaudeBiteTheme(darkTheme = dark, pixelFont = settings.pixelFont) {
                CompositionLocalProvider(
                    LocalMascotStyle provides settings.mascotStyle,
                    LocalAppSettings provides settings,
                ) {
                    ClaudeBiteRoot(container)
                }
            }
        }
    }
}
