package com.coolplayer.music.data

import com.github.promeg.pinyinhelper.Pinyin

/**
 * 中文拼音工具：用于名称类排序与右侧字母索引。
 *
 * - [toSortKey]：整串转可比较的拼音键（小写），使「爱」与以 a 开头的英文排在一起。
 * - [firstLetterKey]：取首字母索引键（0 / A–Z / #），汉字按拼音首字母归组。
 */
object PinyinUtil {

    /** 汉字 Unicode 大致范围（CJK Unified Ideographs）。 */
    private fun Char.isCjk(): Boolean = this in '\u4e00'..'\u9fff'

    /**
     * 生成排序键：汉字转拼音小写，其它字符转小写原样保留。
     * 例：`爱` → `ai`，`Send It` → `send it`，`城南谣` → `chengnanyao`。
     */
    fun toSortKey(text: String): String {
        val src = text.trim()
        if (src.isEmpty()) return ""
        val sb = StringBuilder(src.length * 2)
        for (c in src) {
            when {
                c.isCjk() -> sb.append(Pinyin.toPinyin(c).lowercase())
                else -> sb.append(c.lowercaseChar())
            }
        }
        return sb.toString()
    }

    /**
     * 右侧字母索引键：
     * - 数字开头 → `"0"`
     * - 英文字母开头 → 大写 A–Z
     * - 汉字开头 → 拼音首字母大写（爱→A，城→C）
     * - 其它 → `"#"`
     */
    fun firstLetterKey(text: String): String {
        val c = text.trim().firstOrNull() ?: return "#"
        return when {
            c.isDigit() -> "0"
            c in 'A'..'Z' || c in 'a'..'z' -> c.uppercaseChar().toString()
            c.isCjk() -> {
                val py = Pinyin.toPinyin(c)
                val first = py.firstOrNull()?.uppercaseChar()
                if (first != null && first in 'A'..'Z') first.toString() else "#"
            }
            else -> "#"
        }
    }
}
