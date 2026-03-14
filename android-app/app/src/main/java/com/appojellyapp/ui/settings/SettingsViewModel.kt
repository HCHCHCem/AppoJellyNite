package com.appojellyapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appojellyapp.core.settings.ApolloConfig
import com.appojellyapp.core.settings.JellyfinConfig
import com.appojellyapp.core.settings.PlayniteWebConfig
import com.appojellyapp.core.settings.ServerConfig
import com.appojellyapp.core.settings.SettingsRepository
import com.appojellyapp.feature.jellyfin.data.JellyfinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val jellyfinRepository: JellyfinRepository,
) : ViewModel() {

    val serverConfig: StateFlow<ServerConfig> = settingsRepository.serverConfig

    fun saveJellyfin(url: String, username: String, password: String) {
        viewModelScope.launch {
            val success = jellyfinRepository.authenticate(url, username, password)
            if (!success) {
                // Config is still saved by authenticate() on success;
                // on failure we save the URL/credentials anyway for retry
                settingsRepository.updateJellyfinConfig(
                    JellyfinConfig(
                        serverUrl = url,
                        username = username,
                        password = password,
                    )
                )
            }
        }
    }

    fun savePlayniteWeb(url: String) {
        settingsRepository.updatePlayniteWebConfig(
            PlayniteWebConfig(serverUrl = url)
        )
    }

    fun saveApollo(lanIp: String, tailscaleIp: String?) {
        settingsRepository.updateApolloConfig(
            ApolloConfig(
                lanIp = lanIp,
                tailscaleIp = tailscaleIp,
            )
        )
    }
}
