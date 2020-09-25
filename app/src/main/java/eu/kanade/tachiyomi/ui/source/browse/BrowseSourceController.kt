package eu.kanade.tachiyomi.ui.source.browse

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.jakewharton.rxbinding.support.v7.widget.queryTextChangeEvents
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.ui.library.AddToLibraryCategoriesDialog
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.ui.source.SourceController
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.system.connectivityManager
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.inflate
import eu.kanade.tachiyomi.util.view.scrollViewWith
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.updateLayoutParams
import eu.kanade.tachiyomi.util.view.visible
import eu.kanade.tachiyomi.util.view.visibleIf
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import eu.kanade.tachiyomi.widget.AutofitRecyclerView
import eu.kanade.tachiyomi.widget.EmptyView
import kotlinx.android.synthetic.main.browse_source_controller.*
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import timber.log.Timber
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.TimeUnit

/**
 * Controller to manage the catalogues available in the app.
 */
open class BrowseSourceController(bundle: Bundle) :
    NucleusController<BrowseSourcePresenter>(bundle),
    FlexibleAdapter.OnItemClickListener,
    FlexibleAdapter.OnItemLongClickListener,
    FlexibleAdapter.EndlessScrollListener,
    AddToLibraryCategoriesDialog.Listener {

    constructor(
        source: CatalogueSource,
        searchQuery: String? = null,
        smartSearchConfig: SourceController.SmartSearchConfig? = null
    ) : this(
        Bundle().apply {
            putLong(SOURCE_ID_KEY, source.id)

            if (searchQuery != null)
                putString(SEARCH_QUERY_KEY, searchQuery)

            if (smartSearchConfig != null)
                putParcelable(SMART_SEARCH_CONFIG_KEY, smartSearchConfig)
        }
    )

    constructor(source: CatalogueSource) : this(
        Bundle().apply {
            putLong(SOURCE_ID_KEY, source.id)
        }
    )

    /**
     * Preferences helper.
     */
    private val preferences: PreferencesHelper by injectLazy()

    /**
     * Adapter containing the list of manga from the catalogue.
     */
    private var adapter: FlexibleAdapter<IFlexible<*>>? = null

    /**
     * Snackbar containing an error message when a request fails.
     */
    private var snack: Snackbar? = null

    /**
     * Recycler view with the list of results.
     */
    private var recycler: RecyclerView? = null

    /**
     * Subscription for the search view.
     */
    private var searchViewSubscription: Subscription? = null

    /**
     * Endless loading item.
     */
    private var progressItem: ProgressItem? = null

    init {
        setHasOptionsMenu(true)
    }

    override fun getTitle(): String? {
        return presenter.source.name
    }

    override fun createPresenter(): BrowseSourcePresenter {
        return BrowseSourcePresenter(args.getLong(SOURCE_ID_KEY))
    }

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.browse_source_controller, container, false)
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        // Initialize adapter, scroll listener and recycler views
        adapter = FlexibleAdapter(null, this)
        setupRecycler(view)

        fab.visibleIf(presenter.sourceFilters.isNotEmpty())
        fab.setOnClickListener { showFilters() }
        progress?.visible()
    }

    override fun onDestroyView(view: View) {
        searchViewSubscription?.unsubscribe()
        searchViewSubscription = null
        adapter = null
        snack = null
        recycler = null
        super.onDestroyView(view)
    }

    private fun setupRecycler(view: View) {
        var oldPosition = RecyclerView.NO_POSITION
        val oldRecycler = catalogue_view?.getChildAt(1)
        if (oldRecycler is RecyclerView) {
            oldPosition = (oldRecycler.layoutManager as androidx.recyclerview.widget.LinearLayoutManager).findFirstVisibleItemPosition()
            oldRecycler.adapter = null

            catalogue_view?.removeView(oldRecycler)
        }

        val recycler = if (presenter.isListMode) {
            RecyclerView(view.context).apply {
                id = R.id.recycler
                layoutManager = LinearLayoutManager(context)
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
            }
        } else {
            (catalogue_view.inflate(R.layout.manga_recycler_autofit) as AutofitRecyclerView).apply {
                columnWidth = when (preferences.gridSize().getOrDefault()) {
                    1 -> 1f
                    2 -> 1.25f
                    3 -> 1.66f
                    4 -> 3f
                    else -> .75f
                }

                (layoutManager as androidx.recyclerview.widget.GridLayoutManager).spanSizeLookup = object : androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return when (adapter?.getItemViewType(position)) {
                            R.layout.manga_grid_item, null -> 1
                            else -> spanCount
                        }
                    }
                }
            }
        }
        recycler.clipToPadding = false
        recycler.setHasFixedSize(true)
        recycler.adapter = adapter

        scrollViewWith(
            recycler,
            true,
            afterInsets = { insets ->
                fab?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    bottomMargin = insets.systemWindowInsetBottom + 16.dpToPx
                }
            }
        )

        recycler.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0)
                        fab.extend()
                    else
                        fab.shrink()
                }
            }
        )

        catalogue_view.addView(recycler, 1)
        if (oldPosition != RecyclerView.NO_POSITION) {
            recycler.layoutManager?.scrollToPosition(oldPosition)
        }
        this.recycler = recycler
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.browse_source, menu)

        // Initialize search menu
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView

        val query = presenter.query
        if (!query.isBlank()) {
            searchItem.expandActionView()
            searchView.setQuery(query, true)
            searchView.clearFocus()
        }

        val searchEventsObservable = searchView.queryTextChangeEvents()
            .skip(1)
            .filter { router.backstack.lastOrNull()?.controller() == this@BrowseSourceController }
            .share()
        val writingObservable = searchEventsObservable
            .filter { !it.isSubmitted }
            .debounce(1250, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
        val submitObservable = searchEventsObservable
            .filter { it.isSubmitted }

        searchViewSubscription?.unsubscribe()
        searchViewSubscription = Observable.merge(writingObservable, submitObservable)
            .map { it.queryText().toString() }
            .subscribeUntilDestroy { searchWithQuery(it) }

        searchItem.fixExpand(
            onExpand = { invalidateMenuOnExpand() },
            onCollapse = {
                searchWithQuery("")
                true
            }
        )

        // Show next display mode
        menu.findItem(R.id.action_display_mode).apply {
            val icon = if (presenter.isListMode)
                R.drawable.ic_view_module_24dp
            else
                R.drawable.ic_view_list_24dp
            setIcon(icon)
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        val isHttpSource = presenter.source is HttpSource
        menu.findItem(R.id.action_open_in_web_view).isVisible = isHttpSource

        val isLocalSource = presenter.source is LocalSource
        menu.findItem(R.id.action_local_source_help).isVisible = isLocalSource
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_search -> expandActionViewFromInteraction = true
            R.id.action_display_mode -> swapDisplayMode()
            R.id.action_open_in_web_view -> openInWebView()
            R.id.action_local_source_help -> openLocalSourceHelpGuide()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun showFilters() {
        val sheet = SourceSearchSheet(activity!!)
        sheet.setFilters(presenter.filterItems)
        presenter.filtersChanged = false
        val oldFilters = mutableListOf<Any?>()
        for (i in presenter.sourceFilters) {
            if (i is Filter.Group<*>) {
                val subFilters = mutableListOf<Any?>()
                for (j in i.state) {
                    subFilters.add((j as Filter<*>).state)
                }
                oldFilters.add(subFilters)
            } else {
                oldFilters.add(i.state)
            }
        }
        sheet.onSearchClicked = {
            var matches = true
            for (i in presenter.sourceFilters.indices) {
                val filter = oldFilters[i]
                if (filter is List<*>) {
                    for (j in filter.indices) {
                        if (filter[j] !=
                            (
                                (presenter.sourceFilters[i] as Filter.Group<*>).state[j] as
                                    Filter<*>
                                ).state
                        ) {
                            matches = false
                            break
                        }
                    }
                } else if (oldFilters[i] != presenter.sourceFilters[i].state) {
                    matches = false
                    break
                }
            }
            if (!matches) {
                val allDefault = presenter.sourceFilters == presenter.source.getFilterList()
                showProgressBar()
                adapter?.clear()
                presenter.setSourceFilter(if (allDefault) FilterList() else presenter.sourceFilters)
            }
        }

        sheet.onResetClicked = {
            presenter.appliedFilters = FilterList()
            val newFilters = presenter.source.getFilterList()
            presenter.sourceFilters = newFilters
            sheet.setFilters(presenter.filterItems)
        }
        sheet.show()
    }

    private fun openInWebView() {
        val source = presenter.source as? HttpSource ?: return
        val activity = activity ?: return
        val intent = WebViewActivity.newIntent(
            activity,
            source.id,
            source.baseUrl,
            presenter
                .source.name
        )
        startActivity(intent)
    }

    private fun openLocalSourceHelpGuide() {
        activity?.openInBrowser(LocalSource.HELP_URL)
    }

    /**
     * Restarts the request with a new query.
     *
     * @param newQuery the new query.
     */
    private fun searchWithQuery(newQuery: String) {
        // If text didn't change, do nothing
        if (presenter.query == newQuery)
            return

        showProgressBar()
        adapter?.clear()

        presenter.restartPager(newQuery)
    }

    /**
     * Called from the presenter when the network request is received.
     *
     * @param page the current page.
     * @param mangas the list of manga of the page.
     */
    fun onAddPage(page: Int, mangas: List<BrowseSourceItem>) {
        val adapter = adapter ?: return
        hideProgressBar()
        if (page == 1) {
            adapter.clear()
            resetProgressItem()
        }
        adapter.onLoadMoreComplete(mangas)
    }

    /**
     * Called from the presenter when the network request fails.
     *
     * @param error the error received.
     */
    fun onAddPageError(error: Throwable) {
        Timber.e(error)
        val adapter = adapter ?: return
        adapter.onLoadMoreComplete(null)
        hideProgressBar()

        snack?.dismiss()

        val message = getErrorMessage(error)
        val retryAction = View.OnClickListener {

            // If not the first page, show bottom progress bar.
            if (adapter.mainItemCount > 0 && progressItem != null) {
                adapter.addScrollableFooterWithDelay(progressItem!!, 0, true)
            } else {
                showProgressBar()
            }
            presenter.requestNext()
        }

        if (adapter.isEmpty) {
            val actions = emptyList<EmptyView.Action>().toMutableList()

            actions += if (presenter.source is LocalSource) {
                EmptyView.Action(
                    R.string.local_source_help_guide,
                    View.OnClickListener { openLocalSourceHelpGuide() }
                )
            } else {
                EmptyView.Action(R.string.retry, retryAction)
            }

            if (presenter.source is HttpSource) {
                actions += EmptyView.Action(
                    R.string.open_in_webview,
                    View.OnClickListener { openInWebView() }
                )
            }

            empty_view.show(
                if (presenter.source is HttpSource) R.drawable.ic_browse_off_24dp
                else R.drawable.ic_local_library_24dp,
                message,
                actions
            )
        } else {
            snack = source_layout?.snack(message, Snackbar.LENGTH_INDEFINITE) {
                setAction(R.string.retry, retryAction)
            }
        }
    }

    private fun getErrorMessage(error: Throwable): String {
        if (error is NoResultsException) {
            return activity!!.getString(R.string.no_results_found)
        }

        return when {
            error.message == null -> ""
            error.message!!.startsWith("HTTP error") -> "${error.message}: ${activity!!.getString(R.string.check_site_in_web)}"
            else -> error.message!!
        }
    }

    /**
     * Sets a new progress item and reenables the scroll listener.
     */
    private fun resetProgressItem() {
        progressItem = ProgressItem()
        adapter?.endlessTargetCount = 0
        adapter?.setEndlessScrollListener(this, progressItem!!)
    }

    /**
     * Called by the adapter when scrolled near the bottom.
     */
    override fun onLoadMore(lastPosition: Int, currentPage: Int) {
        if (presenter.hasNextPage()) {
            presenter.requestNext()
        } else {
            adapter?.onLoadMoreComplete(null)
            adapter?.endlessTargetCount = 1
        }
    }

    override fun noMoreLoad(newItemsSize: Int) {
    }

    /**
     * Called from the presenter when a manga is initialized.
     *
     * @param manga the manga initialized
     */
    fun onMangaInitialized(manga: Manga) {
        getHolder(manga)?.setImage(manga)
    }

    /**
     * Swaps the current display mode.
     */
    fun swapDisplayMode() {
        val view = view ?: return
        val adapter = adapter ?: return

        presenter.swapDisplayMode()
        val isListMode = presenter.isListMode
        activity?.invalidateOptionsMenu()
        setupRecycler(view)
        if (!isListMode || !view.context.connectivityManager.isActiveNetworkMetered) {
            // Initialize mangas if going to grid view or if over wifi when going to list view
            val mangas = (0 until adapter.itemCount).mapNotNull {
                (adapter.getItem(it) as? BrowseSourceItem)?.manga
            }
            presenter.initializeMangas(mangas)
        }
    }

    /**
     * Returns the view holder for the given manga.
     *
     * @param manga the manga to find.
     * @return the holder of the manga or null if it's not bound.
     */
    private fun getHolder(manga: Manga): BrowseSourceHolder? {
        val adapter = adapter ?: return null

        adapter.allBoundViewHolders.forEach { holder ->
            val item = adapter.getItem(holder.adapterPosition) as? BrowseSourceItem
            if (item != null && item.manga.id!! == manga.id!!) {
                return holder as BrowseSourceHolder
            }
        }

        return null
    }

    /**
     * Shows the progress bar.
     */
    private fun showProgressBar() {
        empty_view.gone()
        progress?.visible()
        snack?.dismiss()
        snack = null
    }

    /**
     * Hides active progress bars.
     */
    private fun hideProgressBar() {
        empty_view.gone()
        progress?.gone()
    }

    /**
     * Called when a manga is clicked.
     *
     * @param position the position of the element clicked.
     * @return true if the item should be selected, false otherwise.
     */
    override fun onItemClick(view: View?, position: Int): Boolean {
        val item = adapter?.getItem(position) as? BrowseSourceItem ?: return false
        router.pushController(MangaDetailsController(item.manga, true).withFadeTransaction())

        return false
    }

    /**
     * Called when a manga is long clicked.
     *
     * Adds the manga to the default category if none is set it shows a list of categories for the user to put the manga
     * in, the list consists of the default category plus the user's categories. The default category is preselected on
     * new manga, and on already favorited manga the manga's categories are preselected.
     *
     * @param position the position of the element clicked.
     */
    override fun onItemLongClick(position: Int) {
        val manga = (adapter?.getItem(position) as? BrowseSourceItem?)?.manga ?: return
        snack?.dismiss()
        if (manga.favorite) {
            presenter.changeMangaFavorite(manga)
            adapter?.notifyItemChanged(position)
            snack = source_layout?.snack(R.string.removed_from_library, Snackbar.LENGTH_INDEFINITE) {
                setAction(R.string.undo) {
                    if (!manga.favorite) addManga(manga, position)
                }
                addCallback(
                    object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                            super.onDismissed(transientBottomBar, event)
                            if (!manga.favorite) presenter.confirmDeletion(manga)
                        }
                    }
                )
            }
            (activity as? MainActivity)?.setUndoSnackBar(snack)
        } else {
            addManga(manga, position)
            snack = source_layout?.snack(R.string.added_to_library)
        }
    }

    private fun addManga(manga: Manga, position: Int) {
        presenter.changeMangaFavorite(manga)
        adapter?.notifyItemChanged(position)

        val categories = presenter.getCategories()
        val defaultCategoryId = preferences.defaultCategory()
        val defaultCategory = categories.find { it.id == defaultCategoryId }
        when {
            defaultCategory != null -> presenter.moveMangaToCategory(manga, defaultCategory)
            defaultCategoryId == 0 || categories.isEmpty() -> // 'Default' or no category
                presenter.moveMangaToCategory(manga, null)
            else -> {
                val ids = presenter.getMangaCategoryIds(manga)
                if (ids.isNullOrEmpty()) {
                    presenter.moveMangaToCategory(manga, null)
                }
                val preselected = ids.mapNotNull { id ->
                    categories.indexOfFirst { it.id == id }.takeIf { it != -1 }
                }.toTypedArray()

                AddToLibraryCategoriesDialog(this, manga, categories, preselected, position)
                    .showDialog(router)
            }
        }
    }

    /**
     * Update manga to use selected categories.
     *
     * @param mangas The list of manga to move to categories.
     * @param categories The list of categories where manga will be placed.
     */
    override fun updateCategoriesForManga(manga: Manga?, categories: List<Category>) {
        manga?.let { presenter.updateMangaCategories(manga, categories) }
    }

    /**
     * Update manga to remove from favorites
     */
    override fun addToLibraryCancelled(manga: Manga?, position: Int) {
        manga?.let {
            presenter.changeMangaFavorite(manga)
            adapter?.notifyItemChanged(position)
        }
    }

    protected companion object {
        const val SOURCE_ID_KEY = "sourceId"

        const val SEARCH_QUERY_KEY = "searchQuery"
        const val SMART_SEARCH_CONFIG_KEY = "smartSearchConfig"
    }
}
