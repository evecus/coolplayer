package com.coolplayer.music.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.foundation.layout.BoxWithConstraints
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
        /** 默认行距偏紧，接近常见播放器歌词密度（原 50 过疏） */
        const val DEFAULT_LINE_SPACING = 22f
        val DEFAULT_ALIGNMENT = LyricsAlignment.CENTER
        val DEFAULT_FONT_WEIGHT = LyricsFontWeight.NORMAL

        val DEFAULT = LyricsSettings()
    }
}

/** 歌词字号可调范围。 */
object LyricsSizeRange {
    const val MIN_FONT_SIZE = 12f
    const val MAX_FONT_SIZE = 30f
    const val MIN_LINE_SPACING = 8f
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
 * - [updateProgress] 根据当前进度（ms）更新高亮行。
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

    /**
     * 根据播放进度更新当前高亮行。
     *
     * 播放中每 500ms 被调用一次（见 [com.coolplayer.music.player.MusicPlayer] 的
     * position polling）。正常播放时进度单调递增，从上次的 [currentIndex] 开始
     * 往后找即可命中；只有 seek 导致进度跳变（新位置早于当前行）时才回退
     * 重新定位。
     */
    fun updateProgress(positionMs: Long) {
        if (isPlain || lines.isEmpty()) return
        var idx = currentIndex.coerceIn(0, lines.lastIndex)
        if (lines[idx].time > positionMs) {
            // 位置回退（seek 到更早的地方）：从头找。
            idx = 0
            while (idx < lines.lastIndex && lines[idx + 1].time <= positionMs) idx++
        } else {
            // 正常前进：从当前行往后找，不用回到 0。
            while (idx < lines.lastIndex && lines[idx + 1].time <= positionMs) idx++
        }
        if (idx != currentIndex) currentIndex = idx
    }
}

@Composable
fun rememberLrcViewState(): LrcViewState = remember { LrcViewState() }

/**
 * LRC 歌词组件。
 *
 * - 字号 / 行距 / 对齐 / 字重由 [LyricsSettingsState] 控制，保存后立即生效。
 * - 当前行（高亮）字号 +3、字重再粗一档，并平滑滚动到视口垂直居中。
 * - 无时间戳时作为纯文本展示。
 *
 * 实现说明（性能，本次优化的重点之一）：
 * 旧实现用 Compose [Layout] 自绘，每次重组都会把**全部**歌词行重新 measure
 * （一首三分钟的歌常见 40~100 行；播放页此前随进度每 200ms 重组一次，等于
 * 每秒几百次 Text 测量，是播放时 UI 线程 CPU 的稳定开销，歌曲越长越明显）。
 * 现改为 [LazyColumn] 虚拟列表：只有可见区域附近的行会被组合 / 测量，
 * 高亮行切换只影响可见行，成本与歌曲总行数脱钩。
 *
 * [lineSpacing] 参数保留向后兼容，实际行距以 [LyricsSettingsState] 为准。
 */
@Composable
fun LrcView(
    lrcText: String,
    emptyText: String = "暂无歌词",
    highlightColor: Color,
    normalColor: Color,
    lineSpacing: Float = LyricsSettings.DEFAULT_LINE_SPACING,
    /** 列表顶部内边距，避免歌词贴顶栏歌名 */
    contentPaddingTop: androidx.compose.ui.unit.Dp = 24.dp,
    /** 列表底部内边距，避免歌词贴进度条 */
    contentPaddingBottom: androidx.compose.ui.unit.Dp = 32.dp,
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
    val listContentPadding = PaddingValues(
        start = 24.dp,
        end = 24.dp,
        top = contentPaddingTop,
        bottom = contentPaddingBottom
    )

    if (lrcState.isPlain) {
        if (lrcState.plainLines.isEmpty()) {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(emptyText, color = normalColor.copy(alpha = 0.5f), fontSize = 14.sp)
            }
            return
        }
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = columnAlign,
            contentPadding = listContentPadding,
            verticalArrangement = Arrangement.spacedBy(spacingDp.dp)
        ) {
            itemsIndexed(lrcState.plainLines, key = { i, _ -> i }) { _, line ->
                Text(
                    text = line,
                    color = normalColor.copy(alpha = 0.6f),
                    fontSize = normalSize,
                    fontWeight = normalWeight,
                    textAlign = textAlign,
                    modifier = Modifier.fillMaxWidth()
                )
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

    val listState = rememberLazyListState()
    val currentIdx = lrcState.currentIndex
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val topPadPx = with(density) { contentPaddingTop.toPx() }
        val bottomPadPx = with(density) { contentPaddingBottom.toPx() }
        // 高亮行滚到可视区垂直中部（扣除上下内边距后的有效区域）
        LaunchedEffect(currentIdx, lrcState.lines.size, viewportHeightPx, topPadPx, bottomPadPx) {
            if (currentIdx in lrcState.lines.indices && viewportHeightPx > 0f) {
                val usable = (viewportHeightPx - topPadPx - bottomPadPx).coerceAtLeast(0f)
                listState.animateScrollToItem(
                    index = currentIdx,
                    scrollOffset = -((usable / 2f) + topPadPx).toInt()
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = columnAlign,
            contentPadding = listContentPadding,
            verticalArrangement = Arrangement.spacedBy(spacingDp.dp)
        ) {
            itemsIndexed(lrcState.lines, key = { i, _ -> i }) { i, line ->
                val highlighted = i == currentIdx
                Text(
                    text = line.text.ifEmpty { "♪" },
                    color = if (highlighted) highlightColor else normalColor.copy(alpha = 0.55f),
                    fontSize = if (highlighted) highlightSize else normalSize,
                    fontWeight = if (highlighted) highlightWeight else normalWeight,
                    textAlign = textAlign,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
