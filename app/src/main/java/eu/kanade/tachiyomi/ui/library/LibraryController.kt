package eu.kanade.tachiyomi.ui.library

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.util.TypedValue
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
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.reddit.indicatorfastscroll.FastScrollerView
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
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.dpToPxEnd
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.view.applyWindowInsetsForRootController
import eu.kanade.tachiyomi.util.view.doOnApplyWindowInsets
import eu.kanade.tachiyomi.util.view.getItemView
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.hide
import eu.kanade.tachiyomi.util.view.marginTop
import eu.kanade.tachiyomi.util.view.setBackground
import eu.kanade.tachiyomi.util.view.setOnQueryTextChangeListener
import eu.kanade.tachiyomi.util.view.setStartTranslationX
import eu.kanade.tachiyomi.util.view.setStyle
import eu.kanade.tachiyomi.util.view.show
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.updateLayoutParams
import eu.kanade.tachiyomi.util.view.updatePaddingRelative
import eu.kanade.tachiyomi.util.view.visibleIf
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import kotlinx.android.synthetic.main.filter_bottom_sheet.*
import kotlinx.android.synthetic.main.library_grid_recycler.*
import kotlinx.android.synthetic.main.library_list_controller.*
import kotlinx.android.synthetic.main.main_activity.*
import kotlinx.coroutines.delay
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.abs
import kotlin.math.roundToInt

class LibraryController(
    bundle: Bundle? = null,
    private val preferences: PreferencesHelper = Injekt.get()
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

    private var singleCategory: Boolean = false

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

    var snack: Snackbar? = null

    private var scrollDistance = 0f
    private val scrollDistanceTilHidden = 1000.dpToPx

    private var textAnim: ViewPropertyAnimator? = null
    private var scrollAnim: ViewPropertyAnimator? = null
    private var alwaysShowScroller: Boolean = preferences.alwaysShowSeeker().getOrDefault()
    private var filterTooltip: ViewTooltip? = null
    private var elevationAnim: ValueAnimator? = null
    private var elevate = false
    override fun getTitle(): String? {
        return view?.context?.getString(R.string.library)
    }

    private var scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            val notAtTop = recycler.canScrollVertically(-1)
            if (notAtTop != elevate) elevateFunc(notAtTop)
            val order = getCategoryOrder()
            if (filter_bottom_sheet.sheetBehavior?.state != BottomSheetBehavior.STATE_HIDDEN) {
                scrollDistance += abs(dy)
                if (scrollDistance > scrollDistanceTilHidden) {
                    filter_bottom_sheet.sheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
                    scrollDistance = 0f
                }
            } else scrollDistance = 0f
            if (order != null && order != activeCategory && lastItem == null) {
                preferences.lastUsedCategory().set(order)
                activeCategory = order
                setActiveCategory()
                if (presenter.categories.size > 1 && dy != 0) {
                    val headerItem = getHeader() ?: return
                    val view = fast_scroller?.getChildAt(0) ?: return
                    val index = adapter.headerItems.indexOf(headerItem)
                    textAnim?.cancel()
                    textAnim = text_view_m.animate().alpha(0f).setDuration(250L).setStartDelay(2000)
                    textAnim?.start()

                    // fastScroll height * indicator position - center text - fastScroll padding
                    text_view_m.translationY = view.height *
                        (index.toFloat() / (adapter.headerItems.size + 1))
                    - text_view_m.height / 2 + 16.dpToPx
                    text_view_m.translationX = 45f.dpToPxEnd
                    text_view_m.alpha = 1f
                    text_view_m.text = headerItem.category.name
                }
            }
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)
            if (alwaysShowScroller) return
            when (newState) {
                RecyclerView.SCROLL_STATE_DRAGGING -> {
                    scrollAnim?.cancel()
                    if (fast_scroller?.translationX != 0f) {
                        fast_scroller?.show()
                    }
                }
                RecyclerView.SCROLL_STATE_IDLE -> {
                    scrollAnim = fast_scroller?.hide()
                }
            }
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
        fast_scroller.setStartTranslationX(!alwaysShowScroller)
        fast_scroller.setBackground(!alwaysShowScroller)

        show_all.isChecked = preferences.showAllCategories().get()
        show_all.setOnCheckedChangeListener { _, isChecked ->
            preferences.showAllCategories().set(isChecked)
            presenter.getLibrary()
        }

        adapter = LibraryCategoryAdapter(this)
        adapter.expandItemsAtStartUp()
        adapter.isRecursiveCollapse = true
        setRecyclerLayout()
        recycler.manager.spanSizeLookup = (object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                if (libraryLayout == 0) return 1
                val item = this@LibraryController.adapter.getItem(position)
                return if (item is LibraryHeaderItem) recycler.manager.spanCount
                else if (item is LibraryItem && item.manga.isBlank()) recycler.manager.spanCount
                else 1
            }
        })
        recycler.setHasFixedSize(true)
        recycler.adapter = adapter
        fast_scroller.setupWithRecyclerView(recycler, { position ->
            val letter = adapter.getSectionText(position)
            if (!singleCategory && presenter.showAllCategories &&
                !adapter.isHeader(adapter.getItem(position)) &&
                position != adapter.itemCount - 1) null
            else if (letter != null) FastScrollItemIndicator.Text(letter)
            else FastScrollItemIndicator.Icon(R.drawable.ic_star_24dp)
        })
        fast_scroller.useDefaultScroller = false
        fast_scroller.itemIndicatorSelectedCallbacks += object :
            FastScrollerView.ItemIndicatorSelectedCallback {
            override fun onItemIndicatorSelected(
                indicator: FastScrollItemIndicator,
                indicatorCenterY: Int,
                itemPosition: Int
            ) {
                fast_scroller.translationX = 0f
                if (!alwaysShowScroller) {
                    scrollAnim?.cancel()
                    scrollAnim = fast_scroller.hide(2000)
                }

                textAnim?.cancel()
                textAnim = text_view_m.animate().alpha(0f).setDuration(250L).setStartDelay(2000)
                textAnim?.start()

                text_view_m.translationY = indicatorCenterY.toFloat() - text_view_m.height / 2
                text_view_m.translationX = 0f
                text_view_m.alpha = 1f
                text_view_m.text = adapter.onCreateBubbleText(itemPosition)
                val appbar = activity?.appbar

                if (singleCategory) {
                    val order = when (val item = adapter.getItem(itemPosition)) {
                        is LibraryHeaderItem -> item
                        is LibraryItem -> item.header
                        else -> null
                    }?.category?.order
                    if (order != null) {
                        activeCategory = order
                        preferences.lastUsedCategory().set(order)
                    }
                }
                appbar?.y = 0f
                recycler.suppressLayout(true)
                (recycler.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                    itemPosition, 0
                )
                recycler.suppressLayout(false)
            }
        }
        recycler.addOnScrollListener(scrollListener)

        val tv = TypedValue()
        activity!!.theme.resolveAttribute(R.attr.actionBarTintColor, tv, true)
        swipe_refresh.setStyle()

        recycler_cover.setOnClickListener {
            showCategories(false)
        }
        category_recycler.onCategoryClicked = {
            scrollToHeader(it)
            showCategories(false)
        }
        swipe_refresh.setDistanceToTriggerSync(150.dpToPx)
        val marginTop = category_layout.marginTop
        recycler.doOnApplyWindowInsets { recyclerView, insets, _ ->
            recyclerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = marginTop + insets.systemWindowInsetTop
            }
            category_layout?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = marginTop + insets.systemWindowInsetTop
            }
            recycler_cover?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = marginTop + insets.systemWindowInsetTop
            }
            fast_scroller?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = insets.systemWindowInsetTop
            }
            swipe_refresh?.setProgressViewOffset(
                true, (marginTop + insets.systemWindowInsetTop) + (-60).dpToPx, marginTop + insets.systemWindowInsetTop + 10.dpToPx
            )
        }

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
            }
        }

        // pad the recycler if the filter bottom sheet is visible
        val height = view.context.resources.getDimensionPixelSize(R.dimen.rounder_radius) + 4.dpToPx
        recycler.updatePaddingRelative(bottom = height)

        presenter.onRestore()
        if (presenter.libraryItems.isNotEmpty()) {
            presenter.restoreLibrary()
        } else {
            recycler_layout.alpha = 0f
            presenter.getLibrary()
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

    private fun getCategoryOrder(): Int? {
        val position =
            (recycler.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition()
        var order = when (val item = adapter.getItem(position)) {
            is LibraryHeaderItem -> item.category.order
            is LibraryItem -> presenter.categories.find { it.id == item.manga.category }?.order
            else -> null
        }
        if (order == null) {
            val fPosition =
                (recycler.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
            order = when (val item = adapter.getItem(fPosition)) {
                is LibraryHeaderItem -> item.category.order
                is LibraryItem -> presenter.categories.find { it.id == item.manga.category }?.order
                else -> null
            }
        }
        return order
    }

    fun updateShowScrollbar(show: Boolean) {
        alwaysShowScroller = show
        fast_scroller?.setBackground(!show)
        if (libraryLayout == 0) reattachAdapter()
        scrollAnim?.cancel()
        if (show) fast_scroller?.translationX = 0f
        else scrollAnim = fast_scroller?.hide()
        setRecyclerLayout()
    }

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.library_list_controller, container, false)
    }

    private fun updateLibrary(category: Category? = null) {
        val view = view ?: return
        LibraryUpdateService.start(view.context, category)
        snack = view.snack(R.string.updating_library) {
            anchorView = filter_bottom_sheet
            setAction(R.string.cancel) {
                LibraryUpdateService.stop(context)
                Handler().post { NotificationReceiver.dismissNotification(context, Notifications.ID_LIBRARY_PROGRESS) }
            }
        }
    }

    private fun setRecyclerLayout() {
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
                start = (if (alwaysShowScroller) 2 else 5).dpToPx,
                end = (if (alwaysShowScroller) 12 else 5).dpToPx
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
        actionMode = null
        super.onDestroyView(view)
    }

    fun onNextLibraryUpdate(mangaMap: List<LibraryItem>, freshStart: Boolean = false) {
        val view = view ?: return
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
            if (!alwaysShowScroller) {
                fast_scroller?.show(false)
                view.post {
                    scrollAnim = fast_scroller?.hide(2000)
                }
            }
        }
        adapter.isLongPressDragEnabled = canDrag()
        category_recycler.setCategories(presenter.categories)
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
        }
    }

    private fun showCategories(show: Boolean) {
        recycler_cover.isClickable = show
        recycler_cover.isFocusable = show
        val translateY = if (show) category_layout.height.toFloat() + 12f.dpToPx else 0f
        recycler.animate().translationY(translateY).start()
        recycler_cover.animate().translationY(translateY).start()
        recycler_cover.animate().alpha(if (show) 0.75f else 0f).start()
        if (show) {
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
            recycler.itemAnimator = null
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
            (recycler.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                headerPosition, (if (headerPosition == 0) 0 else (-32).dpToPx) + appbarOffset
            )
            recycler.suppressLayout(false)
        }
    }

    private fun onRefresh() {
        activity?.invalidateOptionsMenu()
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
        if (adapter.getItem(position) is LibraryHeaderItem) return
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
                    anchorView = filter_bottom_sheet
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
            anchorView = filter_bottom_sheet
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
            anchorView = filter_bottom_sheet
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
        if (category.id ?: 0 > -1)
            ManageCategoryDialog(this, category).showDialog(router)
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
            filter_bottom_sheet.sheetBehavior?.state == BottomSheetBehavior.STATE_HIDDEN -> filter_bottom_sheet.sheetBehavior?.state =
                BottomSheetBehavior.STATE_COLLAPSED
            filter_bottom_sheet.sheetBehavior?.state != BottomSheetBehavior.STATE_EXPANDED -> filter_bottom_sheet.sheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
            else -> DisplayBottomSheet(this).show()
        }
    }

    override fun toggleSheet() {
        closeTip()
        when {
            filter_bottom_sheet.sheetBehavior?.state == BottomSheetBehavior.STATE_HIDDEN -> filter_bottom_sheet.sheetBehavior?.state =
                BottomSheetBehavior.STATE_COLLAPSED
            filter_bottom_sheet.sheetBehavior?.state != BottomSheetBehavior.STATE_EXPANDED -> filter_bottom_sheet.sheetBehavior?.state =
                BottomSheetBehavior.STATE_EXPANDED
            filter_bottom_sheet.sheetBehavior?.isHideable == true -> filter_bottom_sheet.sheetBehavior?.state =
                BottomSheetBehavior.STATE_HIDDEN
            else -> filter_bottom_sheet.sheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    override fun sheetIsExpanded(): Boolean = false

    override fun handleSheetBack(): Boolean {
        val sheetBehavior = BottomSheetBehavior.from(filter_bottom_sheet)
        if (sheetBehavior.state != BottomSheetBehavior.STATE_COLLAPSED && sheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
            sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
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
            anchorView = filter_bottom_sheet
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
