package com.appojellyapp.feature.playnite.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appojellyapp.core.model.ContentItem
import com.appojellyapp.core.model.UiState
import com.appojellyapp.feature.playnite.data.PlayniteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GameBrowseViewModel @Inject constructor(
    private val playniteRepository: PlayniteRepository,
) : ViewModel() {

    private val _games = MutableStateFlow<List<ContentItem.PcGame>>(emptyList())
    val games: StateFlow<List<ContentItem.PcGame>> = _games.asStateFlow()

    private val _platforms = MutableStateFlow<List<String>>(emptyList())
    val platforms: StateFlow<List<String>> = _platforms.asStateFlow()

    private val _uiState = MutableStateFlow<UiState<Unit>>(UiState.Loading)
    val uiState: StateFlow<UiState<Unit>> = _uiState.asStateFlow()

    init {
        loadGames()
    }

    fun retry() {
        _uiState.value = UiState.Loading
        loadGames()
    }

    private fun loadGames() {
        viewModelScope.launch {
            playniteRepository.getGames()
                .retryWhen { _, attempt -> attempt < 1 }
                .catch {
                    _games.value = emptyList()
                    _uiState.value = UiState.Error(
                        message = "Failed to load games. Check your Playnite Web connection.",
                        retry = ::retry,
                    )
                }
                .collect { games ->
                    _games.value = games.sortedBy { it.title }
                    _platforms.value = games.map { it.platform }
                        .distinct()
                        .sorted()
                    _uiState.value = UiState.Success(Unit)
                }
        }
    }
}
