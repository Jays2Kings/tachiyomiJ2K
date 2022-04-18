package eu.kanade.tachiyomi.ui.manga.track

import android.animation.LayoutTransition
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.TransitionManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.listeners.addClickListener
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.databinding.TrackChaptersDialogBinding
import eu.kanade.tachiyomi.databinding.TrackScoreDialogBinding
import eu.kanade.tachiyomi.databinding.TrackingBottomSheetBinding
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.ui.manga.MangaDetailsDivider
import eu.kanade.tachiyomi.util.lang.indexesOf
import eu.kanade.tachiyomi.util.system.addCheckBoxPrompt
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.isOnline
import eu.kanade.tachiyomi.util.system.isPromptChecked
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.rootWindowInsetsCompat
import eu.kanade.tachiyomi.util.system.toLocalCalendar
import eu.kanade.tachiyomi.util.system.toUtcCalendar
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.withUIContext
import eu.kanade.tachiyomi.util.view.RecyclerWindowInsetsListener
import eu.kanade.tachiyomi.util.view.checkHeightThen
import eu.kanade.tachiyomi.util.view.expand
import eu.kanade.tachiyomi.widget.E2EBottomSheetDialog
import timber.log.Timber
import java.text.DateFormat
import java.util.Calendar

class TrackingBottomSheet(private val controller: MangaDetailsController) :
    E2EBottomSheetDialog<TrackingBottomSheetBinding>(controller.activity!!),
    TrackAdapter.OnClickListener {

    val activity = controller.activity!!

    val presenter = controller.presenter
    private var searchingItem: TrackItem? = null

    private var adapter: TrackAdapter? = null
    private val searchItemAdapter = ItemAdapter<TrackSearchItem>()
    private val searchAdapter = FastAdapter.with(searchItemAdapter)
    private var suggestedStartDate: Long? = null
    private var suggestedFinishDate: Long? = null
    private val dateFormat: DateFormat by lazy {
        presenter.preferences.dateFormat()
    }

    override fun createBinding(inflater: LayoutInflater) =
        TrackingBottomSheetBinding.inflate(inflater)

    override var recyclerView: RecyclerView? = binding.trackRecycler

    init {
        val insets = activity.window.decorView.rootWindowInsetsCompat?.getInsets(systemBars())
        val height = insets?.bottom ?: 0
        sheetBehavior.peekHeight = 525.dpToPx + height
        sheetBehavior.expand()
        sheetBehavior.skipCollapsed = true

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
            binding.trackRecycler.updateLayoutParams<ConstraintLayout.LayoutParams> {
                matchConstraintMaxHeight = fullHeight - (insets?.top ?: 0) - 30.dpToPx
            }
            binding.trackSearchConstraintLayout.updateLayoutParams<ConstraintLayout.LayoutParams> {
                matchConstraintMaxHeight = fullHeight - (insets?.top ?: 0) - 30.dpToPx
            }
        }

        controller.viewScope.launchIO {
            suggestedStartDate = presenter.getSuggestedDate(ReadingDate.Start)
            suggestedFinishDate = presenter.getSuggestedDate(ReadingDate.Finish)
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

    override fun onTitleClick(position: Int) {
        val item = adapter?.getItem(position) ?: return
        if (controller.isNotOnline()) {
            dismiss()
            return
        }

        if (item.service is EnhancedTrackService) {
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

    override fun onTitleLongClick(position: Int) {
        val title = adapter?.getItem(position)?.track?.title ?: return
        controller.copyToClipboard(title, R.string.title, true)
    }

    private fun startTransition(duration: Long = 100) {
        val transition = androidx.transition.TransitionSet()
            .addTransition(androidx.transition.ChangeBounds())
            .addTransition(androidx.transition.Fade())
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
        startTransition()
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
        startTransition()
        setMiddleTrackView(binding.searchEmptyView.id)
        binding.searchProgress.isVisible = false
        binding.trackSearchRecycler.isVisible = false
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
                activity.materialAlertDialog()
                    .setTitle(R.string.remove_previous_tracker)
                    .setItems(arrayOf(wordToSpan, text2)) { dialog, i ->
                        if (i == 0) {
                            removeTracker(searchingItem, true)
                        }
                        refreshTrack(searchingItem.service)
                        presenter.registerTracking(selectedItem, searchingItem.service)
                        hideSearchView()
                        dialog.dismiss()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
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

        val statusList = item.service.getStatusList()
        val statusString = statusList.map { item.service.getStatus(it) }
        val selectedIndex = statusList.indexOf(item.track.status)

        activity.materialAlertDialog()
            .setTitle(R.string.status)
            .setNegativeButton(android.R.string.cancel, null)
            .setSingleChoiceItems(
                statusString.toTypedArray(),
                selectedIndex
            ) { dialog, itemPosition ->
                setStatus(item, itemPosition)
                dialog.dismiss()
            }
            .show()
    }

    override fun onUpdateChaptersClick(position: Int) {
        val item = adapter?.getItem(position) ?: return
        item.track ?: return

        activity.materialAlertDialog()
            .setMessage(R.string.update_chapters_progress_with_tracker)
            .setPositiveButton(R.string.update) { _, _ ->
                presenter.updateChaptersProgress(item.track.last_chapter_read)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onRemoveClick(position: Int) {
        val item = adapter?.getItem(position) ?: return
        if (item.track == null) return

        val dialog = activity.materialAlertDialog()
            .setTitle(R.string.remove_tracking)
            .setNegativeButton(android.R.string.cancel, null)

        if (item.service.canRemoveFromService()) {
            val serviceName = activity.getString(item.service.nameRes())
            if (!activity.isOnline()) {
                dialog.setMessage(
                    activity.getString(
                        R.string.cannot_remove_tracking_while_offline,
                        serviceName
                    )
                )
                    .setPositiveButton(R.string.remove) { _, _ ->
                        removeTracker(item, false)
                    }
            } else {
                dialog.addCheckBoxPrompt(
                    activity.getString(R.string.remove_tracking_from_, serviceName),
                    true
                )
                    .setPositiveButton(R.string.remove) { dialogI, _ ->
                        removeTracker(item, dialogI.isPromptChecked)
                    }
            }
        } else {
            dialog.setPositiveButton(R.string.remove) { _, _ ->
                removeTracker(item, false)
            }
        }
        dialog.show()
    }

    override fun onChaptersClick(position: Int) {
        val item = adapter?.getItem(position) ?: return
        if (item.track == null) return
        if (controller.isNotOnline()) {
            dismiss()
            return
        }

        val binding = TrackChaptersDialogBinding.inflate(activity.layoutInflater)
        val dialog = activity.materialAlertDialog()
            .setTitle(R.string.chapters)
            .setView(binding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                // Remove focus to update selected number
                val np = binding.chaptersPicker
                np.clearFocus()
                setChaptersRead(item, np.value)
            }

        val np = binding.chaptersPicker
        // Set initial value
        np.value = item.track.last_chapter_read
        if (item.track.total_chapters > 0) {
            np.wrapSelectorWheel = true
            np.maxValue = item.track.total_chapters
        } else {
            // Don't allow to go from 0 to 9999
            np.wrapSelectorWheel = false
        }
        dialog.show()
    }

    override fun onScoreClick(position: Int) {
        val item = adapter?.getItem(position) ?: return
        if (item.track == null) return
        if (controller.isNotOnline()) {
            dismiss()
            return
        }

        val binding = TrackScoreDialogBinding.inflate(activity.layoutInflater)
        val dialog = activity.materialAlertDialog()
            .setTitle(R.string.score)
            .setView(binding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val np = binding.scorePicker
                np.clearFocus()

                setScore(item, np.value)
            }

        val np = binding.scorePicker
        val scores = item.service.getScoreList().toTypedArray()
        np.maxValue = scores.size - 1
        np.displayedValues = scores

        // Set initial value
        val displayedScore = item.service.displayScore(item.track)
        if (displayedScore != "-") {
            val index = scores.indexOf(displayedScore)
            np.value = if (index != -1) index else 0
        }

        dialog.show()
    }

    override fun onStartDateClick(view: View, position: Int) {
        val item = adapter?.getItem(position) ?: return
        if (item.track == null) return

        showMenuPicker(view, item, ReadingDate.Start, suggestedStartDate)
    }

    override fun onFinishDateClick(view: View, position: Int) {
        val item = adapter?.getItem(position) ?: return
        if (item.track == null) return

        showMenuPicker(view, item, ReadingDate.Finish, suggestedFinishDate)
    }

    private fun showMenuPicker(view: View, trackItem: TrackItem, readingDate: ReadingDate, suggestedDate: Long?) {
        val date = if (readingDate == ReadingDate.Start) {
            trackItem.track?.started_reading_date
        } else {
            trackItem.track?.finished_reading_date
        } ?: 0L
        if (date <= 0L) {
            showDatePicker(trackItem, readingDate, suggestedDate)
            return
        }
        val popup = PopupMenu(activity, view, Gravity.NO_GRAVITY)
        popup.menu.add(0, 0, 0, R.string.edit)
        getSuggestedDate(trackItem, readingDate, suggestedDate)?.let {
            val subMenu = popup.menu.addSubMenu(0, 1, 0, R.string.use_suggested_date)
            subMenu.add(0, 2, 0, it)
        }
        popup.menu.add(0, 3, 0, R.string.remove)

        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                0 -> showDatePicker(trackItem, readingDate, suggestedDate)
                2 -> setReadingDate(trackItem, readingDate, suggestedDate!!)
                3 -> setReadingDate(trackItem, readingDate, -1L)
            }
            true
        }

        popup.show()
    }

    enum class ReadingDate {
        Start,
        Finish
    }

    private fun showDatePicker(trackItem: TrackItem, readingDate: ReadingDate, suggestedDate: Long?) {
        val dialog = MaterialDatePicker.Builder.datePicker()
            .setTitleText(
                when (readingDate) {
                    ReadingDate.Start -> R.string.started_reading_date
                    ReadingDate.Finish -> R.string.finished_reading_date
                }
            )
            .setSelection(getCurrentDate(trackItem, readingDate, suggestedDate)?.timeInMillis).apply {
            }
            .build()

        dialog.addOnPositiveButtonClickListener { utcMillis ->
            val result = utcMillis.toLocalCalendar()?.timeInMillis
            if (result != null) {
                setReadingDate(trackItem, readingDate, result)
            }
        }
        dialog.show((activity as AppCompatActivity).supportFragmentManager, readingDate.toString())
    }

    private fun getSuggestedDate(trackItem: TrackItem, readingDate: ReadingDate, suggestedDate: Long?): String? {
        trackItem.track ?: return null
        val date = when (readingDate) {
            ReadingDate.Start -> trackItem.track.started_reading_date
            ReadingDate.Finish -> trackItem.track.finished_reading_date
        }
        if (date != 0L) {
            if (suggestedDate != null) {
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = date
                val suggestedCalendar = Calendar.getInstance()
                suggestedCalendar.timeInMillis = suggestedDate
                return if (date > suggestedDate &&
                    (
                        suggestedCalendar.get(Calendar.YEAR) != calendar.get(Calendar.YEAR) ||
                            suggestedCalendar.get(Calendar.MONTH) != calendar.get(Calendar.MONTH) ||
                            suggestedCalendar.get(Calendar.DAY_OF_MONTH) != calendar.get(Calendar.DAY_OF_MONTH)
                        )
                ) {
                    dateFormat.format(suggestedDate)
                } else {
                    null
                }
            }
        }
        suggestedDate?.let {
            return dateFormat.format(suggestedDate)
        }
        return null
    }

    private fun getCurrentDate(trackItem: TrackItem, readingDate: ReadingDate, suggestedDate: Long?): Calendar? {
        // Today if no date is set, otherwise the already set date
        return Calendar.getInstance().apply {
            suggestedDate?.let {
                timeInMillis = it
            }
            trackItem.track?.let {
                val date = when (readingDate) {
                    ReadingDate.Start -> it.started_reading_date
                    ReadingDate.Finish -> it.finished_reading_date
                }
                if (date != 0L) {
                    timeInMillis = date
                }
            }
        }.timeInMillis.toUtcCalendar()
    }

    fun setStatus(item: TrackItem, selection: Int) {
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

    fun setScore(item: TrackItem, score: Int) {
        presenter.setScore(item, score)
        refreshItem(item)
    }

    private fun setChaptersRead(item: TrackItem, chaptersRead: Int) {
        presenter.setLastChapterRead(item, chaptersRead)
        refreshItem(item)
    }

    private fun removeTracker(item: TrackItem, fromServiceAlso: Boolean) {
        refreshTrack(item.service)
        presenter.removeTracker(item, fromServiceAlso)
    }

    private fun setReadingDate(item: TrackItem, type: ReadingDate, date: Long) {
        refreshTrack(item.service)
        when (type) {
            ReadingDate.Start -> controller.presenter.setTrackerStartDate(item, date)
            ReadingDate.Finish -> controller.presenter.setTrackerFinishDate(item, date)
        }
    }
}
