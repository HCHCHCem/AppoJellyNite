package com.appojellyapp.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appojellyapp.core.model.ContentItem
import com.appojellyapp.feature.home.data.HomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
) : ViewModel() {

    private val _results = MutableStateFlow<List<ContentItem>>(emptyList())
    val results: StateFlow<List<ContentItem>> = _results.asStateFlow()

    private var searchJob: Job? = null

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _results.value = emptyList()
            return
        }

        searchJob = viewModelScope.launch {
            delay(300) // debounce
            homeRepository.search(query)
                .catch { _results.value = emptyList() }
                .collect { _results.value = it }
        }
    }
}
