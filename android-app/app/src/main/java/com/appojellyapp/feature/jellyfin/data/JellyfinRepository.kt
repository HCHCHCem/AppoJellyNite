package com.appojellyapp.feature.jellyfin.data

import com.appojellyapp.core.model.ContentItem
import com.appojellyapp.core.model.MediaType
import com.appojellyapp.core.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinRepository @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    private var jellyfin: Jellyfin? = null
    private var apiClient: ApiClient? = null
    private var userId: UUID? = null

    private fun getApi(): ApiClient {
        val config = settingsRepository.serverConfig.value.jellyfin
            ?: throw IllegalStateException("Jellyfin not configured")

        if (apiClient == null) {
            jellyfin = createJellyfin {
                clientInfo = ClientInfo(name = "AppoJellyNite", version = "1.0.0")
            }
            apiClient = jellyfin!!.createApi(baseUrl = config.serverUrl).apply {
                accessToken = config.accessToken
            }
            userId = config.userId?.let { UUID.fromString(it) }
        }
        return apiClient!!
    }

    private fun getUserId(): UUID = userId
        ?: throw IllegalStateException("Jellyfin user not authenticated")

    suspend fun authenticate(serverUrl: String, username: String, password: String): Boolean {
        jellyfin = createJellyfin {
            clientInfo = ClientInfo(name = "AppoJellyNite", version = "1.0.0")
        }
        val api = jellyfin!!.createApi(baseUrl = serverUrl)

        return try {
            val authResult by api.userApi.authenticateUserByName(username, password)
            val token = authResult.accessToken ?: return false
            val uid = authResult.user?.id ?: return false

            api.accessToken = token
            apiClient = api
            userId = uid

            settingsRepository.updateJellyfinConfig(
                com.appojellyapp.core.settings.JellyfinConfig(
                    serverUrl = serverUrl,
                    username = username,
                    password = password,
                    userId = uid.toString(),
                    accessToken = token,
                )
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getResumeItems(): Flow<List<ContentItem.Media>> = flow {
        val api = getApi()
        val result by api.itemsApi.getResumeItems(userId = getUserId())
        emit(result.items?.map { it.toMediaItem(api) } ?: emptyList())
    }.flowOn(Dispatchers.IO)

    fun getLatestMedia(): Flow<List<ContentItem.Media>> = flow {
        val api = getApi()
        val result by api.userLibraryApi.getLatestMedia(userId = getUserId())
        emit(result.map { it.toMediaItem(api) })
    }.flowOn(Dispatchers.IO)

    fun getMovies(): Flow<List<ContentItem.Media>> = flow {
        val api = getApi()
        val result by api.itemsApi.getItems(
            userId = getUserId(),
            includeItemTypes = listOf(BaseItemKind.MOVIE),
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
            recursive = true,
        )
        emit(result.items?.map { it.toMediaItem(api) } ?: emptyList())
    }.flowOn(Dispatchers.IO)

    fun getTvShows(): Flow<List<ContentItem.Media>> = flow {
        val api = getApi()
        val result by api.itemsApi.getItems(
            userId = getUserId(),
            includeItemTypes = listOf(BaseItemKind.SERIES),
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
            recursive = true,
        )
        emit(result.items?.map { it.toMediaItem(api) } ?: emptyList())
    }.flowOn(Dispatchers.IO)

    fun searchMedia(query: String): Flow<List<ContentItem.Media>> = flow {
        val api = getApi()
        val result by api.itemsApi.getItems(
            userId = getUserId(),
            searchTerm = query,
            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.EPISODE),
            recursive = true,
            limit = 20,
        )
        emit(result.items?.map { it.toMediaItem(api) } ?: emptyList())
    }.flowOn(Dispatchers.IO)

    fun getItemDetails(itemId: String): Flow<ContentItem.Media> = flow {
        val api = getApi()
        val result by api.userLibraryApi.getItem(
            userId = getUserId(),
            itemId = UUID.fromString(itemId),
        )
        emit(result.toMediaItem(api))
    }.flowOn(Dispatchers.IO)

    fun getStreamUrl(itemId: String): String {
        val config = settingsRepository.serverConfig.value.jellyfin
            ?: throw IllegalStateException("Jellyfin not configured")
        return "${config.serverUrl}/Videos/$itemId/stream?static=true&api_key=${config.accessToken}"
    }

    /**
     * Report playback start to Jellyfin.
     * This tells the server the client has begun playing an item.
     */
    suspend fun reportPlaybackStart(itemId: String) {
        try {
            val api = getApi()
            api.playStateApi.reportPlaybackStart(
                org.jellyfin.sdk.model.api.PlaybackStartInfo(
                    itemId = UUID.fromString(itemId),
                    canSeek = true,
                    playMethod = org.jellyfin.sdk.model.api.PlayMethod.DIRECT_PLAY,
                )
            )
        } catch (e: Exception) {
            // Non-fatal — don't break playback if reporting fails
        }
    }

    /**
     * Report playback progress to Jellyfin.
     * Called periodically during playback to update the "Continue Watching" position.
     *
     * @param itemId The Jellyfin item ID
     * @param positionTicks The current playback position in ticks (1 tick = 100 nanoseconds)
     * @param isPaused Whether playback is currently paused
     */
    suspend fun reportPlaybackProgress(itemId: String, positionTicks: Long, isPaused: Boolean) {
        try {
            val api = getApi()
            api.playStateApi.reportPlaybackProgress(
                org.jellyfin.sdk.model.api.PlaybackProgressInfo(
                    itemId = UUID.fromString(itemId),
                    positionTicks = positionTicks,
                    isPaused = isPaused,
                    canSeek = true,
                    playMethod = org.jellyfin.sdk.model.api.PlayMethod.DIRECT_PLAY,
                )
            )
        } catch (e: Exception) {
            // Non-fatal
        }
    }

    /**
     * Report playback stopped to Jellyfin.
     * Called when the user stops or finishes playback.
     *
     * @param itemId The Jellyfin item ID
     * @param positionTicks The final playback position in ticks
     */
    suspend fun reportPlaybackStopped(itemId: String, positionTicks: Long) {
        try {
            val api = getApi()
            api.playStateApi.reportPlaybackStopped(
                org.jellyfin.sdk.model.api.PlaybackStopInfo(
                    itemId = UUID.fromString(itemId),
                    positionTicks = positionTicks,
                )
            )
        } catch (e: Exception) {
            // Non-fatal
        }
    }

    private fun BaseItemDto.toMediaItem(api: ApiClient): ContentItem.Media {
        val mediaType = when (type) {
            BaseItemKind.MOVIE -> MediaType.MOVIE
            BaseItemKind.SERIES -> MediaType.SERIES
            BaseItemKind.EPISODE -> MediaType.EPISODE
            else -> MediaType.MOVIE
        }

        val config = settingsRepository.serverConfig.value.jellyfin
        val serverUrl = config?.serverUrl ?: ""
        val imageUrl = id?.let { "$serverUrl/Items/$it/Images/Primary?maxWidth=400&quality=90" }
        val backdropUrl = id?.let { "$serverUrl/Items/$it/Images/Backdrop?maxWidth=1280&quality=80" }

        val progress = userData?.playedPercentage?.let { it.toFloat() / 100f }

        return ContentItem.Media(
            id = id?.toString() ?: "",
            title = name ?: "Unknown",
            imageUrl = imageUrl,
            mediaType = mediaType,
            jellyfinItemId = id?.toString() ?: "",
            progress = progress,
            year = productionYear,
            overview = overview,
            backdropUrl = backdropUrl,
            lastInteraction = userData?.lastPlayedDate?.let {
                Instant.parse(it.toString())
            },
        )
    }
}
