package com.appojellyapp.feature.streaming.apollo

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

data class ApolloApp(
    val name: String,
    val id: String? = null,
    @SerializedName("image-path") val imagePath: String? = null,
)

data class ApolloLoginRequest(
    val username: String,
    val password: String,
)

data class ApolloLaunchRequest(
    val id: String? = null,
    val name: String? = null,
)

@Singleton
class ApolloApiClient @Inject constructor(
    private val httpClient: OkHttpClient,
) {
    private val gson = Gson()
    private var baseUrl: String = ""
    private var authToken: String? = null

    fun configure(host: String, port: Int = 47990) {
        baseUrl = "https://$host:$port"
    }

    suspend fun login(username: String, password: String): Boolean = withContext(Dispatchers.IO) {
        val body = gson.toJson(ApolloLoginRequest(username, password))
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/api/login")
            .post(body)
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                // Extract auth cookie/token from response
                val cookies = response.headers("Set-Cookie")
                authToken = cookies.firstOrNull()
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun launchApp(appId: String): Boolean = withContext(Dispatchers.IO) {
        val body = gson.toJson(ApolloLaunchRequest(id = appId))
            .toRequestBody("application/json".toMediaType())

        val requestBuilder = Request.Builder()
            .url("$baseUrl/api/apps/launch")
            .post(body)

        authToken?.let { requestBuilder.header("Cookie", it) }

        try {
            val response = httpClient.newCall(requestBuilder.build()).execute()
            response.isSuccessful
        } catch (_: Exception) {
            false
        }
    }

    suspend fun launchAppByName(appName: String): Boolean = withContext(Dispatchers.IO) {
        val body = gson.toJson(ApolloLaunchRequest(name = appName))
            .toRequestBody("application/json".toMediaType())

        val requestBuilder = Request.Builder()
            .url("$baseUrl/api/apps/launch")
            .post(body)

        authToken?.let { requestBuilder.header("Cookie", it) }

        try {
            val response = httpClient.newCall(requestBuilder.build()).execute()
            response.isSuccessful
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getApps(): List<ApolloApp> = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
            .url("$baseUrl/api/apps")
            .get()

        authToken?.let { requestBuilder.header("Cookie", it) }

        try {
            val response = httpClient.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext emptyList()
                val result = gson.fromJson(body, AppsResponse::class.java)
                result.apps
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private data class AppsResponse(val apps: List<ApolloApp> = emptyList())
}
