package com.appojellyapp.feature.streaming.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appojellyapp.feature.streaming.moonlight.StreamConfig
import com.appojellyapp.feature.streaming.moonlight.StreamManager
import com.appojellyapp.feature.streaming.moonlight.StreamState
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

    private var currentConfig = StreamConfig()

    fun getStreamConfig(): StreamConfig = currentConfig

    fun updateStreamConfig(config: StreamConfig) {
        currentConfig = config
        streamManager.updateConfig(config)
    }

    fun startStream(apolloAppId: String, gameName: String) {
        viewModelScope.launch {
            streamManager.startStream(apolloAppId, gameName)
        }
    }

    fun stopStream() {
        streamManager.stopStream()
    }
}
