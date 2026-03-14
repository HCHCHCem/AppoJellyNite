package com.appojellyapp.core.network

import android.content.Context
import com.appojellyapp.core.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Resolves the best server address (LAN vs Tailscale) and checks connectivity.
 */
class NetworkHelper(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    /**
     * Resolve the best reachable address for Apollo streaming.
     * Tries LAN first (lower latency), falls back to Tailscale.
     */
    suspend fun resolveApolloAddress(): String? {
        val config = settingsRepository.serverConfig.value.apollo ?: return null

        // Try LAN IP first
        if (isReachable(config.lanIp, config.port, timeoutMs = 1000)) {
            return config.lanIp
        }

        // Fall back to Tailscale IP
        val tailscaleIp = config.tailscaleIp
        if (tailscaleIp != null && isReachable(tailscaleIp, config.port, timeoutMs = 3000)) {
            return tailscaleIp
        }

        return null
    }

    suspend fun isReachable(host: String, port: Int, timeoutMs: Int = 2000): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                    true
                }
            } catch (_: Exception) {
                false
            }
        }
    }
}
