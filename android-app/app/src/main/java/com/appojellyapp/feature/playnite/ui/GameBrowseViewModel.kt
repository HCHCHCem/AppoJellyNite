package com.appojellyapp.feature.playnite.ui

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
class GameBrowseViewModel @Inject constructor(
    private val playniteRepository: PlayniteRepository,
) : ViewModel() {

    private val _games = MutableStateFlow<List<ContentItem.PcGame>>(emptyList())
    val games: StateFlow<List<ContentItem.PcGame>> = _games.asStateFlow()

    private val _platforms = MutableStateFlow<List<String>>(emptyList())
    val platforms: StateFlow<List<String>> = _platforms.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadGames()
    }

    private fun loadGames() {
        viewModelScope.launch {
            _isLoading.value = true
            playniteRepository.getGames()
                .catch {
                    _games.value = emptyList()
                    _isLoading.value = false
                }
                .collect { games ->
                    _games.value = games.sortedBy { it.title }
                    _platforms.value = games.map { it.platform }
                        .distinct()
                        .sorted()
                    _isLoading.value = false
                }
        }
    }
}
