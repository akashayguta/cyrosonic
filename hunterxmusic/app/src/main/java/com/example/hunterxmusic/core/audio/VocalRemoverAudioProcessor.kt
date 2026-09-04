package com.example.hunterxmusic.core.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

enum class AudioVocalMode {
    NORMAL,              // 🎵 Full original stereo mix
    INSTRUMENTAL_ONLY,   // 🎸 Only Music / Karaoke (Center lead vocals phase-cancelled)
    VOCAL_ONLY           // 🎙️ Only Vocals / Acapella (Center lead vocals isolated, music cancelled)
}

/**
 * Real-Time DSP Audio Processor:
 * 3-State Vocal Isolation/Cancellation (Normal, Instrumental Only, Vocal Only)
 * using mid/side stereo decomposition.
 */
@OptIn(UnstableApi::class)
class VocalRemoverAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var mode = AudioVocalMode.NORMAL

    fun setMode(newMode: AudioVocalMode) {
        mode = newMode
    }

    fun getMode(): AudioVocalMode = mode

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT && inputAudioFormat.channelCount == 2) {
            return inputAudioFormat
        }
        return AudioProcessor.AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.limit() - inputBuffer.position()

        if (inputAudioFormat.channelCount != 2 || inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            val buffer = replaceOutputBuffer(size)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(size)

        while (inputBuffer.remaining() >= 4) {
            val left = inputBuffer.short
            val right = inputBuffer.short

            val lVal = left.toFloat()
            val rVal = right.toFloat()

            // Center Mid Channel (Vocals & Center elements)
            val mid = (lVal + rVal) * 0.5f
            // Stereo Side Channel (Panned Instruments, Guitars, Reverb, Synths)
            val side = (lVal - rVal) * 0.5f

            when (mode) {
                AudioVocalMode.NORMAL -> {
                    buffer.putShort(left)
                    buffer.putShort(right)
                }
                AudioVocalMode.INSTRUMENTAL_ONLY -> {
                    // Center vocal attenuation with stereo imaging & mono fallback
                    // Retain side components while attenuating mid vocals
                    var outL = side + (lVal * 0.20f)
                    var outR = -side + (rVal * 0.20f)

                    // If side is nearly zero (pure mono content), retain attenuated original to prevent silence
                    if (kotlin.math.abs(side) < 50f && (kotlin.math.abs(lVal) > 100f || kotlin.math.abs(rVal) > 100f)) {
                        outL = lVal * 0.35f
                        outR = rVal * 0.35f
                    }

                    val finalL = (outL * 1.35f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    val finalR = (outR * 1.35f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    buffer.putShort(finalL)
                    buffer.putShort(finalR)
                }
                AudioVocalMode.VOCAL_ONLY -> {
                    // Center vocal isolation with preserved spatial depth
                    val outL = mid + (side * 0.15f)
                    val outR = mid - (side * 0.15f)

                    val vocalL = (outL * 1.20f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    val vocalR = (outR * 1.20f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    buffer.putShort(vocalL)
                    buffer.putShort(vocalR)
                }
            }
        }

        buffer.flip()
    }
}
