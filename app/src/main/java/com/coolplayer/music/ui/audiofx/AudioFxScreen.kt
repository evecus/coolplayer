package com.coolplayer.music.ui.audiofx

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coolplayer.music.player.audiofx.EqPreset
import com.coolplayer.music.player.audiofx.EqPresetRepository
import com.coolplayer.music.player.audiofx.ParametricEqAudioProcessor
import com.coolplayer.music.ui.theme.boundTabletWidth

/**
 * AudioFX 均衡器页面：展示当前预设的频响曲线 + 可选预设列表。
 * 视觉风格参照 Salt Player 的 AudioFX 页面（频响网格图 + 分类预设列表）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioFxScreen(
    eqProcessor: ParametricEqAudioProcessor,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val currentPreset by eqProcessor.currentPreset.collectAsState()
    val stylePresets = EqPresetRepository.getStylePresets()
    val headphonePresets = EqPresetRepository.loadHeadphonePresets(context)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AUDIOFX", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            currentPreset.name,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(modifier = Modifier.fillMaxHeight().boundTabletWidth()) {
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

            item {
                SectionHeader("通用风格")
            }
            items(stylePresets) { preset ->
                PresetRow(
                    preset = preset,
                    selected = preset.id == currentPreset.id,
                    onClick = { eqProcessor.setPreset(preset) }
                )
            }

            if (headphonePresets.isNotEmpty()) {
                item {
                    SectionHeader("耳机型号（AutoEQ 校正数据）")
                }
                items(headphonePresets) { preset ->
                    PresetRow(
                        preset = preset,
                        selected = preset.id == currentPreset.id,
                        onClick = { eqProcessor.setPreset(preset) }
                    )
                }
            }
        }
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

/**
 * 频响曲线图：把当前预设的 biquad 频段叠加计算出的近似响应画成折线，
 * 背景网格模拟截图里的 20Hz~10000Hz / -15dB~+15dB 坐标系。
 */
@Composable
private fun EqCurveChart(preset: EqPreset, modifier: Modifier = Modifier) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val curveColor = MaterialTheme.colorScheme.primary

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val dbRange = 15f // -15 .. +15
        fun yFor(db: Float): Float = h / 2f - (db / dbRange) * (h / 2f)
        fun xFor(freq: Float): Float {
            // 对数频率轴：20Hz ~ 20000Hz
            val logMin = kotlin.math.ln(20.0)
            val logMax = kotlin.math.ln(20000.0)
            val t = (kotlin.math.ln(freq.toDouble()) - logMin) / (logMax - logMin)
            return (t.coerceIn(0.0, 1.0) * w).toFloat()
        }

        // 网格横线（dB）
        val dbLines = listOf(-15f, -10f, -5f, 0f, 5f, 10f, 15f)
        dbLines.forEach { db ->
            val y = yFor(db)
            drawLine(gridColor, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(w, y), strokeWidth = 1f)
        }
        // 网格竖线（频率）
        val freqLines = listOf(20f, 100f, 200f, 1000f, 2000f, 10000f)
        freqLines.forEach { f ->
            val x = xFor(f)
            drawLine(gridColor, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, h), strokeWidth = 1f)
        }

        // 曲线：对每个 x 采样点，累加所有频段在该频率下的近似增益（简化模型，仅用于可视化）
        if (preset.bands.isNotEmpty()) {
            val points = (0..200).map { i ->
                val t = i / 200f
                val logMin = kotlin.math.ln(20.0)
                val logMax = kotlin.math.ln(20000.0)
                val freq = kotlin.math.exp(logMin + t * (logMax - logMin))
                var gain = preset.preampDb.toDouble()
                for (band in preset.bands) {
                    // 简化的钟形/架式响应近似（仅用于可视化，非精确 biquad 频响）
                    val ratio = freq / band.freqHz
                    val bandwidth = 1.0 / band.q
                    val distance = kotlin.math.ln(ratio) / bandwidth
                    val contribution = band.gainDb * kotlin.math.exp(-distance * distance)
                    gain += contribution
                }
                androidx.compose.ui.geometry.Offset(xFor(freq.toFloat()), yFor(gain.toFloat()))
            }
            for (i in 0 until points.size - 1) {
                drawLine(curveColor, points[i], points[i + 1], strokeWidth = 3f)
            }
        } else {
            // 原声：一条水平线
            val y = yFor(0f)
            drawLine(curveColor, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(w, y), strokeWidth = 3f)
        }
    }
}
