package com.example.magnetdownloader.data.repository

import com.example.magnetdownloader.data.local.DownloadDao
import com.example.magnetdownloader.data.model.DownloadStatus
import com.example.magnetdownloader.data.model.DownloadTask
import com.example.magnetdownloader.data.model.TorrentFile
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(private val downloadDao: DownloadDao) {
    fun getAllTasks(): Flow<List<DownloadTask>> = downloadDao.getAllTasks()
    suspend fun getTaskById(taskId: String): DownloadTask? = downloadDao.getTaskById(taskId)
    suspend fun getTaskByMagnetUri(magnetUri: String): DownloadTask? = downloadDao.getTaskByMagnetUri(magnetUri)
    suspend fun insertTask(task: DownloadTask) = downloadDao.insertTask(task)
    suspend fun updateTask(task: DownloadTask) = downloadDao.updateTask(task)
    suspend fun updateTaskStatus(taskId: String, status: DownloadStatus) = downloadDao.updateTaskStatus(taskId, status)
    suspend fun updateTaskProgress(taskId: String, progress: Float, downloadedBytes: Long, downloadSpeed: Long, uploadSpeed: Long, peers: Int) =
        downloadDao.updateTaskProgress(taskId, progress, downloadedBytes, downloadSpeed, uploadSpeed, peers)
    suspend fun deleteTask(task: DownloadTask) = downloadDao.deleteTask(task)
    suspend fun deleteTaskById(taskId: String) = downloadDao.deleteTaskById(taskId)
    fun getFilesByTaskId(taskId: String): Flow<List<TorrentFile>> = downloadDao.getFilesByTaskId(taskId)
    suspend fun insertFiles(files: List<TorrentFile>) = downloadDao.insertFiles(files)
    suspend fun deleteFiles(files: List<TorrentFile>) = downloadDao.deleteFiles(files)
}
