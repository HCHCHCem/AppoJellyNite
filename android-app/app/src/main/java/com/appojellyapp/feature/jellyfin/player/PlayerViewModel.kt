package com.appojellyapp.feature.jellyfin.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appojellyapp.feature.jellyfin.data.JellyfinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val jellyfinRepository: JellyfinRepository,
) : ViewModel() {

    private val _subtitleTracks = MutableStateFlow<List<JellyfinRepository.SubtitleTrack>>(emptyList())
    val subtitleTracks: StateFlow<List<JellyfinRepository.SubtitleTrack>> = _subtitleTracks.asStateFlow()

    fun getStreamUrl(itemId: String): String {
        return jellyfinRepository.getStreamUrl(itemId)
    }

    fun getTranscodingStreamUrl(itemId: String): String {
        return jellyfinRepository.getTranscodingStreamUrl(itemId)
    }

    fun getSubtitleUrl(itemId: String, subtitleIndex: Int): String {
        return jellyfinRepository.getSubtitleUrl(itemId, subtitleIndex)
    }

    fun loadSubtitleTracks(itemId: String) {
        viewModelScope.launch {
            jellyfinRepository.getSubtitleTracks(itemId)
                .catch { _subtitleTracks.value = emptyList() }
                .collect { _subtitleTracks.value = it }
        }
    }
}
