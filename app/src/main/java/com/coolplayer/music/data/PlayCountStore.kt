package com.coolplayer.music.data

import org.json.JSONObject

/**
 * 歌曲播放次数统计（旧版存储方式，已废弃）。
 *
 * 播放次数现已随音乐库一起迁移进 Room 的 `song_library` 表（[com.coolplayer.music.data.SongLibraryEntity.playCount]），
 * 通过 [com.coolplayer.music.data.SongLibraryDao.incrementPlayCount] 用一条 SQL UPDATE 自增，
 * 不再需要这份单独的 path -> count JSON 映射。
 *
 * 本类只保留用于 [com.coolplayer.music.ui.library.LibraryViewModel] 里的一次性迁移逻辑：
 * 老版本升级上来的用户，首次启动时把这里的历史数据读出来回填进新表，随后清空。
 * 新代码不应再直接调用本类。
 */
@Deprecated("播放次数已迁移进 Room song_library 表，仅供旧数据一次性迁移使用")
object PlayCountStore {

    private fun readAll(): MutableMap<String, Int> {
        val raw = StorageService.getJsonArray(StorageService.kPlayCount, "{}")
        val map = mutableMapOf<String, Int>()
        runCatching {
            val obj = JSONObject(raw)
            obj.keys().forEach { k -> map[k] = obj.optInt(k, 0) }
        }
        return map
    }

    private fun writeAll(map: Map<String, Int>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        StorageService.setJsonArray(StorageService.kPlayCount, obj.toString())
    }

    /** 读取全部播放次数（用于批量回填音乐库列表）。 */
    fun getAll(): Map<String, Int> = readAll()

    fun get(path: String): Int = readAll()[path] ?: 0

    /** 指定路径播放次数 +1，返回自增后的值。 */
    fun increment(path: String): Int {
        val map = readAll()
        val next = (map[path] ?: 0) + 1
        map[path] = next
        writeAll(map)
        return next
    }

    /** 清空全部播放次数统计（配合"清理扫描数据"使用）。 */
    fun clear() {
        StorageService.delete(StorageService.kPlayCount)
    }
}
