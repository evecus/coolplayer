package com.coolplayer.music.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coolplayer.music.data.LrcLine
import com.coolplayer.music.data.StorageService

// ── 歌词显示设置 ───────────────────────────────────────────────────────

/** 歌词对齐方式。 */
enum class LyricsAlignment { CENTER, START }

/** 歌词字重档位。 */
enum class LyricsFontWeight(val weight: FontWeight, val label: String) {
    THIN(FontWeight.Thin, "极细"),
    LIGHT(FontWeight.Light, "细"),
    NORMAL(FontWeight.Normal, "常规"),
    MEDIUM(FontWeight.Medium, "中等"),
    BOLD(FontWeight.Bold, "粗");

    /** 当前行（高亮）比普通行再粗一档，保留视觉强调。 */
    fun bumped(): FontWeight = when (this) {
        THIN -> FontWeight.Light
        LIGHT -> FontWeight.Normal
        NORMAL -> FontWeight.Medium
        MEDIUM -> FontWeight.Bold
        BOLD -> FontWeight.ExtraBold
    }
}

/**
 * 歌词显示设置。由 [LyricsSettingsStore] 持久化，[LyricsSettingsState] 持有可观察状态。
 *
 * - [fontSizeSp]：普通行字号；高亮行 = [fontSizeSp] + 3。
 * - [lineSpacingDp]：行间距。
 * - [alignment]：居中 / 靠左。
 * - [fontWeight]：普通行字重；高亮行使用 [LyricsFontWeight.bumped]。
 */
data class LyricsSettings(
    val fontSizeSp: Float = DEFAULT_FONT_SIZE,
    val lineSpacingDp: Float = DEFAULT_LINE_SPACING,
    val alignment: LyricsAlignment = DEFAULT_ALIGNMENT,
    val fontWeight: LyricsFontWeight = DEFAULT_FONT_WEIGHT
) {
    companion object {
        const val DEFAULT_FONT_SIZE = 17f
        const val DEFAULT_LINE_SPACING = 50f
        val DEFAULT_ALIGNMENT = LyricsAlignment.CENTER
        val DEFAULT_FONT_WEIGHT = LyricsFontWeight.NORMAL

        val DEFAULT = LyricsSettings()
    }
}

/** 歌词字号可调范围。 */
object LyricsSizeRange {
    const val MIN_FONT_SIZE = 12f
    const val MAX_FONT_SIZE = 30f
    const val MIN_LINE_SPACING = 16f
    const val MAX_LINE_SPACING = 120f
}

/** 读写歌词设置到 [StorageService]。 */
object LyricsSettingsStore {

    fun get(): LyricsSettings = LyricsSettings(
        fontSizeSp = StorageService.getFloat(
            StorageService.kLyricsFontSize, LyricsSettings.DEFAULT_FONT_SIZE
        ).coerceIn(LyricsSizeRange.MIN_FONT_SIZE, LyricsSizeRange.MAX_FONT_SIZE),
        lineSpacingDp = StorageService.getFloat(
            StorageService.kLyricsLineSpacing, LyricsSettings.DEFAULT_LINE_SPACING
        ).coerceIn(LyricsSizeRange.MIN_LINE_SPACING, LyricsSizeRange.MAX_LINE_SPACING),
        alignment = runCatching {
            LyricsAlignment.valueOf(
                StorageService.getString(StorageService.kLyricsAlignment, LyricsSettings.DEFAULT_ALIGNMENT.name)
            )
        }.getOrDefault(LyricsSettings.DEFAULT_ALIGNMENT),
        fontWeight = runCatching {
            LyricsFontWeight.valueOf(
                StorageService.getString(StorageService.kLyricsFontWeight, LyricsSettings.DEFAULT_FONT_WEIGHT.name)
            )
        }.getOrDefault(LyricsSettings.DEFAULT_FONT_WEIGHT)
    )

    fun set(settings: LyricsSettings) {
        StorageService.setFloat(StorageService.kLyricsFontSize, settings.fontSizeSp)
        StorageService.setFloat(StorageService.kLyricsLineSpacing, settings.lineSpacingDp)
        StorageService.setString(StorageService.kLyricsAlignment, settings.alignment.name)
        StorageService.setString(StorageService.kLyricsFontWeight, settings.fontWeight.name)
    }
}

/**
 * 全局可观察的歌词设置状态。任何 [LrcView] 实例直接读取 [settings]，
 * [update] 写入持久化并触发所有 [LrcView] 重组 —— 即"保存后立即生效"。
 */
object LyricsSettingsState {
    var settings by mutableStateOf(LyricsSettingsStore.get())
        private set

    fun update(settings: LyricsSettings) {
        LyricsSettingsStore.set(settings)
        this.settings = settings
    }
}

/**
 * LRC 歌词视图状态持有者。
 *
 * - [setText] 解析 LRC 文本为时间轴行（支持多时间戳行 `[00:01.00][00:30.00]lyric`），
 *   跳过 ID3 元数据标签行（`[ar:...]` `[ti:...]` 等），过滤制作信息行（词曲编混等）；
 *   无时间戳时作为纯文本展示。
 * - [updateProgress] 根据当前播放进度（ms）更新高亮行。
 */
class LrcViewState {

    var lines by mutableStateOf<List<LrcLine>>(emptyList())
        private set
    var plainLines by mutableStateOf<List<String>>(emptyList())
        private set
    var isPlain by mutableStateOf(false)
        private set
    var currentIndex by mutableStateOf(0)
        private set

    /** 由 Layout 测量阶段写入的目标垂直偏移，驱动 [animateFloatAsState] 平滑滚动。 */
    var targetOffset by mutableStateOf(0f)
        internal set

    fun setText(lrcText: String) {
        if (lrcText.isBlank()) {
            lines = emptyList()
            plainLines = emptyList()
            isPlain = false
            currentIndex = 0
            return
        }
        val timeRegex = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
        val tagRegex = Regex("""^\[(ar|ti|al|by|offset|length|re|ve|au|id):.*]""", RegexOption.IGNORE_CASE)
        val creditKeywords = listOf(
            "作词", "作曲", "编曲", "混音", "混缩", "录音", "母带", "制作",
            "和声", "后期", "吉他", "贝斯", "键盘", "弦乐", "lyrics", "music",
            "compose", "arrange", "mix", "mastering", "vocal", "guitar", "bass", "drum"
        )

        val timed = mutableListOf<LrcLine>()
        val plain = mutableListOf<String>()
        for (raw in lrcText.split('\n')) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (tagRegex.containsMatchIn(line)) continue
            val matches = timeRegex.findAll(line).toList()
            if (matches.isEmpty()) {
                val lower = line.lowercase()
                if (creditKeywords.any { lower.contains(it.lowercase()) }) continue
                plain.add(line)
                continue
            }
            val lastMatch = matches.last()
            val lyricText = line.substring(lastMatch.range.last + 1).trim()
            matches.forEach { m ->
                val min = m.groupValues[1].toIntOrNull() ?: 0
                val sec = m.groupValues[2].toIntOrNull() ?: 0
                val msStr = m.groupValues[3]
                val ms = when (msStr.length) {
                    0 -> 0
                    1 -> msStr.toInt() * 100
                    2 -> msStr.toInt() * 10
                    else -> msStr.substring(0, 3).toInt()
                }
                timed.add(LrcLine(min * 60_000L + sec * 1000L + ms, lyricText))
            }
        }

        if (timed.isEmpty()) {
            isPlain = true
            plainLines = plain
            lines = emptyList()
        } else {
            isPlain = false
            plainLines = emptyList()
            lines = timed.sortedBy { it.time }
        }
        currentIndex = 0
    }

    /** 根据播放进度更新当前高亮行。 */
    fun updateProgress(positionMs: Long) {
        if (isPlain || lines.isEmpty()) return
        var idx = 0
        for (i in lines.indices) {
            if (lines[i].time <= positionMs) idx = i else break
        }
        if (idx != currentIndex) currentIndex = idx
    }
}

@Composable
fun rememberLrcViewState(): LrcViewState = remember { LrcViewState() }

/**
 * LRC 歌词组件。用 Compose [Layout] 自绘。
 *
 * - 字号 / 行距 / 对齐 / 字重由 [LyricsSettingsState] 控制，保存后立即生效。
 * - 当前行（高亮）字号 +3、字重再粗一档，垂直居中并平滑滚动
 *   （[animateFloatAsState] 每帧逼近目标偏移）。
 * - 无时间戳时作为纯文本展示。
 *
 * [lineSpacing] 参数保留向后兼容，实际行距以 [LyricsSettingsState] 为准。
 */
@Composable
fun LrcView(
    lrcText: String,
    emptyText: String = "暂无歌词",
    highlightColor: Color,
    normalColor: Color,
    lineSpacing: Float = 50f,
    modifier: Modifier = Modifier,
    state: LrcViewState? = null
) {
    val lrcState = state ?: rememberLrcViewState()
    LaunchedEffect(lrcText) { lrcState.setText(lrcText) }

    // 读取歌词显示设置（可观察，保存后立即重组）
    val cfg = LyricsSettingsState.settings
    val normalSize = cfg.fontSizeSp.sp
    val highlightSize = (cfg.fontSizeSp + 3f).sp
    val spacingDp = cfg.lineSpacingDp.coerceAtLeast(0f)
    val textAlign = if (cfg.alignment == LyricsAlignment.CENTER) TextAlign.Center else TextAlign.Start
    val columnAlign = if (cfg.alignment == LyricsAlignment.CENTER)
        Alignment.CenterHorizontally else Alignment.Start
    val normalWeight = cfg.fontWeight.weight
    val highlightWeight = cfg.fontWeight.bumped()

    if (lrcState.isPlain) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (lrcState.plainLines.isEmpty()) {
                Text(emptyText, color = normalColor.copy(alpha = 0.5f), fontSize = 14.sp)
            } else {
                Column(horizontalAlignment = columnAlign) {
                    lrcState.plainLines.forEach { line ->
                        Text(
                            text = line,
                            color = normalColor.copy(alpha = 0.6f),
                            fontSize = normalSize,
                            fontWeight = normalWeight,
                            textAlign = textAlign,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(Modifier.height(spacingDp.dp))
                    }
                }
            }
        }
        return
    }
    if (lrcState.lines.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, color = normalColor.copy(alpha = 0.5f), fontSize = 14.sp)
        }
        return
    }

    val density = LocalDensity.current
    val currentIdx = lrcState.currentIndex
    val animated by animateFloatAsState(
        targetValue = lrcState.targetOffset,
        animationSpec = tween(durationMillis = 320),
        label = "lrcOffset"
    )

    Layout(
        content = {
            lrcState.lines.forEachIndexed { i, line ->
                val highlighted = i == currentIdx
                Text(
                    text = line.text.ifEmpty { "♪" },
                    color = if (highlighted) highlightColor else normalColor.copy(alpha = 0.55f),
                    fontSize = if (highlighted) highlightSize else normalSize,
                    fontWeight = if (highlighted) highlightWeight else normalWeight,
                    textAlign = textAlign,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )
            }
        },
        modifier = modifier
    ) { measurables, constraints ->
        val spacingPx = with(density) { spacingDp.dp.roundToPx() }
        val childConstraints = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val placeables = measurables.map { it.measure(childConstraints) }
        val viewportW = constraints.maxWidth
        val viewportH = constraints.maxHeight
        val bounded = viewportH != Constraints.Infinity && viewportH > 0

        var y = 0
        var currentCenter = 0
        val ys = IntArray(placeables.size)
        placeables.forEachIndexed { i, p ->
            ys[i] = y
            if (i == currentIdx) currentCenter = y + p.height / 2
            y += p.height + spacingPx
        }
        val target = if (bounded) (viewportH / 2 - currentCenter).toFloat() else 0f
        if (target != lrcState.targetOffset) lrcState.targetOffset = target

        val layoutHeight = if (bounded) viewportH else y
        layout(viewportW, layoutHeight) {
            val off = animated.toInt()
            placeables.forEachIndexed { i, p ->
                p.placeRelative(0, ys[i] + off)
            }
        }
    }
}
