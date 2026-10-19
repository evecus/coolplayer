package com.coolplayer.music.player

import android.util.LruCache

/**
 * Simple in-memory LRU cache for embedded cover art bytes.
 * Avoids re-reading the same file's cover on every track change / Service rebuild.
 */
object CoverCache {
    private const val MAX_SIZE_BYTES = 16 * 1024 * 1024 // 16 MB

    private val cache = object : LruCache<String, ByteArray>(MAX_SIZE_BYTES) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    fun get(path: String): ByteArray? = cache.get(path)

    fun put(path: String, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        cache.put(path, bytes)
    }

    fun clear() {
        cache.evictAll()
    }
}
