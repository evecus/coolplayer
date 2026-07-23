package com.coolplayer.music.ui.widget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coolplayer.music.ui.library.LibraryViewModel

/**
 * 扫描进度弹窗。
 *
 * 扫描过程中逐行滚动展示已发现的文件名（类似终端日志滚动效果），
 * 扫描完成后展示汇总信息并启用"确定"按钮，点击后关闭弹窗、
 * 数据已经由 [LibraryViewModel.rescan] 写入 [LibraryViewModel.allSongs]。
 *
 * 弹窗不可通过点击外部或返回键关闭，必须显式点击"确定"。
 */
@Composable
fun ScanProgressDialog(vm: LibraryViewModel, onDismiss: () -> Unit) {
    val lines by vm.scanProgressLines.collectAsState()
    val completed by vm.scanCompleted.collectAsState()
    val total by vm.lastScanTotal.collectAsState()
    val listState = rememberLazyListState()

    // 新增文件名时自动滚动到底部，营造扫描日志滚动效果
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.scrollToItem(lines.size - 1)
        }
    }

    AlertDialog(
        onDismissRequest = { /* 扫描进行中不可通过外部点击关闭 */ },
        title = {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(text = "扫描音乐", fontWeight = FontWeight.Bold)
                if (!completed) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterEnd).size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        },
        text = {
            Column {
                if (completed) {
                    Text(
                        text = "扫描完成，共 $total 首歌曲",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().height(360.dp)
                ) {
                    items(lines) { line ->
                        Text(
                            text = line,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = completed,
                onClick = onDismiss
            ) {
                Text("确定")
            }
        }
    )
}
