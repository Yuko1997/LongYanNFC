package com.longyannfc.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

    /**
     * v2 → v3：移除 Amiibo 功能，同时删除遗留的 amiibo_cache 表。
     * 读卡历史、密钥缓存与操作日志全部保留。
     */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `amiibo_cache`")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "longyan_nfc.db"
        )
            // 仅允许从 v1 破坏性回退：v1 的历史 schema 已无法从仓库还原，
            // 其余版本差异必须提供显式 Migration，否则启动即报错，避免静默清库。
            .fallbackToDestructiveMigrationFrom(1)
            .addMigrations(MIGRATION_2_3)
            .build()
    }

    @Provides
    fun provideKeyDao(db: AppDatabase): KeyDao = db.keyDao()

    @Provides
    fun provideLogDao(db: AppDatabase): LogDao = db.logDao()

    @Provides
    fun provideCardDumpDao(db: AppDatabase): CardDumpDao = db.cardDumpDao()
}
