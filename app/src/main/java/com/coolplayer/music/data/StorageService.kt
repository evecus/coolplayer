package com.coolplayer.music.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 基于 SharedPreferences + JSON 序列化的全局 KV 存储。
 */
object StorageService {

    private lateinit var prefs: SharedPreferences

    fun init(context: Context, boxName: String = "salt_music_store") {
        prefs = context.getSharedPreferences(boxName, Context.MODE_PRIVATE)
    }

    // ── 基础读写 ─────────────────────────────────────────────────────────

    fun getString(key: String, default: String = ""): String =
        prefs.getString(key, default) ?: default

    fun setString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getInt(key: String, default: Int = 0): Int =
        prefs.getInt(key, default)

    fun setInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean =
        prefs.getBoolean(key, default)

    fun setBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun getLong(key: String, default: Long = 0L): Long =
        prefs.getLong(key, default)

    fun setLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    fun getFloat(key: String, default: Float = 0f): Float =
        prefs.getFloat(key, default)

    fun setFloat(key: String, value: Float) {
        prefs.edit().putFloat(key, value).apply()
    }

    // ── List<String> ────────────────────────────────────────────────────

    fun getStringList(key: String, default: List<String> = emptyList()): List<String> {
        val raw = prefs.getString(key, null) ?: return default
        return runCatching {
            JSONArray(raw).let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            }
        }.getOrDefault(default)
    }

    fun setStringList(key: String, value: List<String>) {
        val arr = JSONArray()
        value.forEach { arr.put(it) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    // ── List<Map<String,String>> ────────────────────────────────────────

    fun getMapList(
        key: String,
        default: List<Map<String, String>> = emptyList()
    ): List<Map<String, String>> {
        val raw = prefs.getString(key, null) ?: return default
        return runCatching {
            JSONArray(raw).let { arr ->
                (0 until arr.length()).map { idx ->
                    val obj = arr.getJSONObject(idx)
                    val map = mutableMapOf<String, String>()
                    obj.keys().forEach { k -> map[k] = obj.getString(k) }
                    map
                }
            }
        }.getOrDefault(default)
    }

    fun setMapList(key: String, value: List<Map<String, String>>) {
        val arr = JSONArray()
        value.forEach { m ->
            val obj = JSONObject()
            m.forEach { (k, v) -> obj.put(k, v) }
            arr.put(obj)
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    // ── JSON 对象（用于扫描结果缓存） ────────────────────────────────────

    fun getJsonArray(key: String, default: String = "[]"): String =
        prefs.getString(key, default) ?: default

    fun setJsonArray(key: String, json: String) {
        prefs.edit().putString(key, json).apply()
    }

    fun delete(key: String) {
        prefs.edit().remove(key).apply()
    }

    // ── Key 常量 ───────────────────────────────────────────────────────

    const val kMusicScanPaths = "music_scan_paths"
    const val kScanFilters = "scan_filters"
    const val kFolderBlacklist = "folder_blacklist"
    const val kMusicCategory = "music_category"
    @Deprecated("改用 kMusicSortField + kMusicSortAscending")
    const val kMusicSongSort = "music_song_sort"
    const val kMusicSortField = "music_sort_field"
    const val kMusicSortAscending = "music_sort_ascending"
    const val kMusicGroupSort = "music_group_sort"
    @Deprecated("音乐库缓存已迁移进 Room song_library 表，此 key 仅用于旧数据一次性迁移读取")
    const val kMusicLibraryCache = "music_library_cache"
    const val kHomeSeedColor = "home_seed_color"
    const val kPlayerSeedColor = "player_seed_color"
    const val kThemeMode = "theme_mode"
    const val kDynamicColor = "dynamic_color"
    const val kCoverColor = "cover_color"
    const val kLastPlaylist = "last_playlist"
    const val kLastIndex = "last_index"
    const val kLastPosition = "last_position"
    @Deprecated("播放次数已迁移进 Room song_library 表，此 key 仅用于旧数据一次性迁移读取")
    const val kPlayCount = "play_count_map"
    const val kLastScanCompletedAt = "last_scan_completed_at"

    // ── 歌词显示设置 ───────────────────────────────────────────────────────
    const val kLyricsFontSize = "lyrics_font_size"
    const val kLyricsLineSpacing = "lyrics_line_spacing"
    const val kLyricsAlignment = "lyrics_alignment"
    const val kLyricsFontWeight = "lyrics_font_weight"
}
