package com.longyannfc.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "amiibo_cache")
data class AmiiboCacheEntity(
    @PrimaryKey val id: String,
    val name: String,
    val imageUrl: String,
    val binUrl: String,
    val localPath: String?,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "key_cache")
data class KeyCacheEntity(
    @PrimaryKey val id: String,
    val uid: String,
    val sector: Int,
    val keyA: String?,
    val keyB: String?,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "operation_log")
data class OperationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val detail: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "card_dump")
data class CardDumpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val cardType: String,
    val size: Int,
    val data: ByteArray,
    val failBlocks: String,
    val name: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface AmiiboDao {
    @Query("SELECT * FROM amiibo_cache ORDER BY name")
    fun getAll(): Flow<List<AmiiboCacheEntity>>

    @Query("SELECT * FROM amiibo_cache ORDER BY name")
    suspend fun getAllOnce(): List<AmiiboCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(amiibos: List<AmiiboCacheEntity>)

    @Query("SELECT * FROM amiibo_cache WHERE id = :id")
    suspend fun getById(id: String): AmiiboCacheEntity?

    @Query("DELETE FROM amiibo_cache")
    suspend fun clearAll()
}

@Dao
interface KeyDao {
    @Query("SELECT * FROM key_cache ORDER BY timestamp DESC")
    fun getAll(): Flow<List<KeyCacheEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(key: KeyCacheEntity)

    @Query("SELECT * FROM key_cache WHERE uid = :uid")
    suspend fun getByUid(uid: String): List<KeyCacheEntity>

    @Query("SELECT * FROM key_cache WHERE uid = :uid AND sector = :sector LIMIT 1")
    suspend fun getByKey(uid: String, sector: Int): KeyCacheEntity?

    @Query("DELETE FROM key_cache WHERE uid = :uid")
    suspend fun deleteByUid(uid: String)

    @Query("DELETE FROM key_cache")
    suspend fun clearAll()
}

@Dao
interface LogDao {
    @Query("SELECT * FROM operation_log ORDER BY timestamp DESC")
    fun getAll(): Flow<List<OperationLogEntity>>

    @Insert
    suspend fun insert(log: OperationLogEntity)

    @Query("DELETE FROM operation_log")
    suspend fun clearAll()
}

@Dao
interface CardDumpDao {
    @Query("SELECT * FROM card_dump ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<CardDumpEntity>>

    @Query("SELECT * FROM card_dump WHERE id = :id")
    fun observeById(id: Long): Flow<CardDumpEntity?>

    @Query("SELECT * FROM card_dump WHERE id = :id")
    suspend fun getById(id: Long): CardDumpEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dump: CardDumpEntity): Long

    @Query("UPDATE card_dump SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: Long, name: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE card_dump SET data = :data, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateData(id: Long, data: ByteArray, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM card_dump WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(
    entities = [AmiiboCacheEntity::class, KeyCacheEntity::class, OperationLogEntity::class, CardDumpEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun amiiboDao(): AmiiboDao
    abstract fun keyDao(): KeyDao
    abstract fun logDao(): LogDao
    abstract fun cardDumpDao(): CardDumpDao
}
