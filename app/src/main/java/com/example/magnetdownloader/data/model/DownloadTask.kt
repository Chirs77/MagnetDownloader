package com.example.magnetdownloader.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "download_tasks")
data class DownloadTask(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val magnetUri: String,
    val name: String = "",
    val savePath: String = "",
    val status: DownloadStatus = DownloadStatus.WAITING,
    val progress: Float = 0f,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val downloadSpeed: Long = 0L,
    val uploadSpeed: Long = 0L,
    val totalPeers: Int = 0,
    val connectedPeers: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val errorMessage: String? = null,
    val torrentInfoHash: String = "",
    val fileCount: Int = 0,
    val isSequential: Boolean = false
)

enum class DownloadStatus {
    WAITING, DOWNLOADING, PAUSED, COMPLETED, ERROR, CHECKING
}
