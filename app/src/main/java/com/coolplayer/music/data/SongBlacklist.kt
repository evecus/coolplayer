package com.coolplayer.music.data

/**
 * 歌曲黑名单：用户从音乐库/歌单列表里「删除（列表不显示）」的歌曲 path 集合。
 *
 * 只做软删除——不删物理文件、不动扫描缓存表，仅在 UI 展示层按 path 过滤。
 * 这样重新扫描仍会写库，但列表不会出现，除非用户在设置里恢复（清空黑名单）。
 * 持久化在 [StorageService.kHiddenSongs]。
 */
object SongBlacklist {

    fun get(): List<String> = StorageService.getStringList(StorageService.kHiddenSongs)

    fun contains(path: String): Boolean = path in get()

    fun hide(paths: Collection<String>) {
        if (paths.isEmpty()) return
        val cur = get().toMutableSet()
        cur.addAll(paths)
        StorageService.setStringList(StorageService.kHiddenSongs, cur.toList())
    }

    fun restore(path: String) {
        val cur = get().toMutableSet()
        if (cur.remove(path)) StorageService.setStringList(StorageService.kHiddenSongs, cur.toList())
    }

    fun clear() = StorageService.delete(StorageService.kHiddenSongs)
}
