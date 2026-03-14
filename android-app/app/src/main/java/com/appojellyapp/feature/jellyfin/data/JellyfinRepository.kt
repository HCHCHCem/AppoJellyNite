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
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
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

    fun getEpisodes(seriesId: String): Flow<List<ContentItem.Media>> = flow {
        val api = getApi()
        val result by api.itemsApi.getItems(
            userId = getUserId(),
            parentId = UUID.fromString(seriesId),
            includeItemTypes = listOf(BaseItemKind.EPISODE),
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
            recursive = true,
        )
        emit(result.items?.map { it.toMediaItem(api) } ?: emptyList())
    }.flowOn(Dispatchers.IO)

    fun getStreamUrl(itemId: String): String {
        val config = settingsRepository.serverConfig.value.jellyfin
            ?: throw IllegalStateException("Jellyfin not configured")
        return "${config.serverUrl}/Videos/$itemId/stream?static=true&api_key=${config.accessToken}"
    }

    fun getTranscodingStreamUrl(itemId: String): String {
        val config = settingsRepository.serverConfig.value.jellyfin
            ?: throw IllegalStateException("Jellyfin not configured")
        return "${config.serverUrl}/Videos/$itemId/stream?api_key=${config.accessToken}" +
            "&audioCodec=aac&videoCodec=h264&maxWidth=1920"
    }

    data class SubtitleTrack(
        val index: Int,
        val language: String?,
        val displayTitle: String?,
        val isDefault: Boolean,
    )

    fun getSubtitleTracks(itemId: String): Flow<List<SubtitleTrack>> = flow {
        val api = getApi()
        val result by api.userLibraryApi.getItem(
            userId = getUserId(),
            itemId = UUID.fromString(itemId),
        )
        val tracks = result.mediaSources
            ?.firstOrNull()
            ?.mediaStreams
            ?.filter { it.type == org.jellyfin.sdk.model.api.MediaStreamType.SUBTITLE }
            ?.map { stream ->
                SubtitleTrack(
                    index = stream.index ?: 0,
                    language = stream.language,
                    displayTitle = stream.displayTitle,
                    isDefault = stream.isDefault == true,
                )
            } ?: emptyList()
        emit(tracks)
    }.flowOn(Dispatchers.IO)

    fun getSubtitleUrl(itemId: String, subtitleIndex: Int): String {
        val config = settingsRepository.serverConfig.value.jellyfin
            ?: throw IllegalStateException("Jellyfin not configured")
        return "${config.serverUrl}/Videos/$itemId/$subtitleIndex/Subtitles/$subtitleIndex/Stream.vtt?api_key=${config.accessToken}"
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
