package com.longyannfc.app.di

import android.content.Context
import androidx.room.Room
import com.longyannfc.app.data.local.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "longyan_nfc.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideAmiiboDao(db: AppDatabase): AmiiboDao = db.amiiboDao()

    @Provides
    fun provideKeyDao(db: AppDatabase): KeyDao = db.keyDao()

    @Provides
    fun provideLogDao(db: AppDatabase): LogDao = db.logDao()

    @Provides
    fun provideCardDumpDao(db: AppDatabase): CardDumpDao = db.cardDumpDao()
}
