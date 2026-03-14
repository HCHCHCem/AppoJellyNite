package com.appojellyapp.core.model

import java.time.Duration
import java.time.Instant

/**
 * Unified content model representing any item from any source.
 */
sealed class ContentItem {
    abstract val id: String
    abstract val title: String
    abstract val imageUrl: String?
    abstract val source: ContentSource
    abstract val lastInteraction: Instant?

    data class Media(
        override val id: String,
        override val title: String,
        override val imageUrl: String?,
        val mediaType: MediaType,
        val jellyfinItemId: String,
        val progress: Float? = null,
        val year: Int? = null,
        val overview: String? = null,
        val backdropUrl: String? = null,
        override val lastInteraction: Instant? = null,
    ) : ContentItem() {
        override val source = ContentSource.JELLYFIN
    }

    data class PcGame(
        override val id: String,
        override val title: String,
        override val imageUrl: String?,
        val apolloAppId: String,
        val platform: String,
        val lastPlayed: Instant? = null,
        val playtime: Duration? = null,
        val backgroundUrl: String? = null,
        val description: String? = null,
    ) : ContentItem() {
        override val source = ContentSource.PLAYNITE
        override val lastInteraction: Instant? get() = lastPlayed
    }

    data class LocalRom(
        override val id: String,
        override val title: String,
        override val imageUrl: String?,
        val system: EmulationSystem,
        val romPath: String,
        val coreName: String,
        val saveStatePath: String? = null,
        override val lastInteraction: Instant? = null,
    ) : ContentItem() {
        override val source = ContentSource.LOCAL_EMULATION
    }
}

enum class ContentSource {
    JELLYFIN,
    PLAYNITE,
    LOCAL_EMULATION,
}

enum class MediaType {
    MOVIE,
    SERIES,
    EPISODE,
    MUSIC,
}

enum class EmulationSystem {
    NES,
    SNES,
    N64,
    GAME_BOY,
    GBA,
    NDS,
    GENESIS,
    PLAYSTATION,
    PSP,
    ARCADE,
    OTHER,
}
