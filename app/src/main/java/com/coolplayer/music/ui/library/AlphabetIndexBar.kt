package com.coolplayer.music.ui.library

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 索引条目字符集：0-9 A-Z # */
val alphabetIndexChars: List<String> =
    listOf("0") + ('A'..'Z').map { it.toString() } + listOf("#")

/**
 * 右侧字母/数字索引条，拖动可快速定位列表。
 *
 * [onLetterSelected] 在按下或拖动经过某个字符时回调，调用方负责滚动列表。
 */
@Composable
fun AlphabetIndexBar(
    modifier: Modifier = Modifier,
    onLetterSelected: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var rowHeightPx by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }

    fun indexForY(y: Float): Int {
        if (rowHeightPx <= 0f) return 0
        val idx = (y / rowHeightPx).toInt()
        return idx.coerceIn(0, alphabetIndexChars.size - 1)
    }

    Column(
        modifier = modifier
            .width(20.dp)
            .fillMaxHeight()
            .onGloballyPositioned { coords ->
                if (alphabetIndexChars.isNotEmpty()) {
                    rowHeightPx = coords.size.height / alphabetIndexChars.size.toFloat()
                }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        onLetterSelected(alphabetIndexChars[indexForY(offset.y)])
                    },
                    onVerticalDrag = { change, _ ->
                        onLetterSelected(alphabetIndexChars[indexForY(change.position.y)])
                    }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        alphabetIndexChars.forEach { ch ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 0.5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ch,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 计算字符串首字母索引 key（用于分组滚动定位）。 */
fun firstIndexKeyOf(text: String): String {
    val c = text.trim().firstOrNull() ?: return "#"
    return when {
        c.isDigit() -> "0"
        c.uppercaseChar() in 'A'..'Z' -> c.uppercaseChar().toString()
        else -> "#"
    }
}
