package eu.kanade.tachiyomi.ui.source.browse

import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import eu.kanade.tachiyomi.ui.source.filter.CheckboxItem
import eu.kanade.tachiyomi.ui.source.filter.CheckboxSectionItem
import eu.kanade.tachiyomi.ui.source.filter.GroupItem
import eu.kanade.tachiyomi.ui.source.filter.HeaderItem
import eu.kanade.tachiyomi.ui.source.filter.SelectItem
import eu.kanade.tachiyomi.ui.source.filter.SelectSectionItem
import eu.kanade.tachiyomi.ui.source.filter.SeparatorItem
import eu.kanade.tachiyomi.ui.source.filter.SortGroup
import eu.kanade.tachiyomi.ui.source.filter.SortItem
import eu.kanade.tachiyomi.ui.source.filter.TextItem
import eu.kanade.tachiyomi.ui.source.filter.TextSectionItem
import eu.kanade.tachiyomi.ui.source.filter.TriStateItem
import eu.kanade.tachiyomi.ui.source.filter.TriStateSectionItem
import eu.kanade.tachiyomi.ui.source.searchhistory.FilterApplyResult
import eu.kanade.tachiyomi.ui.source.searchhistory.SavedFilter
import eu.kanade.tachiyomi.ui.source.searchhistory.applyTo
import eu.kanade.tachiyomi.util.manga.duplicateLibraryMangaIds
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.withUIContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Presenter of [BrowseSourceController].
 */
open class BrowseSourcePresenter(
    private val sourceId: Long,
    searchQuery: String? = null,
    var useLatest: Boolean = false,
    val sourceManager: SourceManager = Injekt.get(),
    val db: DatabaseHelper = Injekt.get(),
    val prefs: PreferencesHelper = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
) : BaseCoroutinePresenter<BrowseSourceController>() {
    /**
     * Selected source.
     */
    lateinit var source: CatalogueSource

    val sourceIsInitialized
        get() = this::source.isInitialized

    var filtersChanged = false

    var items = mutableListOf<BrowseSourceItem>()
    val page: Int
        get() = pager.currentPage

    /**
     * Modifiable list of filters.
     */
    var sourceFilters = FilterList()
        set(value) {
            field = value
            filtersChanged = true
            filterItems = value.toItems()
        }

    var filterItems: List<IFlexible<*>> = emptyList()

    /**
     * List of filters used by the [Pager]. If empty alongside [query], the popular query is used.
     */
    var appliedFilters = FilterList()

    private var initialFilters: List<SavedFilter> = emptyList()
    private var initialFiltersSourceId: Long? = null

    /**
     * Pager containing a list of manga results.
     */
    private lateinit var pager: Pager
    private var pagerJob: Job? = null

    /**
     * Subscription for one request from the pager.
     */
    private var nextPageJob: Job? = null

    var query = searchQuery ?: ""

    private val oldFilters = mutableListOf<Any?>()

    fun stageInitialFilters(
        filters: List<SavedFilter>,
        sourceId: Long?,
    ) {
        initialFilters = filters
        initialFiltersSourceId = sourceId
    }

    override fun onCreate() {
        super.onCreate()
        if (!::pager.isInitialized) {
            source = sourceManager.get(sourceId) as? CatalogueSource ?: return

            sourceFilters = source.getFilterList()

            if (oldFilters.isEmpty()) {
                for (i in sourceFilters) {
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
            }
            filtersChanged = false
            // save the original filters above, then apply new ones if provided
            val filterResult =
                initialFilters
                    .takeIf { it.isNotEmpty() }
                    ?.applyTo(sourceFilters, strict = initialFiltersSourceId == sourceId)
            if (filterResult != null && filterResult != FilterApplyResult.NONE) {
                filtersChanged = true
                restartPager(filters = sourceFilters)
            } else {
                restartPager()
            }
        }
    }

    fun filtersMatchDefault(): Boolean {
        for (i in sourceFilters.indices) {
            val filter = oldFilters.getOrNull(i)
            if (filter is List<*>) {
                val group = sourceFilters.getOrNull(i) as? Filter.Group<*> ?: return false
                for (j in filter.indices) {
                    val state = (group.state.getOrNull(j) as? Filter<*>)?.state
                    if (filter[j] != state) {
                        return false
                    }
                }
            } else if (filter != sourceFilters.getOrNull(i)?.state) {
                return false
            }
        }
        return true
    }

    /**
     * Restarts the pager for the active source with the provided query and filters.
     *
     * @param query the query.
     * @param filters the current state of the filters (for search mode).
     */
    fun restartPager(
        query: String = this.query,
        filters: FilterList = this.appliedFilters,
    ) {
        this.query = query
        this.appliedFilters = filters

        // Create a new pager.
        pager =
            createPager(
                query,
                filters.takeIf { it.isNotEmpty() || query.isBlank() }
                    ?: source.getFilterList(),
            )

        val sourceId = source.id

        val browseAsList = prefs.browseAsList()
        val sourceListType = prefs.libraryLayout()
        val outlineCovers = prefs.outlineOnCovers()
        items.clear()

        // Prepare the pager.
        pagerJob?.cancel()
        pagerJob =
            presenterScope.launchIO {
                pager
                    .results()
                    .onEach { (page, second) ->
                        try {
                            val mangas =
                                second
                                    .map { networkToLocalManga(it, sourceId) }
                                    .filter { !prefs.hideInLibraryItems().get() || !it.favorite }
                            if (mangas.isEmpty() && page == 1) {
                                withUIContext { view?.onAddPageError(NoResultsException()) }
                                return@onEach
                            }
                            initializeMangas(mangas)
                            val duplicateIds =
                                if (prefs.showDuplicateInLibraryItems().get()) {
                                    db.duplicateLibraryMangaIds(mangas)
                                } else {
                                    emptySet()
                                }
                            val items =
                                mangas.map {
                                    BrowseSourceItem(
                                        it,
                                        browseAsList,
                                        sourceListType,
                                        outlineCovers,
                                        isDuplicateInLibrary = it.id in duplicateIds,
                                    )
                                }
                            this@BrowseSourcePresenter.items.addAll(items)
                            withUIContext { view?.onAddPage(page, items) }
                        } catch (error: Exception) {
                            Timber.e(error)
                        }
                    }.collect()
            }

        // Request first page.
        requestNext()
    }

    /**
     * Requests the next page for the active pager.
     */
    fun requestNext() {
        if (!hasNextPage()) return

        nextPageJob?.cancel()
        nextPageJob =
            presenterScope.launchIO {
                try {
                    pager.requestNextPage()
                } catch (e: Throwable) {
                    withUIContext { view?.onAddPageError(e) }
                }
            }
    }

    /**
     * Returns true if the last fetched page has a next page.
     */
    fun hasNextPage(): Boolean = pager.hasNextPage

    /**
     * Returns a manga from the database for the given manga from network. It creates a new entry
     * if the manga is not yet in the database.
     *
     * @param sManga the manga from the source.
     * @return a manga from the database.
     */
    private fun networkToLocalManga(
        sManga: SManga,
        sourceId: Long,
    ): Manga {
        var localManga = db.getManga(sManga.url, sourceId).executeAsBlocking()
        if (localManga == null) {
            val newManga = Manga.create(sManga.url, sManga.title, sourceId)
            newManga.copyFrom(sManga)
            val result = db.insertManga(newManga).executeAsBlocking()
            newManga.id = result.insertedId()
            localManga = newManga
        } else if (localManga.title.isBlank()) {
            localManga.title = sManga.title
            db.insertManga(localManga).executeAsBlocking()
        } else if (!localManga.favorite) {
            // if the manga isn't a favorite, set its display title from source
            // if it later becomes a favorite, updated title will go to db
            localManga.title = sManga.title
        }
        return localManga
    }

    /**
     * Initialize a list of manga.
     *
     * @param mangas the list of manga to initialize.
     */
    fun initializeMangas(mangas: List<Manga>) {
        presenterScope.launchIO {
            mangas
                .asFlow()
                .filter { it.thumbnail_url == null && !it.initialized }
                .map { getMangaDetails(it) }
                .onEach {
                    withUIContext { view?.onMangaInitialized(it) }
                }.catch { e -> Timber.e(e) }
                .collect()
        }
    }

    /**
     * Returns the initialized manga.
     *
     * @param manga the manga to initialize.
     * @return the initialized manga
     */
    private suspend fun getMangaDetails(manga: Manga): Manga {
        try {
            val networkManga =
                source.getMangaUpdate(manga.copy(), emptyList(), fetchDetails = true, fetchChapters = false).manga
            manga.copyFrom(networkManga)
            manga.initialized = true
            db.insertManga(manga).executeAsBlocking()
        } catch (e: Exception) {
            Timber.e(e)
        }
        return manga
    }

    fun confirmDeletion(manga: Manga) {
        launchIO {
            coverCache.deleteFromCache(manga)
            val downloadManager: DownloadManager = Injekt.get()
            downloadManager.deleteManga(manga, source)
        }
    }

    /**
     * Set the filter states for the current source.
     *
     * @param filters a list of active filters.
     */
    fun setSourceFilter(filters: FilterList) {
        filtersChanged = true
        restartPager(filters = filters)
    }

    open fun createPager(
        query: String,
        filters: FilterList,
    ): Pager =
        if (useLatest && query.isBlank() && !filtersChanged) {
            LatestUpdatesPager(source)
        } else {
            useLatest = false
            BrowseSourcePager(source, query, filters)
        }

    private fun FilterList.toItems(): List<IFlexible<*>> =
        mapNotNull { filter ->
            when (filter) {
                is Filter.Header -> HeaderItem(filter)
                is Filter.Separator -> SeparatorItem(filter)
                is Filter.CheckBox -> CheckboxItem(filter)
                is Filter.TriState -> TriStateItem(filter)
                is Filter.Text -> TextItem(filter)
                is Filter.Select<*> -> SelectItem(filter)
                is Filter.Group<*> -> {
                    val group = GroupItem(filter)
                    val subItems =
                        filter.state.mapNotNull { type ->
                            when (type) {
                                is Filter.CheckBox -> CheckboxSectionItem(type)
                                is Filter.TriState -> TriStateSectionItem(type)
                                is Filter.Text -> TextSectionItem(type)
                                is Filter.Select<*> -> SelectSectionItem(type)
                                else -> null
                            }
                        }
                    subItems.forEach { it.header = group }
                    group.subItems = subItems
                    group
                }
                is Filter.Sort -> {
                    val group = SortGroup(filter)
                    val subItems =
                        filter.values.map {
                            SortItem(it, group)
                        }
                    group.subItems = subItems
                    group
                }
            }
        }

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    fun getCategories(): List<Category> = db.getCategories().executeAsBlocking()
}
