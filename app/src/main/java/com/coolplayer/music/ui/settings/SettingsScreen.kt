package com.coolplayer.music.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coolplayer.music.data.StorageService
import com.coolplayer.music.ui.theme.ThemeState
import com.coolplayer.music.ui.widget.OptionDialog

/**
 * 设置页：
 *
 * - 主题模式（跟随系统/浅色/深色）
 * - 动态取色（Android 12+）
 * - 封面取色（播放页从封面提取主题色）
 * - 首页主题色 / 播放页主题色（手动选择，分控主题）
 * - 文件夹黑名单（管理）
 * - 重新扫描音乐库
 * - 关于
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var themeMode by remember { mutableStateOf(ThemeState.themeMode) }
    var dynamicColor by remember { mutableStateOf(ThemeState.dynamicColor) }
    var useCoverColor by remember { mutableStateOf(ThemeState.useCoverColor) }
    var homeSeed by remember { mutableStateOf(ThemeState.homeSeed) }
    var playerSeed by remember { mutableStateOf(ThemeState.playerSeed) }

    var showThemeDialog by remember { mutableStateOf(false) }
    var showHomeColorDialog by remember { mutableStateOf(false) }
    var showPlayerColorDialog by remember { mutableStateOf(false) }
    var showBlacklistDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(scheme.background)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    tint = scheme.onBackground,
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(onClick = onBack)
                        .padding(8.dp)
                )
                Text(
                    text = "设置",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onBackground
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    SettingsHeader("外观")
                    SettingsItem(
                        title = "主题模式",
                        subtitle = when (themeMode) {
                            0 -> "跟随系统"
                            1 -> "浅色"
                            2 -> "深色"
                            else -> "跟随系统"
                        }
                    ) {
                        showThemeDialog = true
                    }
                    if (ThemeState.supportsDynamicColor) {
                        SwitchItem(
                            title = "Material You 动态取色",
                            subtitle = "从系统壁纸提取颜色（仅 Android 12+）",
                            checked = dynamicColor
                        ) {
                            dynamicColor = it
                            ThemeState.dynamicColor = it
                        }
                    }
                    SwitchItem(
                        title = "播放页封面取色",
                        subtitle = "从当前播放歌曲的封面提取主题色，覆盖播放页主色",
                        checked = useCoverColor
                    ) {
                        useCoverColor = it
                        ThemeState.useCoverColor = it
                    }
                    SettingsItem(
                        title = "首页主题色",
                        subtitle = "手动选择首页主题色"
                    ) { showHomeColorDialog = true }
                    SettingsItem(
                        title = "播放页主题色",
                        subtitle = "手动选择播放页主题色（关闭封面取色时生效）"
                    ) { showPlayerColorDialog = true }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    SettingsHeader("音乐库")
                    SettingsItem(
                        title = "文件夹黑名单",
                        subtitle = "扫描时排除指定文件夹（已设 ${com.coolplayer.music.data.FolderBlacklist.get().size} 个）"
                    ) { showBlacklistDialog = true }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    SettingsHeader("关于")
                    SettingsItem(
                        title = "Cool Player",
                        subtitle = "版本 1.0.0\n基于 MEPlayer 音乐模块重构"
                    ) {}
                }
            }
        }
    }

    if (showThemeDialog) {
        OptionDialog(
            title = "主题模式",
            options = listOf("跟随系统", "浅色", "深色"),
            selected = themeMode,
            onSelected = {
                themeMode = it
                ThemeState.themeMode = it
            },
            onDismiss = { showThemeDialog = false }
        )
    }
    if (showHomeColorDialog) {
        ColorPickerDialog(
            title = "首页主题色",
            current = homeSeed
        ) {
            homeSeed = it
            ThemeState.homeSeed = it
            showHomeColorDialog = false
        }
    }
    if (showPlayerColorDialog) {
        ColorPickerDialog(
            title = "播放页主题色",
            current = playerSeed
        ) {
            playerSeed = it
            ThemeState.playerSeed = it
            showPlayerColorDialog = false
        }
    }
    if (showBlacklistDialog) {
        BlacklistDialog(onDismiss = { showBlacklistDialog = false })
    }
}

@Composable
private fun SettingsHeader(text: String) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = text,
        color = scheme.primary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                color = scheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = scheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun SwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = scheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = scheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun ColorPickerDialog(
    title: String,
    current: Color,
    onPicked: (Color) -> Unit
) {
    val colors = listOf(
        Color(0xFF3D5AFE), Color(0xFFE91E63), Color(0xFFFF9800),
        Color(0xFF4CAF50), Color(0xFF9C27B0), Color(0xFF00BCD4),
        Color(0xFF795548), Color(0xFF607D8B), Color(0xFFCDDC39),
        Color(0xFFFF5722), Color(0xFF8BC34A), Color(0xFF673AB7)
    )
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { onPicked(current) },
        title = { Text(title) },
        text = {
            LazyColumn {
                items(colors.chunked(3)) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(color)
                                    .clickable { onPicked(color) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onPicked(current) }) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun BlacklistDialog(onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var list by remember { mutableStateOf(com.coolplayer.music.data.FolderBlacklist.get()) }
    var input by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("文件夹黑名单") },
        text = {
            Column {
                Text("扫描时排除以下文件夹（路径前缀匹配）", fontSize = 13.sp, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.height(180.dp)) {
                    items(list) { folder ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = folder,
                                fontSize = 13.sp,
                                color = scheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "移除",
                                color = scheme.error,
                                fontSize = 13.sp,
                                modifier = Modifier.clickable {
                                    com.coolplayer.music.data.FolderBlacklist.remove(folder)
                                    list = com.coolplayer.music.data.FolderBlacklist.get()
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("输入路径") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                androidx.compose.material3.TextButton(onClick = {
                    if (input.isNotBlank()) {
                        com.coolplayer.music.data.FolderBlacklist.add(input.trim())
                        input = ""
                        list = com.coolplayer.music.data.FolderBlacklist.get()
                    }
                }) { Text("添加") }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
