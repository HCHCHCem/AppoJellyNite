package com.appojellyapp.feature.home.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.appojellyapp.core.model.ContentItem

@Composable
fun HomeScreen(
    onContentClick: (ContentItem) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val continueWatching by viewModel.continueWatching.collectAsState()
    val recentlyAdded by viewModel.recentlyAdded.collectAsState()
    val pcGames by viewModel.pcGames.collectAsState()
    val movies by viewModel.movies.collectAsState()
    val tvShows by viewModel.tvShows.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        if (continueWatching.isNotEmpty()) {
            ContentRow(
                title = "Continue Watching",
                items = continueWatching,
                onItemClick = onContentClick,
            )
        }

        if (recentlyAdded.isNotEmpty()) {
            ContentRow(
                title = "Recently Added",
                items = recentlyAdded,
                onItemClick = onContentClick,
            )
        }

        if (pcGames.isNotEmpty()) {
            ContentRow(
                title = "PC Games",
                items = pcGames,
                onItemClick = onContentClick,
            )
        }

        if (movies.isNotEmpty()) {
            ContentRow(
                title = "Movies",
                items = movies,
                onItemClick = onContentClick,
            )
        }

        if (tvShows.isNotEmpty()) {
            ContentRow(
                title = "TV Shows",
                items = tvShows,
                onItemClick = onContentClick,
            )
        }
    }
}

@Composable
fun ContentRow(
    title: String,
    items: List<ContentItem>,
    onItemClick: (ContentItem) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { "${it.source}_${it.id}" }) { item ->
                ContentCard(item = item, onClick = { onItemClick(item) })
            }
        }
    }
}

@Composable
fun ContentCard(
    item: ContentItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = item.imageUrl,
            contentDescription = item.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
            contentScale = ContentScale.Crop,
        )
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
