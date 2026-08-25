package com.coolplayer.music.data

import java.io.File
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey

/**
 * 音频元数据（标题 / 艺人 / 专辑 / 封面 / 歌词）。
 *
 * 底层由 [AudioMetadataReader] 用 JAudioTagger 解析，覆盖 MP3(ID3v1/v2) / FLAC(Vorbis
 * Comment + METADATA_BLOCK_PICTURE) / OGG(Vorbis Comment) / M4A/AAC(MP4 box) /
 * WMA / APE 等主流格式的标签与内嵌封面。
 */
data class AudioMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val coverBytes: ByteArray? = null,
    val lyrics: String? = null
) {
    // ByteArray 在 data class 中默认按引用比较，重写以按内容比较
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/**
 * 基于 JAudioTagger 的音频元数据读取器。
 *
 * 替换了此前纯 Kotlin 手写、只支持 MP3(ID3v2) 的解析器：JAudioTagger 对每种容器格式
 * 使用各自正确的标签体系（ID3v2 / Vorbis Comment / MP4 atom 等），因此 FLAC、M4A、OGG
 * 等此前无法读出标题 / 封面的格式现在也能正确解析。
 */
object AudioMetadataReader {

    fun readFile(filePath: String): AudioMetadata {
        val file = File(filePath)
        if (!file.exists()) return AudioMetadata()

        val result = runCatching {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag

            val title = tag?.getFirst(FieldKey.TITLE)?.takeIf { it.isNotBlank() }
            val artist = tag?.getFirst(FieldKey.ARTIST)?.takeIf { it.isNotBlank() }
            val album = tag?.getFirst(FieldKey.ALBUM)?.takeIf { it.isNotBlank() }

            // 优先取内嵌歌词标签（部分格式为 LYRICS，部分为 UNSYNCED_LYRICS）
            val lyrics = tag?.getFirst(FieldKey.LYRICS)?.takeIf { it.isNotBlank() }

            // 封面：取第一张内嵌图片（多数音频文件只有一张封面）
            val coverBytes = runCatching {
                tag?.firstArtwork?.binaryData
            }.getOrNull()

            AudioMetadata(title, artist, album, coverBytes, lyrics)
        }.getOrElse { AudioMetadata() }

        // 无内嵌歌词时查找外部 .lrc 文件
        if (result.lyrics.isNullOrEmpty()) {
            val lrc = readSidecarLrc(filePath)
            if (lrc != null) {
                return result.copy(lyrics = lrc)
            }
        }
        return result
    }

    private fun readSidecarLrc(audioPath: String): String? {
        val base = audioPath.substringBeforeLast('.', audioPath)
        for (ext in listOf(".lrc", ".LRC")) {
            val f = File("$base$ext")
            if (f.exists()) {
                return runCatching { f.readText(Charsets.UTF_8) }
                    .getOrElse { f.readText(Charsets.ISO_8859_1) }
            }
        }
        return null
    }
}

// ── LRC 解析 ───────────────────────────────────────────────────────────

data class LrcLine(val time: Long, val text: String)

fun parseLrc(lrcText: String): List<LrcLine> {
    val lines = mutableListOf<LrcLine>()
    val regex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)""")

    for (rawLine in lrcText.split('\n')) {
        val line = rawLine.trim()
        for (match in regex.findAll(line)) {
            val min = match.groupValues[1].toInt()
            val sec = match.groupValues[2].toInt()
            val msStr = match.groupValues[3]
            val ms = when (msStr.length) {
                2 -> msStr.toInt() * 10
                3 -> msStr.toInt()
                else -> 0
            }
            val text = match.groupValues[4].trim()
            lines.add(LrcLine(min * 60_000L + sec * 1000L + ms, text))
        }
    }
    lines.sortBy { it.time }
    return lines
}
