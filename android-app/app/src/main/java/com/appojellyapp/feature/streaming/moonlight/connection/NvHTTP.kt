package com.appojellyapp.feature.streaming.moonlight.connection

import com.appojellyapp.feature.streaming.moonlight.crypto.CertificateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * HTTP client for the Moonlight/Sunshine/Apollo HTTPS API.
 *
 * This implements the pairing and server info protocol used by Moonlight.
 * Apollo/Sunshine exposes an HTTPS API on port 47984 (by default) that handles:
 * - Server information queries
 * - PIN-based pairing (certificate exchange)
 * - App listing and launching
 * - Session management
 *
 * All requests include the client's unique ID and, after pairing,
 * use the client certificate for mutual TLS authentication.
 */
class NvHTTP(
    private val host: String,
    private val httpsPort: Int = 47984,
    private val httpPort: Int = 47989,
    private val certificateManager: CertificateManager,
) {
    private val uniqueId: String = certificateManager.getUniqueId()

    // Trust all certificates (Apollo uses self-signed certs)
    private val trustAllManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    private val httpClient: OkHttpClient by lazy {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())

        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private fun baseUrl(https: Boolean = true): String {
        val port = if (https) httpsPort else httpPort
        val scheme = if (https) "https" else "http"
        return "$scheme://$host:$port"
    }

    /**
     * Get server information (version, GPU type, paired status, etc.)
     */
    suspend fun getServerInfo(): ServerInfo = withContext(Dispatchers.IO) {
        val url = "${baseUrl()}/serverinfo?uniqueid=$uniqueId"
        val response = httpClient.newCall(Request.Builder().url(url).build()).execute()
        val body = response.body?.string() ?: throw Exception("Empty server info response")
        ServerInfo.parse(body)
    }

    /**
     * Initiate the pairing process — Phase 1: Send client challenge.
     *
     * The Moonlight pairing protocol is a 4-phase process:
     * 1. Client sends a salt + client cert hash to the server
     * 2. Server responds with its challenge (encrypted with PIN-derived key)
     * 3. Client decrypts, verifies, and sends its own challenge response
     * 4. Server confirms, pairing is complete, certificates are exchanged
     */
    suspend fun pair(pin: String): PairResult = withContext(Dispatchers.IO) {
        try {
            // Generate a random salt for this pairing session
            val salt = ByteArray(16)
            SecureRandom().nextBytes(salt)
            val saltHex = salt.joinToString("") { "%02x".format(it) }

            // Get the client certificate fingerprint
            val clientCertPem = certificateManager.getCertificatePem()

            // Phase 1: Send salt and get server challenge
            val phase1Url = "${baseUrl()}/pair?" +
                    "uniqueid=$uniqueId" +
                    "&devicename=AppoJellyNite" +
                    "&updateState=1" +
                    "&phrase=getservercert" +
                    "&salt=$saltHex" +
                    "&clientcert=${certificateManager.getCertificateHex()}"

            val phase1Response = httpClient.newCall(
                Request.Builder().url(phase1Url).build()
            ).execute()
            val phase1Body = phase1Response.body?.string()
                ?: return@withContext PairResult.Error("Empty Phase 1 response")

            if (phase1Body.contains("<paired>0</paired>")) {
                return@withContext PairResult.Error("Server rejected pairing request")
            }

            // Extract server certificate from response
            val serverCert = extractXmlValue(phase1Body, "plaincert")
                ?: return@withContext PairResult.Error("No server certificate in response")

            // Phase 2: Generate challenge using PIN-derived AES key
            val aesKey = PairingCrypto.generateAesKey(salt, pin)
            val challengeBytes = ByteArray(16)
            SecureRandom().nextBytes(challengeBytes)
            val encryptedChallenge = PairingCrypto.encrypt(challengeBytes, aesKey)
            val challengeHex = encryptedChallenge.joinToString("") { "%02x".format(it) }

            val phase2Url = "${baseUrl()}/pair?" +
                    "uniqueid=$uniqueId" +
                    "&devicename=AppoJellyNite" +
                    "&updateState=1" +
                    "&phrase=pairchallenge" +
                    "&clientchallenge=$challengeHex"

            val phase2Response = httpClient.newCall(
                Request.Builder().url(phase2Url).build()
            ).execute()
            val phase2Body = phase2Response.body?.string()
                ?: return@withContext PairResult.Error("Empty Phase 2 response")

            if (phase2Body.contains("<paired>0</paired>")) {
                return@withContext PairResult.Error("Server rejected challenge")
            }

            // Extract and decrypt server's challenge response
            val serverChallengeHex = extractXmlValue(phase2Body, "challengeresponse")
                ?: return@withContext PairResult.Error("No challenge response from server")
            val serverChallengeBytes = hexToBytes(serverChallengeHex)
            val decryptedServerChallenge = PairingCrypto.decrypt(serverChallengeBytes, aesKey)

            // Phase 3: Send client pairing secret
            val clientPairingSecret = PairingCrypto.generatePairingSecret(
                certificateManager.getPrivateKey(),
                decryptedServerChallenge,
            )
            val secretHex = clientPairingSecret.joinToString("") { "%02x".format(it) }

            val phase3Url = "${baseUrl()}/pair?" +
                    "uniqueid=$uniqueId" +
                    "&devicename=AppoJellyNite" +
                    "&updateState=1" +
                    "&phrase=clientpairingsecret" +
                    "&clientpairingsecret=$secretHex"

            val phase3Response = httpClient.newCall(
                Request.Builder().url(phase3Url).build()
            ).execute()
            val phase3Body = phase3Response.body?.string()
                ?: return@withContext PairResult.Error("Empty Phase 3 response")

            if (phase3Body.contains("<paired>0</paired>")) {
                return@withContext PairResult.Error("Pairing secret rejected")
            }

            // Verify server's pairing secret
            val serverSecretHex = extractXmlValue(phase3Body, "pairingsecret")
            if (serverSecretHex == null) {
                return@withContext PairResult.Error("No server pairing secret")
            }

            val serverSecret = hexToBytes(serverSecretHex)
            val serverCertBytes = hexToBytes(serverCert)

            val verified = PairingCrypto.verifyServerPairingSecret(
                serverSecret,
                serverCertBytes,
                challengeBytes,
            )

            if (!verified) {
                // Unpair on failure
                unpair()
                return@withContext PairResult.Error("Server signature verification failed")
            }

            // Phase 4: Confirm pairing
            val phase4Url = "${baseUrl()}/pair?" +
                    "uniqueid=$uniqueId" +
                    "&devicename=AppoJellyNite" +
                    "&updateState=1" +
                    "&phrase=pairchallenge"

            val phase4Response = httpClient.newCall(
                Request.Builder().url(phase4Url).build()
            ).execute()
            val phase4Body = phase4Response.body?.string()
                ?: return@withContext PairResult.Error("Empty Phase 4 response")

            if (phase4Body.contains("<paired>1</paired>")) {
                // Store the server certificate for future connections
                certificateManager.storeServerCertificate(serverCertBytes)
                PairResult.Success
            } else {
                PairResult.Error("Final pairing confirmation failed")
            }
        } catch (e: Exception) {
            PairResult.Error("Pairing failed: ${e.message}")
        }
    }

    /**
     * Unpair this client from the server.
     */
    suspend fun unpair() = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl()}/unpair?uniqueid=$uniqueId"
            httpClient.newCall(Request.Builder().url(url).build()).execute()
        } catch (_: Exception) {
            // Best effort
        }
    }

    /**
     * Get the list of apps registered on the server.
     */
    suspend fun getAppList(): List<NvApp> = withContext(Dispatchers.IO) {
        val url = "${baseUrl()}/applist?uniqueid=$uniqueId"
        val response = httpClient.newCall(Request.Builder().url(url).build()).execute()
        val body = response.body?.string() ?: return@withContext emptyList()
        NvApp.parseList(body)
    }

    /**
     * Launch an app on the server by its ID.
     */
    suspend fun launchApp(
        appId: Int,
        width: Int,
        height: Int,
        fps: Int,
        bitrate: Int,
        sops: Boolean = true,
        localAudio: Boolean = false,
        surroundAudioInfo: Int = MoonlightConstants.AUDIO_CONFIGURATION_STEREO,
    ): Boolean = withContext(Dispatchers.IO) {
        val riKeyId = (System.currentTimeMillis() / 1000).toInt()

        // Generate a random AES key for the streaming session
        val riKey = ByteArray(16)
        SecureRandom().nextBytes(riKey)
        val riKeyHex = riKey.joinToString("") { "%02x".format(it) }

        val url = "${baseUrl()}/launch?" +
                "uniqueid=$uniqueId" +
                "&appid=$appId" +
                "&mode=${width}x${height}x${fps}" +
                "&additionalStates=1" +
                "&sops=${if (sops) 1 else 0}" +
                "&rikey=$riKeyHex" +
                "&rikeyid=$riKeyId" +
                "&localAudioPlayMode=${if (localAudio) 1 else 0}" +
                "&surroundAudioInfo=$surroundAudioInfo" +
                "&remoteControllersBitmap=0" +
                "&gcmap=0"

        val response = httpClient.newCall(Request.Builder().url(url).build()).execute()
        val body = response.body?.string() ?: return@withContext false
        !body.contains("<root status_code=\"-1\"")
    }

    /**
     * Resume an existing streaming session.
     */
    suspend fun resumeApp(): Boolean = withContext(Dispatchers.IO) {
        val riKeyId = (System.currentTimeMillis() / 1000).toInt()
        val riKey = ByteArray(16)
        SecureRandom().nextBytes(riKey)
        val riKeyHex = riKey.joinToString("") { "%02x".format(it) }

        val url = "${baseUrl()}/resume?" +
                "uniqueid=$uniqueId" +
                "&rikey=$riKeyHex" +
                "&rikeyid=$riKeyId"

        val response = httpClient.newCall(Request.Builder().url(url).build()).execute()
        val body = response.body?.string() ?: return@withContext false
        body.contains("resume=\"1\"") || !body.contains("<root status_code=\"-1\"")
    }

    /**
     * Quit the currently running app.
     */
    suspend fun quitApp(): Boolean = withContext(Dispatchers.IO) {
        val url = "${baseUrl()}/cancel?uniqueid=$uniqueId"
        val response = httpClient.newCall(Request.Builder().url(url).build()).execute()
        val body = response.body?.string() ?: return@withContext false
        body.contains("cancel=\"1\"") || !body.contains("<root status_code=\"-1\"")
    }

    private fun extractXmlValue(xml: String, tag: String): String? {
        val pattern = "<$tag>(.*?)</$tag>".toRegex()
        return pattern.find(xml)?.groupValues?.get(1)
    }

    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun CertificateManager.getCertificateHex(): String {
        return getCertificateBytes().joinToString("") { "%02x".format(it) }
    }
}

sealed class PairResult {
    data object Success : PairResult()
    data class Error(val message: String) : PairResult()
}

data class ServerInfo(
    val hostname: String,
    val serverVersion: String,
    val gpuType: String,
    val isPaired: Boolean,
    val currentGame: Int,
    val state: String,
) {
    val isStreaming: Boolean get() = currentGame != 0

    companion object {
        fun parse(xml: String): ServerInfo {
            fun extract(tag: String): String {
                val pattern = "<$tag>(.*?)</$tag>".toRegex()
                return pattern.find(xml)?.groupValues?.get(1) ?: ""
            }

            return ServerInfo(
                hostname = extract("hostname"),
                serverVersion = extract("appversion"),
                gpuType = extract("gputype"),
                isPaired = extract("PairStatus") == "1",
                currentGame = extract("currentgame").toIntOrNull() ?: 0,
                state = extract("state"),
            )
        }
    }
}

data class NvApp(
    val appId: Int,
    val appName: String,
    val isRunning: Boolean = false,
) {
    companion object {
        fun parseList(xml: String): List<NvApp> {
            val apps = mutableListOf<NvApp>()
            val appPattern = "<App>(.*?)</App>".toRegex(RegexOption.DOT_MATCHES_ALL)

            for (match in appPattern.findAll(xml)) {
                val appXml = match.groupValues[1]
                fun extract(tag: String): String {
                    val p = "<$tag>(.*?)</$tag>".toRegex()
                    return p.find(appXml)?.groupValues?.get(1) ?: ""
                }

                val id = extract("ID").toIntOrNull() ?: continue
                val name = extract("AppTitle").ifEmpty { extract("AppName") }
                val running = extract("IsRunning") == "1"

                apps.add(NvApp(appId = id, appName = name, isRunning = running))
            }
            return apps
        }
    }
}

object MoonlightConstants {
    const val AUDIO_CONFIGURATION_STEREO = 0x0003_0002
    const val AUDIO_CONFIGURATION_51_SURROUND = 0x00FC_0006
}
