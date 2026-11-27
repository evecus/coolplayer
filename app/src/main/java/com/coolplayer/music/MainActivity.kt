package com.coolplayer.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.coolplayer.music.ui.adaptive.ProvideAdaptive
import com.coolplayer.music.ui.theme.AppTheme
import com.coolplayer.music.ui.theme.ThemeState
import com.coolplayer.music.ui.tv.TvMainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme(seedColor = ThemeState.homeSeedDefault) {
                ProvideAdaptive {
                    TvMainScreen()
                }
            }
        }
    }
}
