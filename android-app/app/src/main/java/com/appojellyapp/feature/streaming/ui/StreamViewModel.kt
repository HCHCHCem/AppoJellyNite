package com.appojellyapp.feature.streaming.ui

import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appojellyapp.feature.streaming.moonlight.StreamManager
import com.appojellyapp.feature.streaming.moonlight.StreamState
import com.appojellyapp.feature.streaming.moonlight.connection.NvConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StreamViewModel @Inject constructor(
    private val streamManager: StreamManager,
) : ViewModel() {

    val streamState: StateFlow<StreamState> = streamManager.state
    val error: StateFlow<String?> = streamManager.error
    val connectionStage: StateFlow<String?> = streamManager.connectionStage

    private var surface: Surface? = null
    private var pendingAppId: String? = null
    private var pendingGameName: String? = null

    fun startStream(apolloAppId: String, gameName: String) {
        pendingAppId = apolloAppId
        pendingGameName = gameName

        viewModelScope.launch {
            streamManager.startStream(apolloAppId, gameName, surface)
        }
    }

    /**
     * Called when the SurfaceView's surface is ready.
     * If we were waiting to start streaming, the surface is now available.
     */
    fun onSurfaceReady(newSurface: Surface) {
        surface = newSurface
        // If we're in a state where we need the surface, reconnect
        val currentState = streamManager.state.value
        if (currentState == StreamState.STREAMING && streamManager.getConnection() == null) {
            // Re-trigger stream start with the surface
            val appId = pendingAppId ?: return
            val name = pendingGameName ?: return
            viewModelScope.launch {
                streamManager.startStream(appId, name, newSurface)
            }
        }
    }

    /**
     * Get the active NvConnection for input forwarding.
     */
    fun getConnection(): NvConnection? = streamManager.getConnection()

    fun stopStream() {
        streamManager.stopStream()
        surface = null
    }

    override fun onCleared() {
        super.onCleared()
        stopStream()
    }
}
