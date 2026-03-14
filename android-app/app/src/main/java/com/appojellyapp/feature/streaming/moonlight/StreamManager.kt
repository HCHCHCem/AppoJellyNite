package com.appojellyapp.feature.streaming.moonlight

import android.content.Context
import com.appojellyapp.core.network.NetworkHelper
import com.appojellyapp.core.settings.SettingsRepository
import com.appojellyapp.feature.streaming.apollo.ApolloApiClient
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
 * Manages the game streaming lifecycle.
 *
 * This is a high-level orchestrator. The actual Moonlight protocol handling
 * requires the moonlight-common-c native library (added as a git submodule)
 * and the JNI bridge layer ported from the Moonlight Android app.
 *
 * The streaming flow:
 * 1. Resolve server address (LAN or Tailscale)
 * 2. Launch the game via Apollo's API
 * 3. Establish Moonlight streaming connection
 * 4. Render video frames to a SurfaceView
 * 5. Forward controller/touch input
 * 6. Disconnect and clean up
 */
@Singleton
class StreamManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val networkHelper: NetworkHelper,
    private val apolloApiClient: ApolloApiClient,
) {
    private val _state = MutableStateFlow(StreamState.IDLE)
    val state: StateFlow<StreamState> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var streamConfig = StreamConfig()

    fun updateConfig(config: StreamConfig) {
        streamConfig = config
    }

    /**
     * Start streaming a game.
     *
     * @param apolloAppId The app ID in Apollo's configuration
     * @param gameName The display name (used as fallback for launch)
     */
    suspend fun startStream(apolloAppId: String, gameName: String) {
        try {
            _state.value = StreamState.CONNECTING
            _error.value = null

            // 1. Resolve server address
            val host = networkHelper.resolveApolloAddress()
            if (host == null) {
                _state.value = StreamState.ERROR
                _error.value = "Server unreachable"
                return
            }

            val apolloConfig = settingsRepository.serverConfig.value.apollo
            if (apolloConfig == null) {
                _state.value = StreamState.ERROR
                _error.value = "Apollo not configured"
                return
            }

            // 2. Configure the Apollo API client
            apolloApiClient.configure(host, apolloConfig.port + 1) // web API is port+1

            // 3. Launch the game via Apollo's API
            _state.value = StreamState.LAUNCHING_APP
            val launched = apolloApiClient.launchApp(apolloAppId)
                || apolloApiClient.launchAppByName(gameName)

            if (!launched) {
                _state.value = StreamState.ERROR
                _error.value = "Failed to launch game: $gameName"
                return
            }

            // 4. Start Moonlight streaming connection
            // NOTE: This requires moonlight-common-c native library integration.
            // The actual implementation would:
            //   a. Create an NvConnection with the host IP and client certificate
            //   b. Call NvConnection.start() to initiate the protocol handshake
            //   c. Video frames arrive via the hardware decoder callback
            //   d. Audio frames arrive via the audio decoder callback
            //
            // For now, we set the state to STREAMING.
            // Full implementation requires porting the JNI bridge from Moonlight Android.
            _state.value = StreamState.STREAMING

        } catch (e: Exception) {
            _state.value = StreamState.ERROR
            _error.value = e.message ?: "Unknown streaming error"
        }
    }

    fun stopStream() {
        _state.value = StreamState.DISCONNECTING

        // NOTE: Would call NvConnection.stop() here to cleanly disconnect

        _state.value = StreamState.IDLE
        _error.value = null
    }
}
