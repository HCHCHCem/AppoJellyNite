package com.appojellyapp.feature.playnite.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appojellyapp.core.model.ContentItem
import com.appojellyapp.feature.playnite.data.PlayniteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GameDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playniteRepository: PlayniteRepository,
) : ViewModel() {

    private val gameId: String = savedStateHandle["gameId"] ?: ""

    private val _game = MutableStateFlow<ContentItem.PcGame?>(null)
    val game: StateFlow<ContentItem.PcGame?> = _game.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        if (gameId.isNotEmpty()) {
            loadGame()
        }
    }

    private fun loadGame() {
        viewModelScope.launch {
            playniteRepository.getGames()
                .catch { e ->
                    _error.value = e.message ?: "Failed to load game"
                }
                .collect { games ->
                    _game.value = games.find { it.id == gameId }
                        ?: run {
                            _error.value = "Game not found"
                            null
                        }
                }
        }
    }
}
