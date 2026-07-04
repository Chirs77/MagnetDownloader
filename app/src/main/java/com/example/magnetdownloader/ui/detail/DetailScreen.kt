package com.example.magnetdownloader.ui.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.magnetdownloader.data.model.DownloadStatus
import com.example.magnetdownloader.ui.home.formatBytes
import com.example.magnetdownloader.viewmodel.DownloadViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    taskId: String,
    onBack: () -> Unit,
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val tasks by viewModel.tasks.collectAsState()
    val task = tasks.find { it.id == taskId }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (task == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "下载详情", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    when (task.status) {
                        DownloadStatus.DOWNLOADING -> {
                            IconButton(onClick = { viewModel.pauseDownload(task.id) }) {
                                Icon(Icons.Default.Pause, contentDescription = "暂停")
                            }
                        }
                        DownloadStatus.PAUSED -> {
                            IconButton(onClick = { viewModel.resumeDownload(task.id) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "继续")
                            }
                        }
                        else -> { }
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = task.name.ifEmpty { "未命名任务" }, style = MaterialTheme.typography.headlineSmall)
                    if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.PAUSED) {
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "${(task.progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${formatBytes(task.downloadedBytes)} / ${formatBytes(task.totalBytes)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { task.progress },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                    InfoRow(label = "状态", value = when (task.status) {
                        DownloadStatus.WAITING -> "等待中"
                        DownloadStatus.DOWNLOADING -> "下载中"
                        DownloadStatus.PAUSED -> "已暂停"
                        DownloadStatus.COMPLETED -> "已完成"
                        DownloadStatus.ERROR -> "错误"
                        DownloadStatus.CHECKING -> "校验中"
                    })
                    if (task.status == DownloadStatus.ERROR && task.errorMessage != null) {
                        InfoRow(label = "错误信息", value = task.errorMessage, isError = true)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "传输信息", style = MaterialTheme.typography.titleMedium)
                    if (task.status == DownloadStatus.DOWNLOADING) {
                        InfoRow(label = "下载速度", value = "${formatBytes(task.downloadSpeed)}/s")
                        InfoRow(label = "上传速度", value = "${formatBytes(task.uploadSpeed)}/s")
                    }
                    InfoRow(label = "Peers", value = "${task.connectedPeers}")
                    InfoRow(label = "文件数量", value = "${task.fileCount}")
                    InfoRow(label = "总大小", value = formatBytes(task.totalBytes))
                }
            }

            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "任务信息", style = MaterialTheme.typography.titleMedium)
                    InfoRow(label = "创建时间", value = formatDate(task.createdAt))
                    task.completedAt?.let { InfoRow(label = "完成时间", value = formatDate(it)) }
                    InfoRow(label = "Info Hash", value = task.torrentInfoHash.ifEmpty { "-" })
                    InfoRow(label = "保存路径", value = task.savePath)
                }
            }

            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Magnet 链接", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = task.magnetUri,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除任务") },
            text = { Text("确定要删除 \"${task.name}\" 吗？下载的文件也会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDownload(task.id, deleteFiles = true)
                    showDeleteDialog = false
                    onBack()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
fun InfoRow(label: String, value: String, isError: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.3f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(0.7f))
    }
}

fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}
