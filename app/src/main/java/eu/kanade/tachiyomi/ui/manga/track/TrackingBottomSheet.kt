package eu.kanade.tachiyomi.ui.manga.track

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.net.toUri
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.UnattendedTrackService
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.databinding.TrackingBottomSheetBinding
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.withUIContext
import eu.kanade.tachiyomi.util.view.RecyclerWindowInsetsListener
import eu.kanade.tachiyomi.util.view.checkHeightThen
import eu.kanade.tachiyomi.widget.E2EBottomSheetDialog
import timber.log.Timber
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class TrackingBottomSheet(private val controller: MangaDetailsController) :
    E2EBottomSheetDialog<TrackingBottomSheetBinding>(controller.activity!!),
    TrackAdapter.OnClickListener,
    SetTrackStatusDialog.Listener,
    SetTrackChaptersDialog.Listener,
    SetTrackScoreDialog.Listener,
    TrackRemoveDialog.Listener,
    SetTrackReadingDatesDialog.Listener {

    val activity = controller.activity!!

    val presenter = controller.presenter

    private val sourceManager: SourceManager by injectLazy()
    private var adapter: TrackAdapter? = null

    override fun createBinding(inflater: LayoutInflater) =
        TrackingBottomSheetBinding.inflate(inflater)

    override var recyclerView: RecyclerView? = binding.trackRecycler

    init {
        val height = activity.window.decorView.rootWindowInsets.systemWindowInsetBottom
        sheetBehavior.peekHeight = 500.dpToPx + height

        sheetBehavior.addBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onSlide(bottomSheet: View, progress: Float) { }

                override fun onStateChanged(p0: View, state: Int) {
                    if (state == BottomSheetBehavior.STATE_EXPANDED) {
                        sheetBehavior.skipCollapsed = true
                    }
                }
            }
        )
        binding.displayBottomSheet.checkHeightThen {
            val fullHeight = activity.window.decorView.height
            val insets = activity.window.decorView.rootWindowInsets
            binding.trackRecycler.updateLayoutParams<ConstraintLayout.LayoutParams> {
                matchConstraintMaxHeight =
                    fullHeight - (insets?.systemWindowInsetTop ?: 0) - 30.dpToPx
            }
        }
    }

    override fun onStart() {
        super.onStart()
        sheetBehavior.skipCollapsed = true
    }

    /**
     * Called when the sheet is created. It initializes the listeners and values of the preferences.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = TrackAdapter(this)
        binding.trackRecycler.layoutManager = LinearLayoutManager(context)
        binding.trackRecycler.adapter = adapter
        binding.trackRecycler.setOnApplyWindowInsetsListener(RecyclerWindowInsetsListener)

        adapter?.items = presenter.trackList
    }

    fun onNextTrackings(trackings: List<TrackItem>) {
        onRefreshDone()
        adapter?.items = trackings
        controller.refreshTracker()
    }

    fun onSearchResults(results: List<TrackSearch>) {
        getSearchDialog()?.onSearchResults(results)
    }

    fun onSearchResultsError(error: Throwable) {
        Timber.e(error)
        activity.toast(error.message)
        getSearchDialog()?.onSearchResultsError()
    }

    private fun getSearchDialog(): TrackSearchDialog? {
        return controller.router.getControllerWithTag(TAG_SEARCH_CONTROLLER) as? TrackSearchDialog
    }

    fun onRefreshDone() {
        for (i in adapter!!.items.indices) {
            (binding.trackRecycler.findViewHolderForAdapterPosition(i) as? TrackHolder)?.setProgress(false)
        }
    }

    fun onRefreshError(error: Throwable) {
        for (i in adapter!!.items.indices) {
            (binding.trackRecycler.findViewHolderForAdapterPosition(i) as? TrackHolder)?.setProgress(false)
        }
        activity.toast(error.message)
    }

    override fun onLogoClick(position: Int) {
        val track = adapter?.getItem(position)?.track ?: return
        if (controller.isNotOnline()) {
            dismiss()
            return
        }

        if (track.tracking_url.isBlank()) {
            activity.toast(R.string.url_not_set_click_again)
        } else {
            activity.startActivity(Intent(Intent.ACTION_VIEW, track.tracking_url.toUri()))
            controller.refreshTracker = position
        }
    }

    override fun onSetClick(position: Int) {
        val item = adapter?.getItem(position) ?: return
        if (controller.isNotOnline()) {
            dismiss()
            return
        }

        if (item.service is UnattendedTrackService) {
            if (item.track != null) {
                controller.presenter.removeTracker(item, false)
                return
            }

            if (!item.service.accept(controller.presenter.source)) {
                controller.view?.context?.toast(R.string.source_unsupported)
                return
            }

            launchIO {
                try {
                    item.service.match(controller.presenter.manga)?.let { track ->
                        controller.presenter.registerTracking(track, item.service)
                    }
                        ?: withUIContext { controller.view?.context?.toast(R.string.no_match_found) }
                } catch (e: Exception) {
                    withUIContext { controller.view?.context?.toast(R.string.no_match_found) }
                }
            }
        } else {
            TrackSearchDialog(this, item.service, item.track != null)
                .showDialog(controller.router, TAG_SEARCH_CONTROLLER)
        }
    }

    override fun onStatusClick(position: Int) {
        val item = adapter?.getItem(position) ?: return
        if (item.track == null) return
        if (controller.isNotOnline()) {
            dismiss()
            return
        }

        SetTrackStatusDialog(this, item).showDialog(controller.router)
    }

    override fun onRemoveClick(position: Int) {
        val item = adapter?.getItem(position) ?: return
        if (item.track == null) return

        if (controller.isNotOnline()) {
            dismiss()
            return
        }

        TrackRemoveDialog(this, item).showDialog(controller.router)
    }

    override fun onChaptersClick(position: Int) {
        val item = adapter?.getItem(position) ?: return
        if (item.track == null) return
        if (controller.isNotOnline()) {
            dismiss()
            return
        }
        SetTrackChaptersDialog(this, item).showDialog(controller.router)
    }

    override fun onScoreClick(position: Int) {
        val item = adapter?.getItem(position) ?: return
        if (item.track == null) return
        if (controller.isNotOnline()) {
            dismiss()
            return
        }

        SetTrackScoreDialog(this, item).showDialog(controller.router)
    }

    override fun onStartDateClick(position: Int) {
        val item = adapter?.getItem(position) ?: return
        if (item.track == null) return

        val suggestedDate = presenter.getSuggestedDate(SetTrackReadingDatesDialog.ReadingDate.Start)
        SetTrackReadingDatesDialog(
            controller,
            this,
            SetTrackReadingDatesDialog.ReadingDate.Start,
            item,
            suggestedDate
        )
            .showDialog(controller.router)
    }

    override fun onFinishDateClick(position: Int) {
        val item = adapter?.getItem(position) ?: return
        if (item.track == null) return

        val suggestedDate = presenter.getSuggestedDate(SetTrackReadingDatesDialog.ReadingDate.Finish)
        SetTrackReadingDatesDialog(
            controller,
            this,
            SetTrackReadingDatesDialog.ReadingDate.Finish,
            item,
            suggestedDate
        )
            .showDialog(controller.router)
    }

    override fun setStatus(item: TrackItem, selection: Int) {
        presenter.setStatus(item, selection)
        refreshItem(item)
    }

    private fun refreshItem(item: TrackItem) {
        refreshTrack(item.service)
    }

    fun refreshItem(index: Int) {
        (binding.trackRecycler.findViewHolderForAdapterPosition(index) as? TrackHolder)?.setProgress(true)
    }

    fun refreshTrack(item: TrackService?) {
        val index = adapter?.indexOf(item) ?: -1
        if (index > -1) {
            (binding.trackRecycler.findViewHolderForAdapterPosition(index) as? TrackHolder)
                ?.setProgress(true)
        }
    }

    override fun setScore(item: TrackItem, score: Int) {
        presenter.setScore(item, score)
        refreshItem(item)
    }

    override fun setChaptersRead(item: TrackItem, chaptersRead: Int) {
        presenter.setLastChapterRead(item, chaptersRead)
        refreshItem(item)
    }

    override fun removeTracker(item: TrackItem, fromServiceAlso: Boolean) {
        refreshTrack(item.service)
        presenter.removeTracker(item, fromServiceAlso)
    }

    override fun setReadingDate(item: TrackItem, type: SetTrackReadingDatesDialog.ReadingDate, date: Long) {
        when (type) {
            SetTrackReadingDatesDialog.ReadingDate.Start -> controller.presenter.setTrackerStartDate(item, date)
            SetTrackReadingDatesDialog.ReadingDate.Finish -> controller.presenter.setTrackerFinishDate(item, date)
        }
    }

    private companion object {
        const val TAG_SEARCH_CONTROLLER = "track_search_controller"
    }
}
