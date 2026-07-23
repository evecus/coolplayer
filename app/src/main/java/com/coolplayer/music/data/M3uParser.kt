package com.coolplayer.music.data

import java.io.File

/**
 * M3U / M3U8 播放列表文件解析。
 *
 * 仅支持本地文件路径条目，跳过 #EXTM3U/#EXTINF 等指令行。
 * 相对路径按 [baseDir] 解析为绝对路径。
 */
object M3uParser {

    fun parse(content: String, baseDir: String? = null): List<String> {
        val out = mutableListOf<String>()
        for (raw in content.lineSeparatorSplit()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#")) continue
            val path = if (baseDir != null && !File(line).isAbsolute) {
                File(baseDir, line).absolutePath
            } else {
                line
            }
            if (File(path).exists()) out.add(path)
        }
        return out
    }

    private fun String.lineSeparatorSplit(): List<String> =
        this.split("\r\n", "\n", "\r")
}
