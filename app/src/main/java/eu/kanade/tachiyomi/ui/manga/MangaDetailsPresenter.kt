package eu.kanade.tachiyomi.ui.manga

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.download.model.DownloadQueue
import eu.kanade.tachiyomi.data.library.CustomMangaManager
import eu.kanade.tachiyomi.data.library.LibraryServiceListener
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.fetchChapterListAsync
import eu.kanade.tachiyomi.source.fetchMangaDetailsAsync
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.ui.manga.chapter.ChapterItem
import eu.kanade.tachiyomi.ui.manga.track.TrackItem
import eu.kanade.tachiyomi.ui.security.SecureActivityDelegate
import eu.kanade.tachiyomi.util.chapter.ChapterFilter
import eu.kanade.tachiyomi.util.chapter.ChapterUtil
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import eu.kanade.tachiyomi.util.lang.trimOrNull
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.system.executeOnIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Date

class MangaDetailsPresenter(
    private val controller: MangaDetailsController,
    val manga: Manga,
    val source: Source,
    val preferences: PreferencesHelper = Injekt.get(),
    val coverCache: CoverCache = Injekt.get(),
    private val db: DatabaseHelper = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val chapterFilter: ChapterFilter = Injekt.get()
) : DownloadQueue.DownloadListener, LibraryServiceListener {

    private var scope = CoroutineScope(Job() + Dispatchers.Default)

    private val customMangaManager: CustomMangaManager by injectLazy()

    var isLockedFromSearch = false
    var hasRequested = false
    var isLoading = false
    var scrollType = 0

    private val loggedServices by lazy { Injekt.get<TrackManager>().services.filter { it.isLogged } }
    private var tracks = emptyList<Track>()

    var trackList: List<TrackItem> = emptyList()

    var chapters: List<ChapterItem> = emptyList()
        private set

    var headerItem = MangaHeaderItem(manga, controller.fromCatalogue)

    fun onCreate() {

        isLockedFromSearch = SecureActivityDelegate.shouldBeLocked()
        headerItem.isLocked = isLockedFromSearch
        downloadManager.addListener(this)
        LibraryUpdateService.setListener(this)
        tracks = db.getTracks(manga).executeAsBlocking()
        if (manga.source == LocalSource.ID) {
            refreshAll()
        } else if (!manga.initialized) {
            isLoading = true
            controller.setRefresh(true)
            controller.updateHeader()
            refreshAll()
        } else {
            updateChapters()
            controller.updateChapters(this.chapters)
        }
        setTrackItems()
        refreshTracking(false)
    }

    fun onDestroy() {
        downloadManager.removeListener(this)
        LibraryUpdateService.removeListener(this)
    }

    fun cancelScope() {
        scope.cancel()
    }

    fun fetchChapters(andTracking: Boolean = true) {
        scope.launch {
            getChapters()
            if (andTracking) fetchTracks()
            withContext(Dispatchers.Main) { controller.updateChapters(chapters) }
        }
    }

    private suspend fun getChapters() {
        val chapters = db.getChapters(manga).executeOnIO().map { it.toModel() }

        // Find downloaded chapters
        setDownloadedChapters(chapters)

        // Store the last emission
        this.chapters = applyChapterFilters(chapters)
    }

    private fun updateChapters(fetchedChapters: List<Chapter>? = null) {
        val chapters =
            (fetchedChapters ?: db.getChapters(manga).executeAsBlocking()).map { it.toModel() }

        // Find downloaded chapters
        setDownloadedChapters(chapters)

        // Store the last emission
        this.chapters = applyChapterFilters(chapters)
    }

    /**
     * Finds and assigns the list of downloaded chapters.
     *
     * @param chapters the list of chapter from the database.
     */
    private fun setDownloadedChapters(chapters: List<ChapterItem>) {
        for (chapter in chapters) {
            if (downloadManager.isChapterDownloaded(chapter, manga)) {
                chapter.status = Download.DOWNLOADED
            } else if (downloadManager.hasQueue()) {
                chapter.status = downloadManager.queue.find { it.chapter.id == chapter.id }
                    ?.status ?: 0
            }
        }
    }

    override fun updateDownload(download: Download) {
        chapters.find { it.id == download.chapter.id }?.download = download
        scope.launch(Dispatchers.Main) {
            controller.updateChapterDownload(download)
        }
    }

    override fun updateDownloads() {
        scope.launch(Dispatchers.Default) {
            updateChapters(chapters)
            withContext(Dispatchers.Main) {
                controller.updateChapters(chapters)
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
     * Sets the active display mode.
     * @param hide set title to hidden
     */
    fun hideTitle(hide: Boolean) {
        manga.displayMode = if (hide) Manga.DISPLAY_NUMBER else Manga.DISPLAY_NAME
        db.updateFlags(manga).executeAsBlocking()
        controller.refreshAdapter()
    }

    /**
     * Whether the sorting method is descending or ascending.
     */
    fun sortDescending() = manga.sortDescending(globalSort())

    /**
     * Applies the view filters to the list of chapters obtained from the database.
     * @param chapterList the list of chapters from the database
     * @return an observable of the list of chapters filtered and sorted.
     */
    private fun applyChapterFilters(chapterList: List<ChapterItem>): List<ChapterItem> {
        if (isLockedFromSearch)
            return chapterList

        val chapters = chapterFilter.filterChapters(chapterList, manga)

        val sortFunction: (Chapter, Chapter) -> Int = when (manga.sorting) {
            Manga.SORTING_SOURCE -> when (sortDescending()) {
                true -> { c1, c2 -> c1.source_order.compareTo(c2.source_order) }
                false -> { c1, c2 -> c2.source_order.compareTo(c1.source_order) }
            }
            Manga.SORTING_NUMBER -> when (sortDescending()) {
                true -> { c1, c2 -> c2.chapter_number.compareTo(c1.chapter_number) }
                false -> { c1, c2 -> c1.chapter_number.compareTo(c2.chapter_number) }
            }
            else -> { c1, c2 -> c1.source_order.compareTo(c2.source_order) }
        }
        getScrollType(chapters)
        return chapters.sortedWith(Comparator(sortFunction))
    }

    private fun getScrollType(chapters: List<ChapterItem>) {
        scrollType = when {
            ChapterUtil.hasMultipleVolumes(chapters) -> MULTIPLE_VOLUMES
            ChapterUtil.hasMultipleSeasons(chapters) -> MULTIPLE_SEASONS
            ChapterUtil.hasTensOfChapters(chapters) -> TENS_OF_CHAPTERS
            else -> 0
        }
    }

    /**
     * Returns the next unread chapter or null if everything is read.
     */
    fun getNextUnreadChapter(): ChapterItem? {
        return chapters.sortedByDescending { it.source_order }.find { !it.read }
    }

    fun anyRead(): Boolean = chapters.any { it.read }
    fun hasBookmark(): Boolean = chapters.any { it.bookmark }
    fun hasDownloads(): Boolean = chapters.any { it.isDownloaded }

    fun getUnreadChaptersSorted() =
        chapters.filter { !it.read && it.status == Download.NOT_DOWNLOADED }.distinctBy { it.name }
            .sortedByDescending { it.source_order }

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
        downloadManager.deleteChapters(listOf(chapter), manga, source)
        this.chapters.find { it.id == chapter.id }?.apply {
            status = Download.QUEUE
            download = null
        }

        controller.updateChapters(this.chapters)
    }

    /**
     * Deletes the given list of chapter.
     * @param chapters the list of chapters to delete.
     */
    fun deleteChapters(chapters: List<ChapterItem>, update: Boolean = true) {
        downloadManager.deleteChapters(chapters, manga, source)
        chapters.forEach { chapter ->
            this.chapters.find { it.id == chapter.id }?.apply {
                status = Download.QUEUE
                download = null
            }
        }

        if (update) controller.updateChapters(this.chapters)
    }

    fun refreshMangaFromDb(): Manga {
        val dbManga = db.getManga(manga.id!!).executeAsBlocking()
        manga.copyFrom(dbManga!!)
        return dbManga
    }

    /** Refresh Manga Info and Chapter List (not tracking) */
    fun refreshAll() {
        if (controller.isNotOnline() && manga.source != LocalSource.ID) return
        scope.launch {
            isLoading = true
            var mangaError: java.lang.Exception? = null
            var chapterError: java.lang.Exception? = null
            val chapters = async(Dispatchers.IO) {
                try {
                    source.fetchChapterListAsync(manga)
                } catch (e: Exception) {
                    chapterError = e
                    emptyList<SChapter>()
                } ?: emptyList()
            }
            val thumbnailUrl = manga.thumbnail_url
            val nManga = async(Dispatchers.IO) {
                try {
                    source.fetchMangaDetailsAsync(manga)
                } catch (e: java.lang.Exception) {
                    mangaError = e
                    null
                }
            }

            val networkManga = nManga.await()
            val mangaWasInitalized = manga.initialized
            if (networkManga != null) {
                manga.copyFrom(networkManga)
                manga.initialized = true

                if (thumbnailUrl != networkManga.thumbnail_url) {
                    coverCache.deleteFromCache(thumbnailUrl)
                }
                withContext(Dispatchers.Main) {
                    controller.setPaletteColor()
                }
                db.insertManga(manga).executeAsBlocking()
            }
            val finChapters = chapters.await()
            if (finChapters.isNotEmpty()) {
                val newChapters = syncChaptersWithSource(db, finChapters, manga, source)
                if (newChapters.first.isNotEmpty()) {
                    val downloadNew = preferences.downloadNew().getOrDefault()
                    if (downloadNew && !controller.fromCatalogue && mangaWasInitalized) {
                        val categoriesToDownload = preferences.downloadNewCategories().getOrDefault().map(String::toInt)
                        val shouldDownload = categoriesToDownload.isEmpty() || getMangaCategoryIds().any { it in categoriesToDownload }
                        if (shouldDownload) {
                            downloadChapters(
                                newChapters.first.sortedBy { it.chapter_number }
                                    .map { it.toModel() }
                            )
                        }
                    }
                }
                if (newChapters.second.isNotEmpty()) {
                    val removedChaptersId = newChapters.second.map { it.id }
                    val removedChapters = this@MangaDetailsPresenter.chapters.filter {
                        it.id in removedChaptersId && it.isDownloaded
                    }
                    if (removedChapters.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            controller.showChaptersRemovedPopup(
                                removedChapters
                            )
                        }
                    }
                }
                withContext(Dispatchers.IO) { updateChapters() }
            }
            isLoading = false
            if (chapterError == null) withContext(Dispatchers.Main) { controller.updateChapters(this@MangaDetailsPresenter.chapters) }
            if (chapterError != null) {
                withContext(Dispatchers.Main) {
                    controller.showError(
                        trimException(chapterError!!)
                    )
                }
                return@launch
            } else if (mangaError != null) withContext(Dispatchers.Main) {
                controller.showError(
                    trimException(mangaError!!)
                )
            }
        }
    }

    /**
     * Requests an updated list of chapters from the source.
     */
    fun fetchChaptersFromSource() {
        hasRequested = true
        isLoading = true

        scope.launch(Dispatchers.IO) {
            val chapters = try {
                source.fetchChapterListAsync(manga)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { controller.showError(trimException(e)) }
                return@launch
            } ?: listOf()
            isLoading = false
            try {
                syncChaptersWithSource(db, chapters, manga, source)

                updateChapters()
                withContext(Dispatchers.Main) { controller.updateChapters(this@MangaDetailsPresenter.chapters) }
            } catch (e: java.lang.Exception) {
                withContext(Dispatchers.Main) {
                    controller.showError(trimException(e))
                }
            }
        }
    }

    private fun trimException(e: java.lang.Exception): String {
        return (
            if (e.message?.contains(": ") == true) e.message?.split(": ")?.drop(1)
                ?.joinToString(": ")
            else e.message
            ) ?: preferences.context.getString(R.string.unknown_error)
    }

    /**
     * Bookmarks the given list of chapters.
     * @param selectedChapters the list of chapters to bookmark.
     */
    fun bookmarkChapters(selectedChapters: List<ChapterItem>, bookmarked: Boolean) {
        scope.launch(Dispatchers.IO) {
            selectedChapters.forEach {
                it.bookmark = bookmarked
            }
            db.updateChaptersProgress(selectedChapters).executeAsBlocking()
            getChapters()
            withContext(Dispatchers.Main) { controller.updateChapters(chapters) }
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
        pagesLeft: Int? = null
    ) {
        scope.launch(Dispatchers.IO) {
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
            withContext(Dispatchers.Main) { controller.updateChapters(chapters) }
        }
    }

    /**
     * Sets the sorting order and requests an UI update.
     */
    fun setSortOrder(descend: Boolean) {
        manga.setChapterOrder(if (descend) Manga.SORT_DESC else Manga.SORT_ASC)
        asyncUpdateMangaAndChapters()
    }

    fun globalSort(): Boolean = preferences.chaptersDescAsDefault().getOrDefault()

    fun setGlobalChapterSort(descend: Boolean) {
        preferences.chaptersDescAsDefault().set(descend)
        manga.setSortToGlobal()
        asyncUpdateMangaAndChapters()
    }

    /**
     * Sets the sorting method and requests an UI update.
     */
    fun setSortMethod(bySource: Boolean) {
        manga.sorting = if (bySource) Manga.SORTING_SOURCE else Manga.SORTING_NUMBER
        asyncUpdateMangaAndChapters()
    }

    /**
     * Removes all filters and requests an UI update.
     */
    fun setFilters(read: Boolean, unread: Boolean, downloaded: Boolean, bookmarked: Boolean) {
        manga.readFilter = when {
            read -> Manga.SHOW_READ
            unread -> Manga.SHOW_UNREAD
            else -> Manga.SHOW_ALL
        }
        manga.downloadedFilter = if (downloaded) Manga.SHOW_DOWNLOADED else Manga.SHOW_ALL
        manga.bookmarkedFilter = if (bookmarked) Manga.SHOW_BOOKMARKED else Manga.SHOW_ALL
        asyncUpdateMangaAndChapters()
    }

    private fun asyncUpdateMangaAndChapters(justChapters: Boolean = false) {
        scope.launch {
            if (!justChapters) db.updateFlags(manga).executeOnIO()
            updateChapters()
            withContext(Dispatchers.Main) { controller.updateChapters(chapters) }
        }
    }

    fun currentFilters(): String {
        val filtersId = mutableListOf<Int?>()
        filtersId.add(if (manga.readFilter == Manga.SHOW_READ) R.string.read else null)
        filtersId.add(if (manga.readFilter == Manga.SHOW_UNREAD) R.string.unread else null)
        filtersId.add(if (manga.downloadedFilter == Manga.SHOW_DOWNLOADED) R.string.downloaded else null)
        filtersId.add(if (manga.bookmarkedFilter == Manga.SHOW_BOOKMARKED) R.string.bookmarked else null)
        return filtersId.filterNotNull().joinToString(", ") { preferences.context.getString(it) }
    }

    fun toggleFavorite(): Boolean {
        manga.favorite = !manga.favorite

        when (manga.favorite) {
            true -> {
                manga.date_added = Date().time
            }
            false -> manga.date_added = 0
        }

        db.insertManga(manga).executeAsBlocking()
        controller.updateHeader()
        return manga.favorite
    }

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    fun getCategories(): List<Category> {
        return db.getCategories().executeAsBlocking()
    }

    /**
     * Move the given manga to the category.
     *
     * @param manga the manga to move.
     * @param category the selected category, or null for default category.
     */
    fun moveMangaToCategory(category: Category?) {
        moveMangaToCategories(listOfNotNull(category))
    }

    /**
     * Move the given manga to categories.
     *
     * @param manga the manga to move.
     * @param categories the selected categories.
     */
    fun moveMangaToCategories(categories: List<Category>) {
        val mc = categories.filter { it.id != 0 }.map { MangaCategory.create(manga, it) }
        db.setMangaCategories(mc, listOf(manga))
    }

    /**
     * Gets the category id's the manga is in, if the manga is not in a category, returns the default id.
     *
     * @param manga the manga to get categories from.
     * @return Array of category ids the manga is in, if none returns default id
     */
    fun getMangaCategoryIds(): Array<Int> {
        val categories = db.getCategoriesForManga(manga).executeAsBlocking()
        return categories.mapNotNull { it.id }.toTypedArray()
    }

    fun confirmDeletion() {
        coverCache.deleteFromCache(manga)
        db.resetMangaInfo(manga).executeAsBlocking()
        downloadManager.deleteManga(manga, source)
        customMangaManager.saveMangaInfo(CustomMangaManager.MangaJson(manga.id!!))
        asyncUpdateMangaAndChapters(true)
    }

    fun setFavorite(favorite: Boolean) {
        if (manga.favorite == favorite) {
            return
        }
        toggleFavorite()
    }

    override fun onUpdateManga(manga: LibraryManga) {
        if (manga.id == this.manga.id) {
            fetchChapters()
        }
    }

    fun shareManga(cover: Bitmap) {
        val context = Injekt.get<Application>()

        val destDir = File(context.cacheDir, "shared_image")

        scope.launch(Dispatchers.IO) {
            destDir.deleteRecursively()
            try {
                val image = saveImage(cover, destDir, manga)
                if (image != null) controller.shareManga(image)
                else controller.shareManga()
            } catch (e: java.lang.Exception) {
            }
        }
    }

    private fun saveImage(cover: Bitmap, directory: File, manga: Manga): File? {
        directory.mkdirs()

        // Build destination file.
        val filename = DiskUtil.buildValidFilename("${manga.title} - Cover.jpg")

        val destFile = File(directory, filename)
        val stream: OutputStream = FileOutputStream(destFile)
        cover.compress(Bitmap.CompressFormat.JPEG, 75, stream)
        stream.flush()
        stream.close()
        return destFile
    }

    fun updateManga(
        title: String?,
        author: String?,
        artist: String?,
        uri: Uri?,
        description: String?,
        tags: Array<String>?,
        resetCover: Boolean = false
    ) {
        if (manga.source == LocalSource.ID) {
            manga.title = if (title.isNullOrBlank()) manga.url else title.trim()
            manga.author = author?.trimOrNull()
            manga.artist = artist?.trimOrNull()
            manga.description = description?.trimOrNull()
            val tagsString = tags?.joinToString(", ") { it.capitalize() }
            manga.genre = if (tags.isNullOrEmpty()) null else tagsString?.trim()
            LocalSource(downloadManager.context).updateMangaInfo(manga)
            db.updateMangaInfo(manga).executeAsBlocking()
        } else {
            val genre = if (!tags.isNullOrEmpty() && tags.joinToString(", ") != manga.genre) {
                tags.map { it.capitalize() }.toTypedArray()
            } else {
                null
            }
            val manga = CustomMangaManager.MangaJson(
                manga.id!!,
                title?.trimOrNull(),
                author?.trimOrNull(),
                artist?.trimOrNull(),
                description?.trimOrNull(),
                genre
            )
            customMangaManager.saveMangaInfo(manga)
        }
        if (uri != null) {
            editCoverWithStream(uri)
        } else if (resetCover) {
            coverCache.deleteCustomCover(manga)
            controller.setPaletteColor()
        }
        controller.updateHeader()
    }

    fun editCoverWithStream(uri: Uri): Boolean {
        val inputStream =
            downloadManager.context.contentResolver.openInputStream(uri) ?: return false
        if (manga.source == LocalSource.ID) {
            LocalSource.updateCover(downloadManager.context, manga, inputStream)
            controller.setPaletteColor()
            return true
        }

        if (manga.favorite) {
            coverCache.setCustomCoverToCache(manga, inputStream)
            controller.setPaletteColor()
            return true
        }
        return false
    }

    fun shareCover(): File? {
        return try {
            val destDir = File(coverCache.context.cacheDir, "shared_image")
            val file = saveCover(destDir)
            file
        } catch (e: Exception) {
            null
        }
    }

    fun saveCover(): Boolean {
        return try {
            val directory = File(
                Environment.getExternalStorageDirectory().absolutePath +
                    File.separator + Environment.DIRECTORY_PICTURES +
                    File.separator + "Tachiyomi"
            )
            saveCover(directory)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun saveCover(directory: File): File {
        val cover = coverCache.getCoverFile(manga)
        val type = ImageUtil.findImageType(cover.inputStream())
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

    fun isTracked(): Boolean =
        loggedServices.any { service -> tracks.any { it.sync_id == service.id } }

    fun hasTrackers(): Boolean = loggedServices.isNotEmpty()

    // Tracking
    private fun setTrackItems() {
        scope.launch {
            trackList = loggedServices.map { service ->
                TrackItem(tracks.find { it.sync_id == service.id }, service)
            }
        }
    }

    private suspend fun fetchTracks() {
        tracks = withContext(Dispatchers.IO) { db.getTracks(manga).executeAsBlocking() }
        trackList = loggedServices.map { service ->
            TrackItem(tracks.find { it.sync_id == service.id }, service)
        }
        withContext(Dispatchers.Main) { controller.refreshTracking(trackList) }
    }

    fun refreshTracking(showOfflineSnack: Boolean = false) {
        if (!controller.isNotOnline(showOfflineSnack)) {
            scope.launch {
                val asyncList = trackList.filter { it.track != null }.map { item ->
                    async(Dispatchers.IO) {
                        val trackItem = try {
                            item.service.refresh(item.track!!)
                        } catch (e: Exception) {
                            trackError(e)
                            null
                        }
                        if (trackItem != null) {
                            db.insertTrack(trackItem).executeAsBlocking()
                            trackItem
                        } else item.track
                    }
                }
                asyncList.awaitAll()
                fetchTracks()
            }
        }
    }

    fun trackSearch(query: String, service: TrackService) {
        if (!controller.isNotOnline()) {
            scope.launch(Dispatchers.IO) {
                val results = try {
                    service.search(query)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { controller.trackSearchError(e) }
                    null
                }
                if (!results.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) { controller.onTrackSearchResults(results) }
                } else {
                    withContext(Dispatchers.Main) {
                        controller.trackSearchError(
                            Exception(
                                preferences.context.getString(
                                    R.string.no_results_found
                                )
                            )
                        )
                    }
                }
            }
        }
    }

    fun registerTracking(item: Track?, service: TrackService) {
        if (item != null) {
            item.manga_id = manga.id!!

            scope.launch {
                val binding = try {
                    service.bind(item)
                } catch (e: Exception) {
                    trackError(e)
                    null
                }
                withContext(Dispatchers.IO) {
                    if (binding != null) db.insertTrack(binding).executeAsBlocking()
                }
                fetchTracks()
            }
        }
    }

    fun removeTracker(trackItem: TrackItem, removeFromService: Boolean) {
        scope.launch {
            withContext(Dispatchers.IO) {
                db.deleteTrackForManga(manga, trackItem.service).executeAsBlocking()
                if (removeFromService && trackItem.service.canRemoveFromService()) {
                    trackItem.service.removeFromService(trackItem.track!!)
                }
            }
            fetchTracks()
        }
    }

    private fun updateRemote(track: Track, service: TrackService) {
        scope.launch {
            val binding = try {
                service.update(track)
            } catch (e: Exception) {
                trackError(e)
                null
            }
            if (binding != null) {
                withContext(Dispatchers.IO) { db.insertTrack(binding).executeAsBlocking() }
                fetchTracks()
            } else trackRefreshDone()
        }
    }

    private fun trackRefreshDone() {
        scope.launch(Dispatchers.Main) { controller.trackRefreshDone() }
    }

    private fun trackError(error: Exception) {
        scope.launch(Dispatchers.Main) { controller.trackRefreshError(error) }
    }

    fun setStatus(item: TrackItem, index: Int) {
        val track = item.track!!
        track.status = item.service.getStatusList()[index]
        if (item.service.isCompletedStatus(index) && track.total_chapters > 0)
            track.last_chapter_read = track.total_chapters
        updateRemote(track, item.service)
    }

    fun setScore(item: TrackItem, index: Int) {
        val track = item.track!!
        track.score = item.service.indexToScore(index)
        updateRemote(track, item.service)
    }

    fun setLastChapterRead(item: TrackItem, chapterNumber: Int) {
        val track = item.track!!
        track.last_chapter_read = chapterNumber
        updateRemote(track, item.service)
    }

    companion object {
        const val MULTIPLE_VOLUMES = 1
        const val TENS_OF_CHAPTERS = 2
        const val MULTIPLE_SEASONS = 3
    }
}
