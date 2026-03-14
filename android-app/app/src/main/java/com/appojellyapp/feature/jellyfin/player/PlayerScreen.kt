package com.appojellyapp.feature.jellyfin.player

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
fun PlayerScreen(
    itemId: String,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val subtitleTracks by viewModel.subtitleTracks.collectAsState()
    var showSubtitleMenu by remember { mutableStateOf(false) }
    var isUsingTranscoding by remember { mutableStateOf(false) }

    val streamUrl = viewModel.getStreamUrl(itemId)
    val transcodingUrl = viewModel.getTranscodingStreamUrl(itemId)

    LaunchedEffect(itemId) {
        viewModel.loadSubtitleTracks(itemId)
    }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
            prepare()
            playWhenReady = true

            // Fallback to transcoding on playback error
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    if (!isUsingTranscoding) {
                        isUsingTranscoding = true
                        setMediaItem(MediaItem.fromUri(transcodingUrl))
                        prepare()
                        playWhenReady = true
                    }
                }
            })
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Subtitle selector
        if (subtitleTracks.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
            ) {
                IconButton(onClick = { showSubtitleMenu = true }) {
                    Icon(Icons.Default.Subtitles, contentDescription = "Subtitles")
                }
                DropdownMenu(
                    expanded = showSubtitleMenu,
                    onDismissRequest = { showSubtitleMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Off") },
                        onClick = {
                            showSubtitleMenu = false
                            // Remove subtitles by reloading without subtitle config
                            val currentUrl = if (isUsingTranscoding) transcodingUrl else streamUrl
                            val position = player.currentPosition
                            player.setMediaItem(MediaItem.fromUri(currentUrl))
                            player.seekTo(position)
                            player.prepare()
                        },
                    )
                    subtitleTracks.forEach { track ->
                        DropdownMenuItem(
                            text = { Text(track.displayTitle ?: track.language ?: "Track ${track.index}") },
                            onClick = {
                                showSubtitleMenu = false
                                val subtitleUrl = viewModel.getSubtitleUrl(itemId, track.index)
                                val currentUrl = if (isUsingTranscoding) transcodingUrl else streamUrl
                                val position = player.currentPosition
                                val subtitleConfig = SubtitleConfiguration.Builder(Uri.parse(subtitleUrl))
                                    .setMimeType(MimeTypes.TEXT_VTT)
                                    .setLanguage(track.language)
                                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                    .build()
                                val mediaItem = MediaItem.Builder()
                                    .setUri(currentUrl)
                                    .setSubtitleConfigurations(listOf(subtitleConfig))
                                    .build()
                                player.setMediaItem(mediaItem)
                                player.seekTo(position)
                                player.prepare()
                            },
                        )
                    }
                }
            }
        }
    }
}
