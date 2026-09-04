package com.example.hunterxmusic.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Enterprise-grade security wrapper managing hardware-backed AES keys in the Android Keystore.
 * Provides on-the-fly encryption and decryption streams for media tracks.
 */
class CryptoManager {

    private val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
        load(null)
    }

    /**
     * Resolves the encryption key or generates a new one inside the secure Android Keystore.
     */
    private fun getKey(): SecretKey {
        val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        return existingKey?.secretKey ?: generateKey()
    }

    /**
     * Generates a secure AES key in the Hardware Security Module (HSM) / Keystore system.
     */
    private fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )
        val keySpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        
        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }

    /**
     * Wraps an output stream with a CipherOutputStream initialized to encrypt bytes with the Keystore key.
     * Returns both the stream and the dynamically generated Initialization Vector (IV).
     */
    fun getEncryptingStream(outputStream: OutputStream): EncryptResult {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val cipherOutputStream = CipherOutputStream(outputStream, cipher)
        return EncryptResult(cipherOutputStream, cipher.iv)
    }

    /**
     * Wraps an input stream with a CipherInputStream initialized with the original track IV to decrypt media bytes on the fly.
     */
    fun getDecryptingStream(inputStream: InputStream, iv: ByteArray): InputStream {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, getKey(), spec)
        return CipherInputStream(inputStream, cipher)
    }

    /**
     * Container holding the cipher stream and the generated IV bytes.
     */
    data class EncryptResult(
        val outputStream: CipherOutputStream,
        val iv: ByteArray
    )

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "MusicStreamingDecryptionKey"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
