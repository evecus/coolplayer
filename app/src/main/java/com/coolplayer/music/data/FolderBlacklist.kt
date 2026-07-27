package com.coolplayer.music.data

/**
 * 文件夹黑名单：持久化在 StorageService.kFolderBlacklist 中。
 */
object FolderBlacklist {

    fun get(): List<String> =
        StorageService.getStringList(StorageService.kFolderBlacklist)

    fun set(folders: List<String>) {
        StorageService.setStringList(StorageService.kFolderBlacklist, folders)
    }

    fun add(folder: String) {
        val cur = get().toMutableList()
        val normalized = folder.trimEnd('/')
        if (normalized !in cur) {
            cur.add(normalized)
            set(cur)
        }
    }

    fun remove(folder: String) {
        val cur = get().toMutableList()
        val normalized = folder.trimEnd('/')
        if (cur.remove(normalized)) set(cur)
    }

    fun contains(folder: String): Boolean {
        val normalized = folder.trimEnd('/')
        return get().any { it == normalized }
    }
}
