package com.appojellyapp.feature.playnite.data

import com.apollographql.apollo.ApolloClient
import com.appojellyapp.core.model.ContentItem
import com.appojellyapp.core.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.security.MessageDigest
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayniteRepository @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    private var apolloClient: ApolloClient? = null

    private fun getClient(): ApolloClient {
        val config = settingsRepository.serverConfig.value.playniteWeb
            ?: throw IllegalStateException("Playnite Web not configured")

        if (apolloClient == null) {
            apolloClient = ApolloClient.Builder()
                .serverUrl("${config.serverUrl}/graphql")
                .build()
        }
        return apolloClient!!
    }

    /**
     * Generate the same deterministic app ID as the sync tool,
     * so we can map Playnite games to Apollo app entries.
     */
    fun generateApolloAppId(source: String, gameId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest("$source:$gameId".toByteArray())
        return hash.joinToString("") { "%02x".format(it) }.take(16)
    }

    fun getGames(): Flow<List<ContentItem.PcGame>> = flow {
        val client = getClient()
        val config = settingsRepository.serverConfig.value.playniteWeb
        val serverUrl = config?.serverUrl ?: ""

        val response = client.query(
            com.appojellyapp.feature.playnite.graphql.GetGamesQuery(
                com.apollographql.apollo.api.Optional.absent()
            )
        ).execute()

        val games = response.data?.games?.map { game ->
            val source = game.source ?: "unknown"
            val gameId = game.gameId ?: game.id

            ContentItem.PcGame(
                id = game.id,
                title = game.name,
                imageUrl = game.coverImage?.let { "$serverUrl/asset/$it" },
                apolloAppId = generateApolloAppId(source, gameId),
                platform = source,
                lastPlayed = game.lastActivity?.let {
                    try { Instant.parse(it) } catch (_: Exception) { null }
                },
                backgroundUrl = game.backgroundImage?.let { "$serverUrl/asset/$it" },
                description = game.description,
            )
        } ?: emptyList()

        emit(games)
    }.flowOn(Dispatchers.IO)

    fun getRecentlyPlayed(): Flow<List<ContentItem.PcGame>> = flow {
        val client = getClient()
        val config = settingsRepository.serverConfig.value.playniteWeb
        val serverUrl = config?.serverUrl ?: ""

        val response = client.query(
            com.appojellyapp.feature.playnite.graphql.GetGamesQuery(
                com.apollographql.apollo.api.Optional.absent()
            )
        ).execute()

        val games = response.data?.games
            ?.filter { it.lastActivity != null }
            ?.sortedByDescending { it.lastActivity }
            ?.take(10)
            ?.map { game ->
                val source = game.source ?: "unknown"
                val gameId = game.gameId ?: game.id

                ContentItem.PcGame(
                    id = game.id,
                    title = game.name,
                    imageUrl = game.coverImage?.let { "$serverUrl/asset/$it" },
                    apolloAppId = generateApolloAppId(source, gameId),
                    platform = source,
                    lastPlayed = game.lastActivity?.let {
                        try { Instant.parse(it) } catch (_: Exception) { null }
                    },
                    backgroundUrl = game.backgroundImage?.let { "$serverUrl/asset/$it" },
                    description = game.description,
                )
            } ?: emptyList()

        emit(games)
    }.flowOn(Dispatchers.IO)

    fun searchGames(query: String): Flow<List<ContentItem.PcGame>> = flow {
        val client = getClient()
        val config = settingsRepository.serverConfig.value.playniteWeb
        val serverUrl = config?.serverUrl ?: ""

        val filter = com.appojellyapp.feature.playnite.graphql.type.GameFilter(
            searchTerm = com.apollographql.apollo.api.Optional.present(query),
        )
        val response = client.query(
            com.appojellyapp.feature.playnite.graphql.GetGamesQuery(
                com.apollographql.apollo.api.Optional.present(filter)
            )
        ).execute()

        val games = response.data?.games?.map { game ->
            val source = game.source ?: "unknown"
            val gameId = game.gameId ?: game.id

            ContentItem.PcGame(
                id = game.id,
                title = game.name,
                imageUrl = game.coverImage?.let { "$serverUrl/asset/$it" },
                apolloAppId = generateApolloAppId(source, gameId),
                platform = source,
                lastPlayed = game.lastActivity?.let {
                    try { Instant.parse(it) } catch (_: Exception) { null }
                },
                backgroundUrl = game.backgroundImage?.let { "$serverUrl/asset/$it" },
                description = game.description,
            )
        } ?: emptyList()

        emit(games)
    }.flowOn(Dispatchers.IO)
}
