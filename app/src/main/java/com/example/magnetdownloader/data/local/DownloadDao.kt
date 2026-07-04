package com.example.magnetdownloader.data.local

import androidx.room.*
import com.example.magnetdownloader.data.model.DownloadStatus
import com.example.magnetdownloader.data.model.DownloadTask
import com.example.magnetdownloader.data.model.TorrentFile
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun getAllTasks(): Flow<List<DownloadTask>>

    @Query("SELECT * FROM download_tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: String): DownloadTask?

    @Query("SELECT * FROM download_tasks WHERE magnetUri = :magnetUri LIMIT 1")
    suspend fun getTaskByMagnetUri(magnetUri: String): DownloadTask?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: DownloadTask)

    @Update
    suspend fun updateTask(task: DownloadTask)

    @Query("UPDATE download_tasks SET status = :status WHERE id = :taskId")
    suspend fun updateTaskStatus(taskId: String, status: DownloadStatus)

    @Query("UPDATE download_tasks SET progress = :progress, downloadedBytes = :downloadedBytes, downloadSpeed = :downloadSpeed, uploadSpeed = :uploadSpeed, connectedPeers = :peers WHERE id = :taskId")
    suspend fun updateTaskProgress(taskId: String, progress: Float, downloadedBytes: Long, downloadSpeed: Long, uploadSpeed: Long, peers: Int)

    @Delete
    suspend fun deleteTask(task: DownloadTask)

    @Query("DELETE FROM download_tasks WHERE id = :taskId")
    suspend fun deleteTaskById(taskId: String)

    @Query("SELECT * FROM torrent_files WHERE taskId = :taskId")
    fun getFilesByTaskId(taskId: String): Flow<List<TorrentFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<TorrentFile>)

    @Delete
    suspend fun deleteFiles(files: List<TorrentFile>)
}
