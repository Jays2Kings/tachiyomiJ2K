package eu.kanade.tachiyomi.ui.library.filter

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.view.collapse
import eu.kanade.tachiyomi.util.view.hide
import eu.kanade.tachiyomi.util.view.inflate
import eu.kanade.tachiyomi.util.view.isExpanded
import eu.kanade.tachiyomi.util.view.isHidden
import eu.kanade.tachiyomi.util.view.updatePaddingRelative
import eu.kanade.tachiyomi.util.view.visibleIf
import kotlinx.android.synthetic.main.filter_bottom_sheet.view.*
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

    private lateinit var downloaded: FilterTagGroup

    private lateinit var unread: FilterTagGroup

    private lateinit var allUnread: FilterTagGroup

    private lateinit var completed: FilterTagGroup

    private lateinit var tracked: FilterTagGroup

    // TODO import these from track schema?
    private var trackedStatus: MutableMap<String, FilterTagGroup?> = mutableMapOf(
            "reading" to null,
            "completed" to null,
            "paused" to null,
            "dropped" to null,
            "plan_to_read" to null,
            "rereading" to null
    )

    private var trackers: FilterTagGroup? = null

    private var mangaType: FilterTagGroup? = null

    var sheetBehavior: BottomSheetBehavior<View>? = null

    private lateinit var clearButton: ImageView

    private val filterItems: MutableList<FilterTagGroup> by lazy {
        val list = mutableListOf<FilterTagGroup>()
        list.add(unread)
        list.add(downloaded)
        list.add(completed)
        if (Injekt.get<TrackManager>().hasLoggedServices())
            list.add(tracked)
        list
    }

    var onGroupClicked: (Int) -> Unit = { _ -> }
    var pager: View? = null

    fun onCreate(pagerView: View) {
        clearButton = clear_button
        filter_layout.removeView(clearButton)
        sheetBehavior = BottomSheetBehavior.from(this)
        sheetBehavior?.isHideable = true
        pager = pagerView
        val shadow2: View = (pagerView.parent.parent as ViewGroup).findViewById(R.id.shadow2)
        val shadow: View = (pagerView.parent.parent as ViewGroup).findViewById(R.id.shadow)
        sheetBehavior?.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, progress: Float) {
                pill.alpha = (1 - max(0f, progress)) * 0.25f
                shadow2.alpha = (1 - max(0f, progress)) * 0.25f
                shadow.alpha = 1 + min(0f, progress)
                updateRootPadding(progress)
            }

            override fun onStateChanged(p0: View, state: Int) {
                stateChanged(state)
            }
        })
        if (preferences.hideFiltersAtStart().getOrDefault()) {
            sheetBehavior?.hide()
        }
        hide_filters.isChecked = preferences.hideFiltersAtStart().getOrDefault()
        hide_filters.setOnCheckedChangeListener { _, isChecked ->
            preferences.hideFiltersAtStart().set(isChecked)
            if (isChecked)
                onGroupClicked(ACTION_HIDE_FILTER_TIP)
        }
        view_options.setOnClickListener {
            onGroupClicked(ACTION_DISPLAY)
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

    private fun stateChanged(state: Int) {
        val shadow = ((pager?.parent as? ViewGroup)?.findViewById(R.id.shadow) as? View)
        if (state == BottomSheetBehavior.STATE_COLLAPSED) {
            shadow?.alpha = 1f
            pager?.updatePaddingRelative(bottom = sheetBehavior?.peekHeight ?: 0)
        }
        if (state == BottomSheetBehavior.STATE_EXPANDED) {
            pill.alpha = 0f
        }
        if (state == BottomSheetBehavior.STATE_HIDDEN) {
            onGroupClicked(ACTION_HIDE_FILTER_TIP)
            reSortViews()
            shadow?.alpha = 0f
            pager?.updatePaddingRelative(bottom = 0)
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
        val height = context.resources.getDimensionPixelSize(R.dimen.rounder_radius)
        if (trueProgress >= 0)
            pager?.updatePaddingRelative(bottom = value - height)
        else
            pager?.updatePaddingRelative(bottom = (minHeight * (1 + trueProgress)).toInt())
    }

    fun hasActiveFilters() = filterItems.any { it.isActivated }

    private fun hasActiveFiltersFromPref(): Boolean {
        return preferences.filterDownloaded().getOrDefault() > 0
                || preferences.filterUnread().getOrDefault() > 0
                || preferences.filterCompleted().getOrDefault() > 0
                || preferences.filterTracked().getOrDefault() > 0
                || trackedStatus.any { preferences.filterTrackedStatus(it.key).getOrDefault() > 0 }
                || preferences.filterMangaType().getOrDefault() > 0
                || FILTER_TRACKER.isNotEmpty()
    }

    private fun createTags() {
        hide_categories.isChecked = preferences.hideCategories().getOrDefault()
        hide_categories.setOnCheckedChangeListener { _, isChecked ->
            preferences.hideCategories().set(isChecked)
            onGroupClicked(ACTION_REFRESH)
        }

        downloaded = inflate(R.layout.filter_buttons) as FilterTagGroup
        downloaded.setup(this, R.string.downloaded, R.string.not_downloaded)

        completed = inflate(R.layout.filter_buttons) as FilterTagGroup
        completed.setup(this, R.string.completed, R.string.ongoing)

        unread = inflate(R.layout.filter_buttons) as FilterTagGroup
        unread.setup(this, R.string.not_started, R.string.in_progress, R.string.read)

        allUnread = inflate(R.layout.filter_buttons) as FilterTagGroup
        allUnread.setup(this, R.string.unread)

        tracked = inflate(R.layout.filter_buttons) as FilterTagGroup
        tracked.setup(this, R.string.tracked, R.string.not_tracked)

        trackedStatus.forEach {
            trackedStatus[it.key] = inflate(R.layout.filter_buttons) as FilterTagGroup
            trackedStatus[it.key]!!.setup(this, this.context.resources.getIdentifier(
                    it.key,
                    "string",
                    this.context.packageName
            ))
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
            val hasTracking = Injekt.get<TrackManager>().hasLoggedServices()
            if (types.isNotEmpty()) {
                launchUI {
                    val mangaType = inflate(R.layout.filter_buttons) as FilterTagGroup
                    mangaType.setup(
                            this@FilterBottomSheet,
                            types.first(),
                            types.getOrNull(1),
                            types.getOrNull(2)
                    )
                    this@FilterBottomSheet.mangaType = mangaType
                    filter_layout.addView(mangaType)
                    filterItems.remove(tracked)
                    filterItems.add(mangaType)
                    if (hasTracking)
                        filterItems.add(tracked)
                }
            }
            withContext(Dispatchers.Main) {
                hide_categories.visibleIf(showCategoriesCheckBox)
                downloaded.setState(preferences.filterDownloaded())
                completed.setState(preferences.filterCompleted())
                val unreadP = preferences.filterUnread().getOrDefault()
                if (unreadP == STATE_INCLUDE) {
                    allUnread.state = 0
                    if (!filterItems.contains(allUnread))
                        filterItems.add(allUnread)
                } else if (unreadP > 0) {
                    unread.state = if (unreadP in 3..4) unreadP - 3 else 2
                }
                tracked.setState(preferences.filterTracked())
                val trackedP = preferences.filterTracked().getOrDefault()
                if (trackedP == STATE_INCLUDE) {
                    trackedStatus.forEach {
                        if (it.value == null) return@forEach
                        it.value!!.state = 0
                        if (!filterItems.contains(it.value!!))
                            filterItems.add(it.value!!)
                    }
                }
                mangaType?.setState(preferences.filterMangaType())
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
                        if (tracked.isActivated) {
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

    override fun onFilterClicked(view: FilterTagGroup, index: Int, updatePreference: Boolean) {
        if (updatePreference) {
            when (view) {
                trackers -> {
                    FILTER_TRACKER = view.nameOf(index) ?: ""
                    null
                }
                unread -> {
                    preferences.filterUnread().set(
                            when (index) {
                                in 0..1 -> index + 3
                                2 -> 2
                                else -> 0
                            }
                    )
                    null
                }
                allUnread -> {
                    preferences.filterUnread().set(index + 1)
                    if (index != 0) unread.reset()
                    null
                }
                downloaded -> preferences.filterDownloaded()
                completed -> preferences.filterCompleted()
                tracked -> preferences.filterTracked()
                mangaType -> preferences.filterMangaType()
                else -> {
                    val trackedStatusHasView = trackedStatus.any { it.value == view }
                    if (trackedStatusHasView) {
                        val status = trackedStatus.entries.find { it.value == view }!!.key
                        preferences.filterTrackedStatus(status)
                    } else null
                }
            }?.set(index + 1)
            onGroupClicked(ACTION_FILTER)
        }
        if (allUnread.state == 0 && unread.parent != null) {
            filter_layout.removeView(unread)
            filterItems.remove(unread)
        } else if (allUnread.state != 0 && unread.parent == null) {
            filter_layout.addView(unread, 0)
            filterItems.add(0, unread)
            filter_layout.removeView(allUnread)
            filterItems.remove(allUnread)
        } else if (unread.state in 0..1 && allUnread.parent == null) {
            val unreadIndex = filter_layout.indexOfChild(unread) + 1
            filter_layout.addView(allUnread, unreadIndex)
            filterItems.add(unreadIndex, allUnread)
        } else if (unread.state != 0 && allUnread.parent != null) {
            filter_layout.removeView(allUnread)
            allUnread.reset()
            filterItems.remove(allUnread)
        }
        if (tracked.isActivated && trackers != null && trackers?.parent == null) {
            filter_layout.addView(trackers)
            filterItems.add(trackers!!)
        } else if (!tracked.isActivated && trackers?.parent != null) {
            filter_layout.removeView(trackers)
            trackers?.reset()
            FILTER_TRACKER = ""
            filterItems.remove(trackers!!)
        }
        if (tracked.state == 0) {
            val trackedIndex = filter_layout.indexOfChild(tracked)
            trackedStatus.values.forEachIndexed { i, it ->
                if (it != null && it.parent == null) {
                    filter_layout.addView(it, trackedIndex + i + 1)
                    filterItems.add(trackedIndex + i + 1, it)
                }
            }
        } else if (tracked.state != 0) {
            trackedStatus.values.forEach {
                if (it != null && it.parent != null) {
                    filter_layout.removeView(it)
                    filterItems.remove(it)
                    it.reset()
                }
            }
        }
        val hasFilters = hasActiveFilters()
        if (hasFilters && clearButton.parent == null) {
            filter_layout.addView(clearButton, 0)
        } else if (!hasFilters && clearButton.parent != null) {
            filter_layout.removeView(clearButton)
        }
    }

    private fun clearFilters() {
        preferences.filterDownloaded().set(0)
        preferences.filterUnread().set(0)
        preferences.filterCompleted().set(0)
        preferences.filterTracked().set(0)
        trackedStatus.forEach { preferences.filterTrackedStatus(it.key) }
        preferences.filterMangaType().set(0)
        FILTER_TRACKER = ""

        val transition = androidx.transition.AutoTransition()
        transition.duration = 150
        androidx.transition.TransitionManager.beginDelayedTransition(filter_layout, transition)
        if (!filterItems.contains(unread)) {
            filterItems.add(0, unread)
        }
        filterItems.forEach {
            it.reset()
        }
        trackers?.let {
            filterItems.remove(it)
        }
        filterItems.remove(allUnread)
        trackedStatus.forEach { filterItems.remove(it.value) }
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

        const val STATE_IGNORE = 0
        const val STATE_INCLUDE = 1
        const val STATE_EXCLUDE = 2

        var FILTER_TRACKER = ""
            private set
    }
}
