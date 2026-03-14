package com.appojellyapp.core.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val gson = Gson()

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "appojellyapp_settings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _serverConfig = MutableStateFlow(loadServerConfig())
    val serverConfig: StateFlow<ServerConfig> = _serverConfig.asStateFlow()

    val isSetupComplete: Boolean
        get() = _serverConfig.value.jellyfin != null ||
                _serverConfig.value.playniteWeb != null

    private fun loadServerConfig(): ServerConfig {
        val json = prefs.getString(KEY_SERVER_CONFIG, null)
        return if (json != null) {
            gson.fromJson(json, ServerConfig::class.java)
        } else {
            ServerConfig()
        }
    }

    fun saveServerConfig(config: ServerConfig) {
        prefs.edit().putString(KEY_SERVER_CONFIG, gson.toJson(config)).apply()
        _serverConfig.value = config
    }

    fun updateJellyfinConfig(config: JellyfinConfig) {
        val current = _serverConfig.value
        saveServerConfig(current.copy(jellyfin = config))
    }

    fun updatePlayniteWebConfig(config: PlayniteWebConfig) {
        val current = _serverConfig.value
        saveServerConfig(current.copy(playniteWeb = config))
    }

    fun updateApolloConfig(config: ApolloConfig) {
        val current = _serverConfig.value
        saveServerConfig(current.copy(apollo = config))
    }

    companion object {
        private const val KEY_SERVER_CONFIG = "server_config"
    }
}
