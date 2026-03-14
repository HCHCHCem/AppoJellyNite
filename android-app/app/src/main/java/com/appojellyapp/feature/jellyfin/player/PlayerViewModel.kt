package com.appojellyapp.feature.jellyfin.player

import androidx.lifecycle.ViewModel
import com.appojellyapp.feature.jellyfin.data.JellyfinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val jellyfinRepository: JellyfinRepository,
) : ViewModel() {

    fun getStreamUrl(itemId: String): String {
        return jellyfinRepository.getStreamUrl(itemId)
    }
}
