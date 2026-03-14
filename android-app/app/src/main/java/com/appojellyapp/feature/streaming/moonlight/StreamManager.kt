package com.appojellyapp.feature.streaming.moonlight

import android.content.Context
import android.view.Surface
import com.appojellyapp.core.network.NetworkHelper
import com.appojellyapp.core.settings.SettingsRepository
import com.appojellyapp.feature.streaming.apollo.ApolloApiClient
import com.appojellyapp.feature.streaming.moonlight.connection.NvConnection
import com.appojellyapp.feature.streaming.moonlight.connection.NvHTTP
import com.appojellyapp.feature.streaming.moonlight.crypto.CertificateManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class StreamState {
    IDLE,
    CONNECTING,
    LAUNCHING_APP,
    STARTING_STREAM,
    STREAMING,
    DISCONNECTING,
    ERROR,
}

data class StreamConfig(
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 60,
    val bitrate: Int = 20000, // kbps
    val codec: VideoCodec = VideoCodec.H265,
)

enum class VideoCodec {
    H264,
    H265,
}

/**
 * Manages the game streaming lifecycle using the full Moonlight protocol stack.
 *
 * The streaming flow:
 * 1. Resolve server address (LAN or Tailscale)
 * 2. Verify pairing status via NvHTTP
 * 3. Launch the game via NvHTTP or Apollo API
 * 4. Establish NvConnection (RTSP handshake, control/video/audio/input streams)
 * 5. Render video frames to Surface via hardware-accelerated MediaCodec
 * 6. Forward controller/touch input via the input stream
 * 7. Clean disconnect on exit
 */
@Singleton
class StreamManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val networkHelper: NetworkHelper,
    private val apolloApiClient: ApolloApiClient,
    private val certificateManager: CertificateManager,
) {
    private val _state = MutableStateFlow(StreamState.IDLE)
    val state: StateFlow<StreamState> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _connectionStage = MutableStateFlow<String?>(null)
    val connectionStage: StateFlow<String?> = _connectionStage.asStateFlow()

    private var streamConfig = StreamConfig()
    private var nvConnection: NvConnection? = null
    private var nvHttp: NvHTTP? = null
    private var resolvedHost: String? = null

    fun updateConfig(config: StreamConfig) {
        streamConfig = config
    }

    /**
     * Start streaming a game.
     *
     * @param apolloAppId The app ID in Apollo's configuration
     * @param gameName The display name (used as fallback for launch)
     * @param surface The Surface to render video frames onto
     */
    suspend fun startStream(apolloAppId: String, gameName: String, surface: Surface?) {
        try {
            _state.value = StreamState.CONNECTING
            _error.value = null
            _connectionStage.value = "Resolving server address"

            // 1. Resolve server address
            val host = networkHelper.resolveApolloAddress()
            if (host == null) {
                _state.value = StreamState.ERROR
                _error.value = "Server unreachable"
                return
            }
            resolvedHost = host

            val apolloConfig = settingsRepository.serverConfig.value.apollo
            if (apolloConfig == null) {
                _state.value = StreamState.ERROR
                _error.value = "Apollo not configured"
                return
            }

            // 2. Verify pairing
            _connectionStage.value = "Verifying pairing"
            nvHttp = NvHTTP(
                host = host,
                httpsPort = apolloConfig.httpsPort,
                httpPort = apolloConfig.port,
                certificateManager = certificateManager,
            )

            val serverInfo = try {
                nvHttp!!.getServerInfo()
            } catch (e: Exception) {
                _state.value = StreamState.ERROR
                _error.value = "Cannot reach server: ${e.message}"
                return
            }

            if (!serverInfo.isPaired) {
                _state.value = StreamState.ERROR
                _error.value = "Not paired with server. Please pair first in Settings."
                return
            }

            // 3. Launch the game
            _state.value = StreamState.LAUNCHING_APP
            _connectionStage.value = "Launching $gameName"

            // First try Apollo's REST API, fall back to Moonlight protocol
            apolloApiClient.configure(host, apolloConfig.port + 1)
            var launched = false

            // Try Apollo REST API
            try {
                launched = apolloApiClient.launchApp(apolloAppId) ||
                        apolloApiClient.launchAppByName(gameName)
            } catch (_: Exception) {
                // Fall back to Moonlight protocol
            }

            // Fall back to Moonlight protocol app launch
            if (!launched) {
                val apps = nvHttp!!.getAppList()
                val matchedApp = apps.find {
                    it.appName.equals(gameName, ignoreCase = true)
                }
                if (matchedApp != null) {
                    launched = nvHttp!!.launchApp(
                        appId = matchedApp.appId,
                        width = streamConfig.width,
                        height = streamConfig.height,
                        fps = streamConfig.fps,
                        bitrate = streamConfig.bitrate,
                    )
                } else if (serverInfo.isStreaming) {
                    // A game is already running, try to resume
                    launched = nvHttp!!.resumeApp()
                }
            }

            if (!launched) {
                _state.value = StreamState.ERROR
                _error.value = "Failed to launch: $gameName"
                return
            }

            // 4. Start Moonlight streaming connection
            _state.value = StreamState.STARTING_STREAM
            _connectionStage.value = "Establishing stream"

            if (surface != null) {
                nvConnection = NvConnection(
                    host = host,
                    config = streamConfig,
                    certificateManager = certificateManager,
                )

                nvConnection!!.connectionListener = object : NvConnection.ConnectionListener {
                    override fun onStageStarting(stage: String) {
                        _connectionStage.value = stage
                    }

                    override fun onStageComplete(stage: String) {
                        _connectionStage.value = "$stage complete"
                    }

                    override fun onConnectionStarted() {
                        _state.value = StreamState.STREAMING
                        _connectionStage.value = null
                    }

                    override fun onConnectionStopped() {
                        _state.value = StreamState.IDLE
                    }

                    override fun onConnectionError(message: String) {
                        _state.value = StreamState.ERROR
                        _error.value = message
                    }

                    override fun onRumble(lowFreqMotor: Short, highFreqMotor: Short) {
                        // Forward rumble to connected gamepad if supported
                    }
                }

                // Find the app ID from the server's app list
                val apps = nvHttp!!.getAppList()
                val runningApp = apps.find { it.isRunning }
                val appIdForStream = runningApp?.appId ?: 0

                nvConnection!!.start(surface, appIdForStream)
            } else {
                // No surface yet — mark as streaming, surface will be attached later
                _state.value = StreamState.STREAMING
            }

        } catch (e: Exception) {
            _state.value = StreamState.ERROR
            _error.value = e.message ?: "Unknown streaming error"
        }
    }

    /**
     * Get the current NvConnection for input forwarding.
     */
    fun getConnection(): NvConnection? = nvConnection

    /**
     * Stop the stream and clean up.
     */
    fun stopStream() {
        _state.value = StreamState.DISCONNECTING
        _connectionStage.value = "Disconnecting"

        nvConnection?.stop()
        nvConnection = null

        // Quit the app on the server
        try {
            // This should be called from a coroutine, but for cleanup we do best-effort
            Thread {
                try {
                    kotlinx.coroutines.runBlocking {
                        nvHttp?.quitApp()
                    }
                } catch (_: Exception) {}
            }.start()
        } catch (_: Exception) {}

        _state.value = StreamState.IDLE
        _error.value = null
        _connectionStage.value = null
    }
}
