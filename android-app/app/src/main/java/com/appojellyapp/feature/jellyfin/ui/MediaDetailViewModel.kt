package com.appojellyapp.feature.jellyfin.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appojellyapp.core.model.ContentItem
import com.appojellyapp.core.model.MediaType
import com.appojellyapp.feature.jellyfin.data.JellyfinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MediaDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val jellyfinRepository: JellyfinRepository,
) : ViewModel() {

    private val itemId: String = savedStateHandle["itemId"] ?: ""

    private val _item = MutableStateFlow<ContentItem.Media?>(null)
    val item: StateFlow<ContentItem.Media?> = _item.asStateFlow()

    private val _episodes = MutableStateFlow<List<ContentItem.Media>>(emptyList())
    val episodes: StateFlow<List<ContentItem.Media>> = _episodes.asStateFlow()

    init {
        if (itemId.isNotEmpty()) {
            loadItem()
        }
    }

    private fun loadItem() {
        viewModelScope.launch {
            jellyfinRepository.getItemDetails(itemId)
                .catch { /* handle error */ }
                .collect { media ->
                    _item.value = media
                    if (media.mediaType == MediaType.SERIES) {
                        loadEpisodes()
                    }
                }
        }
    }

    private fun loadEpisodes() {
        viewModelScope.launch {
            jellyfinRepository.getEpisodes(itemId)
                .catch { _episodes.value = emptyList() }
                .collect { _episodes.value = it }
        }
    }
}
