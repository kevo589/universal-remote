package dev.kmedrano.remote.protocol.firetv

import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * Generates and (de)serializes the RSA keypair ADB pairing uses, independent of `AdbCrypto`
 * (which has no getter to retrieve a keypair it generated internally — only a
 * file-based save/load pair). We generate/encode/decode it ourselves with standard
 * PKCS8/X509 encoding and hand the reconstructed [KeyPair] to `AdbCrypto.loadAdbKeyPair`,
 * which does accept one directly.
 */
internal object AdbKeyPairCodec {
    private const val KEY_ALGORITHM = "RSA"
    private const val KEY_SIZE_BITS = 2048

    fun generate(): KeyPair {
        val generator = KeyPairGenerator.getInstance(KEY_ALGORITHM)
        generator.initialize(KEY_SIZE_BITS)
        return generator.generateKeyPair()
    }

    fun encode(keyPair: KeyPair): ByteArray {
        val privateBytes = keyPair.private.encoded
        val publicBytes = keyPair.public.encoded
        return ByteBuffer.allocate(Int.SIZE_BYTES + privateBytes.size + publicBytes.size)
            .putInt(privateBytes.size)
            .put(privateBytes)
            .put(publicBytes)
            .array()
    }

    fun decode(bytes: ByteArray): KeyPair {
        val buffer = ByteBuffer.wrap(bytes)
        val privateLength = buffer.int
        val privateBytes = ByteArray(privateLength).also { buffer.get(it) }
        val publicBytes = ByteArray(buffer.remaining()).also { buffer.get(it) }

        val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM)
        val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateBytes))
        val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicBytes))
        return KeyPair(publicKey, privateKey)
    }
}
