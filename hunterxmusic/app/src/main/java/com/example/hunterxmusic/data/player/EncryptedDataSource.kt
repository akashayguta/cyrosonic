package com.example.hunterxmusic.data.player

import android.net.Uri
import android.util.Base64
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.example.hunterxmusic.core.security.CryptoManager
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

@UnstableApi
class EncryptedDataSource(
    private val cryptoManager: CryptoManager
) : BaseDataSource(/* isNetwork = */ false) {

    private var inputStream: InputStream? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        
        val uri = dataSpec.uri
        this.uri = uri
        
        val path = uri.getQueryParameter("path") 
            ?: throw IllegalArgumentException("EncryptedDataSource requires a valid 'path' parameter in URI query.")
        val ivBase64 = uri.getQueryParameter("iv") 
            ?: throw IllegalArgumentException("EncryptedDataSource requires a valid 'iv' parameter in URI query.")
        
        val file = File(path)
        if (!file.exists()) {
            throw java.io.FileNotFoundException("Encrypted file target does not exist on disk: $path")
        }
        
        val ivBytes = Base64.decode(ivBase64, Base64.NO_WRAP)
        val fileStream = FileInputStream(file)
        
        val decryptedStream = cryptoManager.getDecryptingStream(fileStream, ivBytes)
        
        // Handle skip to support seeking using a robust read loop
        if (dataSpec.position > 0) {
            val tempBuffer = ByteArray(4096)
            var skipped: Long = 0
            while (skipped < dataSpec.position) {
                val toRead = Math.min((dataSpec.position - skipped), tempBuffer.size.toLong()).toInt()
                val readCount = decryptedStream.read(tempBuffer, 0, toRead)
                if (readCount < 0) {
                    throw java.io.EOFException("Reached end of file while seeking inside encrypted stream.")
                }
                skipped += readCount
            }
        }
        
        this.inputStream = decryptedStream
        this.bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            // The ciphertext on disk is the plaintext plus a trailing 16-byte
            // AES/GCM authentication tag. The decrypting stream emits only the
            // plaintext, so the number of readable bytes is the file size minus
            // that tag (and minus anything we skipped for seeking).
            (file.length() - GCM_TAG_BYTES - dataSpec.position).coerceAtLeast(0)
        }
        
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val stream = inputStream ?: return C.RESULT_END_OF_INPUT
        val bytesToRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            Math.min(bytesRemaining, length.toLong()).toInt()
        }

        val bytesRead = stream.read(buffer, offset, bytesToRead)
        if (bytesRead == -1) {
            // A decrypted AES/GCM stream can legitimately end a few bytes before
            // the raw file length suggests (the trailing auth tag is consumed but
            // never emitted). Treat any end-of-stream as a clean end of input so
            // playback finishes gracefully instead of throwing near the last frame.
            bytesRemaining = 0
            return C.RESULT_END_OF_INPUT
        }

        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= bytesRead
        }
        
        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        try {
            inputStream?.close()
        } finally {
            inputStream = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    private companion object {
        // AES/GCM appends a 128-bit (16-byte) authentication tag to the ciphertext.
        const val GCM_TAG_BYTES = 16L
    }
}

@UnstableApi
class EncryptedDataSourceFactory(
    private val cryptoManager: CryptoManager
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return EncryptedDataSource(cryptoManager)
    }
}
