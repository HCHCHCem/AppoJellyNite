package com.appojellyapp.tv.playback

import android.os.Bundle
import android.view.View
import androidx.leanback.app.VideoSupportFragment
import androidx.leanback.app.VideoSupportFragmentGlueHost
import androidx.leanback.media.PlaybackTransportControlGlue
import androidx.leanback.media.PlayerAdapter
import androidx.leanback.widget.PlaybackControlsRow
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.appojellyapp.feature.jellyfin.data.JellyfinRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TvPlaybackFragment : VideoSupportFragment() {

    @Inject
    lateinit var jellyfinRepository: JellyfinRepository

    private var exoPlayer: ExoPlayer? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val itemId = arguments?.getString(ARG_ITEM_ID) ?: return
        val itemTitle = arguments?.getString(ARG_ITEM_TITLE) ?: ""

        exoPlayer = ExoPlayer.Builder(requireContext()).build()

        val streamUrl = jellyfinRepository.getStreamUrl(itemId)
        exoPlayer?.setMediaItem(MediaItem.fromUri(streamUrl))
        exoPlayer?.prepare()

        val playerAdapter = ExoPlayerAdapter(exoPlayer!!)
        val glueHost = VideoSupportFragmentGlueHost(this)
        val transportGlue = PlaybackTransportControlGlue(requireActivity(), playerAdapter)
        transportGlue.host = glueHost
        transportGlue.title = itemTitle
        transportGlue.isSeekEnabled = true

        exoPlayer?.playWhenReady = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        exoPlayer?.release()
        exoPlayer = null
    }

    companion object {
        const val ARG_ITEM_ID = "item_id"
        const val ARG_ITEM_TITLE = "item_title"
    }
}

/**
 * Bridges ExoPlayer to Leanback's PlayerAdapter for transport controls.
 */
class ExoPlayerAdapter(private val player: ExoPlayer) : PlayerAdapter() {

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            callback.onPlayStateChanged(this@ExoPlayerAdapter)
            if (playbackState == Player.STATE_ENDED) {
                callback.onPlayCompleted(this@ExoPlayerAdapter)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            callback.onPlayStateChanged(this@ExoPlayerAdapter)
        }
    }

    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun isPlaying(): Boolean = player.isPlaying

    override fun getCurrentPosition(): Long = player.currentPosition

    override fun getDuration(): Long = player.duration

    override fun getBufferedPosition(): Long = player.bufferedPosition

    override fun seekTo(positionInMs: Long) {
        player.seekTo(positionInMs)
    }

    override fun onAttachedToHost(host: PlaybackGlueHost?) {
        super.onAttachedToHost(host)
        player.addListener(listener)
    }

    override fun onDetachedFromHost() {
        super.onDetachedFromHost()
        player.removeListener(listener)
    }

    override fun isPrepared(): Boolean = player.playbackState != Player.STATE_IDLE
}
