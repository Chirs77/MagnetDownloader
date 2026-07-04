package com.example.magnetdownloader.ui.add

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDownloadDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    val context = LocalContext.current
    var magnetUri by remember { mutableStateOf("") }
    var taskName by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "添加下载任务", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = magnetUri,
                    onValueChange = { magnetUri = it; isError = false },
                    label = { Text("Magnet 链接 *") },
                    placeholder = { Text("magnet:?xt=urn:btih:...") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = isError,
                    supportingText = {
                        if (isError) Text(text = "请输入有效的 Magnet 链接", color = MaterialTheme.colorScheme.error)
                    },
                    singleLine = false, maxLines = 4,
                    trailingIcon = {
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = clipboard.primaryClip
                            if (clip != null && clip.itemCount > 0) {
                                val text = clip.getItemAt(0).text?.toString() ?: ""
                                if (text.startsWith("magnet:")) magnetUri = text
                            }
                        }) { Icon(Icons.Default.ContentPaste, contentDescription = "粘贴") }
                    }
                )
                OutlinedTextField(
                    value = taskName,
                    onValueChange = { taskName = it },
                    label = { Text("任务名称 (可选)") },
                    placeholder = { Text("留空将自动获取") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    text = "提示：Magnet 链接格式为 magnet:?xt=urn:btih:...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (magnetUri.isBlank() || !magnetUri.startsWith("magnet:")) {
                    isError = true
                } else {
                    onConfirm(magnetUri, taskName)
                }
            }) { Text("开始下载") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
