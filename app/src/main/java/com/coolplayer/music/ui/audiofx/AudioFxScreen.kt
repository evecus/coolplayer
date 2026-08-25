package com.coolplayer.music.ui.audiofx

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import com.coolplayer.music.ui.theme.currentWindowInfo

@Composable
fun AudioFxScreen(
    eqProcessor: ParametricEqAudioProcessor,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val currentPreset by eqProcessor.currentPreset.collectAsState()
    val stylePresets = EqPresetRepository.getStylePresets()
    val headphonePresets = EqPresetRepository.loadHeadphonePresets(context)

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
                eqProcessor = eqProcessor,
                onBack = onBack
            )
        } else {
            PhoneAudioFxLayout(
                currentPreset = currentPreset,
                stylePresets = stylePresets,
                headphonePresets = headphonePresets,
                eqProcessor = eqProcessor,
                onBack = onBack
            )
        }
    }
}

@Composable
private fun TabletAudioFxLayout(
    currentPreset: EqPreset,
    stylePresets: List<EqPreset>,
    headphonePresets: List<EqPreset>,
    eqProcessor: ParametricEqAudioProcessor,
    onBack: () -> Unit
) {
    Row(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
                Column {
                    Text("AUDIOFX", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        currentPreset.name,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
                    item { SectionHeader("耳机型号（AutoEQ 校正数据）") }
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
private fun PhoneAudioFxLayout(
    currentPreset: EqPreset,
    stylePresets: List<EqPreset>,
    headphonePresets: List<EqPreset>,
    eqProcessor: ParametricEqAudioProcessor,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.statusBarsPadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }
            Column {
                Text("AUDIOFX", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    currentPreset.name,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
                item { SectionHeader("耳机型号（AutoEQ 校正数据）") }
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
private fun EqCurveChart(preset: EqPreset, modifier: Modifier = Modifier) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val curveColor = MaterialTheme.colorScheme.primary

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val dbRange = 15f
        fun yFor(db: Float): Float = h / 2f - (db / dbRange) * (h / 2f)
        fun xFor(freq: Float): Float {
            val logMin = kotlin.math.ln(20.0)
            val logMax = kotlin.math.ln(20000.0)
            val t = (kotlin.math.ln(freq.toDouble()) - logMin) / (logMax - logMin)
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
                val logMin = kotlin.math.ln(20.0)
                val logMax = kotlin.math.ln(20000.0)
                val freq = kotlin.math.exp(logMin + t * (logMax - logMin))
                var gain = preset.preampDb.toDouble()
                for (band in preset.bands) {
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
            val y = yFor(0f)
            drawLine(curveColor, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(w, y), strokeWidth = 3f)
        }
    }
}
