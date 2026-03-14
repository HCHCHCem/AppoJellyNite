package com.appojellyapp.tv.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.lifecycle.lifecycleScope
import com.appojellyapp.core.model.ContentItem
import com.appojellyapp.core.model.MediaType
import com.appojellyapp.feature.home.data.HomeRepository
import com.appojellyapp.feature.streaming.ui.StreamActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TvHomeFragment : BrowseSupportFragment() {

    @Inject
    lateinit var homeRepository: HomeRepository

    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        title = "AppoJellyNite"
        adapter = rowsAdapter

        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is ContentItem.Media -> {
                    // Navigate to TV media detail/player
                    // For now, this is a placeholder
                }
                is ContentItem.PcGame -> {
                    val intent = Intent(requireContext(), StreamActivity::class.java).apply {
                        putExtra(StreamActivity.EXTRA_APOLLO_APP_ID, item.apolloAppId)
                        putExtra(StreamActivity.EXTRA_GAME_NAME, item.title)
                    }
                    startActivity(intent)
                }
            }
        }

        loadContent()
    }

    private fun loadContent() {
        var rowIndex = 0

        lifecycleScope.launch {
            homeRepository.getContinueWatching()
                .catch { emit(emptyList()) }
                .collect { items ->
                    if (items.isNotEmpty()) {
                        updateRow(rowIndex, "Continue Watching", items)
                    }
                }
        }
        rowIndex++

        lifecycleScope.launch {
            homeRepository.getPcGames()
                .catch { emit(emptyList()) }
                .collect { items ->
                    if (items.isNotEmpty()) {
                        updateRow(rowIndex, "PC Games", items)
                    }
                }
        }
        rowIndex++

        lifecycleScope.launch {
            homeRepository.getMovies()
                .catch { emit(emptyList()) }
                .collect { items ->
                    if (items.isNotEmpty()) {
                        updateRow(rowIndex, "Movies", items)
                    }
                }
        }
        rowIndex++

        lifecycleScope.launch {
            homeRepository.getTvShows()
                .catch { emit(emptyList()) }
                .collect { items ->
                    if (items.isNotEmpty()) {
                        updateRow(rowIndex, "TV Shows", items)
                    }
                }
        }
    }

    private fun updateRow(index: Int, title: String, items: List<ContentItem>) {
        val header = HeaderItem(index.toLong(), title)
        val listRowAdapter = ArrayObjectAdapter(ContentCardPresenter())
        items.forEach { listRowAdapter.add(it) }

        if (index < rowsAdapter.size()) {
            rowsAdapter.replace(index, ListRow(header, listRowAdapter))
        } else {
            rowsAdapter.add(ListRow(header, listRowAdapter))
        }
    }
}

/**
 * Leanback card presenter for ContentItem objects.
 */
class ContentCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: android.view.ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val content = item as ContentItem
        val cardView = viewHolder.view as ImageCardView

        cardView.titleText = content.title
        cardView.contentText = when (content) {
            is ContentItem.Media -> content.year?.toString() ?: ""
            is ContentItem.PcGame -> content.platform
            is ContentItem.LocalRom -> content.system.name
        }

        // Image loading via Coil would be integrated here.
        // For TV, you'd use cardView.mainImageView and load via Coil/Glide.
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        cardView.mainImage = null
    }

    companion object {
        private const val CARD_WIDTH = 313
        private const val CARD_HEIGHT = 176
    }
}
