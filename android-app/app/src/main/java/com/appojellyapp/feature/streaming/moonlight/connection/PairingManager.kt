package com.appojellyapp.feature.streaming.moonlight.connection

import com.appojellyapp.core.network.NetworkHelper
import com.appojellyapp.core.settings.ApolloConfig
import com.appojellyapp.core.settings.SettingsRepository
import com.appojellyapp.feature.streaming.moonlight.crypto.CertificateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class PairingState {
    NOT_PAIRED,
    CHECKING,
    AWAITING_PIN,
    PAIRING,
    PAIRED,
    ERROR,
}

data class PairingStatus(
    val state: PairingState = PairingState.NOT_PAIRED,
    val serverName: String? = null,
    val errorMessage: String? = null,
)

/**
 * Manages the Apollo/Sunshine pairing lifecycle.
 *
 * Pairing flow:
 * 1. User enters Apollo server IP in settings
 * 2. App checks if already paired by querying server info
 * 3. If not paired, user initiates pairing
 * 4. Apollo displays a 4-digit PIN on its web UI / screen
 * 5. User enters the PIN in the app
 * 6. App performs the Moonlight PIN exchange protocol
 * 7. On success, certificates are exchanged and stored
 * 8. Future connections use the stored certificates — no re-pairing needed
 */
@Singleton
class PairingManager @Inject constructor(
    private val certificateManager: CertificateManager,
    private val settingsRepository: SettingsRepository,
    private val networkHelper: NetworkHelper,
) {
    private val _status = MutableStateFlow(PairingStatus())
    val status: StateFlow<PairingStatus> = _status.asStateFlow()

    /**
     * Check if we're already paired with the configured Apollo server.
     */
    suspend fun checkPairingStatus() {
        val apolloConfig = settingsRepository.serverConfig.value.apollo
        if (apolloConfig == null) {
            _status.value = PairingStatus(
                state = PairingState.ERROR,
                errorMessage = "Apollo server not configured"
            )
            return
        }

        _status.value = PairingStatus(state = PairingState.CHECKING)

        try {
            val host = networkHelper.resolveApolloAddress()
            if (host == null) {
                _status.value = PairingStatus(
                    state = PairingState.ERROR,
                    errorMessage = "Server unreachable"
                )
                return
            }

            val nvHttp = NvHTTP(
                host = host,
                httpsPort = apolloConfig.httpsPort,
                httpPort = apolloConfig.port,
                certificateManager = certificateManager,
            )

            val serverInfo = nvHttp.getServerInfo()

            if (serverInfo.isPaired) {
                _status.value = PairingStatus(
                    state = PairingState.PAIRED,
                    serverName = serverInfo.hostname,
                )
                // Update config to reflect paired status
                settingsRepository.updateApolloConfig(apolloConfig.copy(isPaired = true))
            } else {
                _status.value = PairingStatus(
                    state = PairingState.NOT_PAIRED,
                    serverName = serverInfo.hostname,
                )
                settingsRepository.updateApolloConfig(apolloConfig.copy(isPaired = false))
            }
        } catch (e: Exception) {
            _status.value = PairingStatus(
                state = PairingState.ERROR,
                errorMessage = "Failed to check pairing: ${e.message}"
            )
        }
    }

    /**
     * Initiate the pairing process.
     * After calling this, Apollo will display a PIN. The user should
     * then call [completePairing] with that PIN.
     */
    fun requestPairing() {
        _status.value = PairingStatus(state = PairingState.AWAITING_PIN)
    }

    /**
     * Complete the pairing by entering the PIN shown on Apollo.
     */
    suspend fun completePairing(pin: String) {
        val apolloConfig = settingsRepository.serverConfig.value.apollo
        if (apolloConfig == null) {
            _status.value = PairingStatus(
                state = PairingState.ERROR,
                errorMessage = "Apollo server not configured"
            )
            return
        }

        if (pin.length != 4 || !pin.all { it.isDigit() }) {
            _status.value = PairingStatus(
                state = PairingState.ERROR,
                errorMessage = "PIN must be exactly 4 digits"
            )
            return
        }

        _status.value = PairingStatus(state = PairingState.PAIRING)

        try {
            val host = networkHelper.resolveApolloAddress()
            if (host == null) {
                _status.value = PairingStatus(
                    state = PairingState.ERROR,
                    errorMessage = "Server unreachable"
                )
                return
            }

            // Ensure we have a client certificate
            certificateManager.getOrCreateCertificate()

            val nvHttp = NvHTTP(
                host = host,
                httpsPort = apolloConfig.httpsPort,
                httpPort = apolloConfig.port,
                certificateManager = certificateManager,
            )

            val result = nvHttp.pair(pin)

            when (result) {
                is PairResult.Success -> {
                    _status.value = PairingStatus(state = PairingState.PAIRED)
                    settingsRepository.updateApolloConfig(apolloConfig.copy(isPaired = true))
                }
                is PairResult.Error -> {
                    _status.value = PairingStatus(
                        state = PairingState.ERROR,
                        errorMessage = result.message,
                    )
                }
            }
        } catch (e: Exception) {
            _status.value = PairingStatus(
                state = PairingState.ERROR,
                errorMessage = "Pairing failed: ${e.message}",
            )
        }
    }

    /**
     * Unpair from the server and clear stored certificates.
     */
    suspend fun unpair() {
        val apolloConfig = settingsRepository.serverConfig.value.apollo ?: return

        try {
            val host = networkHelper.resolveApolloAddress()
            if (host != null) {
                val nvHttp = NvHTTP(
                    host = host,
                    httpsPort = apolloConfig.httpsPort,
                    httpPort = apolloConfig.port,
                    certificateManager = certificateManager,
                )
                nvHttp.unpair()
            }
        } catch (_: Exception) {
            // Best effort
        }

        certificateManager.clearCertificates()
        settingsRepository.updateApolloConfig(apolloConfig.copy(isPaired = false))
        _status.value = PairingStatus(state = PairingState.NOT_PAIRED)
    }
}
