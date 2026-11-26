package com.coolplayer.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.coolplayer.music.ui.adaptive.ProvideAdaptive
import com.coolplayer.music.ui.theme.AppTheme
import com.coolplayer.music.ui.theme.ThemeState
import com.coolplayer.music.ui.tv.TvMainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // TV 版固定浅色主题，避免深色/跟随系统下浅色背景与文字对比度问题。
            val darkTheme = false
            // TV 关闭动态取色：电视壁纸多为浅色，动态取色会把 primary 拉成浅紫，
            // 导致当前导航项 / 当前歌曲名 / 当前歌词 / 进度条（均用 primary 高亮）
            // 在白底上发白看不清。固定用 seedColor（默认蓝 #3D5AFE）。
            // 固定用默认深蓝种子，不读 ThemeState.homeSeed（它是持久化值，
            // 若之前在设置页把主题色改成过白色会残留，导致 scheme.primary 变白、
            // 所有用 primary 的文字/图标在看不清）。TV 版设置页已删除，无需持久化色。
            AppTheme(seedColor = ThemeState.homeSeedDefault, darkTheme = darkTheme, allowDynamic = false) {
                ProvideAdaptive {
                    TvMainScreen()
                }
            }
        }
    }
}
