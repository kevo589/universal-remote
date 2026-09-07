package dev.kmedrano.remote.core.data

import android.content.Context
import dev.kmedrano.remote.core.ProtocolType
import dev.kmedrano.remote.core.TvDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persists added devices and their (encrypted) pairing credentials across app restarts.
 * Credential bytes are opaque here — only the protocol module that created them knows how to
 * interpret them.
 */
class DeviceRepository(context: Context) {
    private val dao = RemoteDatabase.getInstance(context).deviceDao()

    val devices: Flow<List<TvDevice>> = dao.observeAll().map { entities -> entities.map { it.toTvDevice() } }

    suspend fun addDevice(device: TvDevice) {
        dao.upsert(device.toEntity())
    }

    suspend fun removeDevice(deviceId: String) {
        dao.delete(deviceId)
    }

    suspend fun getCredential(deviceId: String): ByteArray? {
        val entity = dao.getById(deviceId) ?: return null
        val ciphertext = entity.credentialCiphertext ?: return null
        val iv = entity.credentialIv ?: return null
        return runCatching { KeystoreCipherHelper.decrypt(ciphertext, iv) }.getOrNull()
    }

    suspend fun saveCredential(deviceId: String, credential: ByteArray) {
        val (ciphertext, iv) = KeystoreCipherHelper.encrypt(credential)
        dao.updateCredential(deviceId, ciphertext, iv)
    }
}

private fun DeviceEntity.toTvDevice() = TvDevice(
    id = id,
    protocol = ProtocolType.valueOf(protocol),
    displayName = displayName,
    host = host,
    port = port,
)

private fun TvDevice.toEntity() = DeviceEntity(
    id = id,
    protocol = protocol.name,
    displayName = displayName,
    host = host,
    port = port,
    credentialCiphertext = null,
    credentialIv = null,
)
