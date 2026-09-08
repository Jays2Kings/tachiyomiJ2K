package eu.kanade.tachiyomi.ui.source

import android.app.Activity
import android.os.Build
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.RoundedCorner
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.BackEventCompat
import androidx.appcompat.widget.SearchView
import androidx.core.graphics.ColorUtils
import androidx.core.view.doOnNextLayout
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import androidx.recyclerview.widget.RecyclerView
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.BrowseControllerBinding
import eu.kanade.tachiyomi.extension.util.ExtensionInstaller
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.isIncognitoModeForSource
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.extension.ExtensionFilterController
import eu.kanade.tachiyomi.ui.main.BottomSheetController
import eu.kanade.tachiyomi.ui.main.FloatingSearchInterface
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.main.RootSearchInterface
import eu.kanade.tachiyomi.ui.setting.SettingsBrowseController
import eu.kanade.tachiyomi.ui.setting.SettingsSourcesController
import eu.kanade.tachiyomi.ui.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.source.browse.repos.RepoController
import eu.kanade.tachiyomi.ui.source.globalsearch.GlobalSearchController
import eu.kanade.tachiyomi.ui.source.searchhistory.SearchHistoryView
import eu.kanade.tachiyomi.ui.source.searchhistory.addToSearchHistory
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getBottomGestureInsets
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.rootWindowInsetsCompat
import eu.kanade.tachiyomi.util.system.spToPx
import eu.kanade.tachiyomi.util.view.GroupedRowDivider
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.checkHeightThen
import eu.kanade.tachiyomi.util.view.collapse
import eu.kanade.tachiyomi.util.view.expand
import eu.kanade.tachiyomi.util.view.isCollapsed
import eu.kanade.tachiyomi.util.view.isControllerVisible
import eu.kanade.tachiyomi.util.view.requestFilePermissionsSafe
import eu.kanade.tachiyomi.util.view.scrollViewWith
import eu.kanade.tachiyomi.util.view.setOnQueryTextChangeListener
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.toolbarHeight
import eu.kanade.tachiyomi.util.view.updateGradiantBGRadius
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import eu.kanade.tachiyomi.widget.LinearLayoutManagerAccurateOffset
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.Parcelize
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * This controller shows and manages the different catalogues enabled by the user.
 * This controller should only handle UI actions, IO actions should be done by [SourcePresenter]
 * [SourceAdapter.SourceListener] call function data on browse item click.
 */
class BrowseController :
    BaseController<BrowseControllerBinding>(),
    FlexibleAdapter.OnItemClickListener,
    SourceAdapter.SourceListener,
    RootSearchInterface,
    FloatingSearchInterface,
    BottomSheetController {
    /**
     * Application preferences.
     */
    private val preferences: PreferencesHelper = Injekt.get()

    /**
     * Adapter containing sources.
     */
    private var adapter: SourceAdapter? = null

    private var searchHistoryView: SearchHistoryView? = null

    var extQuery = ""
        private set

    var headerHeight = 0

    var showingExtensions = false

    var snackbar: Snackbar? = null

    private var ogRadius = 0f
    private var deviceRadius = 0f to 0f
    private var lastScale = 1f

    override val mainRecycler: RecyclerView
        get() = binding.sourceRecycler

    /**
     * Called when controller is initialized.
     */
    init {
        setHasOptionsMenu(true)
    }

    override fun getTitle(): String? = view?.context?.getString(R.string.browse)

    override fun getSearchTitle(): String? = searchTitle(view?.context?.getString(R.string.sources)?.lowercase(Locale.ROOT))

    val presenter = SourcePresenter(this)

    override fun createBinding(inflater: LayoutInflater) = BrowseControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        val isReturning = adapter != null
        adapter = SourceAdapter(this)
        // Create binding.sourceRecycler and set adapter.
        binding.sourceRecycler.layoutManager = LinearLayoutManagerAccurateOffset(view.context)

        binding.sourceRecycler.adapter = adapter
        binding.sourceRecycler.addItemDecoration(GroupedRowDivider(view.context, { it is SourceHolder }))
        adapter?.isSwipeEnabled = true
        adapter?.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        setUpSearchHistory()
        scrollViewWith(
            binding.sourceRecycler,
            afterInsets = {
                headerHeight = binding.sourceRecycler.paddingTop
                binding.sourceRecycler.updatePaddingRelative(
                    bottom = (activityBinding?.bottomNav?.height ?: it.getBottomGestureInsets()) + 58.spToPx,
                )
                updateSearchHistoryPadding()
                if (activityBinding?.bottomNav == null) {
                    setBottomPadding()
                }
                deviceRadius =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val wInsets = it.toWindowInsets()
                        val lCorner = wInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
                        val rCorner = wInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)
                        (lCorner?.radius?.toFloat() ?: 0f) to (rCorner?.radius?.toFloat() ?: 0f)
                    } else {
                        ogRadius to ogRadius
                    }
            },
            onBottomNavUpdate = {
                setBottomPadding()
            },
        )
        if (!isReturning) {
            activityBinding?.appBar?.lockYPos = true
        }
        binding.sourceRecycler.post {
            setBottomSheetTabs(
                if (binding.bottomSheet.root.sheetBehavior
                        .isCollapsed()
                ) {
                    0f
                } else {
                    1f
                },
            )
            binding.sourceRecycler.updatePaddingRelative(
                bottom = (activityBinding?.bottomNav?.height ?: 0) + 58.spToPx,
            )
            updateTitleAndMenu()
        }

        requestFilePermissionsSafe(301, preferences)
        binding.bottomSheet.root.onCreate(this)

        preferences
            .extensionInstaller()
            .asFlow()
            .drop(1)
            .onEach {
                binding.bottomSheet.root.setCanInstallPrivately(it == ExtensionInstaller.PRIVATE)
            }.launchIn(viewScope)

        binding.bottomSheet.root.sheetBehavior
            ?.isGestureInsetBottomIgnored = true

        binding.bottomSheet.root.sheetBehavior?.addBottomSheetCallback(
            object : BottomSheetBehavior
                .BottomSheetCallback() {
                override fun onSlide(
                    bottomSheet: View,
                    progress: Float,
                ) {
                    val oldShow = showingExtensions
                    showingExtensions = progress > 0.92f
                    if (oldShow != showingExtensions) {
                        updateTitleAndMenu()
                        (activity as? MainActivity)?.reEnableBackPressedCallBack()
                    }
                    binding.bottomSheet.root.apply {
                        if (lastScale != 1f && scaleY != 1f) {
                            val scaleProgress = ((1f - progress) * (1f - lastScale)) + lastScale
                            scaleX = scaleProgress
                            scaleY = scaleProgress
                            for (i in 0 until childCount) {
                                val childView = getChildAt(i)
                                childView.scaleY = scaleProgress
                            }
                        }
                    }
                    binding.bottomSheet.sheetToolbar.isVisible = true
                    setBottomSheetTabs(max(0f, progress))
                }

                override fun onStateChanged(
                    p0: View,
                    state: Int,
                ) {
                    if (state == BottomSheetBehavior.STATE_SETTLING) {
                        binding.bottomSheet.root.updatedNestedRecyclers()
                    } else if (state == BottomSheetBehavior.STATE_EXPANDED && binding.bottomSheet.root.isExpanding) {
                        binding.bottomSheet.root.updatedNestedRecyclers()
                        binding.bottomSheet.root.isExpanding = false
                    }

                    binding.bottomSheet.root.apply {
                        if ((
                                state == BottomSheetBehavior.STATE_COLLAPSED ||
                                    state == BottomSheetBehavior.STATE_EXPANDED ||
                                    state == BottomSheetBehavior.STATE_HIDDEN
                            ) &&
                            scaleY != 1f
                        ) {
                            scaleX = 1f
                            scaleY = 1f
                            pivotY = 0f
                            translationX = 0f
                            for (i in 0 until childCount) {
                                val childView = getChildAt(i)
                                childView.scaleY = 1f
                            }
                            lastScale = 1f
                        }
                    }

                    val extBottomSheet = binding.bottomSheet.root
                    if (state == BottomSheetBehavior.STATE_EXPANDED ||
                        state == BottomSheetBehavior.STATE_COLLAPSED
                    ) {
                        binding.bottomSheet.root.sheetBehavior
                            ?.isDraggable = true
                        showingExtensions = state == BottomSheetBehavior.STATE_EXPANDED
                        binding.bottomSheet.sheetToolbar.isVisible = showingExtensions
                        updateTitleAndMenu()
                        if (state == BottomSheetBehavior.STATE_EXPANDED) {
                            extBottomSheet.fetchOnlineExtensionsIfNeeded()
                        } else {
                            extBottomSheet.shouldCallApi = true
                        }
                    }

                    retainViewMode =
                        if (state == BottomSheetBehavior.STATE_EXPANDED) {
                            RetainViewMode.RETAIN_DETACH
                        } else {
                            RetainViewMode.RELEASE_DETACH
                        }
                    binding.bottomSheet.sheetLayout.isClickable = state == BottomSheetBehavior.STATE_COLLAPSED
                    binding.bottomSheet.sheetLayout.isFocusable = state == BottomSheetBehavior.STATE_COLLAPSED
                    if (state == BottomSheetBehavior.STATE_COLLAPSED || state == BottomSheetBehavior.STATE_EXPANDED) {
                        setBottomSheetTabs(if (state == BottomSheetBehavior.STATE_COLLAPSED) 0f else 1f)
                    }
                }
            },
        )

        if (showingExtensions) {
            binding.bottomSheet.root.sheetBehavior
                ?.expand()
        }
        ogRadius = view.resources.getDimension(R.dimen.rounded_radius)

        setSheetToolbar()
        presenter.onCreate()
        if (presenter.sourceItems.isNotEmpty()) {
            setSources(presenter.sourceItems, presenter.lastUsedItem)
        } else {
            binding.sourceRecycler.checkHeightThen {
                binding.sourceRecycler.scrollToPosition(0)
            }
        }
    }

    private fun updateSheetMenu() {
        binding.bottomSheet.sheetToolbar.title =
            if (binding.bottomSheet.tabs.selectedTabPosition != 0) {
                binding.bottomSheet.root.currentSourceTitle
                    ?: view?.context?.getString(R.string.source_migration)
            } else {
                view?.context?.getString(R.string.extensions)
            }
        val onExtensionTab = binding.bottomSheet.tabs.selectedTabPosition == 0
        if (binding.bottomSheet.sheetToolbar.menu
                .findItem(if (onExtensionTab) R.id.action_search else R.id.action_migration_guide) !=
            null
        ) {
            return
        }
        val oldSearchView =
            binding.bottomSheet.sheetToolbar.menu
                .findItem(R.id.action_search)
                ?.actionView as? SearchView
        oldSearchView?.setOnQueryTextListener(null)
        binding.bottomSheet.sheetToolbar.menu
            .clear()
        binding.bottomSheet.sheetToolbar.inflateMenu(
            if (binding.bottomSheet.tabs.selectedTabPosition == 0) {
                R.menu.extension_main
            } else {
                R.menu.migration_main
            },
        )

        val id =
            when (PreferenceValues.MigrationSourceOrder.fromPreference(preferences)) {
                PreferenceValues.MigrationSourceOrder.Alphabetically -> R.id.action_sort_alpha
                PreferenceValues.MigrationSourceOrder.MostEntries -> R.id.action_sort_largest
                PreferenceValues.MigrationSourceOrder.Obsolete -> R.id.action_sort_obsolete
            }
        binding.bottomSheet.sheetToolbar.menu
            .findItem(id)
            ?.isChecked = true

        // Initialize search option.
        binding.bottomSheet.sheetToolbar.menu.findItem(R.id.action_search)?.let { searchItem ->
            val searchView = searchItem.actionView as SearchView

            // Change hint to show global search.
            searchView.queryHint = view?.context?.getString(R.string.search_extensions)
            if (extQuery.isNotEmpty()) {
                searchView.setOnQueryTextListener(null)
                searchItem.expandActionView()
                searchView.setQuery(extQuery, true)
                searchView.clearFocus()
            } else {
                searchItem.collapseActionView()
            }
            // Create query listener which opens the global search view.
            setOnQueryTextChangeListener(searchView) {
                extQuery = it ?: ""
                binding.bottomSheet.root.drawExtensions()
                true
            }
        }
    }

    private fun setSheetToolbar() {
        binding.bottomSheet.sheetToolbar.setOnMenuItemClickListener { item ->
            val sorting =
                when (item.itemId) {
                    R.id.action_sort_alpha -> PreferenceValues.MigrationSourceOrder.Alphabetically
                    R.id.action_sort_largest -> PreferenceValues.MigrationSourceOrder.MostEntries
                    R.id.action_sort_obsolete -> PreferenceValues.MigrationSourceOrder.Obsolete
                    else -> null
                }
            if (sorting != null) {
                preferences.migrationSourceOrder().set(sorting.value)
                binding.bottomSheet.root.presenter
                    .refreshMigrations()
                item.isChecked = true
                return@setOnMenuItemClickListener true
            }
            when (item.itemId) {
                // Initialize option to open catalogue settings.
                R.id.action_filter -> {
                    router.pushController(ExtensionFilterController().withFadeTransaction())
                }
                R.id.action_migration_guide -> {
                    activity?.openInBrowser(HELP_URL)
                }
                R.id.action_sources_settings -> {
                    router.pushController(SettingsBrowseController().withFadeTransaction())
                }
                R.id.action_ext_repos -> {
                    router.pushController(RepoController().withFadeTransaction())
                }
            }
            return@setOnMenuItemClickListener true
        }
        binding.bottomSheet.sheetToolbar.setNavigationOnClickListener {
            binding.bottomSheet.root.sheetBehavior
                ?.collapse()
        }
        updateSheetMenu()
    }

    fun updateTitleAndMenu() {
        if (isControllerVisible) {
            val activity = (activity as? MainActivity) ?: return
            activityBinding?.appBar?.isInvisible = showingExtensions
            (activity as? MainActivity)?.setStatusBarColorTransparent(showingExtensions)
            updateSheetMenu()
        }
    }

    fun setBottomSheetTabs(progress: Float) {
        val bottomSheet = binding.bottomSheet.root
        val halfStepProgress = (max(0.5f, progress) - 0.5f) * 2
        binding.bottomSheet.tabs.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin =
                (
                    (
                        activityBinding
                            ?.appBar
                            ?.paddingTop
                            ?.minus(9f.dpToPx)
                            ?.plus(toolbarHeight ?: 0) ?: 0f
                    ) * halfStepProgress
                ).toInt()
        }
        binding.bottomSheet.pill.alpha = (1 - progress) * 0.25f
        binding.bottomSheet.sheetToolbar.alpha = progress
        if (isControllerVisible) {
            activityBinding?.appBar?.alpha = (1 - progress * 3) + 0.5f
        }

        binding.bottomSheet.root.updateGradiantBGRadius(
            ogRadius,
            deviceRadius,
            progress,
            binding.bottomSheet.sheetLayout,
        )

        val selectedColor =
            ColorUtils.setAlphaComponent(
                bottomSheet.context.getResourceColor(R.attr.tabBarIconColor),
                (progress * 255).toInt(),
            )
        val unselectedColor =
            ColorUtils.setAlphaComponent(
                bottomSheet.context.getResourceColor(R.attr.actionBarTintColor),
                153,
            )
        binding.bottomSheet.pager.alpha = progress * 10
        binding.bottomSheet.tabs.setSelectedTabIndicatorColor(selectedColor)
        binding.bottomSheet.tabs.setTabTextColors(
            ColorUtils.blendARGB(
                bottomSheet.context.getResourceColor(R.attr.actionBarTintColor),
                unselectedColor,
                progress,
            ),
            ColorUtils.blendARGB(
                bottomSheet.context.getResourceColor(R.attr.actionBarTintColor),
                selectedColor,
                progress,
            ),
        )

        /*binding.bottomSheet.sheetLayout.backgroundTintList = ColorStateList.valueOf(
            ColorUtils.blendARGB(
                bottomSheet.context.getResourceColor(R.attr.colorSurfaceContainer),
                bottomSheet.context.getResourceColor(R.attr.colorSurface),
                progress
            )
        )*/
    }

    private fun setBottomPadding() {
        val bottomBar = activityBinding?.bottomNav
        val pad = bottomBar?.translationY?.minus(bottomBar.height) ?: 0f
        val padding =
            max(
                (-pad).toInt(),
                view?.rootWindowInsetsCompat?.getBottomGestureInsets() ?: 0,
            )
        binding.bottomSheet.root.sheetBehavior
            ?.peekHeight = 56.spToPx + padding
        binding.bottomSheet.root.extensionFrameLayout?.binding?.fastScroller?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = -pad.toInt()
        }
        binding.bottomSheet.root.migrationFrameLayout?.binding?.fastScroller?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = -pad.toInt()
        }
        binding.sourceRecycler.updatePaddingRelative(
            bottom =
                (
                    activityBinding?.bottomNav?.height
                        ?: view?.rootWindowInsetsCompat?.getBottomGestureInsets() ?: 0
                ) + 58.spToPx,
        )
    }

    override fun showSheet() {
        if (!isBindingInitialized) return
        binding.bottomSheet.root.sheetBehavior
            ?.expand()
    }

    fun updateAllPendingExtensions() {
        if (!isBindingInitialized) return
        binding.bottomSheet.root.updateAllPendingExtensions()
    }

    override fun hideSheet() {
        if (!isBindingInitialized) return
        binding.bottomSheet.root.sheetBehavior
            ?.collapse()
    }

    override fun toggleSheet() {
        if (!binding.bottomSheet.root.sheetBehavior
                .isCollapsed()
        ) {
            binding.bottomSheet.root.sheetBehavior
                ?.collapse()
        } else {
            binding.bottomSheet.root.sheetBehavior
                ?.expand()
        }
    }

    override fun canStillGoBack(): Boolean = showingExtensions

    override fun handleOnBackStarted(backEvent: BackEventCompat) {
        if (showingExtensions && !binding.bottomSheet.root.canStillGoBack()) {
            binding.bottomSheet.root.sheetBehavior
                ?.startBackProgress(backEvent)
        }
    }

    override fun handleOnBackProgressed(backEvent: BackEventCompat) {
        if (showingExtensions && !binding.bottomSheet.root.canStillGoBack()) {
            binding.bottomSheet.root.sheetBehavior
                ?.updateBackProgress(backEvent)
        } else {
            super.handleOnBackProgressed(backEvent)
        }
    }

    override fun handleOnBackCancelled() {
        if (showingExtensions && !binding.bottomSheet.root.canStillGoBack()) {
            binding.bottomSheet.root.sheetBehavior
                ?.cancelBackProgress()
        } else {
            super.handleOnBackCancelled()
        }
    }

    override fun handleBack(): Boolean {
        if (showingExtensions) {
            if (binding.bottomSheet.root.canGoBack()) {
                lastScale = binding.bottomSheet.root.scaleX
                binding.bottomSheet.root.sheetBehavior
                    ?.collapse()
            }
            return true
        }
        return false
    }

    override fun onDestroyView(view: View) {
        adapter = null
        searchHistoryView = null
        binding.bottomSheet.root.onDestroy()
        super.onDestroyView(view)
    }

    override fun onDestroy() {
        super.onDestroy()
        presenter.onDestroy()
    }

    override fun onChangeStarted(
        handler: ControllerChangeHandler,
        type: ControllerChangeType,
    ) {
        super.onChangeStarted(handler, type)
        if (!type.isPush) {
            binding.bottomSheet.root.updateExtTitle()
            binding.bottomSheet.root.presenter
                .refreshExtensions()
            presenter.updateSources()
            if (type.isEnter && isControllerVisible) {
                activityBinding?.appBar?.doOnNextLayout {
                    activityBinding?.appBar?.y = 0f
                    activityBinding?.appBar?.updateAppBarAfterY(binding.sourceRecycler)
                }
                updateSheetMenu()
            }
        }
        if (!type.isEnter) {
            binding.bottomSheet.root.canExpand = false
            activityBinding?.appBar?.alpha = 1f
            activityBinding?.appBar?.isInvisible = false
            binding.bottomSheet.sheetToolbar.menu.findItem(R.id.action_search)?.let { searchItem ->
                val searchView = searchItem.actionView as SearchView
                searchView.clearFocus()
            }
        } else {
            binding.bottomSheet.root.presenter
                .refreshMigrations()
            updateTitleAndMenu()
        }
        setBottomPadding()
    }

    override fun onChangeEnded(
        handler: ControllerChangeHandler,
        type: ControllerChangeType,
    ) {
        super.onChangeEnded(handler, type)
        if (type.isEnter) {
            binding.bottomSheet.root.canExpand = true
            setBottomPadding()
            updateTitleAndMenu()
        }
    }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        if (!isBindingInitialized) return
        binding.bottomSheet.root.presenter
            .refreshExtensions()
        binding.bottomSheet.root.presenter
            .refreshMigrations()
        setBottomPadding()
        if (showingExtensions) {
            updateSheetMenu()
        }
    }

    override fun onItemClick(
        view: View,
        position: Int,
    ): Boolean {
        val item = adapter?.getItem(position) as? SourceItem ?: return false
        val source = item.source
        // Open the catalogue view.
        openCatalogue(source, BrowseSourceController(source))
        return false
    }

    fun hideCatalogue(position: Int) {
        val source = (adapter?.getItem(position) as? SourceItem)?.source ?: return
        val current = preferences.hiddenSources().get()
        preferences.hiddenSources().set(current + source.id.toString())

        presenter.updateSources()

        snackbar =
            view?.snack(R.string.source_hidden, Snackbar.LENGTH_INDEFINITE) {
                anchorView = binding.bottomSheet.root
                setAction(R.string.undo) {
                    val newCurrent = preferences.hiddenSources().get()
                    preferences.hiddenSources().set(newCurrent - source.id.toString())
                    presenter.updateSources()
                }
            }
        (activity as? MainActivity)?.setUndoSnackBar(snackbar)
    }

    private fun pinCatalogue(
        source: Source,
        isPinned: Boolean,
    ) {
        val current = preferences.pinnedCatalogues().get()
        if (isPinned) {
            preferences.pinnedCatalogues().set(current - source.id.toString())
        } else {
            preferences.pinnedCatalogues().set(current + source.id.toString())
        }

        presenter.updateSources()
    }

    /**
     * Called when browse is clicked in [SourceAdapter]
     */
    override fun onPinClick(position: Int) {
        val item = adapter?.getItem(position) as? SourceItem ?: return
        val isPinned =
            item.isPinned ?: item.header?.code?.equals(SourcePresenter.PINNED_KEY)
                ?: false
        pinCatalogue(item.source, isPinned)
    }

    /**
     * Called when latest is clicked in [SourceAdapter]
     */
    override fun onLatestClick(position: Int) {
        val item = adapter?.getItem(position) as? SourceItem ?: return
        openCatalogue(item.source, BrowseSourceController(item.source, useLatest = true))
    }

    /**
     * Opens a catalogue with the given controller.
     */
    private fun openCatalogue(
        source: CatalogueSource,
        controller: BrowseSourceController,
    ) {
        if (!isIncognitoModeForSource(source.id, preferences)) {
            preferences.lastUsedCatalogueSource().set(source.id)
            if (source !is LocalSource) {
                val list = preferences.lastUsedSources().get().toMutableSet()
                list.removeAll { it.startsWith("${source.id}:") }
                list.add("${source.id}:${Date().time}")
                val sortedList =
                    list
                        .filter { it.split(":").size == 2 }
                        .sortedByDescending { it.split(":").last().toLong() }
                preferences
                    .lastUsedSources()
                    .set(sortedList.take(2).toSet())
            }
        }
        router.pushController(controller.withFadeTransaction())
    }

    override fun expandSearch() {
        if (showingExtensions) {
            binding.bottomSheet.root.sheetBehavior
                ?.collapse()
        } else {
            activityBinding
                ?.searchToolbar
                ?.menu
                ?.findItem(R.id.action_search)
                ?.expandActionView()
        }
    }

    /**
     * Adds items to the options menu.
     *
     * @param menu menu containing options.
     * @param inflater used to load the menu xml.
     */
    override fun onCreateOptionsMenu(
        menu: Menu,
        inflater: MenuInflater,
    ) {
        // Inflate menu
        inflater.inflate(R.menu.catalogue_main, menu)

        // Initialize search option.
        val searchView = activityBinding?.searchToolbar?.searchView

        // Change hint to show global search.
        activityBinding?.searchToolbar?.searchQueryHint = view?.context?.getString(R.string.global_search)

        // Create query listener which opens the global search view.
        setOnQueryTextChangeListener(
            searchView,
            true,
            onTextChange = { setSearchHistoryVisible(it.isNullOrBlank()) },
        ) {
            if (!it.isNullOrBlank()) performGlobalSearch(it)
            true
        }
    }

    private fun performGlobalSearch(query: String) {
        preferences.addToSearchHistory(query)
        setSearchHistoryVisible(false)
        router.pushController(GlobalSearchController(query).withFadeTransaction())
    }

    private fun setUpSearchHistory() {
        val searchView = { activityBinding?.searchToolbar?.searchView }
        searchHistoryView =
            SearchHistoryView(binding.browseFrameLayout.context).apply {
                isVisible = false
                onQueryClicked = { searchView()?.setQuery(it, true) }
                onQueryFilled = { searchView()?.setQuery(it, false) }
                onHistoryEmptied = { setSearchHistoryVisible(false) }
                binding.browseFrameLayout.addView(
                    this,
                    FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT),
                )
            }
    }

    private fun updateSearchHistoryPadding() {
        searchHistoryView?.setContentPadding(
            top = binding.sourceRecycler.paddingTop,
            bottom = binding.sourceRecycler.paddingBottom,
        )
    }

    private fun setSearchHistoryVisible(show: Boolean) {
        val historyView = searchHistoryView ?: return
        val shouldShow =
            show &&
                !showingExtensions &&
                activityBinding?.searchToolbar?.isSearchExpanded == true &&
                historyView.hasHistory()
        if (historyView.isVisible == shouldShow) return
        historyView.isVisible = shouldShow
        if (shouldShow) {
            updateSearchHistoryPadding()
            historyView.scrollToTop()
        }
    }

    override fun onActionViewExpand(item: MenuItem?) {
        setSearchHistoryVisible(true)
    }

    override fun onActionViewCollapse(item: MenuItem?) {
        setSearchHistoryVisible(false)
    }

    /**
     * Called when an option menu item has been selected by the user.
     *
     * @param item The selected item.
     * @return True if this event has been consumed, false if it has not.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            // Initialize option to open catalogue settings.
            R.id.action_filter -> {
                router.pushController(SettingsSourcesController().withFadeTransaction())
            }
            R.id.action_migration_guide -> {
                activity?.openInBrowser(HELP_URL)
            }
            R.id.action_sources_settings -> {
                router.pushController(SettingsBrowseController().withFadeTransaction())
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    /**
     * Called to update adapter containing sources.
     */
    fun setSources(
        sources: List<IFlexible<*>>,
        lastUsed: SourceItem?,
    ) {
        adapter?.updateDataSet(sources, false)
        setLastUsedSource(lastUsed)
        if (isControllerVisible) {
            activityBinding?.appBar?.lockYPos = false
        }
    }

    /**
     * Called to set the last used catalogue at the top of the view.
     */
    fun setLastUsedSource(item: SourceItem?) {
        adapter?.removeAllScrollableHeaders()
        if (item != null) {
            adapter?.addScrollableHeader(item)
            adapter?.addScrollableHeader(LangItem(SourcePresenter.LAST_USED_KEY))
        }
    }

    @Parcelize
    data class SmartSearchConfig(
        val origTitle: String,
        val origMangaId: Long,
    ) : Parcelable

    companion object {
        const val HELP_URL = "https://mihon.app/docs/guides/source-migration"
    }
}
