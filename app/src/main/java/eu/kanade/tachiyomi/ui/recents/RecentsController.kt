package eu.kanade.tachiyomi.ui.recents

import android.app.Activity
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import androidx.appcompat.widget.SearchView
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.backup.BackupRestoreService
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadService
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.databinding.RecentsControllerBinding
import eu.kanade.tachiyomi.ui.base.controller.BaseCoroutineController
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.main.BottomSheetController
import eu.kanade.tachiyomi.ui.main.FloatingSearchInterface
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.main.RootSearchInterface
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.recents.options.TabbedRecentsOptionsSheet
import eu.kanade.tachiyomi.ui.source.browse.ProgressItem
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getBottomGestureInsets
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.spToPx
import eu.kanade.tachiyomi.util.system.toInt
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.collapse
import eu.kanade.tachiyomi.util.view.expand
import eu.kanade.tachiyomi.util.view.hide
import eu.kanade.tachiyomi.util.view.isCollapsed
import eu.kanade.tachiyomi.util.view.isExpanded
import eu.kanade.tachiyomi.util.view.requestFilePermissionsSafe
import eu.kanade.tachiyomi.util.view.scrollViewWith
import eu.kanade.tachiyomi.util.view.setOnQueryTextChangeListener
import eu.kanade.tachiyomi.util.view.setStyle
import eu.kanade.tachiyomi.util.view.smoothScrollToTop
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.toolbarHeight
import eu.kanade.tachiyomi.util.view.updateLayoutParams
import eu.kanade.tachiyomi.util.view.updatePaddingRelative
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Fragment that shows recently read manga.
 * Uses R.layout.fragment_recently_read.
 * UI related actions should be called from here.
 */
class RecentsController(bundle: Bundle? = null) :
    BaseCoroutineController<RecentsControllerBinding, RecentsPresenter>(bundle),
    RecentMangaAdapter.RecentsInterface,
    FlexibleAdapter.OnItemClickListener,
    FlexibleAdapter.OnItemLongClickListener,
    FlexibleAdapter.OnItemMoveListener,
    FlexibleAdapter.EndlessScrollListener,
    RootSearchInterface,
    FloatingSearchInterface,
    BottomSheetController,
    RemoveHistoryDialog.Listener {

    init {
        setHasOptionsMenu(true)
        retainViewMode = RetainViewMode.RETAIN_DETACH
    }

    constructor(viewType: Int) : this() {
        presenter.toggleGroupRecents(viewType, false)
    }

    /**
     * Adapter containing the recent manga.
     */
    private lateinit var adapter: RecentMangaAdapter
    var displaySheet: TabbedRecentsOptionsSheet? = null

    private var progressItem: ProgressItem? = null
    override var presenter = RecentsPresenter(this)
    private var snack: Snackbar? = null
    private var lastChapterId: Long? = null
    private var showingDownloads = false
    var headerHeight = 0
    val shadowAlpha = 0.15f
    val shadow2Alpha = 0.05f
    private var query = ""
        set(value) {
            field = value
            presenter.query = value
        }

    override fun getTitle(): String? {
        return if (showingDownloads) {
            resources?.getString(R.string.download_queue)
        } else searchTitle(
            view?.context?.getString(
                when (presenter.viewType) {
                    RecentsPresenter.VIEW_TYPE_ONLY_HISTORY -> R.string.history
                    RecentsPresenter.VIEW_TYPE_ONLY_UPDATES -> R.string.updates
                    else -> R.string.updates_and_history
                }
            )?.lowercase(Locale.ROOT)
        )
    }

    override fun createBinding(inflater: LayoutInflater) = RecentsControllerBinding.inflate(inflater)

    /**
     * Called when view is created
     *
     * @param view created view
     */
    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        // Initialize adapter
        adapter = RecentMangaAdapter(this)
        adapter.setPreferenceFlows()
        binding.recycler.adapter = adapter
        binding.recycler.layoutManager = LinearLayoutManager(view.context)
        binding.recycler.setHasFixedSize(true)
        binding.recycler.recycledViewPool.setMaxRecycledViews(0, 0)
        binding.recycler.addItemDecoration(
            RecentMangaDivider(view.context)
        )
        adapter.isSwipeEnabled = true
        adapter.itemTouchHelperCallback.setSwipeFlags(
            ItemTouchHelper.LEFT
        )
        val attrsArray = intArrayOf(android.R.attr.actionBarSize)
        val array = view.context.obtainStyledAttributes(attrsArray)
        val appBarHeight = array.getDimensionPixelSize(0, 0)
        array.recycle()
        binding.swipeRefresh.setStyle()
        scrollViewWith(
            binding.recycler,
            swipeRefreshLayout = binding.swipeRefresh,
            includeTabView = true,
            afterInsets = {
                headerHeight = it.systemWindowInsetTop + appBarHeight + 48.dpToPx
                binding.fakeAppBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    height = it.systemWindowInsetTop + (toolbarHeight ?: appBarHeight)
                }
                binding.recycler.updatePaddingRelative(
                    bottom = activityBinding?.bottomNav?.height ?: it.systemWindowInsetBottom
                )
                binding.recentsEmptyView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    topMargin = headerHeight
                    bottomMargin = activityBinding?.bottomNav?.height ?: it.systemWindowInsetBottom
                }
                if (activityBinding?.bottomNav == null) {
                    setBottomPadding()
                }
            },
            onBottomNavUpdate = {
                setBottomPadding()
            }
        )
        binding.recycler.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    binding.fakeAppBar.y = activityBinding?.appBar?.y ?: 0f
                }
            }
        )

        activityBinding?.root?.post {
            val height = activityBinding?.bottomNav?.height ?: view.rootWindowInsets?.systemWindowInsetBottom ?: 0
            binding.recycler.updatePaddingRelative(bottom = height)
            binding.downloadBottomSheet.dlRecycler.updatePaddingRelative(
                bottom = height
            )
            val isExpanded = binding.downloadBottomSheet.root.sheetBehavior.isExpanded()
            activityBinding?.tabsFrameLayout?.isVisible = !isExpanded
            if (isExpanded) {
                (activity as? MainActivity)?.showTabBar(show = false, animate = false)
            }
            val isCollapsed = binding.downloadBottomSheet.root.sheetBehavior.isCollapsed()
            binding.shadow2.alpha = if (isCollapsed) shadow2Alpha else 0f
            binding.shadow.alpha = if (isCollapsed) shadowAlpha else 0f
            binding.fakeAppBar.alpha = if (isExpanded) 1f else 0f
            binding.downloadBottomSheet.dlRecycler.alpha = isExpanded.toInt().toFloat()
            binding.downloadBottomSheet.sheetLayout.backgroundTintList = ColorStateList.valueOf(
                ColorUtils.blendARGB(
                    view.context.getResourceColor(R.attr.colorPrimaryVariant),
                    view.context.getResourceColor(R.attr.background),
                    isExpanded.toInt().toFloat()
                )
            )
            binding.downloadBottomSheet.root.backgroundTintList = binding.downloadBottomSheet.sheetLayout.backgroundTintList
            updateTitleAndMenu()
        }

        if (presenter.recentItems.isNotEmpty()) {
            adapter.updateDataSet(presenter.recentItems)
        } else {
            binding.frameLayout.alpha = 0f
        }

        binding.downloadBottomSheet.dlBottomSheet.onCreate(this)

        binding.shadow2.alpha =
            if (binding.downloadBottomSheet.dlBottomSheet.sheetBehavior?.state == BottomSheetBehavior.STATE_COLLAPSED) shadow2Alpha else 0f
        binding.shadow.alpha =
            if (binding.downloadBottomSheet.dlBottomSheet.sheetBehavior?.state == BottomSheetBehavior.STATE_COLLAPSED) shadowAlpha else 0f

        binding.downloadBottomSheet.dlBottomSheet.sheetBehavior?.addBottomSheetCallback(
            object :
                BottomSheetBehavior.BottomSheetCallback() {
                override fun onSlide(bottomSheet: View, progress: Float) {
                    binding.shadow2.alpha = (1 - abs(progress)) * shadow2Alpha
                    binding.shadow.alpha = (1 - abs(progress)) * shadowAlpha
                    val height =
                        binding.root.height - binding.downloadBottomSheet.dlRecycler.paddingTop
                    // Doing some fun math to hide the tab bar just as the title text of the
                    // dl sheet is under the toolbar
                    val cap = height * (1 / 12600f) + 479f / 700
                    activityBinding?.appBar?.elevation = min(
                        (1f - progress / cap) * 15f,
                        if (binding.recycler.canScrollVertically(-1)) 15f else 0f
                    ).coerceIn(0f, 15f)
                    binding.fakeAppBar.alpha = max(0f, (progress - cap) / (1f - cap))
                    binding.downloadBottomSheet.sheetLayout.alpha = 1 - max(0f, progress / cap)
                    binding.downloadBottomSheet.dlRecycler.alpha = progress * 10
                    binding.downloadBottomSheet.sheetLayout.backgroundTintList = ColorStateList.valueOf(
                        ColorUtils.blendARGB(
                            view.context.getResourceColor(R.attr.colorPrimaryVariant),
                            view.context.getResourceColor(R.attr.background),
                            (progress * 2f).coerceIn(0f, 1f)
                        )
                    )
                    binding.downloadBottomSheet.root.backgroundTintList = binding.downloadBottomSheet.sheetLayout.backgroundTintList
                    activityBinding?.appBar?.y = max(
                        activityBinding!!.appBar.y,
                        -headerHeight * (1 - progress)
                    )
                    binding.fakeAppBar.y = activityBinding?.appBar?.y ?: 0f
                    activityBinding?.tabsFrameLayout?.let { tabs ->
                        tabs.alpha = 1 - max(0f, progress / cap)
                        if (tabs.alpha <= 0 && tabs.isVisible) {
                            tabs.isVisible = false
                        } else if (tabs.alpha > 0 && !tabs.isVisible) {
                            tabs.isVisible = true
                        }
                    }
                    val oldShow = showingDownloads
                    showingDownloads = progress > 0.92f
                    if (oldShow != showingDownloads) {
                        updateTitleAndMenu()
                        activity?.invalidateOptionsMenu()
                    }
                }

                override fun onStateChanged(p0: View, state: Int) {
                    if (this@RecentsController.view == null) return
                    if (state == BottomSheetBehavior.STATE_EXPANDED) activityBinding?.appBar?.y = 0f
                    if (state == BottomSheetBehavior.STATE_EXPANDED || state == BottomSheetBehavior.STATE_COLLAPSED) {
                        binding.downloadBottomSheet.sheetLayout.alpha =
                            if (state == BottomSheetBehavior.STATE_COLLAPSED) 1f else 0f
                        showingDownloads = state == BottomSheetBehavior.STATE_EXPANDED
                        updateTitleAndMenu()
                        activity?.invalidateOptionsMenu()
                    }

                    activityBinding?.tabsFrameLayout?.isVisible =
                        state != BottomSheetBehavior.STATE_EXPANDED
                    if (state == BottomSheetBehavior.STATE_COLLAPSED) {
                        if (hasQueue()) {
                            binding.downloadBottomSheet.dlBottomSheet.sheetBehavior?.isHideable =
                                false
                        } else {
                            binding.downloadBottomSheet.dlBottomSheet.sheetBehavior?.isHideable =
                                true
                            binding.downloadBottomSheet.dlBottomSheet.sheetBehavior?.state =
                                BottomSheetBehavior.STATE_HIDDEN
                        }
                    } else if (state == BottomSheetBehavior.STATE_HIDDEN) {
                        if (!hasQueue()) {
                            binding.downloadBottomSheet.dlBottomSheet.sheetBehavior?.skipCollapsed =
                                true
                        } else {
                            binding.downloadBottomSheet.dlBottomSheet.sheetBehavior?.skipCollapsed =
                                false
                            binding.downloadBottomSheet.dlBottomSheet.sheetBehavior?.state =
                                BottomSheetBehavior.STATE_COLLAPSED
                        }
                    }

                    if (presenter.downloadManager.hasQueue()) {
                        binding.downloadBottomSheet.downloadFab.alpha = 1f
                        if (state == BottomSheetBehavior.STATE_EXPANDED) {
                            binding.downloadBottomSheet.downloadFab.show()
                        } else {
                            binding.downloadBottomSheet.downloadFab.hide()
                        }
                    }
                    if (state == BottomSheetBehavior.STATE_HIDDEN || state == BottomSheetBehavior.STATE_COLLAPSED) {
                        binding.shadow2.alpha =
                            if (state == BottomSheetBehavior.STATE_COLLAPSED) shadow2Alpha else 0f
                        binding.shadow.alpha =
                            if (state == BottomSheetBehavior.STATE_COLLAPSED) shadowAlpha else 0f
                    }

                    binding.downloadBottomSheet.sheetLayout.isClickable =
                        state == BottomSheetBehavior.STATE_COLLAPSED
                    binding.downloadBottomSheet.sheetLayout.isFocusable =
                        state == BottomSheetBehavior.STATE_COLLAPSED
                    setPadding(binding.downloadBottomSheet.dlBottomSheet.sheetBehavior?.isHideable == true)
                }
            }
        )
        binding.swipeRefresh.isRefreshing = LibraryUpdateService.isRunning()
        binding.swipeRefresh.setOnRefreshListener {
            if (!LibraryUpdateService.isRunning()) {
                snack?.dismiss()
                snack = view.snack(R.string.updating_library) {
                    anchorView =
                        if (binding.downloadBottomSheet.root.sheetBehavior.isCollapsed()) {
                            binding.downloadBottomSheet.root
                        } else {
                            activityBinding?.bottomNav ?: binding.downloadBottomSheet.root
                        }
                    setAction(R.string.cancel) {
                        LibraryUpdateService.stop(context)
                        Handler().post {
                            NotificationReceiver.dismissNotification(
                                context,
                                Notifications.ID_LIBRARY_PROGRESS
                            )
                        }
                    }
                    addCallback(
                        object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                                super.onDismissed(transientBottomBar, event)
                                binding.swipeRefresh.isRefreshing = LibraryUpdateService.isRunning()
                            }
                        }
                    )
                }
                LibraryUpdateService.start(view.context)
            }
        }

        if (showingDownloads) {
            binding.downloadBottomSheet.dlBottomSheet.sheetBehavior?.expand()
        }
        setPadding(binding.downloadBottomSheet.dlBottomSheet.sheetBehavior?.isHideable == true)
        requestFilePermissionsSafe(301, presenter.preferences)

        binding.downloadBottomSheet.root.sheetBehavior?.isGestureInsetBottomIgnored = true
    }

    fun updateTitleAndMenu() {
        if (router.backstack.lastOrNull()?.controller == this) {
            (activity as? MainActivity)?.setFloatingToolbar(!showingDownloads, true)
            setTitle()
        }
    }

    private fun setBottomPadding() {
        val bottomBar = activityBinding?.bottomNav
        val pad = bottomBar?.translationY?.minus(bottomBar.height) ?: 0f
        val padding = max(
            (-pad).toInt(),
            view?.rootWindowInsets?.getBottomGestureInsets() ?: 0
        )
        binding.shadow2.translationY = pad
        binding.downloadBottomSheet.dlBottomSheet.sheetBehavior?.peekHeight = 48.spToPx + padding
        binding.downloadBottomSheet.fastScroller.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = -pad.toInt()
        }
        binding.downloadBottomSheet.dlRecycler.updatePaddingRelative(
            bottom = max(-pad.toInt(), view?.rootWindowInsets?.systemWindowInsetBottom ?: 0) +
                binding.downloadBottomSheet.downloadFab.height + 20.dpToPx
        )
        binding.downloadBottomSheet.downloadFab.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = max(-pad.toInt(), view?.rootWindowInsets?.systemWindowInsetBottom ?: 0) + 16.dpToPx
        }
        setPadding(binding.downloadBottomSheet.dlBottomSheet.sheetBehavior?.isHideable == true)
    }

    fun setRefreshing(refresh: Boolean) {
        binding.swipeRefresh.isRefreshing = refresh
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int) { }

    override fun shouldMoveItem(fromPosition: Int, toPosition: Int) = true

    override fun onActionStateChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        binding.swipeRefresh.isEnabled = actionState != ItemTouchHelper.ACTION_STATE_SWIPE ||
            binding.swipeRefresh.isRefreshing
    }

    override fun handleSheetBack(): Boolean {
        if (showingDownloads) {
            binding.downloadBottomSheet.dlBottomSheet.dismiss()
            return true
        }
        if (presenter.preferences.recentsViewType().get() != presenter.viewType) {
            tempJumpTo(presenter.preferences.recentsViewType().get())
            return true
        }
        return false
    }

    fun setPadding(sheetIsHidden: Boolean, insets: WindowInsets? = null) {
        val peekHeight = binding.downloadBottomSheet.dlBottomSheet.sheetBehavior?.peekHeight ?: 0
        val cInsets = insets ?: view?.rootWindowInsets ?: return
        binding.recycler.updatePaddingRelative(
            bottom = if (sheetIsHidden) {
                activityBinding?.bottomNav?.height ?: cInsets.systemWindowInsetBottom
            } else {
                peekHeight
            }
        )
    }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        if (!isBindingInitialized) return
        if (!presenter.isLoading) {
            refresh()
        }
        setBottomPadding()
        binding.downloadBottomSheet.dlBottomSheet.update()
    }

    override fun onDestroy() {
        super.onDestroy()
        snack?.dismiss()
        snack = null
    }

    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
        displaySheet?.dismiss()
        displaySheet = null
    }

    fun refresh() = presenter.getRecents()

    fun showLists(
        recents: List<RecentMangaItem>,
        hasNewItems: Boolean,
        shouldMoveToTop: Boolean = false
    ) {
        if (view == null) return
        binding.progress.isVisible = false
        binding.frameLayout.alpha = 1f
        binding.swipeRefresh.isRefreshing = LibraryUpdateService.isRunning()
        adapter.removeAllScrollableHeaders()
        adapter.updateItems(recents)
        adapter.onLoadMoreComplete(null)
        if (!hasNewItems || presenter.viewType == RecentsPresenter.VIEW_TYPE_GROUP_ALL ||
            recents.isEmpty()
        ) {
            loadNoMore()
        } else if (hasNewItems && presenter.viewType != RecentsPresenter.VIEW_TYPE_GROUP_ALL) {
            resetProgressItem()
        }
        if (recents.isEmpty()) {
            binding.recentsEmptyView.show(
                if (!isSearching()) R.drawable.ic_history_off_24dp
                else R.drawable.ic_search_off_24dp,
                if (isSearching()) R.string.no_results_found
                else when (presenter.viewType) {
                    RecentsPresenter.VIEW_TYPE_ONLY_UPDATES -> R.string.no_recent_chapters
                    RecentsPresenter.VIEW_TYPE_ONLY_HISTORY -> R.string.no_recently_read_manga
                    else -> R.string.no_recent_read_updated_manga
                }
            )
        } else {
            binding.recentsEmptyView.hide()
        }
        if (shouldMoveToTop) {
            binding.recycler.scrollToPosition(0)
        }
        if (lastChapterId != null) {
            refreshItem(lastChapterId ?: 0L)
            lastChapterId = null
        }
    }

    fun updateChapterDownload(download: Download, updateDLSheet: Boolean = true) {
        if (view == null) return
        if (updateDLSheet) {
            binding.downloadBottomSheet.dlBottomSheet.update()
            binding.downloadBottomSheet.dlBottomSheet.onUpdateProgress(download)
            binding.downloadBottomSheet.dlBottomSheet.onUpdateDownloadedPages(download)
        }
        val id = download.chapter.id ?: return
        val holder = binding.recycler.findViewHolderForItemId(id) as? RecentMangaHolder ?: return
        holder.notifyStatus(download.status, download.progress, download.chapter.read, true)
    }

    fun updateDownloadStatus() {
        binding.downloadBottomSheet.dlBottomSheet.update()
    }

    private fun refreshItem(chapterId: Long) {
        val recentItemPos = adapter.currentItems.indexOfFirst {
            it is RecentMangaItem &&
                it.mch.chapter.id == chapterId
        }
        if (recentItemPos > -1) adapter.notifyItemChanged(recentItemPos)
    }

    override fun downloadChapter(position: Int) {
        val view = view ?: return
        val item = adapter.getItem(position) as? RecentMangaItem ?: return
        val chapter = item.chapter
        val manga = item.mch.manga
        if (item.status != Download.State.NOT_DOWNLOADED && item.status != Download.State.ERROR) {
            presenter.deleteChapter(chapter, manga)
        } else {
            if (item.status == Download.State.ERROR) DownloadService.start(view.context)
            else presenter.downloadChapter(manga, chapter)
        }
    }

    override fun startDownloadNow(position: Int) {
        val chapter = (adapter.getItem(position) as? RecentMangaItem)?.chapter ?: return
        presenter.startDownloadChapterNow(chapter)
    }

    override fun onCoverClick(position: Int) {
        val manga = (adapter.getItem(position) as? RecentMangaItem)?.mch?.manga ?: return
        router.pushController(MangaDetailsController(manga).withFadeTransaction())
    }

    override fun onRemoveHistoryClicked(position: Int) {
        onItemLongClick(position)
    }

    fun tempJumpTo(viewType: Int) {
        presenter.toggleGroupRecents(viewType, false)
        activityBinding?.mainTabs?.selectTab(activityBinding?.mainTabs?.getTabAt(viewType))
        updateTitleAndMenu()
    }

    private fun setViewType(viewType: Int) {
        if (viewType != presenter.viewType) {
            presenter.toggleGroupRecents(viewType)
            updateTitleAndMenu()
        }
    }

    override fun getViewType(): Int = presenter.viewType

    override fun scope() = viewScope

    override fun onItemClick(view: View?, position: Int): Boolean {
        val item = adapter.getItem(position) ?: return false
        if (item is RecentMangaItem) {
            if (item.mch.manga.id == null) {
                val headerItem = adapter.getHeaderOf(item) as? RecentMangaHeaderItem
                tempJumpTo(
                    when (headerItem?.recentsType) {
                        RecentMangaHeaderItem.NEW_CHAPTERS -> RecentsPresenter.VIEW_TYPE_ONLY_UPDATES
                        RecentMangaHeaderItem.CONTINUE_READING -> RecentsPresenter.VIEW_TYPE_ONLY_HISTORY
                        else -> return false
                    }
                )
            } else {
                val activity = activity ?: return false
                val intent = ReaderActivity.newIntent(activity, item.mch.manga, item.chapter)
                startActivity(intent)
            }
        } else if (item is RecentMangaHeaderItem) return false
        return true
    }

    override fun onItemLongClick(position: Int) {
        val item = adapter.getItem(position) as? RecentMangaItem ?: return
        val manga = item.mch.manga
        val history = item.mch.history
        val chapter = item.mch.chapter
        if (history.id != null) {
            RemoveHistoryDialog(this, manga, history, chapter).showDialog(router)
        }
    }

    override fun removeHistory(manga: Manga, history: History, all: Boolean) {
        if (all) {
            // Reset last read of chapter to 0L
            presenter.removeAllFromHistory(manga.id!!)
        } else {
            // Remove all chapters belonging to manga from library
            presenter.removeFromHistory(history)
        }
    }

    override fun markAsRead(position: Int) {
        val item = adapter.getItem(position) as? RecentMangaItem ?: return
        val chapter = item.chapter
        val manga = item.mch.manga
        val lastRead = chapter.last_page_read
        val pagesLeft = chapter.pages_left
        lastChapterId = chapter.id
        val wasRead = chapter.read
        presenter.markChapterRead(chapter, !wasRead)
        snack = view?.snack(
            if (wasRead) R.string.marked_as_unread
            else R.string.marked_as_read,
            Snackbar.LENGTH_INDEFINITE
        ) {
            anchorView = activityBinding?.bottomNav
            var undoing = false
            setAction(R.string.undo) {
                presenter.markChapterRead(chapter, wasRead, lastRead, pagesLeft)
                undoing = true
            }
            addCallback(
                object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        super.onDismissed(transientBottomBar, event)
                        if (!undoing && presenter.preferences.removeAfterMarkedAsRead() &&
                            !wasRead
                        ) {
                            lastChapterId = chapter.id
                            presenter.deleteChapter(chapter, manga)
                        }
                    }
                }
            )
        }
        (activity as? MainActivity)?.setUndoSnackBar(snack)
    }

    override fun isSearching() = query.isNotEmpty()

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (onRoot) (activity as? MainActivity)?.setDismissIcon(showingDownloads)
        if (showingDownloads) {
            inflater.inflate(R.menu.download_queue, menu)
        } else {
            inflater.inflate(R.menu.recents, menu)

            val searchItem = menu.findItem(R.id.action_search)
            val searchView = searchItem.actionView as SearchView
            searchView.queryHint = view?.context?.getString(R.string.search_recents)
            searchItem.collapseActionView()
            if (isSearching()) {
                searchItem.expandActionView()
                searchView.setQuery(query, true)
                searchView.clearFocus()
            }
            setOnQueryTextChangeListener(searchView) {
                if (query != it) {
                    query = it ?: return@setOnQueryTextChangeListener false
                    // loadNoMore()
                    resetProgressItem()
                    refresh()
                }
                true
            }
            searchItem.fixExpandInvalidate()
            hideItemsIfExpanded(searchItem, menu)
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        if (showingDownloads) binding.downloadBottomSheet.dlBottomSheet.prepareMenu(menu)
    }

    override fun onChangeStarted(handler: ControllerChangeHandler, type: ControllerChangeType) {
        super.onChangeStarted(handler, type)
        if (type.isEnter) {
            if (type == ControllerChangeType.POP_ENTER) presenter.onCreate()
            binding.downloadBottomSheet.dlBottomSheet.dismiss()
            activityBinding?.mainTabs?.let { tabs ->
                tabs.removeAllTabs()
                tabs.clearOnTabSelectedListeners()
                val selectedTab = presenter.viewType
                listOf(
                    R.string.grouped,
                    R.string.all,
                    R.string.history,
                    R.string.updates
                ).forEachIndexed { index, resId ->
                    tabs.addTab(
                        tabs.newTab().setText(resId).also { tab ->
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                tab.view.tooltipText = null
                            }
                        },
                        index == selectedTab
                    )
                }
                tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                    override fun onTabSelected(tab: TabLayout.Tab?) {
                        setViewType(tab?.position ?: 0)
                    }

                    override fun onTabUnselected(tab: TabLayout.Tab?) {}
                    override fun onTabReselected(tab: TabLayout.Tab?) {
                        binding.recycler.smoothScrollToTop()
                    }
                })
                (activity as? MainActivity)?.showTabBar(true)
            }
        } else {
            if (type == ControllerChangeType.POP_EXIT) presenter.onDestroy()
            if (router.backstack.lastOrNull()?.controller !is DialogController) {
                (activity as? MainActivity)?.showTabBar(false)
            }
            snack?.dismiss()
        }
        setBottomPadding()
    }

    override fun onChangeEnded(handler: ControllerChangeHandler, type: ControllerChangeType) {
        super.onChangeEnded(handler, type)
        if (type == ControllerChangeType.POP_ENTER) {
            setBottomPadding()
        }
        if (type.isEnter) {
            updateTitleAndMenu()
        }
    }

    fun hasQueue() = presenter.downloadManager.hasQueue()

    override fun showSheet() {
        if (!isBindingInitialized) return
        if (binding.downloadBottomSheet.dlBottomSheet.sheetBehavior?.isHideable == false || hasQueue()) {
            binding.downloadBottomSheet.dlBottomSheet.sheetBehavior?.expand()
        }
    }

    override fun hideSheet() {
        if (!isBindingInitialized) return
        if (binding.downloadBottomSheet.dlBottomSheet.sheetBehavior?.isHideable == true) {
            binding.downloadBottomSheet.dlBottomSheet.sheetBehavior?.hide()
        } else {
            binding.downloadBottomSheet.dlBottomSheet.sheetBehavior?.collapse()
        }
    }

    override fun toggleSheet() {
        if (showingDownloads) binding.downloadBottomSheet.dlBottomSheet.dismiss()
        else binding.downloadBottomSheet.dlBottomSheet.sheetBehavior?.expand()
    }

    override fun sheetIsExpanded(): Boolean = binding.downloadBottomSheet.dlBottomSheet.sheetBehavior.isExpanded()

    override fun expandSearch() {
        if (showingDownloads) {
            binding.downloadBottomSheet.dlBottomSheet.dismiss()
        } else {
            activityBinding?.cardToolbar?.menu?.findItem(R.id.action_search)?.expandActionView()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (showingDownloads) {
            return binding.downloadBottomSheet.dlBottomSheet.onOptionsItemSelected(item)
        }
        when (item.itemId) {
            R.id.display_options -> {
                displaySheet = TabbedRecentsOptionsSheet(
                    this,
                    (presenter.viewType - 1).coerceIn(0, 2)
                )
                displaySheet?.show()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun noMoreLoad(newItemsSize: Int) {}

    override fun onLoadMore(lastPosition: Int, currentPage: Int) {
        val view = view ?: return
        if (presenter.finished ||
            BackupRestoreService.isRunning(view.context.applicationContext) ||
            (
                presenter.viewType == RecentsPresenter.VIEW_TYPE_GROUP_ALL &&
                    !isSearching()
                )
        ) {
            loadNoMore()
            return
        }
        presenter.requestNext()
    }

    private fun loadNoMore() {
        adapter.onLoadMoreComplete(null)
    }

    /**
     * Sets a new progress item and reenables the scroll listener.
     */
    private fun resetProgressItem() {
        adapter.onLoadMoreComplete(null)
        progressItem = ProgressItem()
        adapter.setEndlessScrollListener(this, progressItem!!)
    }
}
