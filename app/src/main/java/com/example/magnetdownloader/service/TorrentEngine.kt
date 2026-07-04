package com.example.magnetdownloader.service

import android.content.Context
import android.os.Environment
import com.example.magnetdownloader.data.model.DownloadStatus
import com.example.magnetdownloader.data.model.DownloadTask
import com.example.magnetdownloader.data.repository.DownloadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.libtorrent4j.*
import org.libtorrent4j.alerts.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Suppress("TooManyFunctions", "LargeClass")
@Singleton
class TorrentEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository
) {
    private val sessionManager = SessionManager()
    private val activeHandles = ConcurrentHashMap<String, TorrentHandle>()
    private val infoHashToTaskId = ConcurrentHashMap<String, String>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _engineStatus = MutableStateFlow<EngineStatus>(EngineStatus.Idle)
    val engineStatus: StateFlow<EngineStatus> = _engineStatus

    init {
        initializeSession()
    }

    private fun initializeSession() {
        try {
            val settings = SettingsPack()
                .apply {
                    setBoolean(settings_pack.bool_types.enable_dht.swigValue(), true)
                    setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), true)
                    setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), true)
                    setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), true)

                    setInteger(
                        settings_pack.int_types.alert_mask.swigValue(),
                        alerts.stats_notification or
                            alerts.status_notification or
                            alerts.error_notification or
                            alerts.storage_notification
                    )

                    setInteger(settings_pack.int_types.max_connections_per_torrent.swigValue(), 100)
                    setInteger(settings_pack.int_types.download_rate_limit.swigValue(), 0)
                    setInteger(settings_pack.int_types.upload_rate_limit.swigValue(), 0)
                }

            sessionManager.start(settings)
            _engineStatus.value = EngineStatus.Ready
            startAlertLoop()
            startProgressUpdateLoop()
        } catch (e: Exception) {
            _engineStatus.value = EngineStatus.Error(
                e.message ?: "Failed to initialize BitTorrent session"
            )
        }
    }

    private fun startAlertLoop() {
        scope.launch {
            while (isActive) {
                try {
                    val alert = sessionManager.waitForAlert(1000)
                    alert?.let { processAlert(it) }
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun startProgressUpdateLoop() {
        scope.launch {
            while (isActive) {
                try {
                    activeHandles.forEach { (taskId, handle) ->
                        if (handle.isValid) {
                            updateTaskProgress(taskId, handle)
                        }
                    }
                    delay(1000)
                } catch (_: Exception) {
                    delay(5000)
                }
            }
        }
    }

    @Suppress("ComplexMethod")
    private fun processAlert(alert: Alert<*>) {
        when (alert.type()) {
            AlertType.TORRENT_FINISHED -> {
                val finishedAlert = alert as TorrentFinishedAlert
                val infoHash = finishedAlert.handle().infoHash().toHex()
                val taskId = infoHashToTaskId[infoHash]

                taskId?.let { id ->
                    scope.launch {
                        val task = downloadRepository.getTaskById(id)
                        task?.let {
                            downloadRepository.updateTask(
                                it.copy(
                                    status = DownloadStatus.COMPLETED,
                                    progress = 1f,
                                    downloadSpeed = 0,
                                    uploadSpeed = 0,
                                    completedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
            }

            AlertType.TORRENT_ERROR -> {
                val errorAlert = alert as TorrentErrorAlert
                val infoHash = errorAlert.handle().infoHash().toHex()
                val taskId = infoHashToTaskId[infoHash]

                taskId?.let { id ->
                    scope.launch {
                        val task = downloadRepository.getTaskById(id)
                        task?.let {
                            downloadRepository.updateTask(
                                it.copy(
                                    status = DownloadStatus.ERROR,
                                    errorMessage = errorAlert.errorMessage()
                                )
                            )
                        }
                    }
                }
            }

            else -> {
            }
        }
    }

    private suspend fun updateTaskProgress(taskId: String, handle: TorrentHandle) {
        try {
            val status = handle.status()
            val progress = if (status.totalWanted() > 0) {
                status.progress().toFloat().coerceIn(0f, 1f)
            } else {
                0f
            }

            downloadRepository.updateTaskProgress(
                taskId = taskId,
                progress = progress,
                downloadedBytes = status.totalWantedDone(),
                downloadSpeed = status.downloadRate().toLong(),
                uploadSpeed = status.uploadRate().toLong(),
                peers = status.listPeers()
            )
        } catch (_: Exception) {
        }
    }

    @Suppress("ReturnCount", "LongMethod")
    suspend fun addMagnetDownload(task: DownloadTask): Boolean =
        suspendCoroutine { continuation ->
            scope.launch {
                try {
                    val saveDir = File(task.savePath).apply { mkdirs() }

                    val addParams = AddTorrentParams().apply {
                        savePath = saveDir.absolutePath
                    }

                    addParams.url = task.magnetUri

                    val handle = sessionManager.addTorrent(addParams, File(saveDir.absolutePath))

                    if (handle.isValid) {
                        val infoHash = handle.infoHash().toHex()
                        activeHandles[task.id] = handle
                        infoHashToTaskId[infoHash] = task.id

                        val torrentInfo = handle.torrentFile()
                        if (torrentInfo != null && torrentInfo.isValid) {
                            downloadRepository.updateTask(
                                task.copy(
                                    status = DownloadStatus.DOWNLOADING,
                                    torrentInfoHash = infoHash,
                                    name = if (task.name.isEmpty()) {
                                        torrentInfo.name()
                                    } else {
                                        task.name
                                    },
                                    totalBytes = torrentInfo.totalSize(),
                                    fileCount = torrentInfo.numFiles()
                                )
                            )
                        } else {
                            downloadRepository.updateTask(
                                task.copy(
                                    status = DownloadStatus.DOWNLOADING,
                                    torrentInfoHash = infoHash,
                                    name = if (task.name.isEmpty()) {
                                        "Fetching metadata..."
                                    } else {
                                        task.name
                                    }
                                )
                            )
                        }

                        continuation.resume(true)
                    } else {
                        downloadRepository.updateTask(
                            task.copy(
                                status = DownloadStatus.ERROR,
                                errorMessage = "Failed to add torrent"
                            )
                        )
                        continuation.resume(false)
                    }
                } catch (e: Exception) {
                    downloadRepository.updateTask(
                        task.copy(
                            status = DownloadStatus.ERROR,
                            errorMessage = e.message ?: "Failed to add download"
                        )
                    )
                    continuation.resume(false)
                }
            }
        }

    suspend fun pauseDownload(taskId: String) {
        withContext(Dispatchers.IO) {
            activeHandles[taskId]?.let { handle ->
                if (handle.isValid) {
                    handle.autoManaged(false)
                    handle.pause()
                    downloadRepository.updateTaskStatus(taskId, DownloadStatus.PAUSED)
                }
            }
        }
    }

    suspend fun resumeDownload(taskId: String) {
        withContext(Dispatchers.IO) {
            activeHandles[taskId]?.let { handle ->
                if (handle.isValid) {
                    handle.autoManaged(true)
                    handle.resume()
                    downloadRepository.updateTaskStatus(taskId, DownloadStatus.DOWNLOADING)
                }
            }
        }
    }

    suspend fun removeDownload(taskId: String, deleteFiles: Boolean = false) {
        withContext(Dispatchers.IO) {
            val handle = activeHandles[taskId]
            handle?.let { h ->
                if (h.isValid) {
                    val infoHash = h.infoHash().toHex()
                    sessionManager.remove(h)
                    infoHashToTaskId.remove(infoHash)
                }
                activeHandles.remove(taskId)
            }

            if (deleteFiles) {
                val task = downloadRepository.getTaskById(taskId)
                task?.let {
                    try {
                        File(it.savePath).deleteRecursively()
                    } catch (_: Exception) {
                    }
                }
            }

            downloadRepository.deleteTaskById(taskId)
        }
    }

    fun getDefaultSavePath(): String = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "MagnetDownloader"
    ).absolutePath

    fun shutdown() {
        scope.cancel()
        activeHandles.values.forEach { handle ->
            try {
                if (handle.isValid) {
                    sessionManager.remove(handle)
                }
            } catch (_: Exception) {
            }
        }
        activeHandles.clear()
        infoHashToTaskId.clear()
        sessionManager.stop()
    }

    sealed class EngineStatus {
        object Idle : EngineStatus()
        object Ready : EngineStatus()
        object Loading : EngineStatus()
        data class Error(val message: String) : EngineStatus()
    }
}
