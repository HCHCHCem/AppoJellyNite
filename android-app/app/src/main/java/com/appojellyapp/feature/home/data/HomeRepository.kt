package com.appojellyapp.feature.home.data

import com.appojellyapp.core.model.ContentItem
import com.appojellyapp.feature.jellyfin.data.JellyfinRepository
import com.appojellyapp.feature.playnite.data.PlayniteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeRepository @Inject constructor(
    private val jellyfinRepo: JellyfinRepository,
    private val playniteRepo: PlayniteRepository,
) {
    fun getContinueWatching(): Flow<List<ContentItem>> {
        return combine(
            jellyfinRepo.getResumeItems().catch { emit(emptyList()) },
            playniteRepo.getRecentlyPlayed().catch { emit(emptyList()) },
        ) { media, games ->
            (media + games).sortedByDescending { it.lastInteraction }
        }
    }

    fun getRecentlyAdded(): Flow<List<ContentItem>> {
        return jellyfinRepo.getLatestMedia()
            .catch { emit(emptyList()) }
    }

    fun getPcGames(): Flow<List<ContentItem.PcGame>> {
        return playniteRepo.getGames()
            .catch { emit(emptyList()) }
    }

    fun getMovies(): Flow<List<ContentItem.Media>> {
        return jellyfinRepo.getMovies()
            .catch { emit(emptyList()) }
    }

    fun getTvShows(): Flow<List<ContentItem.Media>> {
        return jellyfinRepo.getTvShows()
            .catch { emit(emptyList()) }
    }

    fun search(query: String): Flow<List<ContentItem>> {
        if (query.isBlank()) return flowOf(emptyList())

        return combine(
            jellyfinRepo.searchMedia(query).catch { emit(emptyList()) },
            playniteRepo.searchGames(query).catch { emit(emptyList()) },
        ) { media, games ->
            media + games
        }
    }
}
