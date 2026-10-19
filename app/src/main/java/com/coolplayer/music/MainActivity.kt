package com.coolplayer.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.coolplayer.music.player.PlayerConnection
import com.coolplayer.music.ui.navigation.AppNavigation
import com.coolplayer.music.ui.theme.AppTheme
import com.coolplayer.music.ui.theme.ProvideWindowInfo
import com.coolplayer.music.ui.theme.ThemeState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkTheme = when (ThemeState.themeMode) {
                0 -> isSystemInDarkTheme()
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            AppTheme(seedColor = ThemeState.homeSeed, darkTheme = darkTheme) {
                ProvideWindowInfo {
                    AppNavigation()
                }
            }
        }
    }
}
