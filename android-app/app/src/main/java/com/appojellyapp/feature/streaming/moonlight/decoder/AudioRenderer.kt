package com.appojellyapp.feature.streaming.moonlight.decoder

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue

/**
 * Audio renderer for the Moonlight streaming protocol.
 *
 * Receives Opus-encoded audio packets from the server, decodes them
 * using MediaCodec's Opus decoder, and plays them via AudioTrack
 * in low-latency mode.
 *
 * The Moonlight protocol uses Opus at 48kHz for audio streaming,
 * supporting both stereo and 5.1 surround configurations.
 */
class AudioRenderer(
    private val sampleRate: Int = 48000,
    private val channelCount: Int = 2,
) {
    private var audioTrack: AudioTrack? = null
    private var opusDecoder: MediaCodec? = null
    private var decoderThread: Thread? = null
    private var running = false

    private val packetQueue = LinkedBlockingQueue<ByteArray>(60)

    private val channelMask: Int
        get() = if (channelCount == 6) {
            AudioFormat.CHANNEL_OUT_5POINT1
        } else {
            AudioFormat.CHANNEL_OUT_STEREO
        }

    /**
     * Start the audio renderer.
     */
    fun start() {
        if (running) return
        running = true

        // Initialize AudioTrack for low-latency playback
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT,
        )

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(channelMask)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()

        val trackBuilder = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            trackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }

        audioTrack = trackBuilder.build()
        audioTrack!!.play()

        // Initialize Opus decoder via MediaCodec
        initOpusDecoder()

        // Start decoder thread
        decoderThread = Thread({
            decoderLoop()
        }, "AudioDecoder").apply {
            isDaemon = true
            start()
        }
    }

    private fun initOpusDecoder() {
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_OPUS,
            sampleRate,
            channelCount,
        )

        // Opus codec-specific data (CSD)
        // CSD-0: Opus identification header
        val opusHead = buildOpusIdentHeader()
        format.setByteBuffer("csd-0", java.nio.ByteBuffer.wrap(opusHead))

        // CSD-1: Pre-skip in nanoseconds
        val preSkip = java.nio.ByteBuffer.allocate(8)
        preSkip.putLong(0) // No pre-skip
        preSkip.flip()
        format.setByteBuffer("csd-1", preSkip)

        // CSD-2: Seek pre-roll in nanoseconds
        val seekPreroll = java.nio.ByteBuffer.allocate(8)
        seekPreroll.putLong(80_000_000) // 80ms pre-roll
        seekPreroll.flip()
        format.setByteBuffer("csd-2", seekPreroll)

        try {
            opusDecoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
            opusDecoder!!.configure(format, null, null, 0)
            opusDecoder!!.start()
        } catch (e: IOException) {
            // Fall back to raw PCM if Opus decoder isn't available
            opusDecoder = null
        }
    }

    private fun buildOpusIdentHeader(): ByteArray {
        // Minimal Opus identification header
        return byteArrayOf(
            'O'.code.toByte(), 'p'.code.toByte(), 'u'.code.toByte(), 's'.code.toByte(),
            'H'.code.toByte(), 'e'.code.toByte(), 'a'.code.toByte(), 'd'.code.toByte(),
            1, // Version
            channelCount.toByte(), // Channel count
            0x00, 0x00, // Pre-skip (little-endian)
            (sampleRate and 0xFF).toByte(), // Sample rate (little-endian)
            ((sampleRate shr 8) and 0xFF).toByte(),
            ((sampleRate shr 16) and 0xFF).toByte(),
            ((sampleRate shr 24) and 0xFF).toByte(),
            0x00, 0x00, // Output gain
            0x00, // Channel mapping family
        )
    }

    /**
     * Submit an audio packet for decoding and playback.
     */
    fun submitPacket(data: ByteArray, offset: Int, length: Int) {
        if (!running || length <= 0) return

        // Strip RTP header (12 bytes) if present
        val payload = if (length > 12 && (data[offset].toInt() and 0xC0) == 0x80) {
            data.copyOfRange(offset + 12, offset + length)
        } else {
            data.copyOfRange(offset, offset + length)
        }

        packetQueue.offer(payload)
    }

    private fun decoderLoop() {
        val decoder = opusDecoder
        val track = audioTrack ?: return
        val bufferInfo = MediaCodec.BufferInfo()

        while (running) {
            val packet = try {
                packetQueue.take()
            } catch (_: InterruptedException) {
                break
            }

            if (decoder != null) {
                decodeOpus(decoder, packet, track, bufferInfo)
            } else {
                // No Opus decoder — write raw PCM directly
                // (Moonlight actually sends Opus, so this is a fallback)
                track.write(packet, 0, packet.size)
            }
        }
    }

    private fun decodeOpus(
        decoder: MediaCodec,
        packet: ByteArray,
        track: AudioTrack,
        bufferInfo: MediaCodec.BufferInfo,
    ) {
        try {
            // Submit encoded packet to decoder
            val inputIndex = decoder.dequeueInputBuffer(5000)
            if (inputIndex >= 0) {
                val inputBuffer = decoder.getInputBuffer(inputIndex) ?: return
                inputBuffer.clear()
                inputBuffer.put(packet)
                decoder.queueInputBuffer(inputIndex, 0, packet.size, 0, 0)
            }

            // Read decoded PCM output
            val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 0)
            if (outputIndex >= 0) {
                val outputBuffer = decoder.getOutputBuffer(outputIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    val pcmData = ByteArray(bufferInfo.size)
                    outputBuffer.get(pcmData)
                    track.write(pcmData, 0, pcmData.size)
                }
                decoder.releaseOutputBuffer(outputIndex, false)
            }
        } catch (_: MediaCodec.CodecException) {
            // Decoder error — skip this packet
        }
    }

    /**
     * Stop playback and release resources.
     */
    fun stop() {
        running = false
        decoderThread?.interrupt()
        packetQueue.clear()

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}

        try {
            opusDecoder?.stop()
            opusDecoder?.release()
        } catch (_: Exception) {}

        audioTrack = null
        opusDecoder = null
    }
}
