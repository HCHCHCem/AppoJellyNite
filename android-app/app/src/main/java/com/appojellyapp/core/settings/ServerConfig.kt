package com.appojellyapp.core.settings

/**
 * Configuration for all server connections.
 */
data class ServerConfig(
    val jellyfin: JellyfinConfig? = null,
    val playniteWeb: PlayniteWebConfig? = null,
    val apollo: ApolloConfig? = null,
)

data class JellyfinConfig(
    val serverUrl: String,
    val username: String,
    val password: String,
    val userId: String? = null,
    val accessToken: String? = null,
)

data class PlayniteWebConfig(
    val serverUrl: String,
    val apiKey: String? = null,
)

data class ApolloConfig(
    val lanIp: String,
    val tailscaleIp: String? = null,
    val port: Int = 47989,
    val httpsPort: Int = 47984,
    val isPaired: Boolean = false,
)
