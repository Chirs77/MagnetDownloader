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
import org.libtorrent4j.swig.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

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

    private var alertListener: AlertListener? = null

    init {
        initializeSession()
    }

    private fun initializeSession() {
        try {
            val sp = SettingsPack()
                .enableDht(true)
                .enableLsd(true)
                .enableUpnp(true)
                .enableNatpmp(true)
                .connectionsLimit(200)
                .downloadRateLimit(0)
                .uploadRateLimit(0)
                .activeDownloads(10)
                .activeSeeds(5)

            val params = SessionParams(sp)
            sessionManager.start(params)
            _engineStatus.value = EngineStatus.Ready

            setupAlertListener()
            startProgressUpdateLoop()
        } catch (e: Exception) {
            _engineStatus.value = EngineStatus.Error(
                e.message ?: "Failed to initialize BitTorrent session"
            )
        }
    }

    private fun setupAlertListener() {
        alertListener = object : AlertListener {
            override fun types(): IntArray? = null // Listen to all alerts

            override fun alert(alert: Alert<*>) {
                when (alert.type()) {
                    AlertType.TORRENT_FINISHED -> {
                        val finishedAlert = alert as TorrentFinishedAlert
                        val infoHash = finishedAlert.handle().infoHash().toHex()
                        infoHashToTaskId[infoHash]?.let { taskId ->
                            scope.launch {
                                val task = downloadRepository.getTaskById(taskId)
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
                        infoHashToTaskId[infoHash]?.let { taskId ->
                            scope.launch {
                                val task = downloadRepository.getTaskById(taskId)
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

                    else -> {}
                }
            }
        }

        alertListener?.let { sessionManager.addListener(it) }
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
    suspend fun addMagnetDownload(task: DownloadTask): Boolean {
        return try {
            val saveDir = File(task.savePath).apply { mkdirs() }

            // Parse magnet URI using libtorrent4j API
            val ec = error_code()
            val params = libtorrent.parse_magnet_uri(task.magnetUri, ec)

            if (ec.value() != 0) {
                downloadRepository.updateTask(
                    task.copy(
                        status = DownloadStatus.ERROR,
                        errorMessage = "Invalid magnet URI: ${ec.message()}"
                    )
                )
                return false
            }

            params.setSave_path(saveDir.absolutePath)

            // Add the torrent
            val th = sessionManager.swig().add_torrent(params, ec)

            if (th != null && th.is_valid()) {
                val infoHash = Sha1Hash(th.info_hash()).toHex()
                val handle = TorrentHandle(th)
                activeHandles[task.id] = handle
                infoHashToTaskId[infoHash] = task.id

                // Try to get torrent info for metadata
                val torrentInfo = handle.torrentFile()
                if (torrentInfo != null && torrentInfo.isValid) {
                    downloadRepository.updateTask(
                        task.copy(
                            status = DownloadStatus.DOWNLOADING,
                            torrentInfoHash = infoHash,
                            name = if (task.name.isEmpty()) torrentInfo.name() else task.name,
                            totalBytes = torrentInfo.totalSize(),
                            fileCount = torrentInfo.numFiles()
                        )
                    )
                } else {
                    downloadRepository.updateTask(
                        task.copy(
                            status = DownloadStatus.DOWNLOADING,
                            torrentInfoHash = infoHash,
                            name = if (task.name.isEmpty()) "Fetching metadata..." else task.name
                        )
                    )
                }

                true
            } else {
                downloadRepository.updateTask(
                    task.copy(
                        status = DownloadStatus.ERROR,
                        errorMessage = "Failed to add torrent: ${ec.message()}"
                    )
                )
                false
            }
        } catch (e: Exception) {
            downloadRepository.updateTask(
                task.copy(
                    status = DownloadStatus.ERROR,
                    errorMessage = e.message ?: "Failed to add download"
                )
            )
            false
        }
    }

    suspend fun pauseDownload(taskId: String) {
        withContext(Dispatchers.IO) {
            activeHandles[taskId]?.let { handle ->
                if (handle.isValid) {
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
        alertListener?.let { sessionManager.removeListener(it) }
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
