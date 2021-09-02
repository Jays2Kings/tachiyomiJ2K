package eu.kanade.tachiyomi.ui.manga.track

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.TransitionManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.UnattendedTrackService
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.databinding.TrackingBottomSheetBinding
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.withUIContext
import eu.kanade.tachiyomi.util.view.RecyclerWindowInsetsListener
import eu.kanade.tachiyomi.util.view.checkHeightThen
import eu.kanade.tachiyomi.widget.E2EBottomSheetDialog
import timber.log.Timber
import android.animation.LayoutTransition
import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.listeners.addClickListener
import eu.kanade.tachiyomi.ui.manga.MangaDetailsDivider
import eu.kanade.tachiyomi.util.lang.indexesOf
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.view.expand

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
    var searchingItem: TrackItem? = null

    private var adapter: TrackAdapter? = null
    private val searchItemAdapter = ItemAdapter<TrackSearchItem>()
    private val searchAdapter = FastAdapter.with(searchItemAdapter)

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

        binding.searchCloseButton.setOnClickListener {
            onBackPressed()
        }

        binding.trackSearchRecycler.adapter = adapter

        searchAdapter.onClickListener = { _, _, _, position ->
            trackItem(position)
            true
        }

        searchAdapter.addClickListener<TrackSearchItem.ViewHolder, TrackSearchItem>({ it.binding.linkButton }) { _, _, _, item ->
            activity.openInBrowser(item.trackSearch.tracking_url)
        }

        binding.trackSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH) {
                val text = binding.trackSearch.text?.toString() ?: ""
                if (text.isNotBlank()) {
                    startTransition()
                    val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.hideSoftInputFromWindow(binding.trackSearch.windowToken, 0)
                    binding.trackSearch.clearFocus()
                    search(text)
                }
            }
            true
        }

        binding.displayBottomSheet.checkHeightThen {
            val fullHeight = activity.window.decorView.height
            val insets = activity.window.decorView.rootWindowInsets
            binding.trackRecycler.updateLayoutParams<ConstraintLayout.LayoutParams> {
                matchConstraintMaxHeight =
                    fullHeight - (insets?.systemWindowInsetTop ?: 0) - 30.dpToPx
            }
            binding.trackSearchConstraintLayout.updateLayoutParams<ConstraintLayout.LayoutParams> {
                matchConstraintMaxHeight =
                    fullHeight - (insets?.systemWindowInsetTop ?: 0) - 30.dpToPx
            }
        }
    }

    override fun onStart() {
        super.onStart()
        sheetBehavior.skipCollapsed = true
        val lTransition = LayoutTransition()
        lTransition.setAnimateParentHierarchy(false)
        binding.root.layoutTransition = lTransition
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
        binding.textInputLayout.setOnApplyWindowInsetsListener(RecyclerWindowInsetsListener)

        binding.trackSearchRecycler.layoutManager = LinearLayoutManager(activity)
        binding.trackSearchRecycler.adapter = searchAdapter
        binding.trackSearchRecycler.setHasFixedSize(false)
        binding.trackSearchRecycler.addItemDecoration(MangaDetailsDivider(activity, 16.dpToPx))
        binding.trackSearchRecycler.itemAnimator = null

        adapter?.items = presenter.trackList
    }

    fun onNextTrackersUpdate(trackers: List<TrackItem>) {
        onRefreshDone()
        adapter?.items = trackers
        controller.refreshTracker()
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
            activity.openInBrowser(track.tracking_url.toUri())
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
            showSearchView(item)
        }
    }

    private fun startTransition(duration: Long = 100) {
        val transition = androidx.transition.AutoTransition()
        transition.duration = duration
        val mainView = binding.root.parent as ViewGroup
        TransitionManager.endTransitions(mainView)
        TransitionManager.beginDelayedTransition(mainView, transition)
    }

    private fun showSearchView(item: TrackItem) {
        searchingItem = item
        val title = presenter.manga.title
        sheetBehavior.expand()
        sheetBehavior.isDraggable = false
        search(title)
        binding.trackSearch.setText(title, TextView.BufferType.EDITABLE)
        binding.trackRecycler.isVisible = false
        binding.trackSearchConstraintLayout.isVisible = true
    }

    private fun hideSearchView() {
        startTransition()
        searchItemAdapter.clear()
        searchAdapter.notifyAdapterDataSetChanged()
        sheetBehavior.isDraggable = true
        binding.trackRecycler.isVisible = true
        binding.trackSearchConstraintLayout.isVisible = false
        searchingItem = null
    }

    private fun search(query: String) {
        binding.searchProgress.visibility = View.VISIBLE
        binding.trackSearchRecycler.visibility = View.GONE
        setMiddleTrackView(binding.searchProgress.id)
        binding.searchEmptyView.hide()
        val searchingItem = searchingItem ?: return
        presenter.trackSearch(query, searchingItem.service)
    }

    private fun setMiddleTrackView(id: Int) {
        binding.titleLayout.updateLayoutParams<ConstraintLayout.LayoutParams> {
            bottomToTop = id
        }
        binding.textInputLayout.updateLayoutParams<ConstraintLayout.LayoutParams> {
            topToBottom = id
        }
    }

    fun onSearchResults(results: List<TrackSearch>) {
        startTransition()
        binding.searchProgress.visibility = View.GONE
        binding.trackSearchRecycler.visibility = View.VISIBLE
        searchItemAdapter.set(
            results.map {
                TrackSearchItem(it).apply {
                    isSelected = it.tracking_url == searchingItem?.track?.tracking_url
                }
            }
        )
        if (results.isEmpty()) {
            setMiddleTrackView(binding.searchEmptyView.id)
            binding.searchEmptyView.show(
                R.drawable.ic_search_off_24dp,
                R.string.no_results_found
            )
        } else {
            setMiddleTrackView(binding.trackSearchRecycler.id)
            binding.searchEmptyView.hide()
            if (results.size == 1 && searchingItem?.track == null) {
                trackItem(0)
            }
        }
        binding.trackSearchRecycler.isVisible = !binding.searchEmptyView.isVisible
    }

    fun onSearchResultsError(error: Throwable) {
        Timber.e(error)
        setMiddleTrackView(binding.searchEmptyView.id)
        binding.searchProgress.visibility = View.VISIBLE
        binding.trackSearchRecycler.visibility = View.GONE
        searchItemAdapter.clear()
        binding.searchEmptyView.show(
            R.drawable.ic_search_off_24dp,
            error.message ?: ""
        )
    }

    private fun trackItem(position: Int) {
        val searchingItem = searchingItem
        val selectedItem = searchItemAdapter.getAdapterItem(position).trackSearch
        if (searchingItem != null) {
            if (searchingItem.track != null && searchingItem.service.canRemoveFromService() &&
                searchingItem.track.tracking_url != selectedItem.tracking_url
            ) {
                val ogTitle = searchingItem.track.title
                val newTitle = selectedItem.title

                val text = activity.getString(
                    R.string.remove_x_from_service_and_add_y,
                    ogTitle,
                    activity.getString(
                        searchingItem.service.nameRes()
                    ),
                    newTitle
                )

                val wordToSpan: Spannable = SpannableString(text)
                text.indexesOf(ogTitle).forEach {
                    wordToSpan.setSpan(StyleSpan(Typeface.ITALIC), it, it + ogTitle.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                text.indexesOf(newTitle).forEach {
                    wordToSpan.setSpan(StyleSpan(Typeface.ITALIC), it, it + newTitle.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

                val text2 = activity.getString(
                    R.string.keep_both_on_service,
                    activity.getString(
                        searchingItem.service.nameRes()
                    )
                )
                MaterialDialog(activity)
                    .title(R.string.remove_previous_tracker)
                    .listItems(items = listOf(wordToSpan, text2), waitForPositiveButton = false) { dialog, i, _ ->
                        if (i == 0) {
                            removeTracker(searchingItem, true)
                        }
                        refreshTrack(searchingItem.service)
                        presenter.registerTracking(selectedItem, searchingItem.service)
                        hideSearchView()
                        dialog.dismiss()
                    }
                    .negativeButton(android.R.string.cancel)
                    .show()
                return
            }
            refreshTrack(searchingItem.service)
            presenter.registerTracking(selectedItem, searchingItem.service)
        }
        hideSearchView()
    }

    override fun onBackPressed() {
        if (searchingItem != null) {
            hideSearchView()
        } else {
            super.onBackPressed()
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

    private fun refreshTrack(item: TrackService?) {
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
}
