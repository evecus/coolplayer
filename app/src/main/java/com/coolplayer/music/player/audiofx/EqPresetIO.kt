package com.coolplayer.music.player.audiofx

import android.content.Context
import android.net.Uri
import com.coolplayer.music.data.StorageService
import org.json.JSONArray
import org.json.JSONObject

/**
 * EQ 预设的导入 / 导出，JSON 格式与 Salt Player 的 EqualizerPresetBundle 完全兼容
 * （格式标识 "speq.equalizer-presets"，version 1，字段名与 Salt 的
 *  kotlinx.serialization 序列化结果一致）：
 *
 * ```json
 * {
 *   "format": "speq.equalizer-presets",
 *   "version": 1,
 *   "presets": [
 *     {
 *       "id": "style_bass",
 *       "name": "低音 Bass",
 *       "inputGain": 0.0,
 *       "outputGain": -2.0,
 *       "bands": [
 *         {"id": "b0", "type": "LowShelf", "frequency": 80.0, "gain": 6.0, "q": 0.7, "enabled": true}
 *       ]
 *     }
 *   ]
 * }
 * ```
 *
 * 字段映射说明（Salt → coolplayer）：
 *  - outputGain（EQ 后增益，防削波） ↔ [EqPreset.preampDb]；
 *    coolplayer 的 preamp 同样作用在频段链之后，语义一致。
 *  - inputGain 导出时固定写 0（coolplayer 没有 EQ 前增益级）。
 *  - type 枚举名一致：Peaking/LowShelf/HighShelf/LowPass/HighPass。
 *  - enabled=false 的频段导入时直接丢弃（coolplayer 的 DSP 链没有旁路开关）。
 *  - LowPass/HighPass 的 gain 字段不参与滤波（RBJ 标准低通/高通由 Fc 与 Q 决定），
 *    与 Salt 基于 BASS BFX_BQF 的行为对齐。
 */
object EqPresetIO {

    const val BUNDLE_FORMAT = "speq.equalizer-presets"
    const val BUNDLE_VERSION = 1

    private const val STORAGE_KEY = "custom_eq_presets"

    /** 导入结果：成功拿到预设列表，或带上可展示给用户的错误原因。 */
    sealed class ImportResult {
        data class Success(val presets: List<EqPreset>) : ImportResult()
        data class Failure(val reason: String) : ImportResult()
    }

    // ── 序列化 ────────────────────────────────────────────────────────────

    fun exportJson(presets: List<EqPreset>): String {
        val arr = JSONArray()
        presets.forEach { p -> arr.put(presetToJson(p)) }
        return JSONObject().apply {
            put("format", BUNDLE_FORMAT)
            put("version", BUNDLE_VERSION)
            put("presets", arr)
        }.toString(2)
    }

    /** 导出单个预设（按钮“导出当前预设”用）。 */
    fun exportPreset(preset: EqPreset): String = exportJson(listOf(preset))

    private fun presetToJson(p: EqPreset): JSONObject {
        val bands = JSONArray()
        p.bands.forEachIndexed { i, b ->
            bands.put(
                JSONObject().apply {
                    put("id", "b$i")
                    put("type", filterTypeName(b.type))
                    put("frequency", b.freqHz.toDouble())
                    put("gain", b.gainDb.toDouble())
                    put("q", b.q.toDouble())
                    put("enabled", true)
                }
            )
        }
        return JSONObject().apply {
            put("id", p.id)
            put("name", p.name)
            put("inputGain", 0.0)
            put("outputGain", p.preampDb.toDouble())
            put("bands", bands)
        }
    }

    private fun filterTypeName(t: FilterType): String = when (t) {
        FilterType.PK -> "Peaking"
        FilterType.LSC -> "LowShelf"
        FilterType.HSC -> "HighShelf"
        FilterType.LPF -> "LowPass"
        FilterType.HPF -> "HighPass"
    }

    private fun parseFilterType(name: String): FilterType? = when (name) {
        "Peaking", "PK", "peaking" -> FilterType.PK
        "LowShelf", "LSC", "Lowshelf", "low_shelf" -> FilterType.LSC
        "HighShelf", "HSC", "Highshelf", "high_shelf" -> FilterType.HSC
        "LowPass", "LPF", "Lowpass", "low_pass" -> FilterType.LPF
        "HighPass", "HPF", "Highpass", "high_pass" -> FilterType.HPF
        else -> null
    }

    // ── 反序列化 ──────────────────────────────────────────────────────────

    /**
     * 解析预设包 JSON。兼容两种形态：
     *  1. Salt 的 bundle 对象（format/version/presets）；
     *  2. 裸预设数组（容错，部分第三方工具这样导出）。
     */
    fun parseBundle(json: String): ImportResult {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) return ImportResult.Failure("文件为空")
        return runCatching {
            if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                ImportResult.Success(parsePresetArray(arr))
            } else {
                val root = JSONObject(trimmed)
                val format = root.optString("format", "")
                if (format.isNotEmpty() && format != BUNDLE_FORMAT) {
                    return ImportResult.Failure("不支持预设格式：$format")
                }
                val version = root.optInt("version", BUNDLE_VERSION)
                if (version > BUNDLE_VERSION) {
                    return ImportResult.Failure("预设版本过新（v$version），请升级播放器")
                }
                val arr = root.optJSONArray("presets")
                    ?: return ImportResult.Failure("预设包中缺少 presets 字段")
                val presets = parsePresetArray(arr)
                if (presets.isEmpty()) ImportResult.Failure("预设包中没有可用预设")
                else ImportResult.Success(presets)
            }
        }.getOrElse { ImportResult.Failure("JSON 解析失败：${it.message}") }
    }

    private fun parsePresetArray(arr: JSONArray): List<EqPreset> {
        val result = ArrayList<EqPreset>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val name = obj.optString("name").ifBlank { "导入预设 $i" }
            val preamp = obj.optDouble("outputGain", 0.0).takeIf { !it.isNaN() } ?: 0.0
            val bandsArr = obj.optJSONArray("bands") ?: JSONArray()
            val bands = ArrayList<EqBand>(bandsArr.length())
            for (j in 0 until bandsArr.length()) {
                val b = bandsArr.optJSONObject(j) ?: continue
                if (!b.optBoolean("enabled", true)) continue
                val type = parseFilterType(b.optString("type")) ?: continue
                val freq = b.optDouble("frequency", Double.NaN).toFloat()
                val gain = b.optDouble("gain", 0.0).toFloat()
                val q = b.optDouble("q", 0.7).toFloat()
                if (freq.isNaN() || freq <= 0f) continue
                bands.add(EqBand(type = type, freqHz = freq, gainDb = gain, q = q))
            }
            val id = obj.optString("id").ifBlank { "imported_${System.currentTimeMillis()}_$i" }
            result.add(
                EqPreset(
                    id = id,
                    name = name,
                    tag = "导入",
                    source = "本地文件",
                    preampDb = preamp.toFloat(),
                    bands = bands
                )
            )
        }
        return result
    }

    // ── 自定义预设持久化（SharedPreferences，JSON 字符串） ─────────────────

    fun loadCustomPresets(): List<EqPreset> {
        val raw = StorageService.getString(STORAGE_KEY, "")
        if (raw.isBlank()) return emptyList()
        return when (val r = parseBundle(raw)) {
            is ImportResult.Success -> r.presets
            is ImportResult.Failure -> emptyList()
        }
    }

    /** 追加保存（按 id 去重，后写覆盖先写）。 */
    fun addCustomPresets(presets: List<EqPreset>) {
        if (presets.isEmpty()) return
        val merged = LinkedHashMap<String, EqPreset>()
        loadCustomPresets().forEach { merged[it.id] = it }
        presets.forEach { merged[it.id] = it }
        saveCustomPresets(merged.values.toList())
    }

    fun removeCustomPreset(id: String) {
        val kept = loadCustomPresets().filter { it.id != id }
        saveCustomPresets(kept)
    }

    fun saveCustomPresets(presets: List<EqPreset>) {
        StorageService.setString(STORAGE_KEY, exportJson(presets))
    }

    // ── SAF 文件读写 ──────────────────────────────────────────────────────

    /** 通过 ACTION_CREATE_DOCUMENT 选定的 Uri 写入导出 JSON。 */
    fun writeToUri(context: Context, uri: Uri, json: String): Boolean =
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            } ?: error("无法打开输出流")
            true
        }.getOrElse { false }

    /** 通过 ACTION_OPEN_DOCUMENT 选定的 Uri 读取并解析预设包。 */
    fun readFromUri(context: Context, uri: Uri): ImportResult =
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { ins ->
                ins.bufferedReader(Charsets.UTF_8).readText()
            } ?: return ImportResult.Failure("无法打开所选文件")
            parseBundle(text)
        }.getOrElse { ImportResult.Failure("读取文件失败：${it.message}") }
}
