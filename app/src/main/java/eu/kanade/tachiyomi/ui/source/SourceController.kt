package eu.kanade.tachiyomi.ui.source

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Activity
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.FadeChangeHandler
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.ui.extension.SettingsExtensionsController
import eu.kanade.tachiyomi.ui.main.BottomSheetController
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.main.RootSearchInterface
import eu.kanade.tachiyomi.ui.setting.SettingsSourcesController
import eu.kanade.tachiyomi.ui.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.source.global_search.GlobalSearchController
import eu.kanade.tachiyomi.ui.source.latest.LatestUpdatesController
import eu.kanade.tachiyomi.util.view.applyWindowInsetsForRootController
import eu.kanade.tachiyomi.util.view.collapse
import eu.kanade.tachiyomi.util.view.expand
import eu.kanade.tachiyomi.util.view.isCollapsed
import eu.kanade.tachiyomi.util.view.isExpanded
import eu.kanade.tachiyomi.util.view.requestPermissionsSafe
import eu.kanade.tachiyomi.util.view.scrollViewWith
import eu.kanade.tachiyomi.util.view.setOnQueryTextChangeListener
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import kotlinx.android.parcel.Parcelize
import kotlinx.android.synthetic.main.extensions_bottom_sheet.*
import kotlinx.android.synthetic.main.main_activity.*
import kotlinx.android.synthetic.main.source_controller.*
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.max

/**
 * This controller shows and manages the different catalogues enabled by the user.
 * This controller should only handle UI actions, IO actions should be done by [SourcePresenter]
 * [SourceAdapter.SourceListener] call function data on browse item click.
 * [SourceAdapter.OnLatestClickListener] call function data on latest item click
 */
class SourceController :
    NucleusController<SourcePresenter>(),
    FlexibleAdapter.OnItemClickListener,
    SourceAdapter.SourceListener,
    RootSearchInterface,
    BottomSheetController {

    /**
     * Application preferences.
     */
    private val preferences: PreferencesHelper = Injekt.get()

    /**
     * Adapter containing sources.
     */
    private var adapter: SourceAdapter? = null

    var extQuery = ""
        private set

    var headerHeight = 0

    var showingExtensions = false

    var snackbar: Snackbar? = null

    /**
     * Called when controller is initialized.
     */
    init {
        setHasOptionsMenu(true)
    }

    override fun getTitle(): String? {
        return if (showingExtensions)
            view?.context?.getString(R.string.extensions)
        else view?.context?.getString(R.string.sources)
    }

    override fun createPresenter(): SourcePresenter {
        return SourcePresenter()
    }

    /**
     * Initiate the view with [R.layout.source_controller].
     *
     * @param inflater used to load the layout xml.
     * @param container containing parent views.
     * @return inflated view.
     */
    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.source_controller, container, false)
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        view.applyWindowInsetsForRootController(activity!!.bottom_nav)

        adapter = SourceAdapter(this)

        // Create recycler and set adapter.
        recycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(view.context)
        recycler.adapter = adapter
        adapter?.isSwipeEnabled = true
        // recycler.addItemDecoration(SourceDividerItemDecoration(view.context))
        val attrsArray = intArrayOf(android.R.attr.actionBarSize)
        val array = view.context.obtainStyledAttributes(attrsArray)
        val appBarHeight = array.getDimensionPixelSize(0, 0)
        array.recycle()
        scrollViewWith(
            recycler,
            afterInsets = {
                headerHeight = it.systemWindowInsetTop + appBarHeight
            }
        )

        requestPermissionsSafe(arrayOf(WRITE_EXTERNAL_STORAGE), 301)
        ext_bottom_sheet.onCreate(this)

        ext_bottom_sheet.sheetBehavior?.addBottomSheetCallback(
            object : BottomSheetBehavior
            .BottomSheetCallback() {
                override fun onSlide(bottomSheet: View, progress: Float) {
                    val recycler = recycler ?: return
                    shadow2?.alpha = (1 - max(0f, progress)) * 0.25f
                    activity?.appbar?.elevation = max(
                        progress * 15f,
                        if (recycler.canScrollVertically(-1)) 15f else 0f
                    )

                    sheet_layout?.alpha = 1 - progress
                    activity?.appbar?.y = max(activity!!.appbar.y, -headerHeight * (1 - progress))
                    val oldShow = showingExtensions
                    showingExtensions = progress > 0.92f
                    if (oldShow != showingExtensions) {
                        setTitle()
                        activity?.invalidateOptionsMenu()
                    }
                }

                override fun onStateChanged(p0: View, state: Int) {
                    val extBottomSheet = ext_bottom_sheet ?: return
                    if (state == BottomSheetBehavior.STATE_EXPANDED) {
                        activity?.appbar?.y = 0f
                    }
                    if (state == BottomSheetBehavior.STATE_EXPANDED ||
                        state == BottomSheetBehavior.STATE_COLLAPSED
                    ) {
                        sheet_layout?.alpha =
                            if (state == BottomSheetBehavior.STATE_COLLAPSED) 1f else 0f
                        showingExtensions = state == BottomSheetBehavior.STATE_EXPANDED
                        setTitle()
                        if (state == BottomSheetBehavior.STATE_EXPANDED)
                            extBottomSheet.fetchOnlineExtensionsIfNeeded()
                        else extBottomSheet.shouldCallApi = true
                        activity?.invalidateOptionsMenu()
                    }

                    retainViewMode = if (state == BottomSheetBehavior.STATE_EXPANDED)
                        RetainViewMode.RETAIN_DETACH else RetainViewMode.RELEASE_DETACH
                    sheet_layout.isClickable = state == BottomSheetBehavior.STATE_COLLAPSED
                    sheet_layout.isFocusable = state == BottomSheetBehavior.STATE_COLLAPSED
                }
            }
        )

        if (showingExtensions) {
            ext_bottom_sheet.sheetBehavior?.expand()
        }
    }

    override fun showSheet() {
        ext_bottom_sheet.sheetBehavior?.expand()
    }

    override fun toggleSheet() {
        if (!ext_bottom_sheet.sheetBehavior.isCollapsed()) {
            ext_bottom_sheet.sheetBehavior?.collapse()
        } else {
            ext_bottom_sheet.sheetBehavior?.expand()
        }
    }

    override fun sheetIsExpanded(): Boolean = ext_bottom_sheet.sheetBehavior.isExpanded()

    override fun handleSheetBack(): Boolean {
        if (!ext_bottom_sheet.sheetBehavior.isCollapsed()) {
            ext_bottom_sheet.sheetBehavior?.collapse()
            return true
        }
        return false
    }

    override fun onDestroyView(view: View) {
        adapter = null
        super.onDestroyView(view)
    }

    override fun onChangeStarted(handler: ControllerChangeHandler, type: ControllerChangeType) {
        super.onChangeStarted(handler, type)
        if (!type.isPush && handler is SettingsSourcesFadeChangeHandler) {
            view?.applyWindowInsetsForRootController(activity!!.bottom_nav)
            ext_bottom_sheet.updateExtTitle()
            ext_bottom_sheet.presenter.refreshExtensions()
        }
    }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        ext_bottom_sheet?.presenter?.refreshExtensions()
    }

    override fun onItemClick(view: View, position: Int): Boolean {
        val item = adapter?.getItem(position) as? SourceItem ?: return false
        val source = item.source
        // Open the catalogue view.
        openCatalogue(source, BrowseSourceController(source))
        return false
    }

    fun hideCatalogue(position: Int) {
        val source = (adapter?.getItem(position) as? SourceItem)?.source ?: return
        val current = preferences.hiddenSources().getOrDefault()
        preferences.hiddenSources().set(current + source.id.toString())

        presenter.updateSources()

        snackbar = view?.snack(R.string.source_hidden, Snackbar.LENGTH_INDEFINITE) {
            anchorView = ext_bottom_sheet
            setAction(R.string.undo) {
                val newCurrent = preferences.hiddenSources().getOrDefault()
                preferences.hiddenSources().set(newCurrent - source.id.toString())
                presenter.updateSources()
            }
        }
        (activity as? MainActivity)?.setUndoSnackBar(snackbar)
    }

    private fun pinCatalogue(source: Source, isPinned: Boolean) {
        val current = preferences.pinnedCatalogues().getOrDefault()
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
        val isPinned = item.isPinned ?: item.header?.code?.equals(SourcePresenter.PINNED_KEY)
            ?: false
        pinCatalogue(item.source, isPinned)
    }

    /**
     * Called when latest is clicked in [SourceAdapter]
     */
    override fun onLatestClick(position: Int) {
        val item = adapter?.getItem(position) as? SourceItem ?: return
        openCatalogue(item.source, LatestUpdatesController(item.source))
    }

    /**
     * Opens a catalogue with the given controller.
     */
    private fun openCatalogue(source: CatalogueSource, controller: BrowseSourceController) {
        preferences.lastUsedCatalogueSource().set(source.id)
        router.pushController(controller.withFadeTransaction())
    }

    override fun expandSearch() {
        if (showingExtensions) ext_bottom_sheet.sheetBehavior?.collapse()
        else activity?.toolbar?.menu?.findItem(R.id.action_search)?.expandActionView()
    }

    /**
     * Adds items to the options menu.
     *
     * @param menu menu containing options.
     * @param inflater used to load the menu xml.
     */
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (onRoot) (activity as? MainActivity)?.setDismissIcon(showingExtensions)
        if (showingExtensions) {
            // Inflate menu
            inflater.inflate(R.menu.extension_main, menu)

            // Initialize search option.
            val searchItem = menu.findItem(R.id.action_search)
            val searchView = searchItem.actionView as SearchView

            // Change hint to show global search.
            searchView.queryHint = view?.context?.getString(R.string.search_extensions)

            // Create query listener which opens the global search view.
            setOnQueryTextChangeListener(searchView) {
                extQuery = it ?: ""
                ext_bottom_sheet.drawExtensions()
                true
            }
        } else {
            // Inflate menu
            inflater.inflate(R.menu.catalogue_main, menu)

            // Initialize search option.
            val searchItem = menu.findItem(R.id.action_search)
            val searchView = searchItem.actionView as SearchView

            // Change hint to show global search.
            searchView.queryHint = view?.context?.getString(R.string.global_search)

            // Create query listener which opens the global search view.
            setOnQueryTextChangeListener(searchView, true) {
                if (!it.isNullOrBlank()) performGlobalSearch(it)
                true
            }
        }
    }

    private fun performGlobalSearch(query: String) {
        router.pushController(GlobalSearchController(query).withFadeTransaction())
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
                val controller =
                    if (showingExtensions)
                        SettingsExtensionsController()
                    else SettingsSourcesController()
                router.pushController(
                    (RouterTransaction.with(controller)).popChangeHandler(
                        SettingsSourcesFadeChangeHandler()
                    ).pushChangeHandler(FadeChangeHandler())
                )
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    /**
     * Called to update adapter containing sources.
     */
    fun setSources(sources: List<IFlexible<*>>) {
        adapter?.updateDataSet(sources)
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

    class SettingsSourcesFadeChangeHandler : FadeChangeHandler()

    @Parcelize
    data class SmartSearchConfig(val origTitle: String, val origMangaId: Long) : Parcelable
}
