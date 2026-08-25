package com.coolplayer.music.player.audiofx

/**
 * 单个参数均衡滤波器的类型。
 * PK  = Peaking（钟形，最常用，对应截图里 PEAKEQ 的每一段）
 * LSC = Low Shelf（低频架式）
 * HSC = High Shelf（高频架式）
 */
enum class FilterType { PK, LSC, HSC }

/**
 * 单个滤波器参数：中心频率 Fc、增益 Gain、品质因数 Q。
 * 与 AutoEQ 的 ParametricEQ.txt 格式一一对应：
 *   Filter n: ON PK Fc 105 Hz Gain -3.0 dB Q 0.70
 */
data class EqBand(
    val type: FilterType = FilterType.PK,
    val freqHz: Float,
    val gainDb: Float,
    val q: Float = 0.7f
)

/**
 * 一个完整的 EQ 预设：Preamp（整体增益补偿，避免削波）+ 若干频段。
 *
 * @param id       唯一标识，用于持久化保存当前选中的预设
 * @param name     显示名称，如 "低音 Bass"、"Apple AirPods Max"
 * @param tag      分类标签，如 "通用" "定制原声" "耳机型号"
 * @param source   来源/作者署名，如 "Moriafly"、"oratory1990"（AutoEQ 数据必须署名）
 * @param preampDb 整体前置增益（dB），通常为负值，防止叠加后削波失真
 * @param bands    参数均衡频段列表
 */
data class EqPreset(
    val id: String,
    val name: String,
    val tag: String,
    val source: String,
    val preampDb: Float,
    val bands: List<EqBand>
) {
    companion object {
        /** "原声 NONE"：不做任何处理的预设。 */
        val ORIGINAL = EqPreset(
            id = "original",
            name = "原声",
            tag = "定制原声",
            source = "无",
            preampDb = 0f,
            bands = emptyList()
        )
    }
}
