package com.example.ui.components

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * A custom Media3 AudioProcessor that introduces a configurable delay to PCM 16-bit audio.
 */
@OptIn(UnstableApi::class)
class DelayAudioProcessor : BaseAudioProcessor() {

    private var delayMs = 0
    private var buffer = ByteArray(0)
    private var writeIndex = 0
    private var readIndex = 0
    private var bytesBuffered = 0

    fun setDelayMs(delay: Int) {
        if (this.delayMs != delay) {
            this.delayMs = delay
            flush()
        }
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        // Return inputAudioFormat unchanged to allow pass-through of any audio format
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (delayMs <= 0 || inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            // No delay or unsupported format, pass through
            val outputBuffer = replaceOutputBuffer(remaining)
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        // 16-bit PCM has 2 bytes per sample per channel
        val bytesPerSample = 2
        val bytesPerSecond = inputAudioFormat.sampleRate * inputAudioFormat.channelCount * bytesPerSample
        val requiredDelayBytes = ((bytesPerSecond.toLong() * delayMs) / 1000).toInt()

        if (requiredDelayBytes == 0) {
            val outputBuffer = replaceOutputBuffer(remaining)
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        if (buffer.size != requiredDelayBytes) {
            buffer = ByteArray(requiredDelayBytes)
            writeIndex = 0
            readIndex = 0
            bytesBuffered = 0
        }

        val outputBuffer = replaceOutputBuffer(remaining)

        while (inputBuffer.hasRemaining()) {
            val byteValue = inputBuffer.get()

            if (bytesBuffered >= requiredDelayBytes) {
                // Buffer is full, output oldest byte and advance read pointer
                outputBuffer.put(buffer[readIndex])
                readIndex = (readIndex + 1) % requiredDelayBytes
                bytesBuffered--
            } else {
                // If we haven't buffered enough bytes yet, emit silence (zero) to create delay
                outputBuffer.put(0.toByte())
            }

            buffer[writeIndex] = byteValue
            writeIndex = (writeIndex + 1) % requiredDelayBytes
            bytesBuffered++
        }

        outputBuffer.flip()
    }

    override fun onFlush() {
        writeIndex = 0
        readIndex = 0
        bytesBuffered = 0
        if (buffer.isNotEmpty()) {
            buffer.fill(0)
        }
    }

    override fun onReset() {
        buffer = ByteArray(0)
        writeIndex = 0
        readIndex = 0
        bytesBuffered = 0
    }
}
