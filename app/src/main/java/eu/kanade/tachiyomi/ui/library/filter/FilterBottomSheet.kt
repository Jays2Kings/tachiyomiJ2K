package eu.kanade.tachiyomi.ui.library.filter

import android.content.Context
import android.content.res.Configuration
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.ui.library.LibraryController
import eu.kanade.tachiyomi.ui.library.LibraryGroup
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.view.collapse
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.hide
import eu.kanade.tachiyomi.util.view.inflate
import eu.kanade.tachiyomi.util.view.isExpanded
import eu.kanade.tachiyomi.util.view.isHidden
import eu.kanade.tachiyomi.util.view.updatePaddingRelative
import eu.kanade.tachiyomi.util.view.visibleIf
import kotlinx.android.synthetic.main.filter_bottom_sheet.view.*
import kotlinx.android.synthetic.main.library_grid_recycler.*
import kotlinx.android.synthetic.main.library_list_controller.*
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class FilterBottomSheet @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs),
    FilterTagGroupListener {

    /**
     * Preferences helper.
     */
    private val preferences: PreferencesHelper by injectLazy()

    private val trackManager: TrackManager by injectLazy()

    private val hasTracking
        get() = trackManager.hasLoggedServices()

    private lateinit var downloaded: FilterTagGroup

    private lateinit var unreadProgress: FilterTagGroup

    private lateinit var unread: FilterTagGroup

    private lateinit var completed: FilterTagGroup

    private var tracked: FilterTagGroup? = null

    private var trackers: FilterTagGroup? = null

    private var mangaType: FilterTagGroup? = null

    var sheetBehavior: BottomSheetBehavior<View>? = null

    var filterOrder = preferences.filterOrder().get()

    private lateinit var clearButton: ImageView

    private val filterItems: MutableList<FilterTagGroup> by lazy {
        val list = mutableListOf<FilterTagGroup>()
        list.add(unreadProgress)
        list.add(unread)
        list.add(downloaded)
        list.add(completed)
        if (hasTracking)
            tracked?.let { list.add(it) }
        list
    }

    var onGroupClicked: (Int) -> Unit = { _ -> }
    var pager: View? = null
    var controller: LibraryController? = null

    fun onCreate(controller: LibraryController) {
        clearButton = clear_button
        filter_layout.removeView(clearButton)
        sheetBehavior = BottomSheetBehavior.from(this)
        sheetBehavior?.isHideable = true
        this.controller = controller
        pager = controller.recycler
        val shadow2: View = controller.shadow2
        val shadow: View = controller.shadow
        sheetBehavior?.addBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onSlide(bottomSheet: View, progress: Float) {
                    pill.alpha = (1 - max(0f, progress)) * 0.25f
                    shadow2.alpha = (1 - max(0f, progress)) * 0.25f
                    shadow.alpha = 1 + min(0f, progress)
                    updateRootPadding(progress)
                }

                override fun onStateChanged(p0: View, state: Int) {
                    stateChanged(state)
                }
            }
        )

        if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            second_layout.removeView(view_options)
            second_layout.removeView(reorder_filters)
            first_layout.addView(reorder_filters)
            first_layout.addView(view_options)
            second_layout.gone()
        }

        if (preferences.hideFiltersAtStart().getOrDefault()) {
            sheetBehavior?.hide()
        }
        expand_categories.setOnClickListener {
            onGroupClicked(ACTION_EXPAND_COLLAPSE_ALL)
        }
        group_by.setOnClickListener {
            onGroupClicked(ACTION_GROUP_BY)
        }
        view_options.setOnClickListener {
            onGroupClicked(ACTION_DISPLAY)
        }
        reorder_filters.setOnClickListener {
            manageFilterPopup()
        }

        val activeFilters = hasActiveFiltersFromPref()
        if (activeFilters && sheetBehavior.isHidden() && sheetBehavior?.skipCollapsed == false)
            sheetBehavior?.collapse()

        post {
            updateRootPadding(
                when (sheetBehavior?.state) {
                    BottomSheetBehavior.STATE_HIDDEN -> -1f
                    BottomSheetBehavior.STATE_EXPANDED -> 1f
                    else -> 0f
                }
            )
            shadow.alpha = if (sheetBehavior.isHidden()) 0f else 1f
        }

        createTags()
        clearButton.setOnClickListener { clearFilters() }
    }

    fun setExpandText(expand: Boolean) {
        expand_categories.setText(
            if (expand) {
                R.string.expand_all_categories
            } else {
                R.string.collapse_all_categories
            }
        )
        expand_categories.setIconResource(
            if (expand) {
                R.drawable.ic_expand_less_24dp
            } else {
                R.drawable.ic_expand_more_24dp
            }
        )
    }

    private fun stateChanged(state: Int) {
        val shadow = controller?.shadow ?: return
        if (state == BottomSheetBehavior.STATE_COLLAPSED) {
            shadow.alpha = 1f
            pager?.updatePaddingRelative(bottom = sheetBehavior?.peekHeight ?: 0 + 10.dpToPx)
        }
        if (state == BottomSheetBehavior.STATE_EXPANDED) {
            pill.alpha = 0f
        }
        if (state == BottomSheetBehavior.STATE_HIDDEN) {
            onGroupClicked(ACTION_HIDE_FILTER_TIP)
            reSortViews()
            shadow.alpha = 0f
            pager?.updatePaddingRelative(bottom = 10.dpToPx)
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        super.onRestoreInstanceState(state)
        val sheetBehavior = BottomSheetBehavior.from(this)
        stateChanged(sheetBehavior.state)
    }

    fun updateRootPadding(progress: Float? = null) {
        val minHeight = sheetBehavior?.peekHeight ?: 0
        val maxHeight = height
        val trueProgress = progress
            ?: if (sheetBehavior.isExpanded()) 1f else 0f
        val percent = (trueProgress * 100).roundToInt()
        val value = (percent * (maxHeight - minHeight) / 100) + minHeight
        if (trueProgress >= 0)
            pager?.updatePaddingRelative(bottom = value + 10.dpToPx)
        else
            pager?.updatePaddingRelative(bottom = (minHeight * (1 + trueProgress)).toInt())
    }

    fun hasActiveFilters() = filterItems.any { it.isActivated }

    private fun hasActiveFiltersFromPref(): Boolean {
        return preferences.filterDownloaded().getOrDefault() > 0 || preferences.filterUnread()
            .getOrDefault() > 0 || preferences.filterCompleted()
            .getOrDefault() > 0 || preferences.filterTracked()
            .getOrDefault() > 0 || preferences.filterMangaType()
            .getOrDefault() > 0 || FILTER_TRACKER.isNotEmpty()
    }

    private fun createTags() {

        downloaded = inflate(R.layout.filter_buttons) as FilterTagGroup
        downloaded.setup(this, R.string.downloaded, R.string.not_downloaded)

        completed = inflate(R.layout.filter_buttons) as FilterTagGroup
        completed.setup(this, R.string.completed, R.string.ongoing)

        unreadProgress = inflate(R.layout.filter_buttons) as FilterTagGroup
        unreadProgress.setup(this, R.string.not_started, R.string.in_progress)

        unread = inflate(R.layout.filter_buttons) as FilterTagGroup
        unread.setup(this, R.string.unread, R.string.read)

        if (hasTracking) {
            tracked = inflate(R.layout.filter_buttons) as FilterTagGroup
            tracked?.setup(this, R.string.tracked, R.string.not_tracked)
        }

        reSortViews()

        checkForManhwa()
    }

    private fun checkForManhwa() {
        GlobalScope.launch(Dispatchers.IO, CoroutineStart.DEFAULT) {
            val db: DatabaseHelper by injectLazy()
            val showCategoriesCheckBox = withContext(Dispatchers.IO) {
                db.getCategories().executeAsBlocking()
                    .isNotEmpty()
            }
            val libraryManga = db.getLibraryMangas().executeAsBlocking()
            val types = mutableListOf<Int>()
            if (libraryManga.any { it.mangaType() == Manga.TYPE_MANHWA }) types.add(R.string.manhwa)
            if (libraryManga.any { it.mangaType() == Manga.TYPE_MANHUA }) types.add(R.string.manhua)
            if (libraryManga.any { it.mangaType() == Manga.TYPE_COMIC }) types.add(R.string.comic)
            if (types.isNotEmpty()) {
                launchUI {
                    val mangaType = inflate(R.layout.filter_buttons) as FilterTagGroup
                    mangaType.setup(
                        this@FilterBottomSheet,
                        R.string.manga,
                        types.first(),
                        types.getOrNull(1),
                        types.getOrNull(2)
                    )
                    this@FilterBottomSheet.mangaType = mangaType
                    reorderFilters()
                    reSortViews()
                }
            }
            withContext(Dispatchers.Main) {
                downloaded.setState(preferences.filterDownloaded())
                completed.setState(preferences.filterCompleted())
                val unreadP = preferences.filterUnread().getOrDefault()
                if (unreadP <= 2) {
                    unread.state = unreadP - 1
                } else if (unreadP > 3) {
                    unreadProgress.state = unreadP - 3
                }
                tracked?.setState(preferences.filterTracked())
                mangaType?.setState(
                    when (preferences.filterMangaType().getOrDefault()) {
                        Manga.TYPE_MANGA -> context.getString(R.string.manga)
                        Manga.TYPE_MANHUA -> context.getString(R.string.manhua)
                        Manga.TYPE_MANHWA -> context.getString(R.string.manhwa)
                        Manga.TYPE_COMIC -> context.getString(R.string.comic)
                        else -> ""
                    }
                )
                reorderFilters()
                reSortViews()
            }

            if (filterItems.contains(tracked)) {
                val loggedServices = Injekt.get<TrackManager>().services.filter { it.isLogged }
                if (loggedServices.size > 1) {
                    val serviceNames = loggedServices.map { it.name }
                    withContext(Dispatchers.Main) {
                        trackers = inflate(R.layout.filter_buttons) as FilterTagGroup
                        trackers?.setup(
                            this@FilterBottomSheet,
                            serviceNames.first(),
                            serviceNames.getOrNull(1),
                            serviceNames.getOrNull(2)
                        )
                        if (tracked?.isActivated == true) {
                            filter_layout.addView(trackers)
                            filterItems.add(trackers!!)
                            trackers?.setState(FILTER_TRACKER)
                            reSortViews()
                        }
                    }
                }
            }
        }
    }

    private fun reorderFilters() {
        val array = filterOrder.toCharArray().distinct()
        filterItems.clear()
        for (c in array) {
            mapOfFilters(c)?.let {
                filterItems.add(it)
            }
        }
        listOfNotNull(unreadProgress, unread, downloaded, completed, mangaType, tracked)
            .forEach {
                if (!filterItems.contains(it)) {
                    filterItems.add(it)
                }
            }
    }
    private fun indexOf(filterTagGroup: FilterTagGroup): Int {
        charOfFilter(filterTagGroup)?.let {
            return filterOrder.indexOf(it)
        }
        return 0
    }

    private fun addForClear(): Int {
        return if (clearButton.parent != null) 1 else 0
    }

    private fun charOfFilter(filterTagGroup: FilterTagGroup): Char? {
        return when (filterTagGroup) {
            unreadProgress -> 'u'
            unread -> 'r'
            downloaded -> 'd'
            completed -> 'c'
            mangaType -> 'm'
            tracked -> 't'
            else -> null
        }
    }

    fun manageFilterPopup() {
        val recycler = RecyclerView(context)
        if (filterOrder.count() != 6)
            filterOrder = "urdcmt"
        val adapter = FlexibleAdapter(
            filterOrder.toCharArray().map(::ManageFilterItem),
            this,
            true
        )
        recycler.layoutManager = LinearLayoutManager(context)
        recycler.adapter = adapter
        adapter.isHandleDragEnabled = true
        adapter.isLongPressDragEnabled = true
        MaterialDialog(context).title(R.string.reorder_filters)
            .customView(view = recycler, scrollable = false)
            .negativeButton(android.R.string.cancel)
            .positiveButton(android.R.string.ok) {
                val order = adapter.currentItems.map { it.char }.joinToString("")
                preferences.filterOrder().set(order)
                filterOrder = order
                clearFilters()
                recycler.adapter = null
            }
            .show()
    }

    private fun mapOfFilters(char: Char): FilterTagGroup? {
        return when (char) {
            'u' -> unreadProgress
            'r' -> unread
            'd' -> downloaded
            'c' -> completed
            'm' -> mangaType
            't' -> if (hasTracking) tracked else null
            else -> null
        }
    }

    override fun onFilterClicked(view: FilterTagGroup, index: Int, updatePreference: Boolean) {
        if (updatePreference) {
            when (view) {
                trackers -> {
                    FILTER_TRACKER = view.nameOf(index) ?: ""
                    null
                }
                unreadProgress -> {
                    unread.reset()
                    preferences.filterUnread().set(
                        when (index) {
                            in 0..1 -> index + 3
                            else -> 0
                        }
                    )
                    null
                }
                unread -> {
                    unreadProgress.reset()
                    preferences.filterUnread()
                }
                downloaded -> preferences.filterDownloaded()
                completed -> preferences.filterCompleted()
                tracked -> preferences.filterTracked()
                mangaType -> {
                    val newIndex = when (view.nameOf(index)) {
                        context.getString(R.string.manga) -> Manga.TYPE_MANGA
                        context.getString(R.string.manhua) -> Manga.TYPE_MANHUA
                        context.getString(R.string.manhwa) -> Manga.TYPE_MANHWA
                        context.getString(R.string.comic) -> Manga.TYPE_COMIC
                        else -> 0
                    }
                    preferences.filterMangaType().set(newIndex)
                    null
                }
                else -> null
            }?.set(index + 1)
            onGroupClicked(ACTION_FILTER)
        }
        val hasFilters = hasActiveFilters()
        if (hasFilters && clearButton.parent == null) {
            filter_layout.addView(clearButton, 0)
        } else if (!hasFilters && clearButton.parent != null) {
            filter_layout.removeView(clearButton)
        }
        if (tracked?.isActivated == true && trackers != null && trackers?.parent == null) {
            filter_layout.addView(trackers, filterItems.indexOf(tracked!!) + 2)
            filterItems.add(filterItems.indexOf(tracked!!) + 1, trackers!!)
        } else if (tracked?.isActivated == false && trackers?.parent != null) {
            filter_layout.removeView(trackers)
            trackers?.reset()
            FILTER_TRACKER = ""
            filterItems.remove(trackers!!)
        }
    }

    fun updateButtons(showExpand: Boolean, groupType: Int) {
        expand_categories.visibleIf(showExpand && groupType == 0)
        group_by.setIconResource(LibraryGroup.groupTypeDrawableRes(groupType))
    }

    private fun clearFilters() {
        preferences.filterDownloaded().set(0)
        preferences.filterUnread().set(0)
        preferences.filterCompleted().set(0)
        preferences.filterTracked().set(0)
        preferences.filterMangaType().set(0)
        FILTER_TRACKER = ""

        val transition = androidx.transition.AutoTransition()
        transition.duration = 150
        androidx.transition.TransitionManager.beginDelayedTransition(filter_layout, transition)
        reorderFilters()
        filterItems.forEach {
            it.reset()
        }
        trackers?.let {
            filterItems.remove(it)
        }
        reSortViews()
        onGroupClicked(ACTION_FILTER)
    }

    private fun reSortViews() {
        filter_layout.removeAllViews()
        if (filterItems.any { it.isActivated })
            filter_layout.addView(clearButton)
        filterItems.filter { it.isActivated }.forEach {
            filter_layout.addView(it)
        }
        filterItems.filterNot { it.isActivated }.forEach {
            filter_layout.addView(it)
        }
        filter_scroll.scrollTo(0, 0)
    }

    companion object {
        const val ACTION_REFRESH = 0
        const val ACTION_FILTER = 1
        const val ACTION_HIDE_FILTER_TIP = 2
        const val ACTION_DISPLAY = 3
        const val ACTION_EXPAND_COLLAPSE_ALL = 4
        const val ACTION_GROUP_BY = 5

        const val STATE_IGNORE = 0
        const val STATE_INCLUDE = 1
        const val STATE_EXCLUDE = 2

        var FILTER_TRACKER = ""
            private set
    }
}
