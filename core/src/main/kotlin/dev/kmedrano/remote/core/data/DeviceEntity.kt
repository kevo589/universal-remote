package dev.kmedrano.remote.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room row for one added device. [credentialCiphertext]/[credentialIv] hold whatever opaque
 * pairing credential the device's protocol produced (a Samsung token, an Android TV Remote v2
 * client cert, etc.), AES-256-GCM encrypted via [KeystoreCipherHelper] — [core] never
 * interprets the plaintext bytes, only stores/retrieves them for the protocol module that owns
 * that device.
 */
@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val id: String,
    val protocol: String,
    val displayName: String,
    val host: String,
    val port: Int?,
    val credentialCiphertext: ByteArray?,
    val credentialIv: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeviceEntity) return false
        return id == other.id &&
            protocol == other.protocol &&
            displayName == other.displayName &&
            host == other.host &&
            port == other.port &&
            credentialCiphertext.contentEqualsOrBothNull(other.credentialCiphertext) &&
            credentialIv.contentEqualsOrBothNull(other.credentialIv)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + protocol.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + host.hashCode()
        result = 31 * result + (port ?: 0)
        result = 31 * result + (credentialCiphertext?.contentHashCode() ?: 0)
        result = 31 * result + (credentialIv?.contentHashCode() ?: 0)
        return result
    }
}

private fun ByteArray?.contentEqualsOrBothNull(other: ByteArray?): Boolean =
    if (this == null || other == null) this == null && other == null else contentEquals(other)
