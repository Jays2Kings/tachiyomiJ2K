package eu.kanade.tachiyomi.ui.library

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewPropertyAnimator
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.GestureDetectorCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.github.florent37.viewtooltip.ViewTooltip
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.SelectableAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadService
import eu.kanade.tachiyomi.data.library.LibraryServiceListener
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.category.ManageCategoryDialog
import eu.kanade.tachiyomi.ui.library.filter.FilterBottomSheet
import eu.kanade.tachiyomi.ui.main.BottomSheetController
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.main.RootSearchInterface
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.ui.migration.manga.design.PreMigrationController
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.source.global_search.GlobalSearchController
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.view.applyWindowInsetsForRootController
import eu.kanade.tachiyomi.util.view.collapse
import eu.kanade.tachiyomi.util.view.expand
import eu.kanade.tachiyomi.util.view.getItemView
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.hide
import eu.kanade.tachiyomi.util.view.isExpanded
import eu.kanade.tachiyomi.util.view.isHidden
import eu.kanade.tachiyomi.util.view.isVisible
import eu.kanade.tachiyomi.util.view.scrollViewWith
import eu.kanade.tachiyomi.util.view.setOnQueryTextChangeListener
import eu.kanade.tachiyomi.util.view.setStyle
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.updateLayoutParams
import eu.kanade.tachiyomi.util.view.updatePaddingRelative
import eu.kanade.tachiyomi.util.view.visibleIf
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import eu.kanade.tachiyomi.widget.EndAnimatorListener
import kotlinx.android.synthetic.main.filter_bottom_sheet.*
import kotlinx.android.synthetic.main.library_grid_recycler.*
import kotlinx.android.synthetic.main.library_list_controller.*
import kotlinx.android.synthetic.main.main_activity.*
import kotlinx.android.synthetic.main.rounded_category_hopper.*
import kotlinx.coroutines.delay
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.random.nextInt

class LibraryController(
    bundle: Bundle? = null,
    val preferences: PreferencesHelper = Injekt.get()
) : BaseController(bundle),
    ActionMode.Callback,
    ChangeMangaCategoriesDialog.Listener,
    FlexibleAdapter.OnItemClickListener, FlexibleAdapter.OnItemLongClickListener,
    FlexibleAdapter.OnItemMoveListener, LibraryCategoryAdapter.LibraryListener,
    BottomSheetController,
    RootSearchInterface, LibraryServiceListener {

    init {
        setHasOptionsMenu(true)
        retainViewMode = RetainViewMode.RETAIN_DETACH
    }

    /**
     * Position of the active category.
     */
    private var activeCategory: Int = preferences.lastUsedCategory().getOrDefault()

    private var justStarted = true

    /**
     * Action mode for selections.
     */
    private var actionMode: ActionMode? = null

    private var libraryLayout: Int = preferences.libraryLayout().getOrDefault()

    var singleCategory: Boolean = false
        private set

    /**
     * Library search query.
     */
    private var query = ""

    /**
     * Currently selected mangas.
     */
    private val selectedMangas = mutableSetOf<Manga>()

    private lateinit var adapter: LibraryCategoryAdapter

    private var lastClickPosition = -1

    private var lastItemPosition: Int? = null
    private var lastItem: IFlexible<*>? = null

    lateinit var presenter: LibraryPresenter
        private set

    private var observeLater: Boolean = false
    var searchItem = SearchGlobalItem()

    var snack: Snackbar? = null

    private var scrollDistance = 0f
    private val scrollDistanceTilHidden = 1000.dpToPx
    private var textAnim: ViewPropertyAnimator? = null
    var hopperGravity: Int = preferences.hopperGravity().get()
        set(value) {
            field = value
            if (category_hopper_frame == null) return
            jumper_category_text.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                anchorGravity = when (value) {
                    0 -> Gravity.RIGHT or Gravity.CENTER_VERTICAL
                    2 -> Gravity.LEFT or Gravity.CENTER_VERTICAL
                    else -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
                }
                gravity = anchorGravity
            }
        }

    private var filterTooltip: ViewTooltip? = null
    private var isAnimatingHopper: Boolean? = null
    var hasMovedHopper = preferences.shownHopperSwipeTutorial().get()
    private var elevationAnim: ValueAnimator? = null
    private var elevate = false

    override fun getTitle(): String? {
        return view?.context?.getString(R.string.library)
    }

    private var scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            val recyclerCover = recycler_cover ?: return
            val notAtTop = recycler.canScrollVertically(-1)
            if (notAtTop != elevate) elevateFunc(notAtTop)
            val order = getCategoryOrder()
            if (!recyclerCover.isClickable && isAnimatingHopper != true) {
                category_hopper_frame.translationY += dy
                category_hopper_frame.translationY =
                    category_hopper_frame.translationY.coerceIn(0f, 50f.dpToPx)
                up_category.alpha = if (isAtTop()) 0.25f else 1f
                down_category.alpha = if (isAtBottom()) 0.25f else 1f
            }
            if (!filter_bottom_sheet.sheetBehavior.isHidden()) {
                scrollDistance += abs(dy)
                if (scrollDistance > scrollDistanceTilHidden) {
                    filter_bottom_sheet.sheetBehavior?.hide()
                    scrollDistance = 0f
                }
            } else scrollDistance = 0f
            if (order != null && order != activeCategory && lastItem == null) {
                preferences.lastUsedCategory().set(order)
                activeCategory = order
                setActiveCategory()
                if (presenter.categories.size > 1 && dy != 0 && recyclerView.translationY == 0f) {
                    val headerItem = getHeader() ?: return
                    showCategoryText(headerItem.category.name)
                }
            }
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)
            val recyclerCover = recycler_cover ?: return
            when (newState) {
                RecyclerView.SCROLL_STATE_DRAGGING -> {
                    fast_scroller.showScrollbar()
                }
                RecyclerView.SCROLL_STATE_IDLE -> {
                    val shortAnimationDuration = resources?.getInteger(
                        android.R.integer.config_shortAnimTime
                    ) ?: 0
                    if (!recyclerCover.isClickable) {
                        category_hopper_frame.animate().translationY(
                            if (category_hopper_frame.translationY > 25f.dpToPx) 50f.dpToPx
                            else 0f
                        ).setDuration(shortAnimationDuration.toLong()).start()
                    }
                }
            }
        }
    }

    fun showCategoryText(name: String) {
        textAnim?.cancel()
        textAnim = jumper_category_text.animate().alpha(0f).setDuration(250L).setStartDelay(2000)
        textAnim?.start()
        jumper_category_text.alpha = 1f
        jumper_category_text.text = name
    }

    fun isAtTop(): Boolean {
        return if (presenter.showAllCategories) {
            !recycler.canScrollVertically(-1)
        } else {
            getVisibleHeader()?.category?.order == presenter.categories.minBy { it.order }?.order
        }
    }

    fun isAtBottom(): Boolean {
        return if (presenter.showAllCategories) {
            !recycler.canScrollVertically(1)
        } else {
            getVisibleHeader()?.category?.order == presenter.categories.maxBy { it.order }?.order
        }
    }

    private fun showFilterTip() {
        if (preferences.shownFilterTutorial().get()) return
        val activity = activity ?: return
        val icon = activity.bottom_nav.getItemView(R.id.nav_library) ?: return
        filterTooltip =
            ViewTooltip.on(activity, icon).autoHide(false, 0L).align(ViewTooltip.ALIGN.START)
                .position(ViewTooltip.Position.TOP).text(R.string.tap_library_to_show_filters)
                .color(activity.getResourceColor(R.attr.colorAccent))
                .textSize(TypedValue.COMPLEX_UNIT_SP, 15f).textColor(Color.WHITE).withShadow(false)
                .corner(30).arrowWidth(15).arrowHeight(15).distanceWithView(0)

        filterTooltip?.show()
    }

    private fun closeTip() {
        if (filterTooltip != null) {
            filterTooltip?.close()
            filterTooltip = null
            preferences.shownFilterTutorial().set(true)
        }
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        view.applyWindowInsetsForRootController(activity!!.bottom_nav)
        if (!::presenter.isInitialized) presenter = LibraryPresenter(this)

        adapter = LibraryCategoryAdapter(this)
        setRecyclerLayout()

        recycler.manager.spanSizeLookup = (object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                if (libraryLayout == 0) return 1
                val item = this@LibraryController.adapter.getItem(position)
                return if (item is LibraryHeaderItem || item is SearchGlobalItem || (item is LibraryItem && item.manga.isBlank())) {
                    recycler?.manager?.spanCount ?: 1
                } else {
                    1
                }
            }
        })
        recycler.setHasFixedSize(true)
        recycler.adapter = adapter

        adapter.fastScroller = fast_scroller
        recycler.addOnScrollListener(scrollListener)

        val tv = TypedValue()
        activity!!.theme.resolveAttribute(R.attr.actionBarTintColor, tv, true)
        swipe_refresh.setStyle()

        recycler_cover.setOnClickListener {
            showCategories(false)
        }
        category_recycler.onCategoryClicked = {
            recycler.itemAnimator = null
            scrollToHeader(it)
            showCategories(show = false, scroll = false)
        }
        category_recycler.onShowAllClicked = { isChecked ->
            preferences.showAllCategories().set(isChecked)
            presenter.getLibrary()
        }

        category_hopper_frame.gone()
        down_category.setOnClickListener {
            jumpToNextCategory(true)
        }
        up_category.setOnClickListener {
            jumpToNextCategory(false)
        }
        down_category.setOnLongClickListener {
            recycler.scrollToPosition(adapter.itemCount - 1)
            true
        }
        up_category.setOnLongClickListener {
            recycler.scrollToPosition(0)
            true
        }
        category_button.setOnClickListener {
            showCategories(!recycler_cover.isClickable)
        }

        category_button.setOnLongClickListener {
            activity?.toolbar?.menu?.performIdentifierAction(R.id.action_search, 0)
            true
        }

        val gravityPref = if (!hasMovedHopper) {
            Random.nextInt(0..2)
        } else {
            preferences.hopperGravity().get()
        }
        hideHopper(preferences.hideHopper().get())
        category_hopper_frame.updateLayoutParams<CoordinatorLayout.LayoutParams> {
            anchorGravity = Gravity.TOP or when (gravityPref) {
                0 -> Gravity.LEFT
                2 -> Gravity.RIGHT
                else -> Gravity.CENTER
            }
        }
        hopperGravity = gravityPref

        val gestureDetector = GestureDetectorCompat(activity, LibraryGestureDetector(this))
        listOf(category_hopper_layout, up_category, down_category, category_button).forEach {
            it.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
            }
        }

        scrollViewWith(recycler, swipeRefreshLayout = swipe_refresh, afterInsets = { insets ->
            category_layout?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = recycler?.paddingTop ?: 0
            }
            fast_scroller?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = recycler?.paddingTop ?: 0
            }
        })

        swipe_refresh.setOnRefreshListener {
            swipe_refresh.isRefreshing = false
            if (!LibraryUpdateService.isRunning()) {
                when {
                    !presenter.showAllCategories -> {
                        presenter.categories.find { it.id == presenter.currentCategory }?.let {
                            updateLibrary(it)
                        }
                    }
                    presenter.allCategories.size <= 1 -> updateLibrary()
                    preferences.updateOnRefresh().getOrDefault() == -1 -> {
                        MaterialDialog(activity!!).title(R.string.what_should_update)
                            .negativeButton(android.R.string.cancel)
                            .listItemsSingleChoice(items = listOf(
                                view.context.getString(
                                    R.string.top_category, presenter.allCategories.first().name
                                ), view.context.getString(
                                    R.string.categories_in_global_update
                                )
                            ), selection = { _, index, _ ->
                                preferences.updateOnRefresh().set(index)
                                when (index) {
                                    0 -> updateLibrary(presenter.allCategories.first())
                                    else -> updateLibrary()
                                }
                            }).positiveButton(R.string.update).show()
                    }
                    else -> {
                        when (preferences.updateOnRefresh().getOrDefault()) {
                            0 -> updateLibrary(presenter.allCategories.first())
                            else -> updateLibrary()
                        }
                    }
                }
            }
        }

        if (selectedMangas.isNotEmpty()) {
            createActionModeIfNeeded()
        }

        filter_bottom_sheet.onCreate(recycler_layout)

        filter_bottom_sheet.onGroupClicked = {
            when (it) {
                FilterBottomSheet.ACTION_REFRESH -> onRefresh()
                FilterBottomSheet.ACTION_FILTER -> onFilterChanged()
                FilterBottomSheet.ACTION_HIDE_FILTER_TIP -> showFilterTip()
                FilterBottomSheet.ACTION_DISPLAY -> DisplayBottomSheet(this).show()
                FilterBottomSheet.ACTION_EXPAND_COLLAPSE_ALL -> presenter.toggleAllCategoryVisibility()
            }
        }

        presenter.onRestore()
        if (presenter.libraryItems.isNotEmpty()) {
            presenter.restoreLibrary()
        } else {
            recycler_layout.alpha = 0f
            presenter.getLibrary()
        }
    }

    fun hideHopper(hide: Boolean) {
        category_hopper_frame.visibleIf(!hide)
        jumper_category_text.visibleIf(!hide)
    }

    private fun jumpToNextCategory(next: Boolean) {
        val category = getVisibleHeader() ?: return
        if (presenter.showAllCategories) {
            val newOffset = adapter.headerItems.indexOf(category) + (if (next) 1 else -1)
            if (if (!next) {
                    newOffset > -1
                } else {
                    newOffset < adapter.headerItems.size
                }) {
                val newCategory = (adapter.headerItems[newOffset] as LibraryHeaderItem).category
                val newOrder = newCategory.order
                scrollToHeader(newOrder)
                showCategoryText(newCategory.name)
            } else {
                recycler.scrollToPosition(if (next) adapter.itemCount - 1 else 0)
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
            }
        }
    }

    private fun getHeader(): LibraryHeaderItem? {
        val position =
            (recycler.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition()
        if (position > 0) {
            when (val item = adapter.getItem(position)) {
                is LibraryHeaderItem -> return item
                is LibraryItem -> return item.header
            }
        } else {
            val fPosition =
                (recycler.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
            when (val item = adapter.getItem(fPosition)) {
                is LibraryHeaderItem -> return item
                is LibraryItem -> return item.header
            }
        }
        return null
    }

    private fun getVisibleHeader(): LibraryHeaderItem? {
        val position =
            (recycler.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition()
        when (val item = adapter.getItem(position)) {
            is LibraryHeaderItem -> return item
            is LibraryItem -> return item.header
        }
        val fPosition =
            (recycler.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
        when (val item = adapter.getItem(fPosition)) {
            is LibraryHeaderItem -> return item
            is LibraryItem -> return item.header
        }
        return null
    }

    private fun getCategoryOrder(): Int? {
        val position =
            (recycler.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition()
        var order = when (val item = adapter.getItem(position)) {
            is LibraryHeaderItem -> item.category.order
            is LibraryItem -> item.header.category.order
            else -> null
        }
        if (order == null) {
            val fPosition =
                (recycler.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
            order = when (val item = adapter.getItem(fPosition)) {
                is LibraryHeaderItem -> item.category.order
                is LibraryItem -> item.header.category.order
                else -> null
            }
        }
        return order
    }

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.library_list_controller, container, false)
    }

    private fun anchorView(): View? {
        return if (category_hopper_frame.isVisible()) {
            category_hopper_frame
        } else {
            filter_bottom_sheet
        }
    }

    private fun updateLibrary(category: Category? = null) {
        val view = view ?: return
        LibraryUpdateService.start(view.context, category)
        snack = view.snack(R.string.updating_library) {
            anchorView = anchorView()
            view.elevation = 15f.dpToPx
            setAction(R.string.cancel) {
                LibraryUpdateService.stop(context)
                Handler().post { NotificationReceiver.dismissNotification(context, Notifications.ID_LIBRARY_PROGRESS) }
            }
        }
    }

    private fun setRecyclerLayout() {
        recycler.updatePaddingRelative(bottom = 50.dpToPx)
        if (libraryLayout == 0) {
            recycler.spanCount = 1
            recycler.updatePaddingRelative(
                start = 0, end = 0
            )
        } else {
            recycler.columnWidth = when (preferences.gridSize().getOrDefault()) {
                1 -> 1f
                2 -> 1.25f
                3 -> 1.66f
                4 -> 3f
                else -> .75f
            }
            recycler.updatePaddingRelative(
                start = 5.dpToPx,
                end = 5.dpToPx
            )
        }
    }

    override fun onChangeStarted(handler: ControllerChangeHandler, type: ControllerChangeType) {
        super.onChangeStarted(handler, type)
        if (type.isEnter) {
            view?.applyWindowInsetsForRootController(activity!!.bottom_nav)
            presenter.getLibrary()
            DownloadService.callListeners()
            LibraryUpdateService.setListener(this)
            recycler_cover.isClickable = false
            recycler_cover.isFocusable = false
            if (onRoot) {
                activity?.dropdown?.visibleIf(!singleCategory)
            }
        } else {
            closeTip()
            activity?.dropdown?.gone()
        }
    }

    override fun onChangeEnded(handler: ControllerChangeHandler, type: ControllerChangeType) {
        super.onChangeEnded(handler, type)
        if (!type.isEnter) {
            activity?.dropdown?.gone()
        }
    }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        if (view == null) return
        if (observeLater && ::presenter.isInitialized) {
            presenter.getLibrary()
        }
    }

    override fun onActivityPaused(activity: Activity) {
        super.onActivityPaused(activity)
        observeLater = true
        if (::presenter.isInitialized) presenter.onDestroy()
    }

    override fun onDestroy() {
        if (::presenter.isInitialized) presenter.onDestroy()
        super.onDestroy()
    }

    override fun onDestroyView(view: View) {
        LibraryUpdateService.removeListener(this)
        destroyActionModeIfNeeded()
        recycler.removeOnScrollListener(scrollListener)
        super.onDestroyView(view)
    }

    fun onNextLibraryUpdate(mangaMap: List<LibraryItem>, freshStart: Boolean = false) {
        view ?: return
        destroyActionModeIfNeeded()
        if (mangaMap.isNotEmpty()) {
            empty_view?.hide()
        } else {
            empty_view?.show(
                R.drawable.ic_book_black_128dp,
                if (filter_bottom_sheet.hasActiveFilters()) R.string.no_matches_for_filters
                else R.string.library_is_empty_add_from_browse
            )
        }
        adapter.setItems(mangaMap)
        if (recycler.itemAnimator == null)
        recycler.post {
            recycler.itemAnimator = DefaultItemAnimator()
        }
        singleCategory = presenter.categories.size <= 1
        if (onRoot) {
            activity?.dropdown?.visibleIf(!singleCategory)
        }
        progress.gone()
        if (!freshStart) {
            justStarted = false
            if (recycler_layout.alpha == 0f) recycler_layout.animate().alpha(1f).setDuration(500)
                .start()
        } else recycler_layout.alpha = 1f
        if (justStarted && freshStart) {
            scrollToHeader(activeCategory)
        }
        category_hopper_frame.visibleIf(!singleCategory && !preferences.hideHopper().get())
        filter_bottom_sheet.updateButtons(
            showHideCategories = presenter.allCategories.size > 1,
            showExpand = !singleCategory && presenter.showAllCategories
        )
        adapter.isLongPressDragEnabled = canDrag()
        category_recycler.setCategories(presenter.categories)
        filter_bottom_sheet.setExpandText(preferences.collapsedCategories().getOrDefault().isNotEmpty())
        setActiveCategory()
        if (onRoot) {
            activity?.toolbar?.setOnClickListener {
                val recycler = recycler ?: return@setOnClickListener
                if (singleCategory) {
                    recycler.scrollToPosition(0)
                } else {
                    showCategories(recycler.translationY == 0f)
                }
            }
            if (!hasMovedHopper && isAnimatingHopper == null) {
                showSlideAnimation()
            }
        }
    }

    private fun showSlideAnimation() {
        isAnimatingHopper = true
        val slide = 25f.dpToPx
        val animatorSet = AnimatorSet()
        val animations = listOf(
            slideAnimation(0f, slide, 200),
            slideAnimation(slide, -slide),
            slideAnimation(-slide, slide),
            slideAnimation(slide, -slide),
            slideAnimation(-slide, 0f, 233)
        )
        animatorSet.playSequentially(animations)
        animatorSet.startDelay = 1250
        animatorSet.addListener(EndAnimatorListener {
            isAnimatingHopper = false
        })
        animatorSet.start()
    }

    private fun slideAnimation(from: Float, to: Float, duration: Long = 400): ObjectAnimator {
        return ObjectAnimator.ofFloat(category_hopper_frame, View.TRANSLATION_X, from, to)
            .setDuration(duration)
    }

    private fun showCategories(show: Boolean, scroll: Boolean = true) {
        recycler_cover.isClickable = show
        recycler_cover.isFocusable = show
        val full = category_layout.height.toFloat() + recycler.paddingTop
        val translateY = if (show) full else 0f
        recycler.animate().translationY(translateY).start()
        category_hopper_frame.animate().translationY(translateY).start()
        if (scroll) {
            // Smooth scroll the recycler to hide the hidden content blocked by the app bar
            ValueAnimator.ofInt(recycler.translationY.roundToInt(), translateY.roundToInt()).apply {
                var start = 0f
                var last = recycler.translationY.roundToInt()
                val distance = abs(recycler.translationY.roundToInt() - translateY.roundToInt())
                addUpdateListener {
                    val diff = abs(it.animatedValue as Int - last)
                    last = it.animatedValue as Int
                    start += diff.toFloat() / distance * recycler.paddingTop.toFloat()
                    if (start > 1) {
                        recycler.scrollBy(0, start.toInt() * if (show) 1 else -1)
                        start %= 1
                    }
                }
            }.start()
        }
        recycler_cover.animate().translationY(translateY).start()
        recycler_cover.animate().alpha(if (show) 0.75f else 0f).start()
        if (show) {
            activity?.appbar?.y = 0f
            elevateFunc(false)
            activity?.dropdown?.setImageResource(R.drawable.ic_arrow_drop_up_24dp)
        } else {
            val notAtTop = recycler.canScrollVertically(-1)
            if (notAtTop != elevate) elevateFunc(notAtTop)
            activity?.dropdown?.setImageResource(R.drawable.ic_arrow_drop_down_24dp)
        }
    }

    private fun elevateFunc(el: Boolean) {
        elevate = el
        elevationAnim?.cancel()
        elevationAnim = ValueAnimator.ofFloat(
            activity?.appbar?.elevation ?: 0f, if (el) 15f else 0f
        )
        elevationAnim?.addUpdateListener { valueAnimator ->
            activity?.appbar?.elevation = valueAnimator.animatedValue as Float
        }
        elevationAnim?.start()
    }

    fun setActiveCategory() {
        val currentCategory = presenter.categories.indexOfFirst {
            if (presenter.showAllCategories) it.order == activeCategory else presenter.currentCategory == it.id
        }
        category_recycler.setCategories(currentCategory)
    }

    private fun scrollToHeader(pos: Int) {
        if (!presenter.showAllCategories) {
            presenter.switchSection(pos)
            activeCategory = pos
            setActiveCategory()
            recycler.scrollToPosition(0)
            return
        }
        val headerPosition = adapter.indexOf(pos)
        if (headerPosition > -1) {
            val appbar = activity?.appbar
            recycler.suppressLayout(true)
            val appbarOffset = if (appbar?.y ?: 0f > -20) 0 else (appbar?.y?.plus(
                view?.rootWindowInsets?.systemWindowInsetTop ?: 0
            ) ?: 0f).roundToInt() + 30.dpToPx
            val previousHeader = adapter.getItem(adapter.indexOf(pos - 1)) as? LibraryHeaderItem
            (recycler.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                headerPosition, (when {
                    headerPosition == 0 -> 0
                    previousHeader?.category?.isHidden == true -> (-3).dpToPx
                    else -> (-30).dpToPx
                }) + appbarOffset
            )
            recycler.suppressLayout(false)
        }
    }

    private fun onRefresh() {
        activity?.invalidateOptionsMenu()
        showCategories(false)
        presenter.getLibrary()
        destroyActionModeIfNeeded()
    }

    /**
     * Called when a filter is changed.
     */
    private fun onFilterChanged() {
        activity?.invalidateOptionsMenu()
        presenter.requestFilterUpdate()
        destroyActionModeIfNeeded()
    }

    fun reattachAdapter() {
        libraryLayout = preferences.libraryLayout().getOrDefault()
        setRecyclerLayout()
        val position =
            (recycler.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
        recycler.adapter = adapter

        (recycler.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, 0)
    }

    fun search(query: String?): Boolean {
        this.query = query ?: ""
        if (this.query.isNotBlank() && adapter.scrollableHeaders.isEmpty()) {
            searchItem.string = this.query
            adapter.addScrollableHeader(searchItem)
        } else if (this.query.isNotBlank()) {
            searchItem.string = this.query
            (recycler.findViewHolderForAdapterPosition(0) as? SearchGlobalItem.Holder)?.bind(this.query)
        } else if (this.query.isBlank() && adapter.scrollableHeaders.isNotEmpty()) {
            adapter.removeAllScrollableHeaders()
        }
        adapter.setFilter(query)
        adapter.performFilter()
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode?) {
        selectedMangas.clear()
        actionMode = null
        adapter.mode = SelectableAdapter.Mode.SINGLE
        adapter.clearSelection()
        adapter.notifyDataSetChanged()
        lastClickPosition = -1
        adapter.isLongPressDragEnabled = canDrag()
    }

    private fun setSelection(manga: Manga, selected: Boolean) {
        if (manga.isBlank()) return
        val currentMode = adapter.mode
        if (selected) {
            if (selectedMangas.add(manga)) {
                val positions = adapter.allIndexOf(manga)
                if (adapter.mode != SelectableAdapter.Mode.MULTI) {
                    adapter.mode = SelectableAdapter.Mode.MULTI
                }
                launchUI {
                    delay(100)
                    adapter.isLongPressDragEnabled = false
                }
                positions.forEach { position ->
                    adapter.addSelection(position)
                    (recycler.findViewHolderForAdapterPosition(position) as? LibraryHolder)?.toggleActivation()
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
                    (recycler.findViewHolderForAdapterPosition(position) as? LibraryHolder)?.toggleActivation()
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
                (recycler.findViewHolderForAdapterPosition(it) as? LibraryHeaderItem.Holder)?.setSelection()
            }
        }
    }

    override fun startReading(position: Int) {
        if (adapter.mode == SelectableAdapter.Mode.MULTI) {
            toggleSelection(position)
            return
        }
        val manga = (adapter.getItem(position) as? LibraryItem)?.manga ?: return
        val activity = activity ?: return
        val chapter = presenter.getFirstUnread(manga) ?: return
        val intent = ReaderActivity.newIntent(activity, manga, chapter)
        destroyActionModeIfNeeded()
        startActivity(intent)
    }

    private fun toggleSelection(position: Int) {
        val item = adapter.getItem(position) as? LibraryItem ?: return
        if (item.manga.isBlank()) return
        setSelection(item.manga, !adapter.isSelected(position))
        invalidateActionMode()
    }

    override fun canDrag(): Boolean {
        filter_bottom_sheet ?: return false
        val filterOff =
            !filter_bottom_sheet.hasActiveFilters() && !preferences.hideCategories().getOrDefault()
        return filterOff && adapter.mode != SelectableAdapter.Mode.MULTI
    }

    /**
     * Called when a manga is clicked.
     *
     * @param position the position of the element clicked.
     * @return true if the item should be selected, false otherwise.
     */
    override fun onItemClick(view: View?, position: Int): Boolean {
        val item = adapter.getItem(position) as? LibraryItem ?: return false
        return if (adapter.mode == SelectableAdapter.Mode.MULTI) {
            lastClickPosition = position
            toggleSelection(position)
            false
        } else {
            openManga(item.manga)
            false
        }
    }

    private fun openManga(manga: Manga) = router.pushController(
        MangaDetailsController(
            manga
        ).withFadeTransaction()
    )

    /**
     * Called when a manga is long clicked.
     *
     * @param position the position of the element clicked.
     */
    override fun onItemLongClick(position: Int) {
        if (adapter.getItem(position) !is LibraryItem) return
        createActionModeIfNeeded()
        when {
            lastClickPosition == -1 -> setSelection(position)
            lastClickPosition > position -> for (i in position until lastClickPosition) setSelection(
                i
            )
            lastClickPosition < position -> for (i in lastClickPosition + 1..position) setSelection(
                i
            )
            else -> setSelection(position)
        }
        lastClickPosition = position
    }

    override fun globalSearch(query: String) {
        router.pushController(GlobalSearchController(query).withFadeTransaction())
    }

    override fun onActionStateChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        val position = viewHolder?.adapterPosition ?: return
        swipe_refresh.isEnabled = actionState != ItemTouchHelper.ACTION_STATE_DRAG
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            activity?.appbar?.y = 0f
            if (lastItemPosition != null && position != lastItemPosition && lastItem == adapter.getItem(
                    position
                )
            ) {
                // because for whatever reason you can repeatedly tap on a currently dragging manga
                adapter.removeSelection(position)
                (recycler.findViewHolderForAdapterPosition(position) as? LibraryHolder)?.toggleActivation()
                adapter.moveItem(position, lastItemPosition!!)
            } else {
                lastItem = adapter.getItem(position)
                lastItemPosition = position
                onItemLongClick(position)
            }
        }
    }

    override fun onUpdateManga(manga: LibraryManga) {
        if (manga.id == null) adapter.notifyDataSetChanged()
        else presenter.updateManga(manga)
    }

    private fun setSelection(position: Int, selected: Boolean = true) {
        val item = adapter.getItem(position) as? LibraryItem ?: return

        setSelection(item.manga, selected)
        invalidateActionMode()
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int) {
        // Because padding a recycler causes it to scroll up we have to scroll it back down... wild
        if ((adapter.getItem(fromPosition) is LibraryItem && adapter.getItem(fromPosition) is
                LibraryItem) || adapter.getItem(
                fromPosition
            ) == null
        ) {
            recycler.scrollBy(0, recycler.paddingTop)
        }
        activity?.appbar?.y = 0f
        if (lastItemPosition == toPosition) lastItemPosition = null
        else if (lastItemPosition == null) lastItemPosition = fromPosition
    }

    override fun shouldMoveItem(fromPosition: Int, toPosition: Int): Boolean {
        if (adapter.isSelected(fromPosition)) toggleSelection(fromPosition)
        val item = adapter.getItem(fromPosition) as? LibraryItem ?: return false
        val newHeader = adapter.getSectionHeader(toPosition) as? LibraryHeaderItem
        if (toPosition < 1) return false
        return (adapter.getItem(toPosition) !is LibraryHeaderItem) && (newHeader?.category?.id == item.manga.category || !presenter.mangaIsInCategory(
            item.manga, newHeader?.category?.id
        ))
    }

    override fun onItemReleased(position: Int) {
        lastItem = null
        if (adapter.selectedItemCount > 0) {
            lastItemPosition = null
            return
        }
        destroyActionModeIfNeeded()
        // if nothing moved
        if (lastItemPosition == null) return
        val item = adapter.getItem(position) as? LibraryItem ?: return
        val newHeader = adapter.getSectionHeader(position) as? LibraryHeaderItem
        val libraryItems = adapter.getSectionItems(adapter.getSectionHeader(position))
            .filterIsInstance<LibraryItem>()
        val mangaIds = libraryItems.mapNotNull { (it as? LibraryItem)?.manga?.id }
        if (newHeader?.category?.id == item.manga.category) {
            presenter.rearrangeCategory(item.manga.category, mangaIds)
        } else {
            if (presenter.mangaIsInCategory(item.manga, newHeader?.category?.id)) {
                adapter.moveItem(position, lastItemPosition!!)
                snack = view?.snack(R.string.already_in_category) {
                    anchorView = anchorView()
                    view.elevation = 15f.dpToPx
                }
                return
            }
            if (newHeader?.category != null) moveMangaToCategory(
                item.manga, newHeader.category, mangaIds
            )
        }
        lastItemPosition = null
    }

    private fun moveMangaToCategory(
        manga: LibraryManga,
        category: Category?,
        mangaIds: List<Long>
    ) {
        if (category?.id == null) return
        val oldCatId = manga.category
        presenter.moveMangaToCategory(manga, category.id, mangaIds)
        snack?.dismiss()
        snack = view?.snack(
            resources!!.getString(R.string.moved_to_, category.name)
        ) {
            anchorView = anchorView()
            view.elevation = 15f.dpToPx
            setAction(R.string.undo) {
                manga.category = category.id!!
                presenter.moveMangaToCategory(manga, oldCatId, mangaIds)
            }
        }
    }

    override fun updateCategory(catId: Int): Boolean {
        val category = (adapter.getItem(catId) as? LibraryHeaderItem)?.category ?: return false
        val inQueue = LibraryUpdateService.categoryInQueue(category.id)
        snack?.dismiss()
        snack = view?.snack(
            resources!!.getString(
                when {
                    inQueue -> R.string._already_in_queue
                    LibraryUpdateService.isRunning() -> R.string.adding_category_to_queue
                    else -> R.string.updating_
                }, category.name
            ), Snackbar.LENGTH_LONG
        ) {
            anchorView = anchorView()
            view.elevation = 15f.dpToPx
            setAction(R.string.cancel) {
                LibraryUpdateService.stop(context)
                Handler().post { NotificationReceiver.dismissNotification(context, Notifications.ID_LIBRARY_PROGRESS) }
            }
        }
        if (!inQueue) LibraryUpdateService.start(view!!.context, category)
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

    override fun manageCategory(position: Int) {
        val category = (adapter.getItem(position) as? LibraryHeaderItem)?.category ?: return
        if (!category.isDynamic) {
            ManageCategoryDialog(this, category).showDialog(router)
        }
    }

    override fun sortCategory(catId: Int, sortBy: Int) {
        presenter.sortCategory(catId, sortBy)
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
        when {
            filter_bottom_sheet.sheetBehavior.isHidden() -> filter_bottom_sheet.sheetBehavior?.collapse()
            !filter_bottom_sheet.sheetBehavior.isExpanded() -> filter_bottom_sheet.sheetBehavior?.expand()
            else -> DisplayBottomSheet(this).show()
        }
    }

    override fun toggleSheet() {
        closeTip()
        when {
            filter_bottom_sheet.sheetBehavior.isHidden() -> filter_bottom_sheet.sheetBehavior?.collapse()
            !filter_bottom_sheet.sheetBehavior.isExpanded() -> filter_bottom_sheet.sheetBehavior?.expand()
            else -> filter_bottom_sheet.sheetBehavior?.hide()
        }
    }

    override fun sheetIsExpanded(): Boolean = false

    override fun handleSheetBack(): Boolean {
        if (recycler_cover.isClickable) {
            showCategories(false)
            return true
        }
        if (filter_bottom_sheet.sheetBehavior.isExpanded()) {
            filter_bottom_sheet.sheetBehavior?.collapse()
            return true
        }
        return false
    }
    //endregion

    //region Toolbar options methods
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.library, menu)

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = resources?.getString(R.string.library_search_hint)

        searchItem.collapseActionView()
        if (query.isNotEmpty()) {
            searchItem.expandActionView()
            searchView.setQuery(query, true)
            searchView.clearFocus()
        }

        setOnQueryTextChangeListener(searchView) { search(it) }
        searchItem.fixExpand(onExpand = { invalidateMenuOnExpand() })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_search -> expandActionViewFromInteraction = true
            R.id.action_display_options -> DisplayBottomSheet(this).show()
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

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.library_selection, menu)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val count = selectedMangas.size
        // Destroy action mode if there are no items selected.
        if (count == 0) destroyActionModeIfNeeded()
        else mode.title = resources?.getString(R.string.selected_, count)
        return false
    }
    //endregion

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_move_to_category -> showChangeMangaCategoriesDialog()
            R.id.action_delete -> {
                MaterialDialog(activity!!).message(R.string.remove_from_library_question)
                    .positiveButton(R.string.remove) {
                        deleteMangasFromLibrary()
                    }.negativeButton(android.R.string.no).show()
            }
            R.id.action_migrate -> {
                val skipPre = preferences.skipPreMigration().getOrDefault()
                PreMigrationController.navigateToMigration(skipPre, router, selectedMangas.mapNotNull { it.id })
                destroyActionModeIfNeeded()
            }
            else -> return false
        }
        return true
    }

    private fun deleteMangasFromLibrary() {
        val mangas = selectedMangas.toList()
        presenter.removeMangaFromLibrary(mangas)
        destroyActionModeIfNeeded()
        snack?.dismiss()
        snack = view?.snack(
            activity?.getString(R.string.removed_from_library) ?: "", Snackbar.LENGTH_INDEFINITE
        ) {
            anchorView = anchorView()
            view.elevation = 15f.dpToPx
            var undoing = false
            setAction(R.string.undo) {
                presenter.reAddMangas(mangas)
                undoing = true
            }
            addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    super.onDismissed(transientBottomBar, event)
                    if (!undoing) presenter.confirmDeletion(mangas)
                }
            })
        }
        (activity as? MainActivity)?.setUndoSnackBar(snack)
    }

    override fun updateCategoriesForMangas(mangas: List<Manga>, categories: List<Category>) {
        presenter.moveMangasToCategories(categories, mangas)
        destroyActionModeIfNeeded()
    }

    /**
     * Move the selected manga to a list of categories.
     */
    private fun showChangeMangaCategoriesDialog() {
        // Create a copy of selected manga
        val mangas = selectedMangas.toList()

        // Hide the default category because it has a different behavior than the ones from db.
        val categories = presenter.allCategories.filter { it.id != 0 }

        // Get indexes of the common categories to preselect.
        val commonCategoriesIndexes =
            presenter.getCommonCategories(mangas).map { categories.indexOf(it) }.toTypedArray()

        ChangeMangaCategoriesDialog(this, mangas, categories, commonCategoriesIndexes).showDialog(
            router
        )
    }
}
