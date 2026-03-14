package com.appojellyapp.feature.streaming.moonlight.decoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.view.Surface
import com.appojellyapp.feature.streaming.moonlight.VideoCodec
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue

/**
 * Hardware-accelerated video decoder for the Moonlight streaming protocol.
 *
 * Uses Android's MediaCodec API to decode H.264 or H.265/HEVC video frames
 * received from the streaming server. Decoded frames are rendered directly
 * to a Surface (typically a SurfaceView in the streaming UI).
 *
 * The decoder handles:
 * - NAL unit parsing and reassembly from RTP packets
 * - SPS/PPS parameter set management
 * - Codec initialization with optimal settings for low-latency decoding
 * - Frame queuing and presentation timing
 */
class VideoDecoder(
    private val surface: Surface,
    private val width: Int,
    private val height: Int,
    private val fps: Int,
    private val codec: VideoCodec,
) {
    private var mediaCodec: MediaCodec? = null
    private var decoderThread: Thread? = null
    private var running = false

    // Buffer for accumulating NAL units from network packets
    private val nalBuffer = LinkedBlockingQueue<ByteArray>(120)

    // SPS and PPS for H.264 / VPS, SPS, PPS for H.265
    private var spsData: ByteArray? = null
    private var ppsData: ByteArray? = null
    private var vpsData: ByteArray? = null // H.265 only

    private val mimeType: String
        get() = when (codec) {
            VideoCodec.H264 -> MediaFormat.MIMETYPE_VIDEO_AVC
            VideoCodec.H265 -> MediaFormat.MIMETYPE_VIDEO_HEVC
        }

    /**
     * Find the best available hardware decoder for the specified codec.
     */
    private fun findDecoder(): String? {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        var fallback: String? = null

        for (info in codecList.codecInfos) {
            if (info.isEncoder) continue

            for (type in info.supportedTypes) {
                if (type.equals(mimeType, ignoreCase = true)) {
                    // Prefer hardware decoders
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        if (info.isHardwareAccelerated) {
                            return info.name
                        }
                    }
                    // Check by name heuristic for older APIs
                    val name = info.name.lowercase()
                    if (!name.contains("omx.google") && !name.contains("c2.android")) {
                        return info.name
                    }
                    if (fallback == null) {
                        fallback = info.name
                    }
                }
            }
        }
        return fallback
    }

    /**
     * Start the video decoder.
     */
    fun start() {
        if (running) return
        running = true

        val decoderName = findDecoder()
            ?: throw IOException("No hardware decoder found for $mimeType")

        val format = MediaFormat.createVideoFormat(mimeType, width, height).apply {
            setInteger(MediaFormat.KEY_MAX_WIDTH, width)
            setInteger(MediaFormat.KEY_MAX_HEIGHT, height)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)

            // Low latency mode (API 30+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            }

            // Request low latency operating mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setInteger(MediaFormat.KEY_OPERATING_RATE, Short.MAX_VALUE.toInt())
                setInteger(MediaFormat.KEY_PRIORITY, 0) // Realtime priority
            }
        }

        mediaCodec = MediaCodec.createByCodecName(decoderName).apply {
            configure(format, surface, null, 0)
            start()
        }

        decoderThread = Thread({
            decoderLoop()
        }, "VideoDecoder").apply {
            isDaemon = true
            start()
        }

        // Output thread to release decoded frames
        Thread({
            outputLoop()
        }, "VideoDecoderOutput").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * Submit a network packet containing video data.
     *
     * The packet may contain one or more NAL units. This method handles
     * RTP header stripping and NAL unit extraction.
     */
    fun submitPacket(data: ByteArray, offset: Int, length: Int) {
        if (!running || length <= 0) return

        // Extract NAL units from the packet
        // Moonlight protocol sends raw H.264/H.265 NAL units with
        // Annex B start codes (0x00 0x00 0x00 0x01)
        val packetData = data.copyOfRange(offset, offset + length)

        // Check for RTP header and strip it if present
        val nalData = if (packetData.size > 12 && (packetData[0].toInt() and 0xC0) == 0x80) {
            // RTP header is typically 12 bytes
            packetData.copyOfRange(12, packetData.size)
        } else {
            packetData
        }

        // Parse NAL units and handle SPS/PPS/VPS
        parseAndQueueNalUnits(nalData)
    }

    private fun parseAndQueueNalUnits(data: ByteArray) {
        // Find all NAL start codes and split
        val nalUnits = splitNalUnits(data)

        for (nal in nalUnits) {
            if (nal.size < 2) continue

            val nalType = getNalType(nal)

            when {
                // H.264 SPS
                codec == VideoCodec.H264 && nalType == 7 -> {
                    spsData = nal
                }
                // H.264 PPS
                codec == VideoCodec.H264 && nalType == 8 -> {
                    ppsData = nal
                }
                // H.265 VPS
                codec == VideoCodec.H265 && nalType == 32 -> {
                    vpsData = nal
                }
                // H.265 SPS
                codec == VideoCodec.H265 && nalType == 33 -> {
                    spsData = nal
                }
                // H.265 PPS
                codec == VideoCodec.H265 && nalType == 34 -> {
                    ppsData = nal
                }
                else -> {
                    // Regular frame NAL unit - queue for decoding
                    nalBuffer.offer(nal)
                }
            }
        }
    }

    private fun getNalType(nal: ByteArray): Int {
        // Find the first byte after start code
        var i = 0
        while (i < nal.size - 1) {
            if (nal[i] == 0x00.toByte() && nal[i + 1] == 0x00.toByte()) {
                if (i + 2 < nal.size && nal[i + 2] == 0x01.toByte()) {
                    return if (codec == VideoCodec.H265) {
                        (nal[i + 3].toInt() and 0x7E) shr 1
                    } else {
                        nal[i + 3].toInt() and 0x1F
                    }
                }
                if (i + 3 < nal.size && nal[i + 2] == 0x00.toByte() && nal[i + 3] == 0x01.toByte()) {
                    return if (codec == VideoCodec.H265) {
                        (nal[i + 4].toInt() and 0x7E) shr 1
                    } else {
                        nal[i + 4].toInt() and 0x1F
                    }
                }
            }
            i++
        }
        return -1
    }

    private fun splitNalUnits(data: ByteArray): List<ByteArray> {
        val units = mutableListOf<ByteArray>()
        val starts = mutableListOf<Int>()

        var i = 0
        while (i < data.size - 3) {
            if (data[i] == 0x00.toByte() && data[i + 1] == 0x00.toByte()) {
                if (data[i + 2] == 0x01.toByte()) {
                    starts.add(i)
                    i += 3
                    continue
                }
                if (i + 3 < data.size && data[i + 2] == 0x00.toByte() && data[i + 3] == 0x01.toByte()) {
                    starts.add(i)
                    i += 4
                    continue
                }
            }
            i++
        }

        if (starts.isEmpty()) {
            // No start codes found, treat entire buffer as one NAL
            units.add(data)
        } else {
            for (j in starts.indices) {
                val start = starts[j]
                val end = if (j + 1 < starts.size) starts[j + 1] else data.size
                units.add(data.copyOfRange(start, end))
            }
        }

        return units
    }

    /**
     * Main decoder loop - takes NAL units from the queue and feeds them to MediaCodec.
     */
    private fun decoderLoop() {
        val codec = mediaCodec ?: return

        while (running) {
            val nal = try {
                nalBuffer.take()
            } catch (_: InterruptedException) {
                break
            }

            try {
                val inputIndex = codec.dequeueInputBuffer(5000)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex) ?: continue
                    inputBuffer.clear()

                    // Prepend SPS/PPS if this is an IDR frame
                    val nalType = getNalType(nal)
                    val isIdr = if (this.codec == VideoCodec.H264) {
                        nalType == 5
                    } else {
                        nalType in 16..21
                    }

                    if (isIdr) {
                        // Prepend parameter sets for IDR frames
                        vpsData?.let { inputBuffer.put(it) }
                        spsData?.let { inputBuffer.put(it) }
                        ppsData?.let { inputBuffer.put(it) }
                    }

                    inputBuffer.put(nal)
                    codec.queueInputBuffer(inputIndex, 0, inputBuffer.position(), 0, 0)
                }
            } catch (_: MediaCodec.CodecException) {
                // Decoder error - try to recover
            } catch (_: IllegalStateException) {
                break
            }
        }
    }

    /**
     * Output loop - releases decoded frames to the surface.
     */
    private fun outputLoop() {
        val codec = mediaCodec ?: return
        val bufferInfo = MediaCodec.BufferInfo()

        while (running) {
            try {
                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 5000)
                when {
                    outputIndex >= 0 -> {
                        // Release to surface for rendering (true = render)
                        codec.releaseOutputBuffer(outputIndex, true)
                    }
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // Format changed - this is normal on first frame
                    }
                    // INFO_TRY_AGAIN_LATER = timeout, just loop
                }
            } catch (_: MediaCodec.CodecException) {
                // Decoder error
            } catch (_: IllegalStateException) {
                break
            }
        }
    }

    /**
     * Stop the decoder and release resources.
     */
    fun stop() {
        running = false
        decoderThread?.interrupt()
        nalBuffer.clear()

        try {
            mediaCodec?.stop()
            mediaCodec?.release()
        } catch (_: Exception) {
            // Best effort cleanup
        }
        mediaCodec = null
    }
}
