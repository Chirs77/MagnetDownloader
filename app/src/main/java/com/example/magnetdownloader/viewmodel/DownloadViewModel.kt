package com.example.magnetdownloader.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.magnetdownloader.data.model.DownloadStatus
import com.example.magnetdownloader.data.model.DownloadTask
import com.example.magnetdownloader.data.repository.DownloadRepository
import com.example.magnetdownloader.service.DownloadService
import com.example.magnetdownloader.service.TorrentEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    application: Application,
    private val downloadRepository: DownloadRepository,
    private val torrentEngine: TorrentEngine
) : AndroidViewModel(application) {

    val tasks: StateFlow<List<DownloadTask>> = downloadRepository.getAllTasks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var downloadService: DownloadService? = null
    private var serviceBound = false
    private var progressUpdateJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            downloadService = (service as DownloadService.LocalBinder).getService()
            serviceBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            downloadService = null
            serviceBound = false
        }
    }

    init {
        val intent = Intent(getApplication(), DownloadService::class.java)
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        startProgressUpdates()
    }

    private fun startProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = viewModelScope.launch {
            while (true) {
                tasks.value.filter { it.status == DownloadStatus.DOWNLOADING }.forEach { _ -> }
                delay(1000)
            }
        }
    }

    fun addDownload(magnetUri: String, name: String = "") {
        if (!magnetUri.startsWith("magnet:", ignoreCase = true)) {
            _uiState.value = UiState.Error("无效的 Magnet 链接")
            return
        }
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            if (downloadRepository.getTaskByMagnetUri(magnetUri) != null) {
                _uiState.value = UiState.Error("任务已存在")
                return@launch
            }
            val task = DownloadTask(magnetUri = magnetUri, name = name, savePath = torrentEngine.getDefaultSavePath())
            downloadRepository.insertTask(task)
            val success = torrentEngine.addMagnetDownload(task)
            _uiState.value = if (success) UiState.Success("下载任务已添加") else UiState.Error("添加下载任务失败")
        }
    }

    fun pauseDownload(taskId: String) { viewModelScope.launch { torrentEngine.pauseDownload(taskId) } }
    fun resumeDownload(taskId: String) { viewModelScope.launch { torrentEngine.resumeDownload(taskId) } }

    fun deleteDownload(taskId: String, deleteFiles: Boolean = false) {
        viewModelScope.launch { torrentEngine.removeDownload(taskId, deleteFiles) }
    }

    fun clearUiState() { _uiState.value = UiState.Idle }

    override fun onCleared() {
        super.onCleared()
        progressUpdateJob?.cancel()
        if (serviceBound) getApplication<Application>().unbindService(serviceConnection)
    }

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class Success(val message: String) : UiState()
        data class Error(val message: String) : UiState()
    }
}
