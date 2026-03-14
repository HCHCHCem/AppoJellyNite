package com.appojellyapp.tv.detail

import android.os.Bundle
import android.view.View
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.widget.AbstractDetailsDescriptionPresenter
import androidx.leanback.widget.Action
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.DetailsOverviewRow
import androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import com.appojellyapp.core.model.MediaType
import com.appojellyapp.tv.playback.TvPlaybackFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TvDetailFragment : DetailsSupportFragment() {

    private var itemId: String = ""
    private var itemTitle: String = ""
    private var itemOverview: String = ""
    private var mediaTypeStr: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        itemId = arguments?.getString(ARG_ITEM_ID) ?: ""
        itemTitle = arguments?.getString(ARG_ITEM_TITLE) ?: ""
        itemOverview = arguments?.getString(ARG_ITEM_OVERVIEW) ?: ""
        mediaTypeStr = arguments?.getString(ARG_MEDIA_TYPE) ?: ""
        setupDetails()
    }

    private fun setupDetails() {
        val detailsOverview = DetailsOverviewRow(itemTitle).apply {
            // No image for now - would be loaded via Coil
        }

        val actionAdapter = ArrayObjectAdapter()
        actionAdapter.add(Action(ACTION_PLAY, "Play"))
        if (mediaTypeStr == MediaType.SERIES.name) {
            actionAdapter.add(Action(ACTION_EPISODES, "Episodes"))
        }
        detailsOverview.actionsAdapter = actionAdapter

        val presenterSelector = ClassPresenterSelector()
        val detailsPresenter = FullWidthDetailsOverviewRowPresenter(object : AbstractDetailsDescriptionPresenter() {
            override fun onBindDescription(viewHolder: ViewHolder, item: Any) {
                viewHolder.title.text = itemTitle
                viewHolder.body.text = itemOverview
            }
        })

        detailsPresenter.setOnActionClickedListener { action ->
            when (action.id) {
                ACTION_PLAY -> {
                    // Navigate to playback
                    val fragment = TvPlaybackFragment().apply {
                        arguments = Bundle().apply {
                            putString(TvPlaybackFragment.ARG_ITEM_ID, itemId)
                            putString(TvPlaybackFragment.ARG_ITEM_TITLE, itemTitle)
                        }
                    }
                    parentFragmentManager.beginTransaction()
                        .replace(android.R.id.content, fragment)
                        .addToBackStack(null)
                        .commit()
                }
            }
        }

        presenterSelector.addClassPresenter(DetailsOverviewRow::class.java, detailsPresenter)
        presenterSelector.addClassPresenter(ListRow::class.java, ListRowPresenter())

        val rowsAdapter = ArrayObjectAdapter(presenterSelector)
        rowsAdapter.add(detailsOverview)
        adapter = rowsAdapter
    }

    companion object {
        const val ARG_ITEM_ID = "item_id"
        const val ARG_ITEM_TITLE = "item_title"
        const val ARG_ITEM_OVERVIEW = "item_overview"
        const val ARG_MEDIA_TYPE = "media_type"
        private const val ACTION_PLAY = 1L
        private const val ACTION_EPISODES = 2L

        fun newInstance(itemId: String, title: String, overview: String, mediaType: String): TvDetailFragment {
            return TvDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ITEM_ID, itemId)
                    putString(ARG_ITEM_TITLE, title)
                    putString(ARG_ITEM_OVERVIEW, overview)
                    putString(ARG_MEDIA_TYPE, mediaType)
                }
            }
        }
    }
}
