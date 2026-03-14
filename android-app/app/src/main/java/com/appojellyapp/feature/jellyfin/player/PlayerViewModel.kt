package com.appojellyapp.feature.jellyfin.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appojellyapp.feature.jellyfin.data.JellyfinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the media player, managing playback reporting to Jellyfin.
 *
 * Reports playback start, periodic progress updates (every 10 seconds),
 * and playback stop events so that Jellyfin can track "Continue Watching"
 * positions accurately.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val jellyfinRepository: JellyfinRepository,
) : ViewModel() {

    private var progressReportJob: Job? = null
    private var currentItemId: String? = null

    companion object {
        private const val PROGRESS_REPORT_INTERVAL_MS = 10_000L
        // Jellyfin uses "ticks" where 1 tick = 100 nanoseconds = 0.0001 ms
        // So 1 ms = 10_000 ticks
        private const val TICKS_PER_MS = 10_000L
    }

    fun getStreamUrl(itemId: String): String {
        return jellyfinRepository.getStreamUrl(itemId)
    }

    /**
     * Report that playback has started for the given item.
     */
    fun reportPlaybackStart(itemId: String) {
        currentItemId = itemId
        viewModelScope.launch {
            jellyfinRepository.reportPlaybackStart(itemId)
        }
    }

    /**
     * Start periodic progress reporting.
     *
     * @param itemId The Jellyfin item ID
     * @param getPositionMs Lambda that returns the current playback position in milliseconds
     * @param isPaused Lambda that returns whether playback is currently paused
     */
    fun startProgressReporting(
        itemId: String,
        getPositionMs: () -> Long,
        isPaused: () -> Boolean,
    ) {
        stopProgressReporting()
        progressReportJob = viewModelScope.launch {
            while (isActive) {
                delay(PROGRESS_REPORT_INTERVAL_MS)
                val positionTicks = getPositionMs() * TICKS_PER_MS
                jellyfinRepository.reportPlaybackProgress(
                    itemId = itemId,
                    positionTicks = positionTicks,
                    isPaused = isPaused(),
                )
            }
        }
    }

    /**
     * Stop periodic progress reporting.
     */
    fun stopProgressReporting() {
        progressReportJob?.cancel()
        progressReportJob = null
    }

    /**
     * Report that playback has stopped and send the final position.
     *
     * @param positionMs The final playback position in milliseconds
     */
    fun reportPlaybackStopped(positionMs: Long) {
        val itemId = currentItemId ?: return
        stopProgressReporting()
        viewModelScope.launch {
            jellyfinRepository.reportPlaybackStopped(
                itemId = itemId,
                positionTicks = positionMs * TICKS_PER_MS,
            )
        }
        currentItemId = null
    }

    override fun onCleared() {
        super.onCleared()
        stopProgressReporting()
    }
}
