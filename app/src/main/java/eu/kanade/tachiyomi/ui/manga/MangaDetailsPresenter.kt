package eu.kanade.tachiyomi.ui.manga

import android.app.Application
import android.net.Uri
import android.os.Environment
import coil.Coil
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.download.model.DownloadQueue
import eu.kanade.tachiyomi.data.library.CustomMangaManager
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.SourceNotFoundException
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import eu.kanade.tachiyomi.ui.manga.chapter.ChapterItem
import eu.kanade.tachiyomi.ui.manga.track.TrackItem
import eu.kanade.tachiyomi.ui.manga.track.TrackingBottomSheet
import eu.kanade.tachiyomi.ui.security.SecureActivityDelegate
import eu.kanade.tachiyomi.util.chapter.ChapterFilter
import eu.kanade.tachiyomi.util.chapter.ChapterSort
import eu.kanade.tachiyomi.util.chapter.ChapterUtil
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithTrackServiceTwoWay
import eu.kanade.tachiyomi.util.chapter.updateTrackChapterMarkedAsRead
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.lang.trimOrNull
import eu.kanade.tachiyomi.util.manga.MangaShortcutManager
import eu.kanade.tachiyomi.util.shouldDownloadNewChapters
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.system.executeOnIO
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.withUIContext
import eu.kanade.tachiyomi.widget.TriStateCheckBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.util.Locale

class MangaDetailsPresenter(
    val manga: Manga,
    val source: Source,
    val preferences: PreferencesHelper = Injekt.get(),
    val coverCache: CoverCache = Injekt.get(),
    val db: DatabaseHelper = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    chapterFilter: ChapterFilter = Injekt.get(),
) : BaseCoroutinePresenter<MangaDetailsController>(),
    DownloadQueue.DownloadListener {
    private val customMangaManager: CustomMangaManager by injectLazy()
    private val mangaShortcutManager: MangaShortcutManager by injectLazy()
    val sourceManager: SourceManager by injectLazy()

    private val chapterSort = ChapterSort(manga, chapterFilter, preferences)
    val extension by lazy { (source as? HttpSource)?.getExtension() }

    var isLockedFromSearch = false
    var hasRequested = false
    var isLoading = false
    var scrollType = 0

    private val loggedServices by lazy { Injekt.get<TrackManager>().services.filter { it.isLogged } }
    private var tracks = emptyList<Track>()

    var trackList: List<TrackItem> = emptyList()

    var chapters: List<ChapterItem> = emptyList()
        private set

    var allChapters: List<ChapterItem> = emptyList()
        private set

    var allHistory: List<History> = emptyList()
        private set

    val headerItem by lazy { MangaHeaderItem(manga, view?.fromCatalogue == true) }
    var tabletChapterHeaderItem: MangaHeaderItem? = null
    var allChapterScanlators: Set<String> = emptySet()

    fun onFirstLoad() {
        val controller = view ?: return
        headerItem.isTablet = controller.isTablet
        if (controller.isTablet) {
            tabletChapterHeaderItem = MangaHeaderItem(manga, false)
            tabletChapterHeaderItem?.isChapterHeader = true
        }
        isLockedFromSearch =
            controller.shouldLockIfNeeded &&
            SecureActivityDelegate.shouldBeLocked()
        headerItem.isLocked = isLockedFromSearch
        downloadManager.addListener(this)
        LibraryUpdateJob.updateFlow
            .filter { it == manga.id }
            .onEach { fetchChapters() }
            .launchIn(presenterScope)
        tracks = db.getTracks(manga).executeAsBlocking()
        if (manga.isLocal()) {
            refreshAll()
        } else if (!manga.initialized) {
            isLoading = true
            controller.setRefresh(true)
            controller.updateHeader()
            refreshAll()
        } else {
            runBlocking { getChapters() }
            controller.updateChapters(this.chapters)
            getHistory()
        }
        presenterScope.launch {
            setTrackItems()
        }
        refreshTracking(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadManager.removeListener(this)
    }

    fun fetchChapters(andTracking: Boolean = true) {
        presenterScope.launch {
            getChapters()
            if (andTracking) fetchTracks()
            withContext(Dispatchers.Main) { view?.updateChapters(chapters) }
            getHistory()
        }
    }

    suspend fun getChaptersNow(): List<ChapterItem> {
        getChapters()
        return chapters
    }

    private suspend fun getChapters() {
        val chapters = db.getChapters(manga).executeOnIO().map { it.toModel() }

        // Find downloaded chapters
        setDownloadedChapters(chapters)
        allChapterScanlators = chapters.flatMap { ChapterUtil.getScanlators(it.chapter.scanlator) }.toSet()
        // Store the last emission
        allChapters = chapters
        this.chapters = applyChapterFilters(chapters)
    }

    private fun getHistory() {
        presenterScope.launchIO {
            allHistory = manga.id?.let { db.getHistoryByMangaId(it).executeAsBlocking() }.orEmpty()
        }
    }

    /**
     * Finds and assigns the list of downloaded chapters.
     *
     * @param chapters the list of chapter from the database.
     */
    private fun setDownloadedChapters(chapters: List<ChapterItem>) {
        for (chapter in chapters) {
            if (downloadManager.isChapterDownloaded(chapter, manga)) {
                chapter.status = Download.State.DOWNLOADED
            } else if (downloadManager.hasQueue()) {
                chapter.status = downloadManager.queue
                    .find { it.chapter.id == chapter.id }
                    ?.status ?: Download.State.default
            }
        }
    }

    override fun updateDownload(download: Download) {
        chapters.find { it.id == download.chapter.id }?.download = download
        presenterScope.launchUI {
            view?.updateChapterDownload(download)
        }
    }

    override fun updateDownloads() {
        presenterScope.launch(Dispatchers.Default) {
            getChapters()
            withContext(Dispatchers.Main) {
                view?.updateChapters(chapters)
            }
        }
    }

    /**
     * Converts a chapter from the database to an extended model, allowing to store new fields.
     */
    private fun Chapter.toModel(): ChapterItem {
        // Create the model object.
        val model = ChapterItem(this, manga)
        model.isLocked = isLockedFromSearch

        // Find an active download for this chapter.
        val download = downloadManager.queue.find { it.chapter.id == id }

        if (download != null) {
            // If there's an active download, assign it.
            model.download = download
        }
        return model
    }

    /**
     * Whether the sorting method is descending or ascending.
     */
    fun sortDescending() = manga.sortDescending(preferences)

    fun sortingOrder() = manga.chapterOrder(preferences)

    /**
     * Applies the view filters to the list of chapters obtained from the database.
     * @param chapterList the list of chapters from the database
     * @return an observable of the list of chapters filtered and sorted.
     */
    private fun applyChapterFilters(chapterList: List<ChapterItem>): List<ChapterItem> {
        if (isLockedFromSearch) {
            return chapterList
        }
        getScrollType(chapterList)
        return chapterSort.getChaptersSorted(chapterList)
    }

    fun getChapterUrl(chapter: Chapter): String? {
        val source = source as? HttpSource ?: return null
        val chapterUrl =
            try {
                source.getChapterUrl(chapter)
            } catch (_: Exception) {
                null
            }
        return chapterUrl.takeIf { !it.isNullOrBlank() } ?: source.getChapterUrl(manga, chapter)
    }

    private fun getScrollType(chapters: List<ChapterItem>) {
        scrollType =
            when {
                ChapterUtil.hasMultipleVolumes(chapters) -> MULTIPLE_VOLUMES
                ChapterUtil.hasMultipleSeasons(chapters) -> MULTIPLE_SEASONS
                ChapterUtil.hasTensOfChapters(chapters) -> TENS_OF_CHAPTERS
                else -> 0
            }
    }

    /**
     * Returns the next unread chapter or null if everything is read.
     */
    fun getNextUnreadChapter(): ChapterItem? = chapterSort.getNextUnreadChapter(chapters)

    fun anyRead(): Boolean = allChapters.any { it.read }

    fun hasBookmark(): Boolean = allChapters.any { it.bookmark }

    fun hasDownloads(): Boolean = allChapters.any { it.isDownloaded }

    fun getUnreadChaptersSorted() =
        chapters
            .filter { !it.read && it.status == Download.State.NOT_DOWNLOADED }
            .distinctBy { it.name }
            .sortedWith(chapterSort.sortComparator(true))

    fun startDownloadingNow(chapter: Chapter) {
        downloadManager.startDownloadNow(chapter)
    }

    /**
     * Downloads the given list of chapters with the manager.
     * @param chapters the list of chapters to download.
     */
    fun downloadChapters(chapters: List<ChapterItem>) {
        downloadManager.downloadChapters(manga, chapters.filter { !it.isDownloaded })
    }

    /**
     * Deletes the given list of chapter.
     * @param chapter the chapter to delete.
     */
    fun deleteChapter(chapter: ChapterItem) {
        downloadManager.deleteChapters(listOf(chapter), manga, source, true)
        this.chapters.find { it.id == chapter.id }?.apply {
            if (chapter.chapter.bookmark && !preferences.removeBookmarkedChapters().get()) return@apply
            status = Download.State.QUEUE
            download = null
        }

        view?.updateChapters(this.chapters)
    }

    /**
     * Deletes the given list of chapter.
     * @param chapters the list of chapters to delete.
     */
    fun deleteChapters(
        chapters: List<ChapterItem>,
        update: Boolean = true,
        isEverything: Boolean = false,
    ) {
        launchIO {
            if (isEverything) {
                downloadManager.deleteManga(manga, source)
            } else {
                downloadManager.deleteChapters(chapters, manga, source)
            }
        }
        chapters.forEach { chapter ->
            this.chapters.find { it.id == chapter.id }?.apply {
                if (chapter.chapter.bookmark && !preferences.removeBookmarkedChapters().get() && !isEverything) return@apply
                status = Download.State.QUEUE
                download = null
            }
        }

        if (update) view?.updateChapters(this.chapters)
    }

    fun refreshMangaFromDb(): Manga {
        val dbManga = db.getManga(manga.id!!).executeAsBlocking()
        manga.copyFrom(dbManga!!)
        return dbManga
    }

    /** Refresh Manga Info and Chapter List (not tracking) */
    fun refreshAll() {
        if (view?.isNotOnline() == true && !manga.isLocal()) return
        presenterScope.launch {
            isLoading = true
            var error: java.lang.Exception? = null
            val thumbnailUrl = manga.thumbnail_url
            val update =
                withContext(Dispatchers.IO) {
                    try {
                        source.getMangaUpdate(
                            manga.copy(),
                            emptyList(),
                            fetchDetails = true,
                            fetchChapters = true,
                        )
                    } catch (e: Exception) {
                        error = e
                        null
                    }
                }

            val networkManga = update?.manga
            if (networkManga != null) {
                manga.copyFrom(networkManga)
                manga.initialized = true

                if (thumbnailUrl != networkManga.thumbnail_url) {
                    coverCache.deleteFromCache(thumbnailUrl)
                }
                db.insertManga(manga).executeAsBlocking()

                launchIO {
                    val request =
                        ImageRequest
                            .Builder(preferences.context)
                            .data(manga)
                            .memoryCachePolicy(CachePolicy.DISABLED)
                            .diskCachePolicy(CachePolicy.WRITE_ONLY)
                            .build()

                    if (Coil.imageLoader(preferences.context).execute(request) is SuccessResult) {
                        preferences.context.imageLoader.memoryCache
                            ?.remove(MemoryCache.Key(manga.key()))
                        withContext(Dispatchers.Main) {
                            view?.setPaletteColor()
                        }
                    }
                }
            }
            val finChapters = update?.chapters.orEmpty()
            if (finChapters.isNotEmpty()) {
                val newChapters = syncChaptersWithSource(db, finChapters, manga, source)
                if (newChapters.first.isNotEmpty()) {
                    if (manga.shouldDownloadNewChapters(db, preferences)) {
                        downloadChapters(
                            newChapters.first
                                .sortedBy { it.chapter_number }
                                .map { it.toModel() },
                        )
                    }
                    view?.view?.context?.let { mangaShortcutManager.updateShortcuts(it) }
                }
                if (newChapters.second.isNotEmpty()) {
                    val removedChaptersId = newChapters.second.map { it.id }
                    val removedChapters =
                        this@MangaDetailsPresenter.chapters.filter {
                            it.id in removedChaptersId && it.isDownloaded
                        }
                    if (removedChapters.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            view?.showChaptersRemovedPopup(
                                removedChapters,
                            )
                        }
                    }
                }
                getChapters()
            }
            isLoading = false
            if (error == null) {
                withContext(Dispatchers.Main) {
                    view?.updateChapters(this@MangaDetailsPresenter.chapters)
                }
            } else {
                withContext(Dispatchers.Main) {
                    view?.showError(
                        trimException(error),
                    )
                }
                return@launch
            }
            getHistory()
        }
    }

    /**
     * Requests an updated list of chapters from the source.
     */
    fun fetchChaptersFromSource() {
        hasRequested = true
        isLoading = true

        presenterScope.launch(Dispatchers.IO) {
            val chapters =
                try {
                    source
                        .getMangaUpdate(
                            manga,
                            emptyList(),
                            fetchDetails = false,
                            fetchChapters = true,
                        ).chapters
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { view?.showError(trimException(e)) }
                    return@launch
                }
            isLoading = false
            try {
                syncChaptersWithSource(db, chapters, manga, source)

                getChapters()
                withContext(Dispatchers.Main) {
                    view?.updateChapters(this@MangaDetailsPresenter.chapters)
                }
                getHistory()
            } catch (e: java.lang.Exception) {
                withContext(Dispatchers.Main) {
                    view?.showError(trimException(e))
                }
            }
        }
    }

    private fun trimException(e: java.lang.Exception): String =
        (
            if (e !is SourceNotFoundException &&
                e.message?.contains(": ") == true
            ) {
                e.message
                    ?.split(": ")
                    ?.drop(1)
                    ?.joinToString(": ")
            } else {
                e.message
            }
        ) ?: view?.view?.context?.getString(R.string.unknown_error) ?: ""

    /**
     * Bookmarks the given list of chapters.
     * @param selectedChapters the list of chapters to bookmark.
     */
    fun bookmarkChapters(
        selectedChapters: List<ChapterItem>,
        bookmarked: Boolean,
    ) {
        presenterScope.launch(Dispatchers.IO) {
            selectedChapters.forEach {
                it.bookmark = bookmarked
            }
            db.updateChaptersProgress(selectedChapters).executeAsBlocking()
            getChapters()
            withContext(Dispatchers.Main) { view?.updateChapters(chapters) }
        }
    }

    /**
     * Mark the selected chapter list as read/unread.
     * @param selectedChapters the list of selected chapters.
     * @param read whether to mark chapters as read or unread.
     */
    fun markChaptersRead(
        selectedChapters: List<ChapterItem>,
        read: Boolean,
        deleteNow: Boolean = true,
        lastRead: Int? = null,
        pagesLeft: Int? = null,
    ) {
        presenterScope.launch(Dispatchers.IO) {
            selectedChapters.forEach {
                it.read = read
                if (!read) {
                    it.last_page_read = lastRead ?: 0
                    it.pages_left = pagesLeft ?: 0
                }
            }
            db.updateChaptersProgress(selectedChapters).executeAsBlocking()
            if (read && deleteNow && preferences.removeAfterMarkedAsRead()) {
                deleteChapters(selectedChapters, false)
            }
            getChapters()
            withContext(Dispatchers.Main) { view?.updateChapters(chapters) }
            if (read && deleteNow) {
                val latestReadChapter = selectedChapters.maxByOrNull { it.chapter_number.toInt() }?.chapter
                updateTrackChapterMarkedAsRead(db, preferences, latestReadChapter, manga.id) {
                    fetchTracks()
                }
            }
        }
    }

    /**
     * Sets the sorting order and requests an UI update.
     */
    fun setSortOrder(
        sort: Int,
        descend: Boolean,
    ) {
        manga.setChapterOrder(sort, if (descend) Manga.CHAPTER_SORT_DESC else Manga.CHAPTER_SORT_ASC)
        if (mangaSortMatchesDefault()) {
            manga.setSortToGlobal()
        }
        asyncUpdateMangaAndChapters()
    }

    fun setGlobalChapterSort(
        sort: Int,
        descend: Boolean,
    ) {
        preferences.sortChapterOrder().set(sort)
        preferences.chaptersDescAsDefault().set(descend)
        manga.setSortToGlobal()
        asyncUpdateMangaAndChapters()
    }

    fun mangaSortMatchesDefault(): Boolean =
        (
            manga.sortDescending == preferences.chaptersDescAsDefault().get() &&
                manga.sorting == preferences.sortChapterOrder().get()
        ) ||
            !manga.usesLocalSort

    fun mangaFilterMatchesDefault(): Boolean =
        (
            manga.readFilter == preferences.filterChapterByRead().get() &&
                manga.downloadedFilter == preferences.filterChapterByDownloaded().get() &&
                manga.bookmarkedFilter == preferences.filterChapterByBookmarked().get() &&
                manga.hideChapterTitles == preferences.hideChapterTitlesByDefault().get()
        ) ||
            !manga.usesLocalFilter

    fun resetSortingToDefault() {
        manga.setSortToGlobal()
        asyncUpdateMangaAndChapters()
    }

    /**
     * Removes all filters and requests an UI update.
     */
    fun setFilters(
        unread: TriStateCheckBox.State,
        downloaded: TriStateCheckBox.State,
        bookmarked: TriStateCheckBox.State,
    ) {
        manga.readFilter =
            when (unread) {
                TriStateCheckBox.State.CHECKED -> Manga.CHAPTER_SHOW_UNREAD
                TriStateCheckBox.State.IGNORE -> Manga.CHAPTER_SHOW_READ
                else -> Manga.SHOW_ALL
            }
        manga.downloadedFilter =
            when (downloaded) {
                TriStateCheckBox.State.CHECKED -> Manga.CHAPTER_SHOW_DOWNLOADED
                TriStateCheckBox.State.IGNORE -> Manga.CHAPTER_SHOW_NOT_DOWNLOADED
                else -> Manga.SHOW_ALL
            }
        manga.bookmarkedFilter =
            when (bookmarked) {
                TriStateCheckBox.State.CHECKED -> Manga.CHAPTER_SHOW_BOOKMARKED
                TriStateCheckBox.State.IGNORE -> Manga.CHAPTER_SHOW_NOT_BOOKMARKED
                else -> Manga.SHOW_ALL
            }
        manga.setFilterToLocal()
        if (mangaFilterMatchesDefault()) {
            manga.setFilterToGlobal()
        }
        asyncUpdateMangaAndChapters()
    }

    /**
     * Sets the active display mode.
     * @param hide set title to hidden
     */
    fun hideTitle(hide: Boolean) {
        manga.displayMode = if (hide) Manga.CHAPTER_DISPLAY_NUMBER else Manga.CHAPTER_DISPLAY_NAME
        manga.setFilterToLocal()
        db.updateChapterFlags(manga).executeAsBlocking()
        if (mangaFilterMatchesDefault()) {
            manga.setFilterToGlobal()
        }
        view?.refreshAdapter()
    }

    fun resetFilterToDefault() {
        manga.setFilterToGlobal()
        asyncUpdateMangaAndChapters()
    }

    fun setGlobalChapterFilters(
        unread: TriStateCheckBox.State,
        downloaded: TriStateCheckBox.State,
        bookmarked: TriStateCheckBox.State,
    ) {
        preferences.filterChapterByRead().set(
            when (unread) {
                TriStateCheckBox.State.CHECKED -> Manga.CHAPTER_SHOW_UNREAD
                TriStateCheckBox.State.IGNORE -> Manga.CHAPTER_SHOW_READ
                else -> Manga.SHOW_ALL
            },
        )
        preferences.filterChapterByDownloaded().set(
            when (downloaded) {
                TriStateCheckBox.State.CHECKED -> Manga.CHAPTER_SHOW_DOWNLOADED
                TriStateCheckBox.State.IGNORE -> Manga.CHAPTER_SHOW_NOT_DOWNLOADED
                else -> Manga.SHOW_ALL
            },
        )
        preferences.filterChapterByBookmarked().set(
            when (bookmarked) {
                TriStateCheckBox.State.CHECKED -> Manga.CHAPTER_SHOW_BOOKMARKED
                TriStateCheckBox.State.IGNORE -> Manga.CHAPTER_SHOW_NOT_BOOKMARKED
                else -> Manga.SHOW_ALL
            },
        )
        preferences.hideChapterTitlesByDefault().set(manga.hideChapterTitles)
        manga.setFilterToGlobal()
        asyncUpdateMangaAndChapters()
    }

    private fun asyncUpdateMangaAndChapters(justChapters: Boolean = false) {
        presenterScope.launch {
            if (!justChapters) db.updateChapterFlags(manga).executeOnIO()
            getChapters()
            withContext(Dispatchers.Main) { view?.updateChapters(chapters) }
        }
    }

    fun currentFilters(): String {
        val filtersId = mutableListOf<Int?>()
        filtersId.add(if (manga.readFilter(preferences) == Manga.CHAPTER_SHOW_READ) R.string.read else null)
        filtersId.add(if (manga.readFilter(preferences) == Manga.CHAPTER_SHOW_UNREAD) R.string.unread else null)
        filtersId.add(if (manga.downloadedFilter(preferences) == Manga.CHAPTER_SHOW_DOWNLOADED) R.string.downloaded else null)
        filtersId.add(if (manga.downloadedFilter(preferences) == Manga.CHAPTER_SHOW_NOT_DOWNLOADED) R.string.not_downloaded else null)
        filtersId.add(if (manga.bookmarkedFilter(preferences) == Manga.CHAPTER_SHOW_BOOKMARKED) R.string.bookmarked else null)
        filtersId.add(if (manga.bookmarkedFilter(preferences) == Manga.CHAPTER_SHOW_NOT_BOOKMARKED) R.string.not_bookmarked else null)
        filtersId.add(if (manga.filtered_scanlators?.isNotEmpty() == true) R.string.scanlators else null)
        return filtersId
            .filterNotNull()
            .joinToString(", ") { view?.view?.context?.getString(it) ?: "" }
    }

    fun setScanlatorFilter(filteredScanlators: Set<String>) {
        val manga = manga
        manga.filtered_scanlators =
            if (filteredScanlators.size == allChapterScanlators.size ||
                filteredScanlators.isEmpty()
            ) {
                null
            } else {
                ChapterUtil.getScanlatorString(filteredScanlators)
            }
        db.updateMangaFilteredScanlators(manga).executeAsBlocking()
        asyncUpdateMangaAndChapters()
    }

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    fun getCategories(): List<Category> = db.getCategories().executeAsBlocking()

    fun confirmDeletion() {
        launchIO {
            coverCache.deleteFromCache(manga)
            customMangaManager.saveMangaInfo(CustomMangaManager.MangaJson(manga.id!!))
            downloadManager.deleteManga(manga, source)
            asyncUpdateMangaAndChapters(true)
        }
    }

    fun shareManga() {
        val context = Injekt.get<Application>()

        val destDir = File(context.cacheDir, "shared_image")

        presenterScope.launchIO {
            destDir.deleteRecursively()
            try {
                val file = saveCover(destDir)
                withUIContext {
                    view?.shareManga(file)
                }
            } catch (_: java.lang.Exception) {
            }
        }
    }

    fun updateManga(
        title: String?,
        author: String?,
        artist: String?,
        uri: Uri?,
        description: String?,
        tags: Array<String>?,
        status: Int?,
        seriesType: Int?,
        lang: String?,
        resetCover: Boolean = false,
    ) {
        if (manga.isLocal()) {
            manga.title = if (title.isNullOrBlank()) manga.url else title.trim()
            manga.author = author?.trimOrNull()
            manga.artist = artist?.trimOrNull()
            manga.description = description?.trimOrNull()
            val tagsString =
                tags?.joinToString(", ") { tag ->
                    tag.replaceFirstChar {
                        it.uppercase(Locale.getDefault())
                    }
                }
            manga.genre = if (tags.isNullOrEmpty()) null else tagsString?.trim()
            if (seriesType != null) {
                manga.genre =
                    setSeriesType(seriesType, manga.genre).joinToString(", ") {
                        it.replaceFirstChar { genre ->
                            genre.titlecase(Locale.getDefault())
                        }
                    }
                manga.viewer_flags = -1
                db.updateViewerFlags(manga).executeAsBlocking()
            }
            manga.status = status ?: SManga.UNKNOWN
            LocalSource(downloadManager.context).updateMangaInfo(manga, lang)
            db.updateMangaInfo(manga).executeAsBlocking()
        } else {
            var genre =
                if (!tags.isNullOrEmpty() && tags.joinToString(", ") != manga.originalGenre) {
                    tags
                        .map { tag -> tag.replaceFirstChar { it.titlecase(Locale.getDefault()) } }
                        .toTypedArray()
                } else {
                    null
                }
            if (seriesType != null) {
                genre = setSeriesType(seriesType, genre?.joinToString(", "))
                manga.viewer_flags = -1
                db.updateViewerFlags(manga).executeAsBlocking()
            }
            val manga =
                CustomMangaManager.MangaJson(
                    manga.id!!,
                    title?.trimOrNull(),
                    author?.trimOrNull(),
                    artist?.trimOrNull(),
                    description?.trimOrNull(),
                    genre,
                    if (status != this.manga.originalStatus) status else null,
                )
            customMangaManager.saveMangaInfo(manga)
        }
        if (uri != null) {
            editCoverWithStream(uri)
        } else if (resetCover) {
            coverCache.deleteCustomCover(manga)
            view?.setPaletteColor()
        }
        view?.updateHeader()
    }

    private fun setSeriesType(
        seriesType: Int,
        genres: String? = null,
    ): Array<String> {
        val tags = (genres ?: manga.genre)?.split(",")?.map { it.trim() }?.toMutableList() ?: mutableListOf()
        tags.removeAll { manga.isSeriesTag(it) }
        when (seriesType) {
            Manga.TYPE_MANGA -> tags.add("Manga")
            Manga.TYPE_MANHUA -> tags.add("Manhua")
            Manga.TYPE_MANHWA -> tags.add("Manhwa")
            Manga.TYPE_COMIC -> tags.add("Comic")
            Manga.TYPE_WEBTOON -> tags.add("Webtoon")
        }
        return tags.toTypedArray()
    }

    fun editCoverWithStream(uri: Uri): Boolean {
        val inputStream =
            downloadManager.context.contentResolver.openInputStream(uri) ?: return false
        if (manga.isLocal()) {
            LocalSource.updateCover(downloadManager.context, manga, inputStream)
            view?.setPaletteColor()
            return true
        }

        if (manga.favorite) {
            coverCache.setCustomCoverToCache(manga, inputStream)
            view?.setPaletteColor()
            return true
        }
        return false
    }

    fun shareCover(): File? =
        try {
            val destDir = File(coverCache.context.cacheDir, "shared_image")
            val file = saveCover(destDir)
            file
        } catch (_: Exception) {
            null
        }

    fun saveCover(): Boolean =
        try {
            val directory =
                if (preferences.folderPerManga().get()) {
                    val baseDir =
                        Environment.getExternalStorageDirectory().absolutePath +
                            File.separator + Environment.DIRECTORY_PICTURES +
                            File.separator + preferences.context.getString(R.string.app_name)

                    File(baseDir + File.separator + DiskUtil.buildValidFilename(manga.title))
                } else {
                    File(
                        Environment.getExternalStorageDirectory().absolutePath +
                            File.separator + Environment.DIRECTORY_PICTURES +
                            File.separator + preferences.context.getString(R.string.app_name),
                    )
                }
            val file = saveCover(directory)
            DiskUtil.scanMedia(preferences.context, file)
            true
        } catch (_: Exception) {
            false
        }

    private fun saveCover(directory: File): File {
        val cover = coverCache.getCustomCoverFile(manga).takeIf { it.exists() } ?: coverCache.getCoverFile(manga)
        val type =
            ImageUtil.findImageType(cover.inputStream())
                ?: throw Exception("Not an image")

        directory.mkdirs()

        // Build destination file.
        val filename = DiskUtil.buildValidFilename("${manga.title}.${type.extension}")

        val destFile = File(directory, filename)
        cover.inputStream().use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return destFile
    }

    fun isTracked(): Boolean = loggedServices.any { service -> tracks.any { it.sync_id == service.id } }

    fun hasTrackers(): Boolean = loggedServices.isNotEmpty()

    // Tracking
    private fun setTrackItems() {
        trackList =
            loggedServices
                .filter { service ->
                    if (service !is EnhancedTrackService) return@filter true
                    service.accept(source)
                }.map { service ->
                    TrackItem(tracks.find { it.sync_id == service.id }, service)
                }
    }

    suspend fun fetchTracks() {
        tracks = withContext(Dispatchers.IO) { db.getTracks(manga).executeAsBlocking() }
        setTrackItems()
        withContext(Dispatchers.Main) { view?.refreshTracking(trackList) }
    }

    fun refreshTracking(
        showOfflineSnack: Boolean = false,
        trackIndex: Int? = null,
    ) {
        if (view?.isNotOnline(showOfflineSnack) == false) {
            presenterScope.launch {
                val asyncList =
                    (trackIndex?.let { listOf(trackList[it]) } ?: trackList.filter { it.track != null })
                        .map { item ->
                            async(Dispatchers.IO) {
                                val trackItem =
                                    try {
                                        item.service.refresh(item.track!!)
                                    } catch (e: Exception) {
                                        trackError(e)
                                        null
                                    }
                                if (trackItem != null) {
                                    db.insertTrack(trackItem).executeAsBlocking()
                                    if (item.service is EnhancedTrackService) {
                                        syncChaptersWithTrackServiceTwoWay(db, chapters, trackItem, item.service)
                                    }
                                    trackItem
                                } else {
                                    item.track
                                }
                            }
                        }
                asyncList.awaitAll()
                fetchTracks()
            }
        }
    }

    fun trackSearch(
        query: String,
        service: TrackService,
    ) {
        if (view?.isNotOnline() == false) {
            presenterScope.launch(Dispatchers.IO) {
                val results =
                    try {
                        service.search(query)
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { view?.trackSearchError(e) }
                        return@launch
                    }
                withContext(Dispatchers.Main) { view?.onTrackSearchResults(results) }
            }
        }
    }

    fun registerTracking(
        item: Track?,
        service: TrackService,
    ) {
        if (item != null) {
            item.manga_id = manga.id!!

            presenterScope.launch {
                val binding =
                    try {
                        service.bind(item)
                    } catch (e: Exception) {
                        trackError(e)
                        null
                    }
                withContext(Dispatchers.IO) {
                    if (binding != null) {
                        db.insertTrack(binding).executeAsBlocking()
                    }

                    if (service is EnhancedTrackService) {
                        syncChaptersWithTrackServiceTwoWay(db, chapters, item, service)
                    }
                }
                fetchTracks()
            }
        }
    }

    fun removeTracker(
        trackItem: TrackItem,
        removeFromService: Boolean,
    ) {
        presenterScope.launch {
            withContext(Dispatchers.IO) {
                db.deleteTrackForManga(manga, trackItem.service).executeAsBlocking()
                if (removeFromService && trackItem.service.canRemoveFromService()) {
                    trackItem.service.removeFromService(trackItem.track!!)
                }
            }
            fetchTracks()
        }
    }

    private fun updateRemote(
        track: Track,
        service: TrackService,
    ) {
        presenterScope.launch {
            val binding =
                try {
                    service.update(track)
                } catch (e: Exception) {
                    trackError(e)
                    null
                }
            if (binding != null) {
                withContext(Dispatchers.IO) { db.insertTrack(binding).executeAsBlocking() }
                fetchTracks()
            } else {
                trackRefreshDone()
            }
        }
    }

    private fun trackRefreshDone() {
        presenterScope.launch(Dispatchers.Main) { view?.trackRefreshDone() }
    }

    private fun trackError(error: Exception) {
        presenterScope.launch(Dispatchers.Main) { view?.trackRefreshError(error) }
    }

    fun setStatus(
        item: TrackItem,
        index: Int,
    ) {
        val track = item.track!!
        track.status = item.service.getStatusList()[index]
        if (item.service.isCompletedStatus(index) && track.total_chapters > 0) {
            track.last_chapter_read = track.total_chapters.toFloat()
        }
        updateRemote(track, item.service)
    }

    fun setScore(
        item: TrackItem,
        index: Int,
    ) {
        val track = item.track!!
        track.score = item.service.indexToScore(index)
        updateRemote(track, item.service)
    }

    fun setLastChapterRead(
        item: TrackItem,
        chapterNumber: Int,
    ) {
        val track = item.track!!
        track.last_chapter_read = chapterNumber.toFloat()
        updateRemote(track, item.service)
    }

    fun setTrackerStartDate(
        item: TrackItem,
        date: Long,
    ) {
        val track = item.track!!
        track.started_reading_date = date
        updateRemote(track, item.service)
    }

    fun setPrivate(
        item: TrackItem,
        private: Boolean,
    ) {
        val track = item.track!!
        track.private = private
        updateRemote(track, item.service)
    }

    fun setTrackerFinishDate(
        item: TrackItem,
        date: Long,
    ) {
        val track = item.track!!
        track.finished_reading_date = date
        updateRemote(track, item.service)
    }

    fun getSuggestedDate(readingDate: TrackingBottomSheet.ReadingDate): Long? {
        val chapters = db.getHistoryByMangaId(manga.id ?: 0L).executeAsBlocking()
        val date =
            when (readingDate) {
                TrackingBottomSheet.ReadingDate.Start -> chapters.minOfOrNull { it.last_read }
                TrackingBottomSheet.ReadingDate.Finish -> chapters.maxOfOrNull { it.last_read }
            } ?: return null
        return if (date <= 0L) null else date
    }

    companion object {
        const val MULTIPLE_VOLUMES = 1
        const val TENS_OF_CHAPTERS = 2
        const val MULTIPLE_SEASONS = 3
    }
}
