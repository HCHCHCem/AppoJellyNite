package com.appojellyapp.feature.streaming.moonlight.connection

import android.view.Surface
import com.appojellyapp.feature.streaming.moonlight.StreamConfig
import com.appojellyapp.feature.streaming.moonlight.VideoCodec
import com.appojellyapp.feature.streaming.moonlight.crypto.CertificateManager
import com.appojellyapp.feature.streaming.moonlight.decoder.AudioRenderer
import com.appojellyapp.feature.streaming.moonlight.decoder.VideoDecoder
import com.appojellyapp.feature.streaming.moonlight.input.ControllerHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

/**
 * Main connection manager for the Moonlight streaming protocol.
 *
 * Handles the full lifecycle of a game streaming session:
 * 1. Establishes control stream (TCP) for session negotiation
 * 2. Sets up video stream (RTP over UDP/TCP) for receiving video frames
 * 3. Sets up audio stream (RTP over UDP) for receiving audio
 * 4. Sets up input stream (TCP) for sending controller/mouse/keyboard input
 *
 * In a production build, this would use moonlight-common-c via JNI for the
 * actual protocol implementation. This Kotlin implementation provides the
 * connection orchestration and delegates to the native library for
 * protocol-level operations.
 */
class NvConnection(
    private val host: String,
    private val config: StreamConfig,
    private val certificateManager: CertificateManager,
) {
    // Connection state
    private var controlSocket: SSLSocket? = null
    private var videoSocket: Socket? = null
    private var audioSocket: DatagramSocket? = null
    private var inputSocket: SSLSocket? = null

    // Session info
    private var sessionId: Int = 0
    private var riKey: ByteArray = ByteArray(16)
    private var riKeyId: Int = 0

    // Components
    private var videoDecoder: VideoDecoder? = null
    private var audioRenderer: AudioRenderer? = null
    private var controllerHandler: ControllerHandler? = null

    // Listener
    var connectionListener: ConnectionListener? = null

    // Ports (Moonlight protocol defaults)
    companion object {
        const val CONTROL_PORT = 47995
        const val VIDEO_PORT = 47998
        const val AUDIO_PORT = 48000
        const val INPUT_PORT = 35043
        const val RTSP_PORT = 48010
        const val FIRST_FRAME_PORT = 47996
    }

    /**
     * Start the streaming connection.
     *
     * @param surface The Android Surface to render video frames onto
     * @param appId The app ID on the server to stream (already launched via NvHTTP)
     */
    suspend fun start(surface: Surface, appId: Int) = withContext(Dispatchers.IO) {
        try {
            connectionListener?.onStageStarting("Initializing")

            // Generate session keys
            SecureRandom().nextBytes(riKey)
            riKeyId = (System.currentTimeMillis() / 1000).toInt()
            sessionId = SecureRandom().nextInt(Int.MAX_VALUE)

            // Step 1: RTSP handshake to negotiate stream parameters
            connectionListener?.onStageStarting("RTSP Handshake")
            performRtspHandshake()
            connectionListener?.onStageComplete("RTSP Handshake")

            // Step 2: Start control stream
            connectionListener?.onStageStarting("Control Stream")
            startControlStream()
            connectionListener?.onStageComplete("Control Stream")

            // Step 3: Start video stream
            connectionListener?.onStageStarting("Video Stream")
            startVideoStream(surface)
            connectionListener?.onStageComplete("Video Stream")

            // Step 4: Start audio stream
            connectionListener?.onStageStarting("Audio Stream")
            startAudioStream()
            connectionListener?.onStageComplete("Audio Stream")

            // Step 5: Start input stream
            connectionListener?.onStageStarting("Input Stream")
            startInputStream()
            connectionListener?.onStageComplete("Input Stream")

            connectionListener?.onConnectionStarted()

        } catch (e: Exception) {
            connectionListener?.onConnectionError("Connection failed: ${e.message}")
            stop()
            throw e
        }
    }

    /**
     * Perform the RTSP handshake to set up the streaming session.
     * RTSP (Real Time Streaming Protocol) is used to negotiate
     * video/audio stream parameters.
     */
    private fun performRtspHandshake() {
        val socket = Socket()
        socket.connect(InetSocketAddress(host, RTSP_PORT), 5000)
        socket.soTimeout = 10000

        val codecStr = when (config.codec) {
            VideoCodec.H265 -> "hevc"
            VideoCodec.H264 -> "h264"
        }

        // RTSP OPTIONS
        val optionsRequest = buildString {
            append("OPTIONS rtsp://$host:$RTSP_PORT RTSP/1.0\r\n")
            append("CSeq: 1\r\n")
            append("\r\n")
        }
        socket.getOutputStream().write(optionsRequest.toByteArray())
        readRtspResponse(socket)

        // RTSP DESCRIBE - request stream info
        val describeRequest = buildString {
            append("DESCRIBE rtsp://$host:$RTSP_PORT RTSP/1.0\r\n")
            append("CSeq: 2\r\n")
            append("Accept: application/sdp\r\n")
            append("If-Modified-Since: Thu, 01 Jan 1970 00:00:00 GMT\r\n")
            append("\r\n")
        }
        socket.getOutputStream().write(describeRequest.toByteArray())
        readRtspResponse(socket)

        // RTSP SETUP for video
        val setupVideoRequest = buildString {
            append("SETUP streamid=video/0/0 RTSP/1.0\r\n")
            append("CSeq: 3\r\n")
            append("Transport: unicast;client_port=$VIDEO_PORT\r\n")
            append("If-Modified-Since: Thu, 01 Jan 1970 00:00:00 GMT\r\n")
            append("\r\n")
        }
        socket.getOutputStream().write(setupVideoRequest.toByteArray())
        readRtspResponse(socket)

        // RTSP SETUP for audio
        val setupAudioRequest = buildString {
            append("SETUP streamid=audio/0/0 RTSP/1.0\r\n")
            append("CSeq: 4\r\n")
            append("Transport: unicast;client_port=$AUDIO_PORT\r\n")
            append("If-Modified-Since: Thu, 01 Jan 1970 00:00:00 GMT\r\n")
            append("\r\n")
        }
        socket.getOutputStream().write(setupAudioRequest.toByteArray())
        readRtspResponse(socket)

        // RTSP PLAY
        val playRequest = buildString {
            append("PLAY rtsp://$host:$RTSP_PORT RTSP/1.0\r\n")
            append("CSeq: 5\r\n")
            append("Range: npt=0.000-\r\n")
            append("\r\n")
        }
        socket.getOutputStream().write(playRequest.toByteArray())
        readRtspResponse(socket)

        socket.close()
    }

    private fun readRtspResponse(socket: Socket): String {
        val buffer = ByteArray(4096)
        val bytesRead = socket.getInputStream().read(buffer)
        return if (bytesRead > 0) String(buffer, 0, bytesRead) else ""
    }

    /**
     * Start the control stream (TCP with TLS).
     * The control stream carries session management messages.
     */
    private fun startControlStream() {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(NvTrustManager()), SecureRandom())

        val factory = sslContext.socketFactory
        val socket = factory.createSocket(host, CONTROL_PORT) as SSLSocket
        socket.soTimeout = 0 // No timeout for control messages
        socket.startHandshake()
        controlSocket = socket

        // Start control message reader thread
        Thread({
            try {
                val input = socket.getInputStream()
                val buffer = ByteArray(4096)
                while (!socket.isClosed) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead == -1) break
                    handleControlMessage(buffer.copyOf(bytesRead))
                }
            } catch (_: IOException) {
                // Connection closed
            }
        }, "ControlStream").apply {
            isDaemon = true
            start()
        }

        // Send initial control messages
        sendControlMessage(ControlMessage.startA())
        sendControlMessage(ControlMessage.startB())
    }

    /**
     * Start the video stream and decoder.
     */
    private fun startVideoStream(surface: Surface) {
        videoDecoder = VideoDecoder(
            surface = surface,
            width = config.width,
            height = config.height,
            fps = config.fps,
            codec = config.codec,
        )
        videoDecoder!!.start()

        // Start video receiver thread
        videoSocket = Socket()
        videoSocket!!.connect(InetSocketAddress(host, VIDEO_PORT), 5000)
        videoSocket!!.soTimeout = 0

        Thread({
            try {
                val input = videoSocket!!.getInputStream()
                val buffer = ByteArray(65536)
                while (!videoSocket!!.isClosed) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead == -1) break
                    videoDecoder?.submitPacket(buffer, 0, bytesRead)
                }
            } catch (_: IOException) {
                // Connection closed
            }
        }, "VideoStream").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * Start the audio stream and renderer.
     */
    private fun startAudioStream() {
        audioRenderer = AudioRenderer(
            sampleRate = 48000,
            channelCount = 2,
        )
        audioRenderer!!.start()

        // Start audio receiver thread (UDP)
        audioSocket = DatagramSocket(AUDIO_PORT)
        audioSocket!!.soTimeout = 0

        Thread({
            try {
                val buffer = ByteArray(2048)
                val packet = DatagramPacket(buffer, buffer.size)
                while (!audioSocket!!.isClosed) {
                    audioSocket!!.receive(packet)
                    audioRenderer?.submitPacket(
                        packet.data, packet.offset, packet.length
                    )
                }
            } catch (_: IOException) {
                // Connection closed
            }
        }, "AudioStream").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * Start the input stream for sending controller/mouse/keyboard events.
     */
    private fun startInputStream() {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(NvTrustManager()), SecureRandom())

        val factory = sslContext.socketFactory
        val socket = factory.createSocket(host, INPUT_PORT) as SSLSocket
        socket.soTimeout = 0
        socket.startHandshake()
        inputSocket = socket

        controllerHandler = ControllerHandler(socket)
    }

    /**
     * Send a controller input event.
     */
    fun sendControllerInput(
        buttonFlags: Int,
        leftTrigger: Byte,
        rightTrigger: Byte,
        leftStickX: Short,
        leftStickY: Short,
        rightStickX: Short,
        rightStickY: Short,
    ) {
        controllerHandler?.sendMultiControllerInput(
            controllerNumber = 0,
            activeGamepadMask = 0x01,
            buttonFlags = buttonFlags,
            leftTrigger = leftTrigger,
            rightTrigger = rightTrigger,
            leftStickX = leftStickX,
            leftStickY = leftStickY,
            rightStickX = rightStickX,
            rightStickY = rightStickY,
        )
    }

    /**
     * Send a mouse move event.
     */
    fun sendMouseMove(deltaX: Int, deltaY: Int) {
        controllerHandler?.sendMouseMove(deltaX.toShort(), deltaY.toShort())
    }

    /**
     * Send a mouse button event.
     */
    fun sendMouseButton(button: Int, pressed: Boolean) {
        controllerHandler?.sendMouseButton(button.toByte(), pressed)
    }

    /**
     * Send a keyboard event.
     */
    fun sendKeyboardInput(keyCode: Short, pressed: Boolean, modifiers: Byte = 0) {
        controllerHandler?.sendKeyboardInput(keyCode, pressed, modifiers)
    }

    /**
     * Stop the streaming connection and release all resources.
     */
    fun stop() {
        // Stop decoder/renderer
        videoDecoder?.stop()
        audioRenderer?.stop()

        // Close sockets
        try { controlSocket?.close() } catch (_: Exception) {}
        try { videoSocket?.close() } catch (_: Exception) {}
        try { audioSocket?.close() } catch (_: Exception) {}
        try { inputSocket?.close() } catch (_: Exception) {}

        controlSocket = null
        videoSocket = null
        audioSocket = null
        inputSocket = null
        videoDecoder = null
        audioRenderer = null
        controllerHandler = null

        connectionListener?.onConnectionStopped()
    }

    private fun sendControlMessage(message: ByteArray) {
        try {
            controlSocket?.getOutputStream()?.write(message)
            controlSocket?.getOutputStream()?.flush()
        } catch (_: IOException) {
            // Control stream broken
        }
    }

    private fun handleControlMessage(data: ByteArray) {
        // Process incoming control messages (e.g., display resolution changes,
        // HDR state changes, rumble feedback)
        if (data.size < 4) return

        val messageType = (data[0].toInt() and 0xFF) or
                ((data[1].toInt() and 0xFF) shl 8)

        when (messageType) {
            0x0305 -> {
                // Rumble feedback - extract motor values and send to gamepad
                if (data.size >= 8) {
                    val lowFreqMotor = (data[4].toInt() and 0xFF) or
                            ((data[5].toInt() and 0xFF) shl 8)
                    val highFreqMotor = (data[6].toInt() and 0xFF) or
                            ((data[7].toInt() and 0xFF) shl 8)
                    connectionListener?.onRumble(lowFreqMotor.toShort(), highFreqMotor.toShort())
                }
            }
            0x0204 -> {
                // Connection terminated by server
                connectionListener?.onConnectionError("Server terminated the connection")
                stop()
            }
        }
    }

    interface ConnectionListener {
        fun onStageStarting(stage: String)
        fun onStageComplete(stage: String)
        fun onConnectionStarted()
        fun onConnectionStopped()
        fun onConnectionError(message: String)
        fun onRumble(lowFreqMotor: Short, highFreqMotor: Short)
    }
}

/**
 * Control message builder for Moonlight protocol control stream.
 */
private object ControlMessage {
    fun startA(): ByteArray {
        // Start control A message
        val msg = ByteArray(16)
        msg[0] = 0x0A
        msg[1] = 0x00
        msg[2] = 0x02 // Payload length
        msg[3] = 0x00
        return msg
    }

    fun startB(): ByteArray {
        // Start control B message
        val msg = ByteArray(16)
        msg[0] = 0x0B
        msg[1] = 0x00
        msg[2] = 0x02
        msg[3] = 0x00
        return msg
    }
}

/**
 * Trust manager that accepts all certificates (for self-signed Apollo/Sunshine certs).
 */
private class NvTrustManager : javax.net.ssl.X509TrustManager {
    override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
    override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
}
