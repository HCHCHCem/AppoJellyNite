package com.appojellyapp.feature.jellyfin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appojellyapp.core.model.ContentItem
import com.appojellyapp.feature.jellyfin.data.JellyfinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MediaBrowseViewModel @Inject constructor(
    private val jellyfinRepository: JellyfinRepository,
) : ViewModel() {

    private val _movies = MutableStateFlow<List<ContentItem.Media>>(emptyList())
    val movies: StateFlow<List<ContentItem.Media>> = _movies.asStateFlow()

    private val _tvShows = MutableStateFlow<List<ContentItem.Media>>(emptyList())
    val tvShows: StateFlow<List<ContentItem.Media>> = _tvShows.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadMedia()
    }

    private fun loadMedia() {
        viewModelScope.launch {
            _isLoading.value = true
            jellyfinRepository.getMovies()
                .catch { _movies.value = emptyList() }
                .collect { _movies.value = it }
        }
        viewModelScope.launch {
            jellyfinRepository.getTvShows()
                .catch { _tvShows.value = emptyList() }
                .collect {
                    _tvShows.value = it
                    _isLoading.value = false
                }
        }
    }
}
