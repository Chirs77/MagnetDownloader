package com.example.magnetdownloader.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.magnetdownloader.data.model.DownloadStatus
import com.example.magnetdownloader.data.model.DownloadTask
import com.example.magnetdownloader.ui.add.AddDownloadDialog
import com.example.magnetdownloader.viewmodel.DownloadViewModel
import kotlinx.coroutines.launch
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: DownloadViewModel = hiltViewModel(),
    onTaskClick: (DownloadTask) -> Unit = {}
) {
    val context = LocalContext.current
    val tasks by viewModel.tasks.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<DownloadTask?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState) {
        when (uiState) {
            is DownloadViewModel.UiState.Success -> {
                snackbarHostState.showSnackbar((uiState as DownloadViewModel.UiState.Success).message)
                viewModel.clearUiState()
            }
            is DownloadViewModel.UiState.Error -> {
                snackbarHostState.showSnackbar((uiState as DownloadViewModel.UiState.Error).message, duration = SnackbarDuration.Short)
                viewModel.clearUiState()
            }
            else -> {}
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (!permissions.entries.all { it.value }) {
            scope.launch { snackbarHostState.showSnackbar("需要存储权限才能下载文件") }
        }
    }

    LaunchedEffect(Unit) {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        val needPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needPermissions.isNotEmpty()) storagePermissionLauncher.launch(needPermissions.toTypedArray())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Magnet Downloader", style = MaterialTheme.typography.headlineSmall) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) { Icon(Icons.Default.Add, contentDescription = "添加下载") }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (tasks.isEmpty()) {
                EmptyDownloadView()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(items = tasks, key = { it.id }) { task ->
                        DownloadTaskCard(
                            task = task,
                            onClick = { onTaskClick(task) },
                            onPause = { viewModel.pauseDownload(task.id) },
                            onResume = { viewModel.resumeDownload(task.id) },
                            onDelete = { showDeleteDialog = task },
                            modifier = Modifier.animateItemPlacement()
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddDownloadDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { magnetUri, name ->
                viewModel.addDownload(magnetUri, name)
                showAddDialog = false
            }
        )
    }

    showDeleteDialog?.let { task ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("删除任务") },
            text = { Text("确定要删除 \"${task.name}\" 吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDownload(task.id)
                    showDeleteDialog = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadTaskCard(
    task: DownloadTask,
    onClick: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = task.name.ifEmpty { "未命名任务" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                StatusChip(status = task.status)
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.CHECKING) {
                LinearProgressIndicator(
                    progress = { task.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = when (task.status) {
                        DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
                        DownloadStatus.PAUSED -> MaterialTheme.colorScheme.outline
                        else -> MaterialTheme.colorScheme.secondary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${formatBytes(task.downloadedBytes)} / ${formatBytes(task.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (task.status == DownloadStatus.DOWNLOADING) {
                        Text(
                            text = "${formatBytes(task.downloadSpeed)}/s · ${task.connectedPeers} peers",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Row {
                    when (task.status) {
                        DownloadStatus.DOWNLOADING -> {
                            IconButton(onClick = onPause) {
                                Icon(Icons.Default.Pause, contentDescription = "暂停", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        DownloadStatus.PAUSED -> {
                            IconButton(onClick = onResume) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "继续", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        else -> { }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(status: DownloadStatus) {
    val (text, color) = when (status) {
        DownloadStatus.WAITING -> "等待中" to MaterialTheme.colorScheme.outline
        DownloadStatus.DOWNLOADING -> "下载中" to MaterialTheme.colorScheme.primary
        DownloadStatus.PAUSED -> "已暂停" to MaterialTheme.colorScheme.outline
        DownloadStatus.COMPLETED -> "已完成" to MaterialTheme.colorScheme.tertiary
        DownloadStatus.ERROR -> "错误" to MaterialTheme.colorScheme.error
        DownloadStatus.CHECKING -> "校验中" to MaterialTheme.colorScheme.secondary
    }
    Surface(color = color.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small) {
        Text(text = text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
fun EmptyDownloadView() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "暂无下载任务", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "点击右下角按钮添加 Magnet 链接", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}
