package com.example.magnetdownloader.di

import android.content.Context
import androidx.room.Room
import com.example.magnetdownloader.data.local.DownloadDatabase
import com.example.magnetdownloader.data.local.DownloadDao
import com.example.magnetdownloader.data.repository.DownloadRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DownloadDatabase {
        return Room.databaseBuilder(context, DownloadDatabase::class.java, DownloadDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideDownloadDao(database: DownloadDatabase): DownloadDao = database.downloadDao()

    @Provides
    @Singleton
    fun provideDownloadRepository(downloadDao: DownloadDao): DownloadRepository = DownloadRepository(downloadDao)
}
