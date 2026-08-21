package eu.kanade.tachiyomi.ui.library

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewPropertyAnimator
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.animation.doOnEnd
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.ime
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.marginTop
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.view.updatePaddingRelative
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.transition.ChangeBounds
import androidx.transition.Fade
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.fredporciuncula.flow.preferences.Preference
import com.github.florent37.viewtooltip.ViewTooltip
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.SelectableAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.flexibleadapter.items.IHeader
import eu.davidea.flexibleadapter.items.ISectionable
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadJob
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.LibraryControllerBinding
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.ui.base.MaterialMenuSheet
import eu.kanade.tachiyomi.ui.base.MiniSearchView
import eu.kanade.tachiyomi.ui.base.controller.BaseCoroutineController
import eu.kanade.tachiyomi.ui.category.CategoryController
import eu.kanade.tachiyomi.ui.category.ManageCategoryDialog
import eu.kanade.tachiyomi.ui.library.LibraryGroup.BY_AUTHOR
import eu.kanade.tachiyomi.ui.library.LibraryGroup.BY_DEFAULT
import eu.kanade.tachiyomi.ui.library.LibraryGroup.BY_LANGUAGE
import eu.kanade.tachiyomi.ui.library.LibraryGroup.BY_SOURCE
import eu.kanade.tachiyomi.ui.library.LibraryGroup.BY_STATUS
import eu.kanade.tachiyomi.ui.library.LibraryGroup.BY_TAG
import eu.kanade.tachiyomi.ui.library.LibraryGroup.BY_TRACK_STATUS
import eu.kanade.tachiyomi.ui.library.LibraryGroup.UNGROUPED
import eu.kanade.tachiyomi.ui.library.display.TabbedLibraryDisplaySheet
import eu.kanade.tachiyomi.ui.library.filter.FilterBottomSheet
import eu.kanade.tachiyomi.ui.library.search.compose.LibrarySearchFieldOption
import eu.kanade.tachiyomi.ui.library.search.compose.LibrarySearchFilterSheetContent
import eu.kanade.tachiyomi.ui.library.search.compose.SearchCombinator
import eu.kanade.tachiyomi.ui.main.BottomSheetController
import eu.kanade.tachiyomi.ui.main.FloatingSearchInterface
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.main.RootSearchInterface
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.ui.migration.manga.design.PreMigrationController
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.source.globalsearch.GlobalSearchController
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.moveCategories
import eu.kanade.tachiyomi.util.system.disableItems
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.getResourceDrawable
import eu.kanade.tachiyomi.util.system.ignoredSystemInsets
import eu.kanade.tachiyomi.util.system.isImeVisible
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.rootWindowInsetsCompat
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.collapse
import eu.kanade.tachiyomi.util.view.expand
import eu.kanade.tachiyomi.util.view.fullAppBarHeight
import eu.kanade.tachiyomi.util.view.getItemView
import eu.kanade.tachiyomi.util.view.hide
import eu.kanade.tachiyomi.util.view.isControllerVisible
import eu.kanade.tachiyomi.util.view.isExpanded
import eu.kanade.tachiyomi.util.view.isHidden
import eu.kanade.tachiyomi.util.view.isSettling
import eu.kanade.tachiyomi.util.view.scrollViewWith
import eu.kanade.tachiyomi.util.view.setOnQueryTextChangeListener
import eu.kanade.tachiyomi.util.view.setStyle
import eu.kanade.tachiyomi.util.view.smoothScrollToTop
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import eu.kanade.tachiyomi.widget.ComposeAnchoredDialog
import eu.kanade.tachiyomi.widget.EmptyView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.time.Duration.Companion.milliseconds

open class LibraryController(
    bundle: Bundle? = null,
    val preferences: PreferencesHelper = Injekt.get(),
) : BaseCoroutineController<LibraryControllerBinding, LibraryPresenter>(bundle),
    ActionMode.Callback,
    FlexibleAdapter.OnItemClickListener,
    FlexibleAdapter.OnItemLongClickListener,
    FlexibleAdapter.OnItemMoveListener,
    LibraryCategoryAdapter.LibraryListener,
    BottomSheetController,
    RootSearchInterface,
    FloatingSearchInterface {
    init {
        setHasOptionsMenu(true)
        retainViewMode = RetainViewMode.RETAIN_DETACH
    }

    /**
     * Position of the active category.
     */
    private var activeCategory: Int = preferences.lastUsedCategory().get()
    private var lastUsedCategory: Int = preferences.lastUsedCategory().get()

    private var justStarted = true

    /**
     * Action mode for selections.
     */
    private var actionMode: ActionMode? = null

    private var libraryLayout: Int = preferences.libraryLayout().get()

    var singleCategory: Boolean = false
        private set
    var hopperAnimation: ValueAnimator? = null
    var catGestureDetector: GestureDetector? = null

    /**
     * Library search query.
     */
    private var query = ""

    val isSubClass: Boolean
        get() = this is FilteredLibraryController

    /**
     * Currently selected mangas.
     */
    private val selectedMangas = mutableSetOf<Manga>()

    private var mAdapter: LibraryCategoryAdapter? = null
    private val adapter: LibraryCategoryAdapter
        get() = mAdapter!!

    private var lastClickPosition = -1

    private var lastItemPosition: Int? = null
    private var lastItem: IFlexible<*>? = null

    override var presenter = LibraryPresenter()

    private var observeLater: Boolean = false
    var searchItem = SearchGlobalItem()

    var snack: Snackbar? = null
    var displaySheet: TabbedLibraryDisplaySheet? = null

    private var scrollDistance = 0f
    private val scrollDistanceTilHidden = 1000.dpToPx
    private var textAnim: ViewPropertyAnimator? = null
    private var hasExpanded = false

    val hasActiveFilters: Boolean
        get() = presenter.hasActiveFilters

    var hopperGravity: Int = preferences.hopperGravity().get()
        @SuppressLint("RtlHardcoded")
        set(value) {
            field = value
            binding.jumperCategoryText.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                anchorGravity =
                    when (value) {
                        0 -> Gravity.RIGHT or Gravity.CENTER_VERTICAL
                        2 -> Gravity.LEFT or Gravity.CENTER_VERTICAL
                        else -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    }
                gravity = anchorGravity
            }
        }

    private var filterTooltip: ViewTooltip? = null
    private var isAnimatingHopper: Boolean? = null
    private var animatorSet: AnimatorSet? = null
    var hasMovedHopper = preferences.shownHopperSwipeTutorial().get()
    private var shouldScrollToTop = false
    private val showCategoryInTitle
        get() = preferences.showCategoryInTitle().get() && presenter.showAllCategories
    private lateinit var elevateAppBar: ((Boolean) -> Unit)
    private var hopperOffset = 0f
    private val maxHopperOffset: Float
        get() =
            if (activityBinding?.bottomNav != null && !isSubClass) {
                55f.dpToPx
            } else {
                (
                    view
                        ?.rootWindowInsetsCompat
                        ?.getInsets(systemBars())
                        ?.bottom
                        ?.toFloat()
                        ?: 0f
                ) + 55f.dpToPx
            }

    override val mainRecycler: RecyclerView
        get() = binding.libraryGridRecycler.recycler
    private var staggeredBundle: Parcelable? = null
    private var staggeredObserver: ViewTreeObserver.OnGlobalLayoutListener? = null
    var isPoppingIn = false
    var tempItems: List<LibraryItem>? = null
    private var isFindingDownloads = false

    // Dynamically injected into the search bar, controls category visibility during search
    private var showAllCategoriesView: ImageView? = null

    // Range of the token last inserted by insertSearchToken() within the search box's text - if
    // the very next edit is a single backspace landing inside it, the whole token is removed
    // instead of leaving a malformed partial (e.g. `title:"` after only the opening quote gets
    // deleted). Cleared after any edit, matched or not - a one-shot "undo the insertion" affordance.
    private var pendingTokenRange: IntRange? = null
    private var tokenBackspaceWatcher: TextWatcher? = null

    private var filterJob: Job? = null

    override fun getTitle(): String? {
        setSubtitle()
        return view?.context?.getString(R.string.library)
    }

    override fun getSearchTitle(): String? {
        setSubtitle()
        return searchTitle(
            if (preferences.showLibrarySearchSuggestions().get() &&
                preferences.librarySearchSuggestion().get().isNotBlank()
            ) {
                "\"${preferences.librarySearchSuggestion().get()}\""
            } else {
                view?.context?.getString(R.string.your_library)?.lowercase(Locale.ROOT)
            },
        )
    }

    val cb =
        object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
            override fun onStart(
                animation: WindowInsetsAnimationCompat,
                bounds: WindowInsetsAnimationCompat.BoundsCompat,
            ): WindowInsetsAnimationCompat.BoundsCompat {
                hopperOffset = 0f
                updateHopperY()
                return bounds
            }

            override fun onProgress(
                insets: WindowInsetsCompat,
                runningAnimations: List<WindowInsetsAnimationCompat>,
            ): WindowInsetsCompat {
                updateHopperY(insets)
                return insets
            }

            override fun onEnd(animation: WindowInsetsAnimationCompat) {
                updateHopperY()
            }
        }

    private var scrollListener =
        object : RecyclerView.OnScrollListener() {
            override fun onScrolled(
                recyclerView: RecyclerView,
                dx: Int,
                dy: Int,
            ) {
                super.onScrolled(recyclerView, dx, dy)
                val recyclerCover = binding.recyclerCover
                if (!recyclerCover.isClickable && isAnimatingHopper != true) {
                    if (preferences.autohideHopper().get()) {
                        hopperOffset += dy
                        hopperOffset = hopperOffset.coerceIn(0f, maxHopperOffset)
                    }
                    if (!preferences.hideBottomNavOnScroll().get() ||
                        activityBinding?.bottomNav == null ||
                        isSubClass
                    ) {
                        updateFilterSheetY()
                    }
                    if (!binding.fastScroller.isFastScrolling) {
                        updateSmallerViewsTopMargins()
                    }
                    updateHopperAlpha()
                }
                if (!binding.filterBottomSheet.filterBottomSheet.sheetBehavior
                        .isHidden()
                ) {
                    scrollDistance += abs(dy)
                    if (scrollDistance > scrollDistanceTilHidden) {
                        binding.filterBottomSheet.filterBottomSheet.sheetBehavior
                            ?.hide()
                        scrollDistance = 0f
                    }
                } else {
                    scrollDistance = 0f
                }
                val currentCategory = getHeader()?.category ?: return
                if (currentCategory.order != activeCategory) {
                    saveActiveCategory(currentCategory)
                    if (!showCategoryInTitle && presenter.categories.size > 1 && dy != 0 && recyclerView.translationY == 0f) {
                        showCategoryText(currentCategory.name)
                    }
                }
                val savedCurrentCategory = getHeader(true)?.category ?: return
                if (savedCurrentCategory.order != lastUsedCategory) {
                    lastUsedCategory = savedCurrentCategory.order
                    if (!isSubClass) {
                        preferences.lastUsedCategory().set(savedCurrentCategory.order)
                    }
                }
            }

            override fun onScrollStateChanged(
                recyclerView: RecyclerView,
                newState: Int,
            ) {
                super.onScrollStateChanged(recyclerView, newState)
                when (newState) {
                    RecyclerView.SCROLL_STATE_DRAGGING -> {
                        binding.fastScroller.showScrollbar()
                    }
                    RecyclerView.SCROLL_STATE_IDLE -> {
                        updateHopperPosition()
                    }
                }
                if (newState != RecyclerView.SCROLL_STATE_IDLE) {
                    removeStaggeredObserver()
                }
            }
        }

    fun updateHopperAlpha() {
        binding.roundedCategoryHopper.upCategory.alpha = if (isAtTop()) 0.25f else 1f
        binding.roundedCategoryHopper.downCategory.alpha = if (isAtBottom()) 0.25f else 1f
    }

    private fun removeStaggeredObserver() {
        if (staggeredObserver != null) {
            binding.libraryGridRecycler.recycler.viewTreeObserver.removeOnGlobalLayoutListener(
                staggeredObserver,
            )
            staggeredObserver = null
        }
    }

    fun updateFilterSheetY() {
        val bottomBar = if (!isSubClass) activityBinding?.bottomNav else null
        val systemInsets = view?.rootWindowInsetsCompat?.getInsets(systemBars())
        val bottomSheet = binding.filterBottomSheet.filterBottomSheet
        if (bottomBar != null) {
            bottomSheet.translationY =
                if (bottomSheet.sheetBehavior.isHidden()) {
                    bottomBar.translationY - bottomBar.height
                } else {
                    0f
                }
            val pad = bottomBar.translationY - bottomBar.height
            val padding = max((-pad).toInt(), systemInsets?.bottom ?: 0)
            bottomSheet.updatePaddingRelative(bottom = padding)

            bottomSheet.sheetBehavior?.peekHeight = 60.dpToPx + padding
            updateHopperY()
            binding.fastScroller.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = -pad.toInt()
            }
        } else {
            bottomSheet.updatePaddingRelative(bottom = systemInsets?.bottom ?: 0)
            updateHopperY()
            bottomSheet.sheetBehavior?.peekHeight = 60.dpToPx + (systemInsets?.bottom ?: 0)
        }
    }

    fun updateHopperPosition() {
        val shortAnimationDuration =
            resources?.getInteger(
                android.R.integer.config_shortAnimTime,
            ) ?: 0
        if (preferences.autohideHopper().get()) {
            val bottomBar = if (isSubClass) null else activityBinding?.bottomNav
            // Flow same snap rules as bottom nav
            val closerToHopperBottom = hopperOffset > maxHopperOffset / 2
            val halfWayBottom = bottomBar?.height?.toFloat()?.div(2) ?: 0f
            val closerToBottom = (bottomBar?.translationY ?: 0f) > halfWayBottom
            val atTop = !binding.libraryGridRecycler.recycler.canScrollVertically(-1)
            val closerToEdge =
                if (preferences.hideBottomNavOnScroll().get() && bottomBar != null) {
                    closerToBottom && !atTop
                } else {
                    closerToHopperBottom
                }
            val end = if (closerToEdge) maxHopperOffset else 0f
            hopperAnimation?.cancel()
            val alphaAnimation = ValueAnimator.ofFloat(hopperOffset, end)
            alphaAnimation.addUpdateListener { valueAnimator ->
                hopperOffset = valueAnimator.animatedValue as Float
                updateHopperY()
            }
            alphaAnimation.doOnEnd {
                hopperOffset = end
                updateHopperY()
            }
            alphaAnimation.duration = shortAnimationDuration.toLong()
            hopperAnimation = alphaAnimation
            alphaAnimation.start()
        }
    }

    fun saveActiveCategory(category: Category) {
        activeCategory = category.order
        val headerItem = getHeader() ?: return
        binding.headerTitle.text = headerItem.category.name
        setActiveCategory()
    }

    private fun setActiveCategory() {
        val currentCategory =
            presenter.categories.indexOfFirst {
                if (presenter.showAllCategories) it.order == activeCategory else presenter.currentCategory == it.id
            }
        if (currentCategory > -1) {
            binding.categoryRecycler.setCategories(currentCategory)
            binding.headerTitle.text = presenter.categories[currentCategory].name
            setSubtitle()
        }
    }

    fun showMiniBar() {
        binding.headerCard.isVisible = showCategoryInTitle
        setSubtitle()
    }

    /** Called by the presenter once the download cache's initial scan starts/finishes. */
    fun setDownloadCacheScanning(scanning: Boolean) {
        isFindingDownloads = scanning
        setSubtitle()
    }

    private fun setSubtitle() {
        if (isBindingInitialized &&
            !singleCategory &&
            presenter.showAllCategories &&
            !binding.headerTitle.text.isNullOrBlank() &&
            !binding.recyclerCover.isClickable &&
            isControllerVisible
        ) {
            val categoryName = binding.headerTitle.text.toString()
            activityBinding?.searchToolbar?.subtitle =
                if (isFindingDownloads) {
                    "$categoryName - ${view?.context?.getString(R.string.finding_downloads)}"
                } else {
                    categoryName
                }
        } else if (isBindingInitialized && isFindingDownloads && isControllerVisible) {
            activityBinding?.searchToolbar?.subtitle = view?.context?.getString(R.string.finding_downloads)
        } else {
            activityBinding?.searchToolbar?.subtitle = null
        }
    }

    fun showCategoryText(name: String) {
        textAnim?.cancel()
        binding.jumperCategoryText.alpha = 1f
        binding.jumperCategoryText.text = name
        textAnim =
            binding.jumperCategoryText.animate().alpha(0f).setDuration(250L).setStartDelay(
                2000,
            )
        textAnim?.start()
    }

    fun isAtTop(): Boolean =
        if (presenter.showAllCategories) {
            !binding.libraryGridRecycler.recycler.canScrollVertically(-1)
        } else {
            getVisibleHeader()?.category?.order == presenter.categories.minOfOrNull { it.order }
        }

    fun isAtBottom(): Boolean =
        if (presenter.showAllCategories) {
            !binding.libraryGridRecycler.recycler.canScrollVertically(1)
        } else {
            getVisibleHeader()?.category?.order == presenter.categories.maxOfOrNull { it.order }
        }

    private fun showFilterTip() {
        if (preferences.shownFilterTutorial().get() || !hasExpanded) return
        if (filterTooltip != null) return
        val activityBinding = activityBinding ?: return
        val activity = activity ?: return
        val icon = (activityBinding.bottomNav ?: activityBinding.sideNav)?.getItemView(R.id.nav_library) ?: return
        filterTooltip =
            ViewTooltip
                .on(activity, icon)
                .autoHide(false, 0L)
                .align(ViewTooltip.ALIGN.START)
                .position(ViewTooltip.Position.TOP)
                .text(R.string.tap_library_to_show_filters)
                .textColor(activity.getResourceColor(R.attr.colorOnPrimary))
                .color(activity.getResourceColor(R.attr.colorPrimary))
                .textSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                .withShadow(false)
                .corner(30)
                .arrowWidth(15)
                .arrowHeight(15)
                .distanceWithView(0)

        filterTooltip?.show()
    }

    private fun openRandomManga() {
        val items =
            adapter.currentItems.filter {
                (
                    it is LibraryItem &&
                        !it.manga.isBlank() &&
                        !it.manga.isHidden() &&
                        (!it.manga.initialized || it.manga.unread > 0)
                )
            }
        if (items.isNotEmpty()) {
            val item = items.random() as LibraryItem
            openManga(item.manga)
        }
    }

    internal fun showGroupOptions() {
        val groupItems = mutableListOf(BY_DEFAULT, BY_TAG, BY_SOURCE, BY_STATUS, BY_AUTHOR)
        if (presenter.isLoggedIntoTracking) {
            groupItems.add(BY_TRACK_STATUS)
        }
        groupItems.add(BY_LANGUAGE)
        if (presenter.allCategories.size > 1) {
            groupItems.add(UNGROUPED)
        }
        val items =
            groupItems.map { id ->
                MaterialMenuSheet.MenuSheetItem(
                    id,
                    LibraryGroup.groupTypeDrawableRes(id),
                    LibraryGroup.groupTypeStringRes(id, presenter.allCategories.size > 1),
                )
            }
        MaterialMenuSheet(
            activity!!,
            items,
            activity!!.getString(R.string.group_library_by),
            presenter.groupType,
        ) { _, item ->
            if (!isSubClass) {
                preferences.groupLibraryBy().set(item)
            }
            presenter.groupType = item
            shouldScrollToTop = true
            presenter.getLibrary()
            true
        }.show()
    }

    internal fun showDisplayOptions() {
        if (displaySheet == null) {
            displaySheet = TabbedLibraryDisplaySheet(this)
            displaySheet?.show()
        }
    }

    internal fun closeTip() {
        if (filterTooltip != null) {
            filterTooltip?.close()
            filterTooltip = null
            if (!isSubClass) {
                preferences.shownFilterTutorial().set(true)
            }
        }
    }

    override fun createBinding(inflater: LayoutInflater) = LibraryControllerBinding.inflate(inflater)

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        mAdapter = LibraryCategoryAdapter(this)
        adapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        setRecyclerLayout()
        binding.libraryGridRecycler.recycler.setHasFixedSize(true)
        binding.libraryGridRecycler.recycler.adapter = adapter

        adapter.fastScroller = binding.fastScroller
        binding.fastScroller.controller = this
        binding.libraryGridRecycler.recycler.addOnScrollListener(scrollListener)

        binding.swipeRefresh.setStyle()

        binding.recyclerCover.setOnClickListener {
            showCategories(false)
        }
        binding.categoryRecycler.onCategoryClicked = {
            showCategories(show = false, closeSearch = true, category = it)
            scrollToHeader(it)
        }
        binding.categoryRecycler.setOnTouchListener { _, _ ->
            val searchView =
                activityBinding
                    ?.searchToolbar
                    ?.menu
                    ?.findItem(R.id.action_search)
                    ?.actionView
                    ?: return@setOnTouchListener false
            val imm = activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm!!.hideSoftInputFromWindow(searchView.windowToken, 0)
            false
        }
        setupFilterSheet()
        setUpHopper()
        setPreferenceFlows()
        // Debounce so a burst of updates (e.g. restoring a backup emits one per manga)
        // collapses into a single library refresh instead of one per manga.
        LibraryUpdateJob.updateFlow
            .debounce(200)
            .onEach(::onUpdateManga)
            .launchIn(viewScope)

        elevateAppBar =
            scrollViewWith(
                binding.libraryGridRecycler.recycler,
                swipeRefreshLayout = binding.swipeRefresh,
                ignoreInsetVisibility = true,
                afterInsets = { insets ->
                    val systemInsets = insets.ignoredSystemInsets
                    binding.categoryRecycler.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        topMargin = systemInsets.top + (activityBinding?.searchToolbar?.height ?: 0) + 12.dpToPx
                    }
                    updateSmallerViewsTopMargins()
                    binding.headerCard.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        topMargin = systemInsets.top + 4.dpToPx
                    }
                    updateFilterSheetY()
                },
                onLeavingController = {
                    binding.headerCard.isVisible = false
                },
                onBottomNavUpdate = {
                    updateFilterSheetY()
                },
            )

        viewScope.launchUI {
            delay(50.milliseconds)
            updateHopperY()
        }
        setSwipeRefresh()

        ViewCompat.setWindowInsetsAnimationCallback(view, cb)

        if (selectedMangas.isNotEmpty()) {
            createActionModeIfNeeded()
        }

        if (presenter.libraryItems.isNotEmpty() && !isSubClass) {
            presenter.restoreLibrary()
            if (justStarted) {
                val activityBinding = activityBinding ?: return
                val bigToolbarHeight = fullAppBarHeight ?: return
                if (lastUsedCategory > 0) {
                    activityBinding.appBar.y =
                        -bigToolbarHeight + activityBinding.cardFrame.height.toFloat()
                    activityBinding.appBar.useSearchToolbarForMenu(true)
                }
                activityBinding.appBar.lockYPos = true
            }
        } else {
            binding.recyclerLayout.alpha = 0f
        }
    }

    private fun updateSmallerViewsTopMargins() {
        val activityBinding = activityBinding ?: return
        val bigToolbarHeight = fullAppBarHeight ?: return
        val value =
            max(
                0,
                bigToolbarHeight + activityBinding.appBar.y.roundToInt(),
            ) + activityBinding.appBar.paddingTop
        if (value != binding.fastScroller.marginTop) {
            binding.fastScroller.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = value
            }
            binding.emptyView.updatePadding(
                top = bigToolbarHeight + activityBinding.appBar.paddingTop,
                bottom = binding.libraryGridRecycler.recycler.paddingBottom,
            )
            binding.progress.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = (bigToolbarHeight + activityBinding.appBar.paddingTop) / 2
            }
        }
    }

    private fun setSwipeRefresh() =
        with(binding.swipeRefresh) {
            setOnRefreshListener {
                isRefreshing = false
                if (!LibraryUpdateJob.isRunning(context)) {
                    when {
                        !presenter.showAllCategories && presenter.groupType == BY_DEFAULT -> {
                            presenter.allCategories.find { it.id == presenter.currentCategory }?.let {
                                updateLibrary(it)
                            }
                        }
                        !presenter.showAllCategories -> updateCategory(0)
                        else -> updateLibrary()
                    }
                }
            }
        }

    private fun setupFilterSheet() {
        binding.filterBottomSheet.filterBottomSheet.onCreate(this)

        binding.filterBottomSheet.filterBottomSheet.onGroupClicked = {
            when (it) {
                FilterBottomSheet.ACTION_REFRESH -> onRefresh()
                FilterBottomSheet.ACTION_FILTER -> onFilterChanged()
                FilterBottomSheet.ACTION_HIDE_FILTER_TIP -> showFilterTip()
                FilterBottomSheet.ACTION_DISPLAY -> showDisplayOptions()
                FilterBottomSheet.ACTION_EXPAND_COLLAPSE_ALL -> presenter.toggleAllCategoryVisibility()
                FilterBottomSheet.ACTION_GROUP_BY -> showGroupOptions()
            }
        }
    }

    @SuppressLint("RtlHardcoded", "ClickableViewAccessibility")
    private fun setUpHopper() {
        binding.categoryHopperFrame.isVisible = false
        binding.roundedCategoryHopper.downCategory.setOnClickListener {
            jumpToNextCategory(true)
        }
        binding.roundedCategoryHopper.upCategory.setOnClickListener {
            jumpToNextCategory(false)
        }
        binding.roundedCategoryHopper.downCategory.setOnLongClickListener {
            binding.libraryGridRecycler.recycler.scrollToPosition(adapter.itemCount - 1)
            true
        }
        binding.roundedCategoryHopper.upCategory.setOnLongClickListener {
            binding.libraryGridRecycler.recycler.smoothScrollToTop()
            true
        }
        binding.roundedCategoryHopper.categoryButton.setOnClickListener {
            val items =
                presenter.categories.map { category ->
                    MaterialMenuSheet.MenuSheetItem(
                        category.order,
                        text =
                            category.name +
                                if (adapter.showNumber && adapter.itemsPerCategory[category.id] != null) {
                                    " (${adapter.itemsPerCategory[category.id]})"
                                } else {
                                    ""
                                },
                    )
                }
            if (items.isEmpty()) return@setOnClickListener
            MaterialMenuSheet(
                activity!!,
                items,
                it.context.getString(R.string.jump_to_category),
                activeCategory,
                300.dpToPx,
            ) { _, item ->
                scrollToHeader(item)
                true
            }.show()
        }
        catGestureDetector =
            GestureDetector(binding.root.context, LibraryCategoryGestureDetector(this))

        binding.roundedCategoryHopper.categoryButton.setOnLongClickListener {
            when (preferences.hopperLongPressAction().get()) {
                4 -> openRandomManga()
                3 -> showGroupOptions()
                2 -> showDisplayOptions()
                1 -> if (canCollapseOrExpandCategory() != null) presenter.toggleAllCategoryVisibility()
                else ->
                    if (!isSubClass) {
                        activityBinding?.searchToolbar?.menu?.performIdentifierAction(
                            R.id.action_search,
                            0,
                        )
                    }
            }
            true
        }

        val gravityPref =
            if (!hasMovedHopper) {
                Random.nextInt(0..2)
            } else {
                preferences.hopperGravity().get()
            }
        hideHopper(preferences.hideHopper().get())
        binding.categoryHopperFrame.updateLayoutParams<CoordinatorLayout.LayoutParams> {
            gravity = Gravity.TOP or
                when (gravityPref) {
                    0 -> Gravity.LEFT
                    2 -> Gravity.RIGHT
                    else -> Gravity.CENTER
                }
        }
        hopperGravity = gravityPref

        val gestureDetector = GestureDetector(binding.root.context, LibraryGestureDetector(this))
        with(binding.roundedCategoryHopper) {
            listOf(categoryHopperLayout, upCategory, downCategory, categoryButton).forEach {
                it.setOnTouchListener { _, event ->
                    if (event?.action == MotionEvent.ACTION_DOWN) {
                        animatorSet?.end()
                    }
                    if (event?.action == MotionEvent.ACTION_UP) {
                        val result = gestureDetector.onTouchEvent(event)
                        if (!result) {
                            binding.categoryHopperFrame
                                .animate()
                                .setDuration(150L)
                                .translationX(0f)
                                .start()
                        }
                        result
                    } else {
                        gestureDetector.onTouchEvent(event)
                    }
                }
            }
        }
    }

    fun handleGeneralEvent(event: MotionEvent) {
        if (presenter.showAllCategories) return
        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
            val result = catGestureDetector?.onTouchEvent(event) ?: false
            if (!result && binding.libraryGridRecycler.recycler.translationX != 0f) {
                binding.libraryGridRecycler.recycler
                    .animate()
                    .setDuration(150L)
                    .translationX(0f)
                    .start()
            }
        } else {
            catGestureDetector?.onTouchEvent(event)
        }
    }

    open fun updateHopperY(windowInsets: WindowInsetsCompat? = null) {
        val view = view ?: return
        val insets = windowInsets ?: view.rootWindowInsetsCompat
        val bottomNav = if (isSubClass) null else activityBinding?.bottomNav
        val listOfYs =
            mutableListOf(
                binding.filterBottomSheet.filterBottomSheet.y,
                bottomNav?.y ?: binding.filterBottomSheet.filterBottomSheet.y,
            )
        val insetBottom = insets?.getInsets(systemBars())?.bottom ?: 0
        if (!preferences.autohideHopper().get() || bottomNav == null) {
            listOfYs.add(view.height - (insetBottom).toFloat())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && insets?.isImeVisible() == true) {
            val insetKey = insets.getInsets(ime() or systemBars()).bottom
            listOfYs.add(view.height - (insetKey).toFloat())
        }
        binding.categoryHopperFrame.y = -binding.categoryHopperFrame.height +
            (listOfYs.minOrNull() ?: binding.filterBottomSheet.filterBottomSheet.y) +
            hopperOffset +
            binding.libraryGridRecycler.recycler.translationY
        if (view.height - insetBottom < binding.categoryHopperFrame.y) {
            binding.jumperCategoryText.translationY =
                -(binding.categoryHopperFrame.y - (view.height - insetBottom)) +
                binding.libraryGridRecycler.recycler.translationY
        } else {
            binding.jumperCategoryText.translationY = binding.libraryGridRecycler.recycler.translationY
        }
    }

    fun resetHopperY() {
        hopperOffset = 0f
    }

    fun hideHopper(hide: Boolean) {
        binding.categoryHopperFrame.isVisible = !singleCategory && !hide
        binding.jumperCategoryText.isVisible = !hide
    }

    fun jumpToNextCategory(next: Boolean): Boolean {
        val category = getVisibleHeader() ?: return false
        if (presenter.showAllCategories) {
            if (!next) {
                val fPosition = binding.libraryGridRecycler.recycler.findFirstVisibleItemPosition()
                if (fPosition > adapter.currentItems.indexOf(category)) {
                    scrollToHeader(category.category.order)
                    return true
                }
            }
            val newOffset = adapter.headerItems.indexOf(category) + (if (next) 1 else -1)
            return if (if (!next) newOffset > -1 else newOffset < adapter.headerItems.size) {
                val newCategory = (adapter.headerItems[newOffset] as LibraryHeaderItem).category
                val newOrder = newCategory.order
                scrollToHeader(newOrder)
                showCategoryText(newCategory.name)
                true
            } else {
                binding.libraryGridRecycler.recycler.scrollToPosition(if (next) adapter.itemCount - 1 else 0)
                true
            }
        } else {
            val newOffset =
                presenter.categories.indexOfFirst { presenter.currentCategory == it.id } +
                    (if (next) 1 else -1)
            if (if (!next) {
                    newOffset > -1
                } else {
                    newOffset < presenter.categories.size
                }
            ) {
                val newCategory = presenter.categories[newOffset]
                val newOrder = newCategory.order
                scrollToHeader(newOrder)
                showCategoryText(newCategory.name)
                hopperAnimation?.cancel()
                hopperOffset = 0f
                updateHopperY()
                return true
            }
        }
        return false
    }

    fun visibleHeaderHolder(): LibraryHeaderHolder? =
        adapter.getHeaderPositions().firstOrNull()?.let {
            binding.libraryGridRecycler.recycler.findViewHolderForAdapterPosition(it) as? LibraryHeaderHolder
        }

    private fun getHeader(firstCompletelyVisible: Boolean = false): LibraryHeaderItem? {
        val position =
            if (firstCompletelyVisible) {
                binding.libraryGridRecycler.recycler.findFirstCompletelyVisibleItemPosition()
            } else {
                -1
            }
        if (position > 0) {
            when (val item = adapter.getItem(position)) {
                is LibraryHeaderItem -> return item
                is LibraryItem -> return item.header
            }
        } else {
            val fPosition = binding.libraryGridRecycler.recycler.findFirstVisibleItemPosition()
            when (val item = adapter.getItem(fPosition)) {
                is LibraryHeaderItem -> return item
                is LibraryItem -> return item.header
            }
        }
        return null
    }

    private fun getVisibleHeader(): LibraryHeaderItem? {
        val fPosition = binding.libraryGridRecycler.recycler.findFirstVisibleItemPosition()
        when (val item = adapter.getItem(fPosition)) {
            is LibraryHeaderItem -> return item
            is LibraryItem -> return item.header
        }
        return adapter.headerItems.firstOrNull() as? LibraryHeaderItem
    }

    private fun anchorView(): View =
        if (binding.categoryHopperFrame.isVisible) {
            binding.categoryHopperFrame
        } else {
            binding.filterBottomSheet.filterBottomSheet
        }

    private fun updateLibrary(category: Category? = null) {
        val view = view ?: return
        LibraryUpdateJob.startNow(view.context, category)
        snack =
            view.snack(R.string.updating_library) {
                anchorView = anchorView()
                this.view.elevation = 15f.dpToPx
                setAction(R.string.cancel) {
                    LibraryUpdateJob.stop(context)
                    viewScope.launchUI {
                        NotificationReceiver.dismissNotification(
                            context,
                            Notifications.ID_LIBRARY_PROGRESS,
                        )
                    }
                }
            }
    }

    /** The grid sizes its columns to the available width, so it has to recount when that changes. */
    fun onSideNavWidthChanged() {
        if (!isBindingInitialized) return
        binding.libraryGridRecycler.recycler.requestSpanUpdate()
    }

    private fun setRecyclerLayout() {
        with(binding.libraryGridRecycler.recycler) {
            val bottomNav = if (isSubClass) null else activityBinding?.bottomNav
            viewScope.launchUI {
                updatePaddingRelative(
                    bottom = 50.dpToPx + (bottomNav?.height ?: 0),
                )
            }
            useStaggered(preferences)
            if (libraryLayout == LibraryItem.LAYOUT_LIST) {
                spanCount = 1
                updatePaddingRelative(
                    start = 0,
                    end = 0,
                )
            } else {
                setGridSize(preferences)
                updatePaddingRelative(
                    start = 5.dpToPx,
                    end = 5.dpToPx,
                )
            }
            (manager as? GridLayoutManager)?.spanSizeLookup =
                object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        if (libraryLayout == LibraryItem.LAYOUT_LIST) return managerSpanCount
                        val item = this@LibraryController.mAdapter?.getItem(position)
                        return if (item is LibraryHeaderItem || item is SearchGlobalItem || (item is LibraryItem && item.manga.isBlank())) {
                            managerSpanCount
                        } else {
                            1
                        }
                    }
                }
        }
    }

    private fun setPreferenceFlows() {
        // They may have been changed by another controller while this view was gone
        LibraryItem.updateDisplayPrefs()
        listOf(
            preferences.libraryLayout(),
            preferences.uniformGrid(),
            preferences.gridSize(),
            preferences.useStaggeredGrid(),
        ).forEach {
            it
                .asFlow()
                .drop(1)
                .onEach {
                    LibraryItem.updateDisplayPrefs()
                    reattachAdapter()
                }.launchIn(viewScope)
        }
        preferences.hideStartReadingButton().register { LibraryItem.updateDisplayPrefs() }
        preferences.outlineOnCovers().register { adapter.showOutline = it }
        preferences.categoryNumberOfItems().register { adapter.showNumber = it }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun <T> Preference<T>.register(onChanged: ((T) -> Unit)? = null) {
        asFlow()
            .drop(1)
            .onEach {
                onChanged?.invoke(it)
                adapter.notifyDataSetChanged()
            }.launchIn(viewScope)
    }

    override fun onChangeStarted(
        handler: ControllerChangeHandler,
        type: ControllerChangeType,
    ) {
        super.onChangeStarted(handler, type)
        if (type.isEnter) {
            binding.filterBottomSheet.filterBottomSheet.isVisible = true
            if (type == ControllerChangeType.POP_ENTER) {
                presenter.getLibrary()
                isPoppingIn = true
            }
            DownloadJob.callListeners()
            binding.recyclerCover.isClickable = false
            binding.recyclerCover.isFocusable = false
            singleCategory = presenter.categories.size <= 1

            if (binding.libraryGridRecycler.recycler.manager is StaggeredGridLayoutManager && staggeredBundle != null) {
                binding.libraryGridRecycler.recycler.manager
                    .onRestoreInstanceState(staggeredBundle)
                staggeredBundle = null
            }
        } else {
            saveStaggeredState()
            updateFilterSheetY()
            closeTip()
            if (binding.filterBottomSheet.filterBottomSheet.sheetBehavior
                    .isHidden()
            ) {
                binding.filterBottomSheet.filterBottomSheet.isInvisible = true
            }
            activityBinding?.searchToolbar?.setOnLongClickListener(null)
            setToolbarFilterButtonVisible(false, animated = false)
        }
    }

    override fun onChangeEnded(
        changeHandler: ControllerChangeHandler,
        changeType: ControllerChangeType,
    ) {
        super.onChangeEnded(changeHandler, changeType)
        if (isPoppingIn) {
            isPoppingIn = false
            tempItems?.let { onNextLibraryUpdate(it) }
            tempItems = null
        }
    }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        if (!isBindingInitialized) return
        updateFilterSheetY()
        if (observeLater) {
            presenter.getLibrary()
        }
    }

    override fun onActivityPaused(activity: Activity) {
        super.onActivityPaused(activity)
        observeLater = true
    }

    override fun onDestroyView(view: View) {
        destroyActionModeIfNeeded()
        if (isBindingInitialized) {
            binding.libraryGridRecycler.recycler.removeOnScrollListener(scrollListener)
            binding.fastScroller.controller = null
        }
        displaySheet?.dismiss()
        displaySheet = null
        mAdapter = null
        saveStaggeredState()

        showAllCategoriesView?.let {
            (activityBinding?.searchToolbar?.searchView as? MiniSearchView)?.removeSearchModifierIcon(it)
        }
        activityBinding?.toolbarFilterButton?.let {
            it.isGone = true
            it.setOnClickListener(null)
        }
        tokenBackspaceWatcher?.let { searchEditText()?.removeTextChangedListener(it) }
        tokenBackspaceWatcher = null
        pendingTokenRange = null
        super.onDestroyView(view)
    }

    open fun onNextLibraryUpdate(
        mangaMap: List<LibraryItem>,
        freshStart: Boolean = false,
    ) {
        if (isPoppingIn) {
            tempItems = mangaMap
            return
        }
        view ?: return
        destroyActionModeIfNeeded()
        if (mangaMap.isNotEmpty()) {
            if (!binding.progress.isVisible) {
                (activity as? MainActivity)?.showNotificationPermissionPrompt()
            }
            binding.emptyView.hide()
        } else {
            binding.emptyView.show(
                R.drawable.ic_heart_off_24dp,
                if (hasActiveFilters) {
                    R.string.no_matches_for_filters
                } else {
                    R.string.library_is_empty_add_from_browse
                },
                if (!hasActiveFilters) {
                    listOf(
                        EmptyView.Action(R.string.getting_started_guide) {
                            activity?.openInBrowser("https://mihon.app/docs/guides/getting-started#adding-sources")
                        },
                    )
                } else {
                    emptyList()
                },
            )
        }
        adapter.setItems(mangaMap)
        if (binding.libraryGridRecycler.recycler.translationX != 0f) {
            val time =
                binding.root.resources
                    .getInteger(
                        android.R.integer.config_shortAnimTime,
                    ).toLong()
            viewScope.launchUI {
                delay((time / 2).milliseconds)
                binding.libraryGridRecycler.recycler.translationX = 0f
            }
        }
        singleCategory = presenter.categories.size <= 1
        binding.progress.isVisible = false
        if (!freshStart) {
            justStarted = false
        } // else binding.recyclerLayout.alpha = 1f
        if (binding.recyclerLayout.alpha == 0f) {
            binding.recyclerLayout
                .animate()
                .alpha(1f)
                .setDuration(500)
                .start()
        }
        if (justStarted && freshStart && !isSubClass) {
            val activeC = activeCategory
            scrollToHeader(activeCategory)
            binding.libraryGridRecycler.recycler.post {
                if (isControllerVisible) {
                    activityBinding?.appBar?.y = 0f
                    activityBinding?.appBar?.updateAppBarAfterY(binding.libraryGridRecycler.recycler)
                    if (activeC > 0) {
                        activityBinding?.appBar?.useSearchToolbarForMenu(true)
                    }
                }
            }

            if (binding.libraryGridRecycler.recycler.manager is StaggeredGridLayoutManager && isControllerVisible) {
                staggeredObserver =
                    ViewTreeObserver.OnGlobalLayoutListener {
                        binding.libraryGridRecycler.recycler.postOnAnimation {
                            if (!isControllerVisible) return@postOnAnimation
                            scrollToHeader(activeC, false)
                            activityBinding?.appBar?.y = 0f
                            activityBinding?.appBar?.updateAppBarAfterY(binding.libraryGridRecycler.recycler)
                            if (activeC > 0) {
                                activityBinding?.appBar?.useSearchToolbarForMenu(true)
                            }
                        }
                    }
                binding.libraryGridRecycler.recycler.viewTreeObserver
                    .addOnGlobalLayoutListener(staggeredObserver)
                viewScope.launchUI {
                    delay(500.milliseconds)
                    removeStaggeredObserver()
                    if (!isControllerVisible) return@launchUI
                    if (activeC > 0) {
                        activityBinding?.appBar?.useSearchToolbarForMenu(true)
                    }
                }
            }
        }
        if (isControllerVisible) {
            activityBinding?.appBar?.lockYPos = false
        }
        binding.libraryGridRecycler.recycler.post {
            elevateAppBar(binding.libraryGridRecycler.recycler.canScrollVertically(-1))
            setActiveCategory()
        }

        binding.categoryHopperFrame.isVisible = !singleCategory && !preferences.hideHopper().get()
        adapter.isLongPressDragEnabled = canDrag()
        binding.categoryRecycler.setCategories(
            presenter.categories,
            if (adapter.showNumber) {
                adapter.itemsPerCategory
            } else {
                emptyMap()
            },
        )
        with(binding.filterBottomSheet.root) {
            viewScope.launch {
                checkForManhwa(presenter.sourceManager)
            }
            updateGroupTypeButton(presenter.groupType)
            setExpandText(canCollapseOrExpandCategory())
        }
        if (shouldScrollToTop) {
            binding.libraryGridRecycler.recycler.scrollToPosition(0)
            shouldScrollToTop = false
        }
        if (isControllerVisible) {
            binding.headerTitle.setOnClickListener {
                val recycler = binding.libraryGridRecycler.recycler
                if (!singleCategory) {
                    showCategories(recycler.translationY == 0f)
                }
            }
            if (!hasMovedHopper && isAnimatingHopper == null) {
                showSlideAnimation()
            }
            setSubtitle()
            showMiniBar()
        }
        updateHopperAlpha()
        val isSingleCategory = !presenter.showAllCategories && !presenter.forceShowAllCategories
        binding.roundedCategoryHopper.upCategory.setIconResource(
            if (isSingleCategory) {
                R.drawable.ic_arrow_start_24dp
            } else {
                R.drawable.ic_expand_less_24dp
            },
        )
        binding.roundedCategoryHopper.downCategory.setIconResource(
            if (isSingleCategory) {
                R.drawable.ic_arrow_end_24dp
            } else {
                R.drawable.ic_expand_more_24dp
            },
        )
        binding.roundedCategoryHopper.categoryButton.setIconResource(
            LibraryGroup.groupTypeDrawableRes(presenter.groupType),
        )
    }

    private fun showSlideAnimation() {
        isAnimatingHopper = true
        val slide = 25f.dpToPx
        val animatorSet = AnimatorSet()
        this.animatorSet = animatorSet
        val animations =
            listOf(
                slideAnimation(0f, slide, 200),
                slideAnimation(slide, -slide),
                slideAnimation(-slide, slide),
                slideAnimation(slide, -slide),
                slideAnimation(-slide, 0f, 233),
            )
        animatorSet.playSequentially(animations)
        animatorSet.startDelay = 1250
        animatorSet.doOnEnd {
            binding.categoryHopperFrame.translationX = 0f
            isAnimatingHopper = false
            this.animatorSet = null
        }
        animatorSet.start()
    }

    private fun slideAnimation(
        from: Float,
        to: Float,
        duration: Long = 400,
    ): ObjectAnimator =
        ObjectAnimator
            .ofFloat(binding.categoryHopperFrame, View.TRANSLATION_X, from, to)
            .setDuration(duration)

    open fun showCategories(
        show: Boolean,
        closeSearch: Boolean = false,
        category: Int = -1,
    ) {
        binding.recyclerCover.isClickable = show
        binding.recyclerCover.isFocusable = show
        (activity as? MainActivity)?.apply {
            reEnableBackPressedCallBack()
            if (show && !binding.appBar.compactSearchMode && binding.appBar.useLargeToolbar) {
                binding.appBar.compactSearchMode = binding.appBar.useLargeToolbar
                if (binding.appBar.compactSearchMode) {
                    setFloatingToolbar(true)
                    mainRecycler.requestApplyInsets()
                    binding.appBar.y = 0f
                    binding.appBar.updateAppBarAfterY(mainRecycler)
                }
            } else if (!show &&
                binding.appBar.compactSearchMode &&
                binding.appBar.useLargeToolbar &&
                resources.configuration.screenHeightDp >= 600
            ) {
                binding.appBar.compactSearchMode = false
                mainRecycler.requestApplyInsets()
            }
        }
        if (closeSearch) {
            activityBinding?.searchToolbar?.searchItem?.collapseActionView()
        }
        val full = binding.categoryRecycler.height.toFloat() + binding.categoryRecycler.marginTop
        val translateY = if (show) full else 0f
        binding.libraryGridRecycler.recycler
            .animate()
            .translationY(translateY)
            .apply {
                setUpdateListener {
                    activityBinding?.appBar?.updateAppBarAfterY(binding.libraryGridRecycler.recycler)
                    updateHopperY()
                }
            }.start()
        binding.recyclerShadow
            .animate()
            .translationY(translateY - 8.dpToPx)
            .start()
        binding.recyclerCover
            .animate()
            .translationY(translateY)
            .start()
        binding.recyclerCover
            .animate()
            .alpha(if (show) 0.75f else 0f)
            .start()
        activityBinding?.appBar?.updateAppBarAfterY(binding.libraryGridRecycler.recycler)
        binding.swipeRefresh.isEnabled = !show
        setSubtitle()
        binding.categoryRecycler.isInvisible = !show
        if (show) {
            binding.categoryRecycler.post {
                binding.categoryRecycler.scrollToCategory(activeCategory)
            }
            binding.fastScroller.hideScrollbar()
            elevateAppBar(false)
            binding.filterBottomSheet.filterBottomSheet.sheetBehavior
                ?.hide()
        } else {
            val notAtTop = binding.libraryGridRecycler.recycler.canScrollVertically(-1)
            elevateAppBar((notAtTop || category > 0) && category != 0)
        }
    }

    fun scrollToCategory(category: Category?) {
        if (category != null && activeCategory != category.order) {
            scrollToHeader(category.order)
        }
    }

    private fun scrollToHeader(
        pos: Int,
        removeObserver: Boolean = true,
    ) {
        if (removeObserver) {
            removeStaggeredObserver()
        }
        if (!presenter.showAllCategories) {
            shouldScrollToTop = true
            presenter.switchSection(pos)
            activeCategory = pos
            setActiveCategory()
            return
        }
        val headerPosition = adapter.indexOf(pos)
        if (headerPosition > -1) {
            val activityBinding = activityBinding ?: return
            val index = adapter.headerItems.indexOf(adapter.getItem(headerPosition))
            val appbarOffset = if (index <= 0) 0 else -fullAppBarHeight!! + activityBinding.cardFrame.height
            val previousHeader = adapter.headerItems.getOrNull(index - 1) as? LibraryHeaderItem
            binding.libraryGridRecycler.recycler.scrollToPositionWithOffset(
                headerPosition,
                (
                    when {
                        headerPosition == 0 -> 0
                        previousHeader?.category?.isHidden == true -> (-3).dpToPx
                        else -> (-30).dpToPx
                    }
                ) + appbarOffset,
            )
            (adapter.getItem(headerPosition) as? LibraryHeaderItem)?.category?.let {
                saveActiveCategory(it)
            }
            activeCategory = pos
            if (!isSubClass) {
                preferences.lastUsedCategory().set(pos)
            }
            binding.libraryGridRecycler.recycler.post {
                if (isControllerVisible) {
                    activityBinding.appBar.y = 0f
                    activityBinding.appBar.updateAppBarAfterY(binding.libraryGridRecycler.recycler)
                }
            }
        }
    }

    private fun onRefresh() {
        showCategories(false)
        presenter.getLibrary()
        destroyActionModeIfNeeded()
    }

    /**
     * Called when a filter is changed.
     */
    private fun onFilterChanged() {
        presenter.requestFilterUpdate()
        destroyActionModeIfNeeded()
    }

    private fun reattachAdapter() {
        libraryLayout = preferences.libraryLayout().get()
        setRecyclerLayout()
        val position = binding.libraryGridRecycler.recycler.findFirstVisibleItemPosition()
        binding.libraryGridRecycler.recycler.adapter = adapter
        binding.libraryGridRecycler.recycler.scrollToPositionWithOffset(position, 0)
    }

    fun search(query: String?) {
        val isShowAllCategoriesSet = preferences.showAllCategories().get()
        if (!query.isNullOrBlank() && this.query.isBlank() && !isShowAllCategoriesSet) {
            presenter.forceShowAllCategories = preferences.showAllCategoriesWhenSearchingSingleCategory().get()
            presenter.getLibrary()
        } else if (query.isNullOrBlank() && this.query.isNotBlank() && !isShowAllCategoriesSet) {
            if (!isSubClass) {
                preferences
                    .showAllCategoriesWhenSearchingSingleCategory()
                    .set(presenter.forceShowAllCategories)
            }
            presenter.forceShowAllCategories = false
            presenter.getLibrary()
        }

        if (query != this.query && !query.isNullOrBlank()) {
            binding.libraryGridRecycler.recycler.scrollToPosition(0)
        }
        this.query = query ?: ""
        showAllCategoriesView?.isGone = isShowAllCategoriesSet || presenter.groupType != BY_DEFAULT || this.query.isBlank()
        showAllCategoriesView?.isSelected = presenter.forceShowAllCategories
        if (this.query.isNotBlank()) {
            searchItem.string = this.query
            if (adapter.scrollableHeaders.isEmpty() && !isSubClass) {
                adapter.addScrollableHeader(searchItem)
            }
        } else if (this.query.isBlank() && adapter.scrollableHeaders.isNotEmpty()) {
            adapter.removeAllScrollableHeaders()
        }
        adapter.setFilter(query)
        if (presenter.allLibraryItems.isEmpty()) return
        filterJob?.cancel()
        filterJob =
            viewScope.launchUI {
                adapter.performFilterAsync()
            }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onDestroyActionMode(mode: ActionMode?) {
        selectedMangas.clear()
        actionMode = null
        adapter.mode = SelectableAdapter.Mode.SINGLE
        adapter.clearSelection()
        adapter.notifyDataSetChanged()
        lastClickPosition = -1
        adapter.isLongPressDragEnabled = canDrag()
    }

    private fun setSelection(
        manga: Manga,
        selected: Boolean,
    ) {
        if (manga.isBlank()) return
        val currentMode = adapter.mode
        if (selected) {
            if (selectedMangas.add(manga)) {
                val positions = adapter.allIndexOf(manga)
                if (adapter.mode != SelectableAdapter.Mode.MULTI) {
                    adapter.mode = SelectableAdapter.Mode.MULTI
                }
                launchUI {
                    delay(100.milliseconds)
                    adapter.isLongPressDragEnabled = false
                }
                positions.forEach { position ->
                    adapter.addSelection(position)
                    (binding.libraryGridRecycler.recycler.findViewHolderForAdapterPosition(position) as? LibraryHolder)?.toggleActivation()
                }
            }
        } else {
            if (selectedMangas.remove(manga)) {
                val positions = adapter.allIndexOf(manga)
                lastClickPosition = -1
                if (selectedMangas.isEmpty()) {
                    adapter.mode = SelectableAdapter.Mode.SINGLE
                    adapter.isLongPressDragEnabled = canDrag()
                }
                positions.forEach { position ->
                    adapter.removeSelection(position)
                    (binding.libraryGridRecycler.recycler.findViewHolderForAdapterPosition(position) as? LibraryHolder)?.toggleActivation()
                }
            }
        }
        updateHeaders(currentMode != adapter.mode)
    }

    private fun updateHeaders(changedMode: Boolean = false) {
        val headerPositions = adapter.getHeaderPositions()
        headerPositions.forEach {
            if (changedMode) {
                adapter.notifyItemChanged(it)
            } else {
                (binding.libraryGridRecycler.recycler.findViewHolderForAdapterPosition(it) as? LibraryHeaderHolder)?.setSelection()
            }
        }
    }

    override fun startReading(
        position: Int,
        view: View?,
    ) {
        if (adapter.mode == SelectableAdapter.Mode.MULTI) {
            toggleSelection(position)
            return
        }
        val manga = (adapter.getItem(position) as? LibraryItem)?.manga ?: return
        val activity = activity ?: return
        val chapter = presenter.getFirstUnread(manga) ?: return
        activity.apply {
            if (view != null) {
                val (intent, bundle) =
                    ReaderActivity
                        .newIntentWithTransitionOptions(activity, manga, chapter, view)
                startActivity(intent, bundle)
            } else {
                startActivity(ReaderActivity.newIntent(activity, manga, chapter))
            }
        }
        destroyActionModeIfNeeded()
    }

    private fun toggleSelection(position: Int) {
        val item = adapter.getItem(position) as? LibraryItem ?: return
        if (item.manga.isBlank()) return
        setSelection(item.manga, !adapter.isSelected(position))
        invalidateActionMode()
    }

    override fun canDrag(): Boolean {
        val filterOff = !hasActiveFilters && presenter.groupType == BY_DEFAULT
        return filterOff && adapter.mode != SelectableAdapter.Mode.MULTI
    }

    /**
     * Called when a manga is clicked.
     *
     * @param position the position of the element clicked.
     * @return true if the item should be selected, false otherwise.
     */
    override fun onItemClick(
        view: View?,
        position: Int,
    ): Boolean {
        val item = adapter.getItem(position) as? LibraryItem ?: return false
        return if (adapter.mode == SelectableAdapter.Mode.MULTI) {
            snack?.dismiss()
            lastClickPosition = position
            toggleSelection(position)
            false
        } else {
            openManga(item.manga)
            false
        }
    }

    private fun saveStaggeredState() {
        if (binding.libraryGridRecycler.recycler.manager is StaggeredGridLayoutManager) {
            staggeredBundle =
                binding.libraryGridRecycler.recycler.manager
                    .onSaveInstanceState()
        }
    }

    private fun openManga(manga: Manga) {
        router.pushController(MangaDetailsController(manga).withFadeTransaction())
    }

    /**
     * Called when a manga is long clicked.
     *
     * @param position the position of the element clicked.
     */
    override fun onItemLongClick(position: Int) {
        val item = adapter.getItem(position)
        if (item !is LibraryItem) return
        snack?.dismiss()
        if (libraryLayout == LibraryItem.LAYOUT_COVER_ONLY_GRID && actionMode == null) {
            snack =
                view?.snack(item.manga.title) {
                    anchorView = activityBinding?.bottomNav
                    view.elevation = 15f.dpToPx
                }
        }
        createActionModeIfNeeded()
        when {
            lastClickPosition == -1 -> setSelection(position)
            lastClickPosition > position -> for (i in position until lastClickPosition) {
                setSelection(
                    i,
                )
            }
            lastClickPosition < position -> for (i in lastClickPosition + 1..position) {
                setSelection(
                    i,
                )
            }
            else -> setSelection(position)
        }
        lastClickPosition = position
    }

    override fun globalSearch(query: String) {
        router.pushController(GlobalSearchController(query).withFadeTransaction())
    }

    override fun onActionStateChanged(
        viewHolder: RecyclerView.ViewHolder?,
        actionState: Int,
    ) {
        val position = viewHolder?.bindingAdapterPosition ?: return
        binding.swipeRefresh.isEnabled = actionState != ItemTouchHelper.ACTION_STATE_DRAG
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            if (lastItemPosition != null &&
                position != lastItemPosition &&
                lastItem == adapter.getItem(position)
            ) {
                // because for whatever reason you can repeatedly tap on a currently dragging manga
                adapter.removeSelection(position)
                (binding.libraryGridRecycler.recycler.findViewHolderForAdapterPosition(position) as? LibraryHolder)?.toggleActivation()
                adapter.moveItem(position, lastItemPosition!!)
            } else {
                isDragging = true
                lastItem = adapter.getItem(position)
                lastItemPosition = position
                onItemLongClick(position)
            }
        }
    }

    private fun onUpdateManga(mangaId: Long?) {
        if (mangaId == LibraryUpdateJob.STARTING_UPDATE_SOURCE) return
        if (mangaId == null) {
            adapter.getHeaderPositions().forEach { adapter.notifyItemChanged(it) }
        } else {
            presenter.updateManga()
        }
    }

    private fun setSelection(
        position: Int,
        selected: Boolean = true,
    ) {
        val item = adapter.getItem(position) as? LibraryItem ?: return

        setSelection(item.manga, selected)
        invalidateActionMode()
    }

    override fun onItemMove(
        fromPosition: Int,
        toPosition: Int,
    ) {
        // Because padding a recycler causes it to scroll up we have to scroll it back down... wild
        val fromItem = adapter.getItem(fromPosition)
        val toItem = adapter.getItem(toPosition)
        if (binding.libraryGridRecycler.recycler.layoutManager !is StaggeredGridLayoutManager &&
            (
                (fromItem is LibraryItem && toItem is LibraryItem) || fromItem == null
            )
        ) {
            binding.libraryGridRecycler.recycler.scrollBy(
                0,
                binding.libraryGridRecycler.recycler.paddingTop,
            )
        }
        if (lastItemPosition == toPosition) {
            lastItemPosition = null
        } else if (lastItemPosition == null) {
            lastItemPosition = fromPosition
        }
    }

    override fun shouldMoveItem(
        fromPosition: Int,
        toPosition: Int,
    ): Boolean {
        if (adapter.isSelected(fromPosition)) toggleSelection(fromPosition)
        val item = adapter.getItem(fromPosition) as? LibraryItem ?: return false
        val newHeader = adapter.getSectionHeader(toPosition) as? LibraryHeaderItem
        if (toPosition < 1) return false
        return (adapter.getItem(toPosition) !is LibraryHeaderItem) &&
            (
                newHeader?.category?.id == item.manga.category ||
                    !presenter.mangaIsInCategory(
                        item.manga,
                        newHeader?.category?.id,
                    )
            )
    }

    override fun onItemReleased(position: Int) {
        lastItem = null
        isDragging = false
        binding.swipeRefresh.isEnabled = true
        if (adapter.selectedItemCount > 0) {
            lastItemPosition = null
            return
        }
        destroyActionModeIfNeeded()
        // if nothing moved
        if (lastItemPosition == null) return
        val item = adapter.getItem(position) as? LibraryItem ?: return
        val newHeader = adapter.getSectionHeader(position) as? LibraryHeaderItem
        val libraryItems =
            getSectionItems(adapter.getSectionHeader(position), item)
                .filterIsInstance<LibraryItem>()
        val mangaIds = libraryItems.mapNotNull { it.manga.id }
        if (newHeader?.category?.id == item.manga.category) {
            presenter.rearrangeCategory(item.manga.category, mangaIds)
        } else {
            if (presenter.mangaIsInCategory(item.manga, newHeader?.category?.id)) {
                adapter.moveItem(position, lastItemPosition!!)
                snack =
                    view?.snack(R.string.already_in_category) {
                        anchorView = anchorView()
                        view.elevation = 15f.dpToPx
                    }
                return
            }
            if (newHeader?.category != null) {
                moveMangaToCategory(
                    item.manga,
                    newHeader.category,
                    mangaIds,
                )
            }
        }
        lastItemPosition = null
    }

    private fun getSectionItems(
        header: IHeader<*>,
        skipItem: ISectionable<*, *>,
    ): List<ISectionable<*, *>> {
        val sectionItems: MutableList<ISectionable<*, *>> = ArrayList()
        var startPosition: Int = adapter.getGlobalPositionOf(header)
        var item = adapter.getItem(++startPosition) as? LibraryItem
        while (item?.header == header || item == skipItem) {
            sectionItems.add(item as ISectionable<*, *>)
            item = adapter.getItem(++startPosition) as? LibraryItem
        }
        return sectionItems
    }

    private fun moveMangaToCategory(
        manga: LibraryManga,
        category: Category?,
        mangaIds: List<Long>,
    ) {
        if (category?.id == null) return
        val oldCatId = manga.category
        presenter.moveMangaToCategory(manga, category.id, mangaIds)
        snack?.dismiss()
        snack =
            view?.snack(
                resources!!.getString(R.string.moved_to_, category.name),
            ) {
                anchorView = anchorView()
                view.elevation = 15f.dpToPx
                setAction(R.string.undo) {
                    manga.category = category.id!!
                    presenter.moveMangaToCategory(manga, oldCatId, mangaIds)
                }
            }
    }

    override fun updateCategory(position: Int): Boolean {
        val category = (adapter.getItem(position) as? LibraryHeaderItem)?.category ?: return false
        val inQueue = LibraryUpdateJob.categoryInQueue(category.id)
        snack?.dismiss()
        snack =
            view?.snack(
                resources!!.getString(
                    when {
                        inQueue -> R.string._already_in_queue
                        LibraryUpdateJob.isRunning(view!!.context) -> R.string.adding_category_to_queue
                        else -> R.string.updating_
                    },
                    category.name,
                ),
                Snackbar.LENGTH_LONG,
            ) {
                anchorView = anchorView()
                view.elevation = 15f.dpToPx
                setAction(R.string.cancel) {
                    LibraryUpdateJob.stop(context)
                    viewScope.launchUI {
                        NotificationReceiver.dismissNotification(
                            context,
                            Notifications.ID_LIBRARY_PROGRESS,
                        )
                    }
                }
            }
        if (!inQueue) {
            LibraryUpdateJob.startNow(
                view!!.context,
                category,
                mangaToUse =
                    if (category.isDynamic) {
                        presenter.getMangaInCategories(category.id)
                    } else {
                        null
                    },
            )
        }
        return true
    }

    override fun toggleCategoryVisibility(position: Int) {
        if (!presenter.showAllCategories) {
            showCategories(true)
            return
        }
        val catId = (adapter.getItem(position) as? LibraryHeaderItem)?.category?.id ?: return
        presenter.toggleCategoryVisibility(catId)
    }

    /**
     * Nullable Boolean to tell is all is collapsed/expanded/applicable
     * true = all categories are expanded
     * false = all or some categories are collapsed
     * null = is in single category mode
     */
    fun canCollapseOrExpandCategory(): Boolean? {
        if (singleCategory || !presenter.showAllCategories || isSubClass) {
            return null
        }
        return presenter.allCategoriesExpanded()
    }

    override fun manageCategory(position: Int) {
        val category = (adapter.getItem(position) as? LibraryHeaderItem)?.category ?: return
        if (!category.isDynamic) {
            ManageCategoryDialog(category) {
                presenter.getLibrary()
            }.showDialog(router)
        }
    }

    override fun sortCategory(
        catId: Int,
        sortBy: Char,
    ) {
        val category = presenter.categories.find { it.id == catId }
        if (category?.isDynamic == false && sortBy == LibrarySort.DragAndDrop.categoryValue) {
            val item = adapter.findCategoryHeader(catId) ?: return
            val libraryItems =
                adapter
                    .getSectionItems(item)
                    .filterIsInstance<LibraryItem>()
            val mangaIds = libraryItems.mapNotNull { it.manga.id }
            presenter.rearrangeCategory(catId, mangaIds)
        } else {
            presenter.sortCategory(catId, sortBy)
        }
    }

    override fun selectAll(position: Int) {
        val header = adapter.getSectionHeader(position) ?: return
        val items = adapter.getSectionItemPositions(header)
        val allSelected = allSelected(position)
        for (i in items) setSelection(i, !allSelected)
    }

    override fun allSelected(position: Int): Boolean {
        val header = adapter.getSectionHeader(position) ?: return false
        val items = adapter.getSectionItemPositions(header)
        return items.all { adapter.isSelected(it) }
    }

    //region sheet methods
    override fun showSheet() {
        closeTip()
        val sheetBehavior = binding.filterBottomSheet.filterBottomSheet.sheetBehavior
        when {
            sheetBehavior.isHidden() -> sheetBehavior?.collapse()
            !sheetBehavior.isExpanded() -> sheetBehavior?.expand()
            else -> showDisplayOptions()
        }
    }

    override fun hideSheet() {
        val sheetBehavior = binding.filterBottomSheet.filterBottomSheet.sheetBehavior
        when {
            sheetBehavior.isExpanded() -> sheetBehavior?.collapse()
            !sheetBehavior.isHidden() ->
                binding.filterBottomSheet.filterBottomSheet.sheetBehavior
                    ?.hide()
        }
    }

    override fun toggleSheet() {
        closeTip()
        when {
            binding.filterBottomSheet.filterBottomSheet.sheetBehavior
                .isHidden() ->
                binding.filterBottomSheet.filterBottomSheet.sheetBehavior
                    ?.collapse()
            !binding.filterBottomSheet.filterBottomSheet.sheetBehavior
                .isExpanded() ->
                binding.filterBottomSheet.filterBottomSheet.sheetBehavior
                    ?.expand()
            else ->
                binding.filterBottomSheet.filterBottomSheet.sheetBehavior
                    ?.hide()
        }
    }

    override fun canStillGoBack(): Boolean =
        isBindingInitialized &&
            (
                binding.recyclerCover.isClickable ||
                    binding.filterBottomSheet.filterBottomSheet.sheetBehavior
                        .isExpanded()
            )

    @Deprecated("Deprecated in Java")
    override fun handleBack(): Boolean {
        if (binding.recyclerCover.isClickable) {
            showCategories(false)
            return true
        }
        if (binding.filterBottomSheet.filterBottomSheet.sheetBehavior
                .isExpanded()
        ) {
            binding.filterBottomSheet.filterBottomSheet.sheetBehavior
                ?.collapse()
            return true
        }
        return false
    }
    //endregion

    //region Toolbar options methods
    override fun onCreateOptionsMenu(
        menu: Menu,
        inflater: MenuInflater,
    ) {
        inflater.inflate(R.menu.library, menu)

        val searchItem = activityBinding?.searchToolbar?.searchItem
        val searchView = activityBinding?.searchToolbar?.searchView
        activityBinding?.searchToolbar?.setQueryHint(resources?.getString(R.string.library_search_hint), query.isEmpty())
        attachTokenBackspaceWatcher()

        showAllCategoriesView = showAllCategoriesView ?: (searchView as? MiniSearchView)?.addSearchModifierIcon { context ->
            ImageView(context).apply {
                isSelected = presenter.forceShowAllCategories
                isGone = true
                setOnClickListener {
                    presenter.forceShowAllCategories = !presenter.forceShowAllCategories
                    presenter.getLibrary()
                    isSelected = presenter.forceShowAllCategories
                }
                val pad = 12.dpToPx
                setPadding(pad, 0, pad, 0)
                setImageResource(R.drawable.ic_show_all_categories_24dp)
                background = context.getResourceDrawable(R.attr.selectableItemBackgroundBorderless)
                imageTintList = ColorStateList.valueOf(context.getResourceColor(R.attr.actionBarTintColor))
                tooltipText = resources?.getText(R.string.show_all_categories)
            }
        }!!

        activityBinding?.toolbarFilterButton?.setOnClickListener { openSearchFilterSheet() }

        if (query.isNotEmpty()) {
            if (activityBinding?.searchToolbar?.isSearchExpanded != true) {
                searchItem?.expandActionView()
                searchView?.setQuery(query, true)
                searchView?.clearFocus()
            } else {
                searchView?.setQuery(query, false)
            }
            search(query)
        } else if (activityBinding?.searchToolbar?.isSearchExpanded == true) {
            searchItem?.collapseActionView()
        }
        activityBinding?.toolbarFilterButton?.isGone = activityBinding?.searchToolbar?.isSearchExpanded != true

        setOnQueryTextChangeListener(activityBinding?.searchToolbar?.searchView) {
            if (!it.isNullOrEmpty() && binding.recyclerCover.isClickable) {
                showCategories(false)
            }
            search(it)
            true
        }
    }

    override fun onActionViewExpand(item: MenuItem?) {
        setToolbarFilterButtonVisible(true, animated = true)
        if (!binding.recyclerCover.isClickable &&
            query.isBlank() &&
            !singleCategory &&
            presenter.showAllCategories
        ) {
            showCategories(true)
        }
    }

    private fun openSearchFilterSheet() {
        val activity = activity as? MainActivity ?: return
        val anchor = activityBinding?.toolbarFilterButton ?: return
        val dialog = ComposeAnchoredDialog(activity, anchor)
        dialog.setContent {
            LibrarySearchFilterSheetContent(
                minDateAddedMillis = presenter.earliestDateAdded,
                showOptional = query.isNotBlank(),
                seriesTypeOptions = presenter.availableSeriesTypes,
                showScore = presenter.isLoggedIntoTracking,
                onFieldSelected = { combinator, optional, field, separator, value ->
                    insertSearchToken(combinator, optional, field, separator, value)
                    dialog.dismiss()
                },
                onDateRangeSelected = { combinator, optional, field, startValue, endValue ->
                    insertSearchDateRangeToken(combinator, optional, field, startValue, endValue)
                    dialog.dismiss()
                },
            )
        }
        dialog.show()
    }

    private fun insertSearchToken(
        combinator: SearchCombinator,
        optional: Boolean,
        field: LibrarySearchFieldOption,
        separator: String,
        value: String?,
    ) {
        // For non-comparison fields with no value yet, insert an empty quoted pair and drop the
        // cursor inside it, ready to type - a comparison field's value is either typed after a
        // bare "=" or already filled in (e.g. a picked date), so it doesn't get this treatment.
        val wrapInQuotes = !field.isComparison && value == null
        val insertedValue = if (wrapInQuotes) "\"\"" else value.orEmpty()

        insertSearchTokenText(
            combinator = combinator,
            optional = optional,
            core = "${field.token}$separator$insertedValue",
            // A quoted-empty insertion always has a cursor slot to place, and a date-picked value
            // is a single already-complete token - neither needs the backspace-removal tracking a
            // date range's two-comparison group does (see insertSearchDateRangeToken).
            trackBackspaceRange = !field.opensDatePicker,
            cursorInsideQuotes = wrapInQuotes,
        )
    }

    /** Inserts a date range as a single `(field>=start, field<=end)` group. */
    private fun insertSearchDateRangeToken(
        combinator: SearchCombinator,
        optional: Boolean,
        field: LibrarySearchFieldOption,
        startValue: String,
        endValue: String,
    ) {
        insertSearchTokenText(
            combinator = combinator,
            optional = optional,
            core = "(${field.token}>=$startValue, ${field.token}<=$endValue)",
            trackBackspaceRange = false,
            cursorInsideQuotes = false,
        )
    }

    /**
     * Builds the join prefix from two independent axes and the [core]: [combinator] decides to
     * include/exclude the new term (whether it gets a leading `-`). [optional] decides
     * whether to join the existing query with AND (",") logic or with OR ("||") logic.
     */
    private fun insertSearchTokenText(
        combinator: SearchCombinator,
        optional: Boolean,
        core: String,
        trackBackspaceRange: Boolean,
        cursorInsideQuotes: Boolean,
    ) {
        val searchView = activityBinding?.searchToolbar?.searchView ?: return
        val existing =
            searchView.query
                ?.toString()
                .orEmpty()
                .trimEnd()
                .removeSuffix(",")
                .removeSuffix("||")
                .removeSuffix("-")
                .trimEnd()
        val excluding = combinator == SearchCombinator.NOT
        val prefix =
            when {
                existing.isEmpty() -> if (excluding) "-" else ""
                optional -> if (excluding) "$existing || -" else "$existing || "
                else -> if (excluding) "$existing -" else "$existing, "
            }

        val newQuery = "$prefix$core"
        val searchItem = activityBinding?.searchToolbar?.searchItem
        if (searchItem?.isActionViewExpanded != true) {
            searchItem?.expandActionView()
        }
        searchView.setQuery(newQuery, false)
        searchView.requestFocus()

        if (trackBackspaceRange) {
            pendingTokenRange = existing.length until newQuery.length
        }

        if (cursorInsideQuotes) {
            searchEditText()?.setSelection(newQuery.length - 1)
        }
    }

    private fun searchEditText(): EditText? =
        activityBinding?.searchToolbar?.searchView?.findViewById(androidx.appcompat.R.id.search_src_text)

    /** See [pendingTokenRange]. Attached once per view lifecycle; torn down in [onDestroyView]. */
    private fun attachTokenBackspaceWatcher() {
        if (tokenBackspaceWatcher != null) return
        val editText = searchEditText() ?: return

        val watcher =
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {}

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {
                    val range = pendingTokenRange
                    pendingTokenRange = null
                    if (range == null || before != 1 || count != 0 || start !in range) return

                    // Deferred: mutating the Editable synchronously from within its own change
                    // dispatch is unsafe (this callback's own edit will still re-enter the
                    // watcher, but pendingTokenRange is already null by then, so it's a no-op).
                    editText.post {
                        val text = editText.text ?: return@post
                        text.delete(range.first, range.last)
                        editText.setSelection(range.first.coerceIn(0, text.length))
                    }
                }

                override fun afterTextChanged(s: Editable?) {}
            }
        editText.addTextChangedListener(watcher)
        tokenBackspaceWatcher = watcher
    }

    override fun onActionViewCollapse(item: MenuItem?) {
        setToolbarFilterButtonVisible(false, animated = true)
        if (binding.recyclerCover.isClickable) {
            showCategories(false)
        }
    }

    private fun setToolbarFilterButtonVisible(
        visible: Boolean,
        animated: Boolean = false,
    ) {
        val button = activityBinding?.toolbarFilterButton ?: return
        if (animated) {
            (button.parent as? ViewGroup)?.let { parent ->
                TransitionManager.beginDelayedTransition(
                    parent,
                    TransitionSet()
                        .addTransition(Fade())
                        .addTransition(ChangeBounds())
                        .setDuration(200L),
                )
            }
        }
        button.isVisible = visible
    }

    override fun onSearchActionViewLongClickQuery(): String? {
        if (preferences.showLibrarySearchSuggestions().get()) {
            val suggestion = preferences.librarySearchSuggestion().get().takeIf { it.isNotBlank() }
            return suggestion?.removeSuffix("…")
        }
        return null
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_search -> expandActionViewFromInteraction = true
            R.id.action_filter -> {
                hasExpanded = true
                val sheetBehavior = binding.filterBottomSheet.filterBottomSheet.sheetBehavior
                if (!sheetBehavior.isExpanded() && !sheetBehavior.isSettling()) {
                    sheetBehavior?.expand()
                } else {
                    showDisplayOptions()
                }
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }
    //endregion

    //region Action Mode Methods

    /**
     * Creates the action mode if it's not created already.
     */
    private fun createActionModeIfNeeded() {
        if (actionMode == null) {
            actionMode = (activity as AppCompatActivity).startSupportActionMode(this)
            val view = activity?.window?.currentFocus ?: return
            val imm =
                activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    ?: return
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    fun showCategoriesController() {
        router.pushController(CategoryController().withFadeTransaction())
        displaySheet?.dismiss()
    }

    /**
     * Destroys the action mode.
     */
    private fun destroyActionModeIfNeeded() {
        actionMode?.finish()
    }

    /**
     * Invalidates the action mode, forcing it to refresh its content.
     */
    private fun invalidateActionMode() {
        actionMode?.invalidate()
    }

    override fun onCreateActionMode(
        mode: ActionMode,
        menu: Menu,
    ): Boolean {
        mode.menuInflater.inflate(R.menu.library_selection, menu)
        return true
    }

    override fun onPrepareActionMode(
        mode: ActionMode,
        menu: Menu,
    ): Boolean {
        val count = selectedMangas.size
        // Destroy action mode if there are no items selected.
        val migrationItem = menu.findItem(R.id.action_migrate)
        val shareItem = menu.findItem(R.id.action_share)
        val categoryItem = menu.findItem(R.id.action_move_to_category)
        val editItem = menu.findItem(R.id.action_edit_manga)
        categoryItem.isVisible = presenter.allCategories.size > 1
        editItem.isVisible = selectedMangas.isNotEmpty() && selectedMangas.all { !it.isLocal() }
        migrationItem.isVisible = selectedMangas.any { it.source != LocalSource.ID }
        shareItem.isVisible = migrationItem.isVisible
        if (count == 0) {
            destroyActionModeIfNeeded()
        } else {
            mode.title = resources?.getString(R.string.selected_, count)
        }
        return false
    }
    //endregion

    override fun onActionItemClicked(
        mode: ActionMode,
        item: MenuItem,
    ): Boolean {
        when (item.itemId) {
            R.id.action_move_to_category -> showChangeMangaCategoriesSheet()
            R.id.action_edit_manga -> {
                val ids = selectedMangas.mapNotNull { it.id }.distinct().toLongArray()
                if (ids.isNotEmpty()) {
                    BulkEditMangaDialog(this, ids).showDialog(router)
                }
            }
            R.id.action_share -> shareManga()
            R.id.action_delete -> {
                val options =
                    arrayOf(
                        R.string.remove_downloads,
                        R.string.remove_from_library,
                    ).map { activity!!.getString(it) }
                activity!!
                    .materialAlertDialog()
                    .setTitle(R.string.remove)
                    .setMultiChoiceItems(
                        options.toTypedArray(),
                        options.map { true }.toBooleanArray(),
                    ) { dialog, position, _ ->
                        if (position == 0) {
                            val listView = (dialog as AlertDialog).listView
                            listView.setItemChecked(position, true)
                        }
                    }.setPositiveButton(R.string.remove) { dialog, _ ->
                        val listView = (dialog as AlertDialog).listView
                        if (listView.isItemChecked(1)) {
                            deleteMangasFromLibrary()
                        } else {
                            val mangas = selectedMangas.toList()
                            presenter.confirmDeletion(mangas, false)
                        }
                    }.setNegativeButton(android.R.string.cancel, null)
                    .show()
                    .apply {
                        disableItems(arrayOf(options.first()))
                    }
            }
            R.id.action_download_unread -> {
                presenter.downloadUnread(selectedMangas.toList())
            }
            R.id.action_mark_as_read -> {
                activity!!
                    .materialAlertDialog()
                    .setMessage(R.string.mark_all_chapters_as_read)
                    .setPositiveButton(R.string.mark_as_read) { _, _ ->
                        markReadStatus(R.string.marked_as_read, true)
                    }.setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            R.id.action_mark_as_unread -> {
                activity!!
                    .materialAlertDialog()
                    .setMessage(R.string.mark_all_chapters_as_unread)
                    .setPositiveButton(R.string.mark_as_unread) { _, _ ->
                        markReadStatus(R.string.marked_as_unread, false)
                    }.setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            R.id.action_migrate -> {
                val skipPre = preferences.skipPreMigration().get()
                PreMigrationController.navigateToMigration(
                    skipPre,
                    router,
                    selectedMangas.filter { !it.isLocal() }.mapNotNull { it.id },
                )
                destroyActionModeIfNeeded()
            }
            else -> return false
        }
        return true
    }

    private fun markReadStatus(
        resource: Int,
        markRead: Boolean,
    ) {
        val mapMangaChapters = presenter.markReadStatus(selectedMangas.toList(), markRead)
        destroyActionModeIfNeeded()
        snack?.dismiss()
        snack =
            view?.snack(resource, Snackbar.LENGTH_INDEFINITE) {
                anchorView = anchorView()
                view.elevation = 15f.dpToPx
                var undoing = false
                setAction(R.string.undo) {
                    presenter.undoMarkReadStatus(mapMangaChapters)
                    undoing = true
                }
                addCallback(
                    object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                        override fun onDismissed(
                            transientBottomBar: Snackbar?,
                            event: Int,
                        ) {
                            super.onDismissed(transientBottomBar, event)
                            if (!undoing) {
                                presenter.confirmMarkReadStatus(
                                    mapMangaChapters,
                                    markRead,
                                )
                            }
                        }
                    },
                )
            }
        (activity as? MainActivity)?.setUndoSnackBar(snack)
    }

    private fun shareManga() {
        val context = view?.context ?: return
        val mangas = selectedMangas.toList()
        val urlList = presenter.getMangaUrls(mangas)
        if (urlList.isEmpty()) return
        val urls = presenter.getMangaUrls(mangas).joinToString("\n")
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/*"
                putExtra(Intent.EXTRA_TEXT, urls)
            }
        startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
    }

    open fun deleteMangasFromLibrary() {
        val mangas = selectedMangas.toList()
        presenter.removeMangaFromLibrary(mangas)
        destroyActionModeIfNeeded()
        snack?.dismiss()
        snack =
            view?.snack(
                activity?.getString(R.string.removed_from_library) ?: "",
                Snackbar.LENGTH_INDEFINITE,
            ) {
                anchorView = anchorView()
                view.elevation = 15f.dpToPx
                var undoing = false
                setAction(R.string.undo) {
                    presenter.reAddMangas(mangas)
                    undoing = true
                }
                addCallback(
                    object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                        override fun onDismissed(
                            transientBottomBar: Snackbar?,
                            event: Int,
                        ) {
                            super.onDismissed(transientBottomBar, event)
                            if (!undoing) presenter.confirmDeletion(mangas)
                        }
                    },
                )
            }
        (activity as? MainActivity)?.setUndoSnackBar(snack)
    }

    /**
     * Move the selected manga to a list of categories.
     */
    private fun showChangeMangaCategoriesSheet() {
        val activity = activity ?: return
        selectedMangas.toList().moveCategories(presenter.db, activity) {
            presenter.getLibrary()
            destroyActionModeIfNeeded()
        }
    }
}
