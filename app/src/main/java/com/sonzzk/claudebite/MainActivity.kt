package com.sonzzk.claudebite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.sonzzk.claudebite.ui.navigation.ClaudeBiteRoot
import com.sonzzk.claudebite.ui.theme.ClaudeBiteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ClaudeBiteApp).container
        setContent {
            ClaudeBiteTheme {
                ClaudeBiteRoot(container)
            }
        }
    }
}
