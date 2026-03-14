package com.appojellyapp.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appojellyapp.core.model.ContentItem
import com.appojellyapp.feature.home.data.HomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
) : ViewModel() {

    private val _continueWatching = MutableStateFlow<List<ContentItem>>(emptyList())
    val continueWatching: StateFlow<List<ContentItem>> = _continueWatching.asStateFlow()

    private val _recentlyAdded = MutableStateFlow<List<ContentItem>>(emptyList())
    val recentlyAdded: StateFlow<List<ContentItem>> = _recentlyAdded.asStateFlow()

    private val _pcGames = MutableStateFlow<List<ContentItem.PcGame>>(emptyList())
    val pcGames: StateFlow<List<ContentItem.PcGame>> = _pcGames.asStateFlow()

    private val _movies = MutableStateFlow<List<ContentItem.Media>>(emptyList())
    val movies: StateFlow<List<ContentItem.Media>> = _movies.asStateFlow()

    private val _tvShows = MutableStateFlow<List<ContentItem.Media>>(emptyList())
    val tvShows: StateFlow<List<ContentItem.Media>> = _tvShows.asStateFlow()

    init {
        loadContent()
    }

    private fun loadContent() {
        viewModelScope.launch {
            homeRepository.getContinueWatching()
                .catch { emit(emptyList()) }
                .collect { _continueWatching.value = it }
        }
        viewModelScope.launch {
            homeRepository.getRecentlyAdded()
                .catch { emit(emptyList()) }
                .collect { _recentlyAdded.value = it }
        }
        viewModelScope.launch {
            homeRepository.getPcGames()
                .catch { emit(emptyList()) }
                .collect { _pcGames.value = it }
        }
        viewModelScope.launch {
            homeRepository.getMovies()
                .catch { emit(emptyList()) }
                .collect { _movies.value = it }
        }
        viewModelScope.launch {
            homeRepository.getTvShows()
                .catch { emit(emptyList()) }
                .collect { _tvShows.value = it }
        }
    }
}
