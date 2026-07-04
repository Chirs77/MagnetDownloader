package com.example.magnetdownloader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.magnetdownloader.data.model.DownloadTask
import com.example.magnetdownloader.data.model.TorrentFile

@Database(entities = [DownloadTask::class, TorrentFile::class], version = 1, exportSchema = false)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    companion object {
        const val DATABASE_NAME = "magnet_downloader.db"
    }
}
