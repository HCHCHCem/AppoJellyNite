package com.appojellyapp.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appojellyapp.core.model.ContentItem
import com.appojellyapp.core.model.UiState
import com.appojellyapp.feature.home.data.HomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
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

    private val _uiState = MutableStateFlow<UiState<Unit>>(UiState.Loading)
    val uiState: StateFlow<UiState<Unit>> = _uiState.asStateFlow()

    init {
        loadContent()
    }

    fun retry() {
        _uiState.value = UiState.Loading
        loadContent()
    }

    private fun loadContent() {
        var hasData = false
        var errorCount = 0
        val totalSections = 5

        viewModelScope.launch {
            homeRepository.getContinueWatching()
                .retryWhen { _, attempt -> attempt < 1 }
                .catch {
                    emit(emptyList())
                    errorCount++
                    checkLoadComplete(hasData, errorCount, totalSections)
                }
                .collect {
                    _continueWatching.value = it
                    if (it.isNotEmpty()) hasData = true
                    checkLoadComplete(hasData, errorCount, totalSections)
                }
        }
        viewModelScope.launch {
            homeRepository.getRecentlyAdded()
                .retryWhen { _, attempt -> attempt < 1 }
                .catch {
                    emit(emptyList())
                    errorCount++
                    checkLoadComplete(hasData, errorCount, totalSections)
                }
                .collect {
                    _recentlyAdded.value = it
                    if (it.isNotEmpty()) hasData = true
                    checkLoadComplete(hasData, errorCount, totalSections)
                }
        }
        viewModelScope.launch {
            homeRepository.getPcGames()
                .retryWhen { _, attempt -> attempt < 1 }
                .catch {
                    emit(emptyList())
                    errorCount++
                    checkLoadComplete(hasData, errorCount, totalSections)
                }
                .collect {
                    _pcGames.value = it
                    if (it.isNotEmpty()) hasData = true
                    checkLoadComplete(hasData, errorCount, totalSections)
                }
        }
        viewModelScope.launch {
            homeRepository.getMovies()
                .retryWhen { _, attempt -> attempt < 1 }
                .catch {
                    emit(emptyList())
                    errorCount++
                    checkLoadComplete(hasData, errorCount, totalSections)
                }
                .collect {
                    _movies.value = it
                    if (it.isNotEmpty()) hasData = true
                    checkLoadComplete(hasData, errorCount, totalSections)
                }
        }
        viewModelScope.launch {
            homeRepository.getTvShows()
                .retryWhen { _, attempt -> attempt < 1 }
                .catch {
                    emit(emptyList())
                    errorCount++
                    checkLoadComplete(hasData, errorCount, totalSections)
                }
                .collect {
                    _tvShows.value = it
                    if (it.isNotEmpty()) hasData = true
                    checkLoadComplete(hasData, errorCount, totalSections)
                }
        }
    }

    private fun checkLoadComplete(hasData: Boolean, errorCount: Int, total: Int) {
        if (hasData) {
            _uiState.value = UiState.Success(Unit)
        } else if (errorCount >= total) {
            _uiState.value = UiState.Error(
                message = "Unable to connect to servers. Check your settings and network.",
                retry = ::retry,
            )
        }
    }
}
