package dev.kmedrano.remote.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices")
    fun observeAll(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE id = :id")
    suspend fun getById(id: String): DeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DeviceEntity)

    @Query("UPDATE devices SET credentialCiphertext = :ciphertext, credentialIv = :iv WHERE id = :id")
    suspend fun updateCredential(id: String, ciphertext: ByteArray, iv: ByteArray)

    @Query("DELETE FROM devices WHERE id = :id")
    suspend fun delete(id: String)
}
