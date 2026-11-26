package com.coolplayer.music.player.audiofx

import android.content.Context
import org.json.JSONObject

/**
 * EQ 预设仓库：合并内置的通用风格预设（自制，无版权顾虑）与
 * assets/audiofx/headphone_presets.json 中的耳机型号预设（数据来自 AutoEQ，已署名）。
 */
object EqPresetRepository {

    /** 通用风格预设：手工调制的少量频段，风格类似截图中的"低音 Bass"“清澈”等。 */
    private val builtinStylePresets: List<EqPreset> = listOf(
        EqPreset.ORIGINAL,
        EqPreset(
            id = "style_bass",
            name = "低音 Bass",
            tag = "通用",
            source = "内置",
            preampDb = -2.0f,
            bands = listOf(
                EqBand(FilterType.LSC, freqHz = 80f, gainDb = 6.0f, q = 0.7f),
                EqBand(FilterType.PK, freqHz = 150f, gainDb = 3.0f, q = 1.0f),
                EqBand(FilterType.PK, freqHz = 3000f, gainDb = -1.5f, q = 1.0f)
            )
        ),
        EqPreset(
            id = "style_clear",
            name = "清澈 Clear",
            tag = "通用",
            source = "内置",
            preampDb = -1.5f,
            bands = listOf(
                EqBand(FilterType.PK, freqHz = 200f, gainDb = -2.0f, q = 0.8f),
                EqBand(FilterType.PK, freqHz = 3000f, gainDb = 3.0f, q = 1.2f),
                EqBand(FilterType.HSC, freqHz = 8000f, gainDb = 2.5f, q = 0.7f)
            )
        ),
        EqPreset(
            id = "style_vocal",
            name = "人声增强 Vocal",
            tag = "通用",
            source = "内置",
            preampDb = -1.5f,
            bands = listOf(
                EqBand(FilterType.PK, freqHz = 120f, gainDb = -2.0f, q = 0.9f),
                EqBand(FilterType.PK, freqHz = 1500f, gainDb = 2.5f, q = 1.0f),
                EqBand(FilterType.PK, freqHz = 3500f, gainDb = 3.0f, q = 1.3f),
                EqBand(FilterType.HSC, freqHz = 9000f, gainDb = -1.5f, q = 0.7f)
            )
        )
    )

    private var cachedHeadphonePresets: List<EqPreset>? = null

    /** 返回全部可用预设：内置风格 + 耳机型号（耳机部分需要 Context 从 assets 读取，首次调用后缓存）。 */
    fun getAllPresets(context: Context): List<EqPreset> {
        return builtinStylePresets + loadHeadphonePresets(context)
    }

    fun getStylePresets(): List<EqPreset> = builtinStylePresets

    fun loadHeadphonePresets(context: Context): List<EqPreset> {
        cachedHeadphonePresets?.let { return it }
        val presets = runCatching {
            val json = context.assets.open("audiofx/headphone_presets.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            parseHeadphonePresets(json)
        }.getOrElse { emptyList() }
        cachedHeadphonePresets = presets
        return presets
    }

    private fun parseHeadphonePresets(json: String): List<EqPreset> {
        val root = JSONObject(json)
        val arr = root.getJSONArray("presets")
        val result = ArrayList<EqPreset>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val bandsArr = obj.getJSONArray("bands")
            val bands = ArrayList<EqBand>(bandsArr.length())
            for (j in 0 until bandsArr.length()) {
                val b = bandsArr.getJSONObject(j)
                bands.add(
                    EqBand(
                        type = FilterType.valueOf(b.getString("type")),
                        freqHz = b.getDouble("freqHz").toFloat(),
                        gainDb = b.getDouble("gainDb").toFloat(),
                        q = b.getDouble("q").toFloat()
                    )
                )
            }
            result.add(
                EqPreset(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    tag = obj.getString("tag"),
                    source = obj.getString("source"),
                    preampDb = obj.getDouble("preampDb").toFloat(),
                    bands = bands
                )
            )
        }
        return result
    }

    fun findById(context: Context, id: String): EqPreset? =
        getAllPresets(context).firstOrNull { it.id == id }
}
