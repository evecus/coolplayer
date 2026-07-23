package com.coolplayer.music.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coolplayer.music.data.FolderBlacklist
import com.coolplayer.music.data.PermissionUtil
import com.coolplayer.music.data.ScanFilters
import com.coolplayer.music.data.ScanFiltersStore
import com.coolplayer.music.ui.theme.boundTabletWidth
import com.coolplayer.music.ui.widget.ScanProgressDialog

/**
 * 媒体来源 / 扫描设置页。
 *
 * 承担应用中所有与"扫描音乐"相关的配置与操作入口：
 * - 开始扫描（按需申请权限，扫描时弹出进度弹窗）
 * - 使用 Android 媒体库 开关
 * - 自定义扫描文件夹（系统目录选择器添加 / 移除，为空则全局扫描）
 * - 管理外部存储权限（跳转系统"所有文件访问权限"设置页）
 * - 不扫描 60 秒以下音频
 * - 音频格式过滤
 * - 音频大小范围过滤（MB）
 * - 被屏蔽的文件夹（黑名单）
 */
@Composable
fun ScanSettingsScreen(vm: LibraryViewModel, onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    var filters by remember { mutableStateOf(ScanFiltersStore.get()) }
    fun updateFilters(new: ScanFilters) {
        filters = new
        ScanFiltersStore.set(new)
    }

    var showScanDialog by remember { mutableStateOf(false) }
    var showBlacklistDialog by remember { mutableStateOf(false) }
    var showFormatDialog by remember { mutableStateOf(false) }
    var showSizeRangeDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var intervalInput by remember(filters.periodicScanIntervalDays) {
        mutableStateOf(filters.periodicScanIntervalDays.toString())
    }

    val isScanning by vm.isScanning.collectAsState()

    // 系统目录选择器：选中后持久化 URI 权限，并记录其对应的真实文件路径（尽力而为）。
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val path = uriTreeToFilePath(uri)
            if (path != null) {
                val next = filters.copy(customFolders = filters.customFolders + path)
                updateFilters(next)
            }
        }
    }

    // 音频权限请求：用户点击"开始扫描"时才会触发。
    val audioPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.all { it } || PermissionUtil.hasAudioPermission(context)
        vm.onPermissionResult(granted)
        if (granted) {
            showScanDialog = true
            vm.rescan {}
        }
    }

    fun startScan() {
        if (PermissionUtil.hasAudioPermission(context)) {
            showScanDialog = true
            vm.rescan {}
        } else {
            audioPermLauncher.launch(PermissionUtil.audioPermissions())
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(scheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(modifier = Modifier.fillMaxHeight().boundTabletWidth().statusBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    tint = scheme.onBackground,
                    modifier = Modifier.size(40.dp).clickable(onClick = onBack).padding(8.dp)
                )
                Text(text = "媒体来源", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = scheme.onBackground)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    ScanActionCard(
                        isScanning = isScanning,
                        onClick = { startScan() }
                    )
                }

                item {
                    SwitchRow(
                        title = "使用 Android 媒体库",
                        subtitle = null,
                        checked = filters.useMediaStore,
                        onCheckedChange = { updateFilters(filters.copy(useMediaStore = it)) }
                    )
                }

                item {
                    SwitchRow(
                        title = "扫描去重",
                        subtitle = "标题和艺术家完全相同时只保留一首",
                        checked = filters.dedupeEnabled,
                        onCheckedChange = { updateFilters(filters.copy(dedupeEnabled = it)) }
                    )
                }

                item {
                    ActionRow(
                        icon = Icons.Default.Delete,
                        title = "清理扫描数据",
                        onClick = { showClearConfirm = true }
                    )
                }

                item {
                    SectionLabel("自定义文件夹")
                }
                items(filters.customFolders) { folder ->
                    FolderRow(
                        path = folder,
                        onRemove = {
                            updateFilters(filters.copy(customFolders = filters.customFolders - folder))
                        }
                    )
                }
                item {
                    ActionRow(
                        icon = Icons.Default.CreateNewFolder,
                        title = "添加自定义文件夹",
                        onClick = {
                            runCatching { folderPickerLauncher.launch(null) }
                        }
                    )
                }

                item {
                    SectionLabel("设置")
                }
                item {
                    ActionRow(
                        icon = Icons.Default.Folder,
                        title = "管理外部存储权限",
                        trailingIcon = Icons.Default.OpenInNew,
                        onClick = { PermissionUtil.requestAllFilesAccess(context) }
                    )
                }
                item {
                    SwitchRow(
                        title = "不扫描 60 秒以下音频",
                        subtitle = null,
                        checked = filters.skipShortAudio,
                        onCheckedChange = { updateFilters(filters.copy(skipShortAudio = it)) }
                    )
                }
                item {
                    ArrowRow(
                        title = "音频格式",
                        subtitle = if (filters.allowedExtensions.isEmpty()) {
                            "不限制"
                        } else {
                            filters.allowedExtensions.joinToString(" / ") { it.removePrefix(".").uppercase() }
                        },
                        onClick = { showFormatDialog = true }
                    )
                }
                item {
                    ArrowRow(
                        title = "音频大小范围",
                        subtitle = sizeRangeSummary(filters.minSizeMb, filters.maxSizeMb),
                        onClick = { showSizeRangeDialog = true }
                    )
                }
                item {
                    ArrowRow(
                        title = "被屏蔽的文件夹",
                        subtitle = "已设 ${FolderBlacklist.get().size} 个",
                        onClick = { showBlacklistDialog = true }
                    )
                }

                item {
                    SwitchRow(
                        title = "定期扫描",
                        subtitle = "App 运行时，超过设定天数会自动在后台静默扫描一次",
                        checked = filters.periodicScanEnabled,
                        onCheckedChange = { updateFilters(filters.copy(periodicScanEnabled = it)) }
                    )
                }
                if (filters.periodicScanEnabled) {
                    item {
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Update, contentDescription = null, tint = scheme.primary)
                                Spacer(Modifier.size(12.dp))
                                Text(
                                    text = "间隔天数",
                                    color = scheme.onSurface,
                                    fontSize = 15.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = intervalInput,
                                    onValueChange = { input ->
                                        val digitsOnly = input.filter { it.isDigit() }
                                        intervalInput = digitsOnly
                                        val days = digitsOnly.toIntOrNull()
                                        if (days != null && days > 0) {
                                            updateFilters(filters.copy(periodicScanIntervalDays = days))
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier.width(80.dp)
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "若使用自定义文件夹功能的同时开启了使用 Android 媒体库功能，那么最终扫描到的歌曲为二者并集。如果仅需使用自定义文件夹请关闭使用 Android 媒体库功能",
                        color = scheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)
                    )
                }
            }
        }
    }

    if (showScanDialog) {
        ScanProgressDialog(
            vm = vm,
            onDismiss = { showScanDialog = false }
        )
    }

    if (showFormatDialog) {
        FormatFilterDialog(
            selected = filters.allowedExtensions,
            onDismiss = { showFormatDialog = false },
            onConfirm = {
                updateFilters(filters.copy(allowedExtensions = it))
                showFormatDialog = false
            }
        )
    }

    if (showSizeRangeDialog) {
        SizeRangeDialog(
            minMb = filters.minSizeMb,
            maxMb = filters.maxSizeMb,
            onDismiss = { showSizeRangeDialog = false },
            onConfirm = { min, max ->
                updateFilters(filters.copy(minSizeMb = min, maxSizeMb = max))
                showSizeRangeDialog = false
            }
        )
    }

    if (showBlacklistDialog) {
        ScanBlacklistDialog(onDismiss = { showBlacklistDialog = false })
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清理扫描数据") },
            text = { Text("将清空当前已扫描到的歌曲列表与封面缓存，不影响收藏、歌单和播放历史。清理后需要重新扫描才能看到歌曲。确定继续吗？") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearScannedData()
                    showClearConfirm = false
                }) { Text("清理", color = scheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            }
        )
    }
}

/** 从 SAF 目录树 URI 中尽力还原出真实文件路径（多数厂商 ROM 上适用于主存储 / SD 卡）。 */
private fun uriTreeToFilePath(uri: Uri): String? {
    val docId = runCatching {
        android.provider.DocumentsContract.getTreeDocumentId(uri)
    }.getOrNull() ?: return null
    val parts = docId.split(":")
    if (parts.size < 2) return null
    val type = parts[0]
    val relPath = parts[1]
    return when {
        type.equals("primary", ignoreCase = true) -> {
            "/storage/emulated/0" + if (relPath.isNotEmpty()) "/$relPath" else ""
        }
        else -> {
            // 非 primary 卷（如 SD 卡），大多数 ROM 用卷 UUID 作为 /storage 下的目录名
            "/storage/$type" + if (relPath.isNotEmpty()) "/$relPath" else ""
        }
    }
}

private fun sizeRangeSummary(minMb: Int?, maxMb: Int?): String {
    return when {
        minMb == null && maxMb == null -> "不限制"
        minMb != null && maxMb != null -> "${minMb}MB ~ ${maxMb}MB"
        minMb != null -> "≥ ${minMb}MB"
        else -> "≤ ${maxMb}MB"
    }
}

// ── 子组件 ───────────────────────────────────────────────────────────

@Composable
private fun ScanActionCard(isScanning: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().clickable(enabled = !isScanning, onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(imageVector = Icons.Default.Sync, contentDescription = null, tint = scheme.primary)
            }
            Spacer(Modifier.size(12.dp))
            Text(
                text = if (isScanning) "扫描中…" else "开始扫描",
                color = scheme.primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = text,
        color = scheme.onSurfaceVariant,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = scheme.onSurface, fontSize = 15.sp)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = scheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun ArrowRow(title: String, subtitle: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = scheme.onSurface, fontSize = 15.sp)
                Text(
                    text = subtitle,
                    color = scheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = scheme.primary)
            Spacer(Modifier.size(12.dp))
            Text(text = title, color = scheme.primary, fontSize = 15.sp, modifier = Modifier.weight(1f))
            if (trailingIcon != null) {
                Icon(imageVector = trailingIcon, contentDescription = null, tint = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FolderRow(path: String, onRemove: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = path,
                color = scheme.onSurface,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "移除",
                color = scheme.error,
                fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onRemove).padding(4.dp)
            )
        }
    }
}

// ── 弹窗 ─────────────────────────────────────────────────────────────

private val KNOWN_AUDIO_FORMATS = listOf(
    ".mp3", ".flac", ".wav", ".ogg", ".m4a", ".aac", ".wma", ".ape", ".opus"
)

@Composable
private fun FormatFilterDialog(
    selected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var current by remember { mutableStateOf(selected) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("音频格式") },
        text = {
            Column {
                Text("不选则不限制格式", fontSize = 12.sp, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                KNOWN_AUDIO_FORMATS.forEach { ext ->
                    val checked = current.contains(ext)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                current = if (checked) current - ext else current + ext
                            }
                            .padding(vertical = 6.dp)
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                current = if (it) current + ext else current - ext
                            }
                        )
                        Text(text = ext.removePrefix(".").uppercase(), color = scheme.onSurface)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun SizeRangeDialog(
    minMb: Int?,
    maxMb: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int?, Int?) -> Unit
) {
    var minInput by remember { mutableStateOf(minMb?.toString() ?: "") }
    var maxInput by remember { mutableStateOf(maxMb?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("音频大小范围（MB）") },
        text = {
            Column {
                OutlinedTextField(
                    value = minInput,
                    onValueChange = { minInput = it.filter { c -> c.isDigit() } },
                    label = { Text("最小值") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = maxInput,
                    onValueChange = { maxInput = it.filter { c -> c.isDigit() } },
                    label = { Text("最大值") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("留空表示不限制", fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(minInput.toIntOrNull(), maxInput.toIntOrNull())
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun ScanBlacklistDialog(onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var list by remember { mutableStateOf(FolderBlacklist.get()) }
    var input by remember { mutableStateOf("") }
    val context = LocalContext.current

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            uriTreeToFilePath(uri)?.let {
                FolderBlacklist.add(it)
                list = FolderBlacklist.get()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("被屏蔽的文件夹") },
        text = {
            Column {
                Text("以下文件夹（及其子目录）不会被扫描", fontSize = 12.sp, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.height(180.dp)) {
                    items(list) { folder ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
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
                                    FolderBlacklist.remove(folder)
                                    list = FolderBlacklist.get()
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("输入路径") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        if (input.isNotBlank()) {
                            FolderBlacklist.add(input.trim())
                            input = ""
                            list = FolderBlacklist.get()
                        }
                    }) { Text("添加") }
                }
                TextButton(onClick = { runCatching { pickerLauncher.launch(null) } }) {
                    Text("从系统选择文件夹")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
