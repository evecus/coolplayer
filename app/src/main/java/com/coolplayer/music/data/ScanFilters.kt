package com.coolplayer.music.data

/**
 * 扫描过滤条件：控制"媒体来源"页里除文件夹黑名单外的所有可调选项。
 *
 * 持久化在 [StorageService] 中，所有字段均有合理默认值（即"不限制"）。
 */
data class ScanFilters(
    /** 是否通过 Android 媒体库（MediaStore）扫描。 */
    val useMediaStore: Boolean = true,
    /** 自定义扫描文件夹（为空表示不限制来源目录，走全局扫描）。 */
    val customFolders: List<String> = emptyList(),
    /** 是否排除时长低于 [minDurationSeconds] 的音频。 */
    val skipShortAudio: Boolean = true,
    val minDurationSeconds: Int = 60,
    /** 允许的音频扩展名集合（小写，含点，如 ".mp3"）。为空表示不限制（等价于全部支持格式）。 */
    val allowedExtensions: Set<String> = emptySet(),
    /** 文件大小范围（MB）。为 null 表示不限制。 */
    val minSizeMb: Int? = null,
    val maxSizeMb: Int? = null,
    /** 扫描去重：标题 + 艺术家完全相同时只保留第一条。默认开启。 */
    val dedupeEnabled: Boolean = true,
    /** 定期扫描：App 运行时，若距上次*完整完成*的扫描已超过间隔天数则静默扫描一次。默认关闭。 */
    val periodicScanEnabled: Boolean = false,
    val periodicScanIntervalDays: Int = 1
) {
    fun toJson(): org.json.JSONObject = org.json.JSONObject().apply {
        put("useMediaStore", useMediaStore)
        put("customFolders", org.json.JSONArray(customFolders))
        put("skipShortAudio", skipShortAudio)
        put("minDurationSeconds", minDurationSeconds)
        put("allowedExtensions", org.json.JSONArray(allowedExtensions.toList()))
        put("minSizeMb", minSizeMb ?: -1)
        put("maxSizeMb", maxSizeMb ?: -1)
        put("dedupeEnabled", dedupeEnabled)
        put("periodicScanEnabled", periodicScanEnabled)
        put("periodicScanIntervalDays", periodicScanIntervalDays)
    }

    companion object {
        fun fromJson(obj: org.json.JSONObject): ScanFilters {
            val folders = obj.optJSONArray("customFolders")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            }.orEmpty()
            val exts = obj.optJSONArray("allowedExtensions")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            }.orEmpty().toSet()
            val minSize = obj.optInt("minSizeMb", -1).takeIf { it >= 0 }
            val maxSize = obj.optInt("maxSizeMb", -1).takeIf { it >= 0 }
            return ScanFilters(
                useMediaStore = obj.optBoolean("useMediaStore", true),
                customFolders = folders,
                skipShortAudio = obj.optBoolean("skipShortAudio", true),
                minDurationSeconds = obj.optInt("minDurationSeconds", 60),
                allowedExtensions = exts,
                minSizeMb = minSize,
                maxSizeMb = maxSize,
                dedupeEnabled = obj.optBoolean("dedupeEnabled", true),
                periodicScanEnabled = obj.optBoolean("periodicScanEnabled", false),
                periodicScanIntervalDays = obj.optInt("periodicScanIntervalDays", 1).coerceAtLeast(1)
            )
        }
    }
}

/** [ScanFilters] 的读写封装。 */
object ScanFiltersStore {
    fun get(): ScanFilters {
        val raw = StorageService.getJsonArray(StorageService.kScanFilters, "")
        if (raw.isBlank()) return ScanFilters()
        return runCatching { ScanFilters.fromJson(org.json.JSONObject(raw)) }.getOrDefault(ScanFilters())
    }

    fun set(filters: ScanFilters) {
        StorageService.setJsonArray(StorageService.kScanFilters, filters.toJson().toString())
    }
}
