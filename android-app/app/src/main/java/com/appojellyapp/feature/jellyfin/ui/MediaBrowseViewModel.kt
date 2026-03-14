package com.appojellyapp.feature.jellyfin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appojellyapp.core.model.ContentItem
import com.appojellyapp.core.model.UiState
import com.appojellyapp.feature.jellyfin.data.JellyfinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
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

    private val _uiState = MutableStateFlow<UiState<Unit>>(UiState.Loading)
    val uiState: StateFlow<UiState<Unit>> = _uiState.asStateFlow()

    init {
        loadMedia()
    }

    fun retry() {
        _uiState.value = UiState.Loading
        loadMedia()
    }

    private fun loadMedia() {
        viewModelScope.launch {
            jellyfinRepository.getMovies()
                .retryWhen { _, attempt -> attempt < 1 }
                .catch {
                    _movies.value = emptyList()
                    _uiState.value = UiState.Error(
                        message = "Failed to load media library. Check your Jellyfin connection.",
                        retry = ::retry,
                    )
                }
                .collect { _movies.value = it }
        }
        viewModelScope.launch {
            jellyfinRepository.getTvShows()
                .retryWhen { _, attempt -> attempt < 1 }
                .catch { _tvShows.value = emptyList() }
                .collect {
                    _tvShows.value = it
                    _uiState.value = UiState.Success(Unit)
                }
        }
    }
}
