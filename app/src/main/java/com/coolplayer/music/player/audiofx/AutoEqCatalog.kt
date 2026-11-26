package com.coolplayer.music.player.audiofx

import android.content.Context
import android.util.Base64

/**
 * AutoEQ 参数均衡校正库（8850 款耳机的频响校正曲线）。
 *
 * 数据来源：https://github.com/jaakkopasanen/AutoEq （MIT License，
 * 详见 assets/audiofx/autoeq_catalog_LICENSE.txt，含各测量原作者署名）。
 *
 * TSV 行格式（制表符分隔，头部 # 为注释）：
 *   hash \t type \t base64(source) \t base64(name) \t preamp \t bands
 *   bands = "L,105,5.5,0.70;P,180,-3.3,0.57;P,9140,5.6,3.46;H,10000,-1.1,0.70"
 *   L = 低频架 LowShelf，P = 钟形 Peaking，H = 高频架 HighShelf
 *
 * 内存策略：整表 2.1MB / 8850 行。首次调用 [entries] 时一次性读取并建立
 * 预解码索引（name/source 在搜索前解码好，避免每次按键都做 8850 次 base64），
 * 之后常驻内存约 4MB；单条预设按需解析（[presetAt]），不提前物化 8850 个
 * EqPreset 对象（那会占用 50MB+ 堆内存）。
 */
object AutoEqCatalog {

    const val ASSET_PATH = "audiofx/autoeq_catalog.tsv"

    /** 搜索索引条目（预解码，轻量）。 */
    data class Entry(
        /** 数据指纹，同时用作预设 id（autoeq_<hash>） */
        val hash: String,
        /** 耳机型号名，如 "1Custom SA02" */
        val name: String,
        /** 测量原作者，如 "oratory1990" / "crinacle" / "Rtings" */
        val source: String,
        /** 佩戴类型：in-ear / over-ear / earbud */
        val type: String,
        /** 在原始行列表中的下标，供 [presetAt] 定位 */
        val lineIndex: Int
    ) {
        /** 本地化的佩戴类型标签，直接用于 UI 副标题。 */
        val typeLabel: String
            get() = when (type) {
                "in-ear" -> "入耳式"
                "over-ear" -> "头戴式"
                "earbud" -> "耳塞"
                else -> type
            }
    }

    private val lock = Any()

    @Volatile private var cachedLines: List<String>? = null
    @Volatile private var cachedIndex: List<Entry>? = null

    /**
     * 加载（或取缓存）全表索引。线程安全；重复调用直接返回缓存。
     * 首次调用约耗时几十毫秒，建议在 IO 线程调用。
     */
    fun entries(context: Context): List<Entry> {
        cachedIndex?.let { return it }
        synchronized(lock) {
            cachedIndex?.let { return it }
            val lines = readLines(context)
            val index = ArrayList<Entry>(lines.size)
            for (i in lines.indices) {
                val parts = lines[i].split('\t')
                if (parts.size < 6) continue
                index.add(
                    Entry(
                        hash = parts[0],
                        name = decodeB64(parts[3]),
                        source = decodeB64(parts[2]),
                        type = parts[1],
                        lineIndex = i
                    )
                )
            }
            cachedLines = lines
            cachedIndex = index
            return index
        }
    }

    /** 表内数据总条数（用于 UI 显示“8850 款”）。 */
    fun count(context: Context): Int = entries(context).size

    /**
     * 大小写不敏感的子串搜索，匹配耳机名与测量作者。
     * @param limit 最多返回条数（UI 只展示前几十条）
     */
    fun search(context: Context, query: String, limit: Int = 50): List<Entry> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val needle = q.lowercase()
        val result = ArrayList<Entry>()
        for (e in entries(context)) {
            if (e.name.lowercase().contains(needle) ||
                e.source.lowercase().contains(needle)
            ) {
                result.add(e)
                if (result.size >= limit) break
            }
        }
        return result
    }

    /** 把索引条目解析为可直接喂给 [ParametricEqAudioProcessor.setPreset] 的预设。 */
    fun presetAt(context: Context, entry: Entry): EqPreset? {
        val lines = cachedLines ?: run {
            runCatching { entries(context) }
            cachedLines
        } ?: return null
        val line = lines.getOrNull(entry.lineIndex) ?: return null
        return runCatching { parseLine(line) }.getOrNull()
    }

    private fun parseLine(line: String): EqPreset? {
        val parts = line.split('\t')
        if (parts.size < 6) return null
        val preamp = parts[4].toFloatOrNull() ?: return null
        val bands = ArrayList<EqBand>(12)
        for (token in parts[5].split(';')) {
            if (token.isEmpty()) continue
            val f = token.split(',')
            if (f.size < 4) continue
            val type = when (f[0]) {
                "L" -> FilterType.LSC
                "H" -> FilterType.HSC
                else -> FilterType.PK
            }
            val freq = f[1].toFloatOrNull() ?: continue
            val gain = f[2].toFloatOrNull() ?: continue
            val q = f[3].toFloatOrNull() ?: continue
            bands.add(EqBand(type = type, freqHz = freq, gainDb = gain, q = q))
        }
        return EqPreset(
            id = "autoeq_${parts[0]}",
            name = decodeB64(parts[3]),
            tag = entryTypeLabel(parts[1]),
            source = decodeB64(parts[2]),
            preampDb = preamp,
            bands = bands
        )
    }

    private fun entryTypeLabel(type: String): String = when (type) {
        "in-ear" -> "入耳式"
        "over-ear" -> "头戴式"
        "earbud" -> "耳塞"
        else -> type
    }

    private fun readLines(context: Context): List<String> =
        context.assets.open(ASSET_PATH)
            .bufferedReader(Charsets.UTF_8)
            .use { reader ->
                val lines = ArrayList<String>(9200)
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty() || line[0] == '#') continue
                    lines.add(line)
                }
                lines
            }

    private fun decodeB64(s: String): String =
        runCatching { String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8) }
            .getOrElse { s }
}
