package com.coolplayer.music.player

import android.util.LruCache

/**
 * 播放页封面字节数组的内存缓存。
 *
 * 背景：播放页展示的是音频文件内嵌的原始封面（不压缩，保画质）。原来的实现是
 * 每次 [MusicPlayer.loadMetadata] 都用 JAudioTagger 把整个文件重新解析一遍，
 * 意味着"单曲循环同一首歌"或"Service 重建后恢复播放"时会反复读盘+解析。
 * JAudioTagger 是纯 Java 实现，单个文件全量解析（读音频头 + 标签 + 提取封面）
 * 在旗舰机上也往往需要几十到上百毫秒 CPU。
 *
 * 本缓存把"路径 -> 封面原始字节"按最近使用保留在内存里：
 * - 同一首歌再次播放直接命中，零读盘；
 * - 容量按字节数限制（默认 16MB，约可容纳 4~8 张常见尺寸的原图），
 *   超出后按 LRU 淘汰，不会造成内存压力；
 * - 只缓存播放页用得到的那一份，扫描期写入数据库的小图缩略图走
 *   [com.coolplayer.music.data.SongCoverEntity]，互不影响。
 *
 * 线程安全：LruCache 的所有公开方法内部都有 synchronized，可从 IO 线程直接调用。
 */
object CoverCache {

    /** 默认 16MB。原始内嵌封面常见 100KB~2MB，16MB 足以覆盖最近播放的若干首。 */
    private const val DEFAULT_MAX_BYTES = 16 * 1024 * 1024

    private val cache: LruCache<String, ByteArray> = object : LruCache<String, ByteArray>(DEFAULT_MAX_BYTES) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    /** 取缓存；未命中返回 null。调用方负责读盘后调用 [put] 回填。 */
    fun get(path: String): ByteArray? = cache.get(path)

    /** 写入缓存（读盘成功后调用）。 */
    fun put(path: String, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        cache.put(path, bytes)
    }

    /** 清空（退出播放器等场景）。 */
    fun clear() = cache.evictAll()
}
