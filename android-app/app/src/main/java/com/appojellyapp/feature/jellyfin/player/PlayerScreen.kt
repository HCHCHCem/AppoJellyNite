package com.appojellyapp.feature.jellyfin.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
fun PlayerScreen(
    itemId: String,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val streamUrl = viewModel.getStreamUrl(itemId)

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
            prepare()
            playWhenReady = true
        }
    }

    // Report playback start when the player begins playing
    LaunchedEffect(itemId) {
        viewModel.reportPlaybackStart(itemId)
    }

    // Start progress reporting once the player is ready
    LaunchedEffect(player) {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    viewModel.startProgressReporting(
                        itemId = itemId,
                        getPositionMs = { player.currentPosition },
                        isPaused = { !player.isPlaying },
                    )
                }
            }
        })
    }

    DisposableEffect(Unit) {
        onDispose {
            // Report final position when leaving the player
            viewModel.reportPlaybackStopped(positionMs = player.currentPosition)
            player.release()
        }
    }

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
}
