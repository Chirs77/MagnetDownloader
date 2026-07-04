package com.example.magnetdownloader.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "torrent_files",
    foreignKeys = [ForeignKey(
        entity = DownloadTask::class,
        parentColumns = ["id"],
        childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class TorrentFile(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val taskId: String,
    val filePath: String,
    val fileName: String,
    val fileSize: Long,
    val downloadedBytes: Long = 0,
    val priority: Int = 1,
    val isCompleted: Boolean = false
)
