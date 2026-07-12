package com.coolplayer.music.ui.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 扫描进度对话框：显示已发现的歌曲数和取消按钮。
 */
@Composable
fun ScanProgressDialog(
    foundCount: Int,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("扫描中") },
        text = {
            Column {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("已发现 $foundCount 首歌曲", fontSize = 14.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) { Text("取消") }
        }
    )
}

/**
 * 全屏加载视图。
 */
@Composable
fun LoadingView(text: String = "加载中…") {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(color = scheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(text, color = scheme.onSurfaceVariant, fontSize = 14.sp)
        }
    }
}

/**
 * 空内容占位。
 */
@Composable
fun EmptyView(message: String = "暂无内容") {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = scheme.onSurfaceVariant,
            fontSize = 14.sp,
            fontWeight = FontWeight.Light
        )
    }
}
