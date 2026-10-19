package com.coolplayer.music.ui.audiofx

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coolplayer.music.player.audiofx.AutoEqCatalog
import com.coolplayer.music.player.audiofx.EqPreset
import com.coolplayer.music.player.audiofx.EqPresetIO
import com.coolplayer.music.player.audiofx.EqPresetRepository
import com.coolplayer.music.player.audiofx.FilterType
import com.coolplayer.music.player.audiofx.ParametricEqAudioProcessor
import com.coolplayer.music.ui.theme.currentWindowInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10

@Composable
fun AudioFxScreen(
    eqProcessor: ParametricEqAudioProcessor,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val currentPreset by eqProcessor.currentPreset.collectAsState()
    val stylePresets = EqPresetRepository.getStylePresets()
    val headphonePresets = EqPresetRepository.loadHeadphonePresets(context)

    // 导入的自定义预设（持久化在 SharedPreferences）
    var customPresets by remember { mutableStateOf(EqPresetIO.loadCustomPresets()) }

    // ── SAF 导出：先把待写入内容挂起，等用户在系统选文件页确认 Uri ──
    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingExportJson
        pendingExportJson = null
        if (uri != null && json != null) {
            val ok = EqPresetIO.writeToUri(context, uri, json)
            Toast.makeText(
                context,
                if (ok) "预设已导出（兼容 Salt Player 格式）" else "导出失败",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ── SAF 导入 ──
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            when (val r = EqPresetIO.readFromUri(context, uri)) {
                is EqPresetIO.ImportResult.Success -> {
                    EqPresetIO.addCustomPresets(r.presets)
                    customPresets = EqPresetIO.loadCustomPresets()
                    Toast.makeText(context, "已导入 ${r.presets.size} 个预设", Toast.LENGTH_SHORT).show()
                }
                is EqPresetIO.ImportResult.Failure -> {
                    Toast.makeText(context, r.reason, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val onExport: () -> Unit = {
        // 导出当前预设 + 全部自定义预设（去重后合并为一个 bundle）
        pendingExportJson = EqPresetIO.exportJson(listOf(currentPreset) + customPresets)
        exportLauncher.launch(sanitizeFileName(currentPreset.name) + ".json")
    }
    val onImport: () -> Unit = {
        importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
    }
    val onDeleteCustom: (EqPreset) -> Unit = { preset ->
        EqPresetIO.removeCustomPreset(preset.id)
        customPresets = EqPresetIO.loadCustomPresets()
    }

    val windowInfo = currentWindowInfo
    val isTablet = windowInfo.isTablet

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        if (isTablet) {
            TabletAudioFxLayout(
                currentPreset = currentPreset,
                stylePresets = stylePresets,
                headphonePresets = headphonePresets,
                customPresets = customPresets,
                eqProcessor = eqProcessor,
                onBack = onBack,
                onExport = onExport,
                onImport = onImport,
                onDeleteCustom = onDeleteCustom
            )
        } else {
            PhoneAudioFxLayout(
                currentPreset = currentPreset,
                stylePresets = stylePresets,
                headphonePresets = headphonePresets,
                customPresets = customPresets,
                eqProcessor = eqProcessor,
                onBack = onBack,
                onExport = onExport,
                onImport = onImport,
                onDeleteCustom = onDeleteCustom
            )
        }
    }
}

/** 文件名净化：去掉 SAF 创建文档不允许的字符。 */
private fun sanitizeFileName(name: String): String {
    val cleaned = name.replace(Regex("[\\\\/:*?\"<>|\\s]"), "_")
    return cleaned.ifBlank { "eq_preset" }
}

@Composable
private fun AudioFxTopBar(
    currentPreset: EqPreset,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("AUDIOFX", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                currentPreset.name,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onImport) {
            Icon(
                Icons.Default.Upload,
                contentDescription = "导入预设",
                modifier = Modifier.rotate(180f)
            )
        }
        IconButton(onClick = onExport) {
            Icon(Icons.Default.Upload, contentDescription = "导出预设")
        }
    }
}

@Composable
private fun TabletAudioFxLayout(
    currentPreset: EqPreset,
    stylePresets: List<EqPreset>,
    headphonePresets: List<EqPreset>,
    customPresets: List<EqPreset>,
    eqProcessor: ParametricEqAudioProcessor,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDeleteCustom: (EqPreset) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight()
        ) {
            AudioFxTopBar(
                currentPreset = currentPreset,
                onBack = onBack,
                onExport = onExport,
                onImport = onImport
            )
            EqCurveChart(
                preset = currentPreset,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .padding(24.dp)
            )
        }

        VerticalDivider()

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = 56.dp)
            ) {
                item { SectionHeader("通用风格") }
                items(stylePresets) { preset ->
                    PresetRow(
                        preset = preset,
                        selected = preset.id == currentPreset.id,
                        onClick = { eqProcessor.setPreset(preset) }
                    )
                }
                if (headphonePresets.isNotEmpty()) {
                    item { SectionHeader("热门耳机（AutoEQ 校正数据）") }
                    items(headphonePresets) { preset ->
                        PresetRow(
                            preset = preset,
                            selected = preset.id == currentPreset.id,
                            onClick = { eqProcessor.setPreset(preset) }
                        )
                    }
                }
                item {
                    AutoEqSearchSection(
                        onApply = { preset -> eqProcessor.setPreset(preset) }
                    )
                }
                if (customPresets.isNotEmpty()) {
                    item { SectionHeader("我的预设（导入）") }
                    items(customPresets) { preset ->
                        DeletablePresetRow(
                            preset = preset,
                            selected = preset.id == currentPreset.id,
                            onClick = { eqProcessor.setPreset(preset) },
                            onDelete = { onDeleteCustom(preset) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneAudioFxLayout(
    currentPreset: EqPreset,
    stylePresets: List<EqPreset>,
    headphonePresets: List<EqPreset>,
    customPresets: List<EqPreset>,
    eqProcessor: ParametricEqAudioProcessor,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDeleteCustom: (EqPreset) -> Unit
) {
    Column(modifier = Modifier.statusBarsPadding()) {
        AudioFxTopBar(
            currentPreset = currentPreset,
            onBack = onBack,
            onExport = onExport,
            onImport = onImport
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                EqCurveChart(
                    preset = currentPreset,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .padding(16.dp)
                )
                Divider()
            }
            item { SectionHeader("通用风格") }
            items(stylePresets) { preset ->
                PresetRow(
                    preset = preset,
                    selected = preset.id == currentPreset.id,
                    onClick = { eqProcessor.setPreset(preset) }
                )
            }
            if (headphonePresets.isNotEmpty()) {
                item { SectionHeader("热门耳机（AutoEQ 校正数据）") }
                items(headphonePresets) { preset ->
                    PresetRow(
                        preset = preset,
                        selected = preset.id == currentPreset.id,
                        onClick = { eqProcessor.setPreset(preset) }
                    )
                }
            }
            item {
                AutoEqSearchSection(
                    onApply = { preset -> eqProcessor.setPreset(preset) }
                )
            }
            if (customPresets.isNotEmpty()) {
                item { SectionHeader("我的预设（导入）") }
                items(customPresets) { preset ->
                    DeletablePresetRow(
                        preset = preset,
                        selected = preset.id == currentPreset.id,
                        onClick = { eqProcessor.setPreset(preset) },
                        onDelete = { onDeleteCustom(preset) }
                    )
                }
            }
        }
    }
}

/**
 * AutoEQ 校正库搜索区：首次进入异步加载 8850 条索引（IO 线程），
 * 之后搜索在 Default 线程执行，避免每次按键都阻塞主线程。
 */
@Composable
private fun AutoEqSearchSection(onApply: (EqPreset) -> Unit) {
    val context = LocalContext.current
    var index by remember { mutableStateOf<List<AutoEqCatalog.Entry>?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            index = AutoEqCatalog.entries(context)
        }
    }

    val results by produceState<List<AutoEqCatalog.Entry>>(
        initialValue = emptyList(),
        key1 = query,
        key2 = index
    ) {
        val idx = index
        value = if (query.isBlank() || idx == null) {
            emptyList()
        } else {
            withContext(Dispatchers.Default) {
                AutoEqCatalog.search(context, query, limit = 50)
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "AutoEQ 校正库",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            index?.let {
                Text(
                    "${it.size} 款耳机 · 数据 MIT 许可（AutoEq / jaakkopasanen）",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索耳机型号，如 Sony WH-1000XM5", fontSize = 13.sp) },
            singleLine = true,
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "清空")
                    }
                }
            },
            shape = RoundedCornerShape(12.dp)
        )
        if (results.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                items(results) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                AutoEqCatalog.presetAt(context, entry)?.let(onApply)
                            }
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.name, fontSize = 15.sp)
                            Text(
                                "${entry.typeLabel} · ${entry.source}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Divider()
                }
            }
        } else if (query.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "没有找到匹配的耳机，换个关键词试试",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun PresetRow(preset: EqPreset, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else Color.Transparent
            )
    ) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Column {
                Text(
                    preset.name,
                    fontSize = 16.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    "${preset.tag} · ${preset.source}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Divider()
    }
}

@Composable
private fun DeletablePresetRow(
    preset: EqPreset,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else Color.Transparent
            )
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Column {
                Text(
                    preset.name,
                    fontSize = 16.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    "${preset.tag} · ${preset.bands.size} 段 · ${preset.preampDb} dB",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Divider()
}

/**
 * 均衡曲线预览。各滤波器类型的频响近似：
 *  - PK   钟形：gain·exp(-(q·ln(f/fc))²)
 *  - LSC  低于 Fc 满增益，高于按钟形衰减
 *  - HSC  高于 Fc 满增益，低于按钟形衰减
 *  - LPF/HPF 精确的二阶幅度响应：|H|² = 1（或 r²）/ ((1-r²)² + (r/Q)²)
 */
@Composable
private fun EqCurveChart(preset: EqPreset, modifier: Modifier = Modifier) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val curveColor = MaterialTheme.colorScheme.primary

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val dbRange = 15f
        fun yFor(db: Float): Float = h / 2f - (db / dbRange) * (h / 2f)
        fun xFor(freq: Float): Float {
            val logMin = ln(20.0)
            val logMax = ln(20000.0)
            val t = (ln(freq.toDouble()) - logMin) / (logMax - logMin)
            return (t.coerceIn(0.0, 1.0) * w).toFloat()
        }

        val dbLines = listOf(-15f, -10f, -5f, 0f, 5f, 10f, 15f)
        dbLines.forEach { db ->
            val y = yFor(db)
            drawLine(gridColor, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(w, y), strokeWidth = 1f)
        }
        val freqLines = listOf(20f, 100f, 200f, 1000f, 2000f, 10000f)
        freqLines.forEach { f ->
            val x = xFor(f)
            drawLine(gridColor, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, h), strokeWidth = 1f)
        }

        if (preset.bands.isNotEmpty()) {
            val points = (0..200).map { i ->
                val t = i / 200f
                val logMin = ln(20.0)
                val logMax = ln(20000.0)
                val freq = exp(logMin + t * (logMax - logMin))
                var gain = preset.preampDb.toDouble()
                for (band in preset.bands) {
                    gain += responseDb(band, freq)
                }
                androidx.compose.ui.geometry.Offset(xFor(freq.toFloat()), yFor(gain.toFloat()))
            }
            for (i in 0 until points.size - 1) {
                drawLine(curveColor, points[i], points[i + 1], strokeWidth = 3f)
            }
        } else {
            val y = yFor(0f)
            drawLine(curveColor, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(w, y), strokeWidth = 3f)
        }
    }
}

/** 单个频段在频率 f 处的增益（dB），供 [EqCurveChart] 绘制预览曲线。 */
private fun responseDb(band: com.coolplayer.music.player.audiofx.EqBand, f: Double): Double {
    val r = f / band.freqHz
    val lnr = ln(r)
    return when (band.type) {
        FilterType.PK -> {
            val d = lnr * band.q
            band.gainDb * exp(-d * d)
        }
        FilterType.LSC ->
            if (r < 1.0) band.gainDb.toDouble()
            else {
                val d = lnr * band.q
                band.gainDb * exp(-d * d)
            }
        FilterType.HSC ->
            if (r > 1.0) band.gainDb.toDouble()
            else {
                val d = lnr * band.q
                band.gainDb * exp(-d * d)
            }
        FilterType.LPF, FilterType.HPF -> {
            val r2 = r * r
            val denom = (1.0 - r2) * (1.0 - r2) + r2 / (band.q * band.q)
            val mag2 = if (band.type == FilterType.LPF) 1.0 / denom else r2 / denom
            10.0 * log10(mag2.coerceAtLeast(1e-6))
        }
    }
}
