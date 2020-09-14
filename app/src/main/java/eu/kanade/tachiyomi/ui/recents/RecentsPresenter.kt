package eu.kanade.tachiyomi.ui.recents

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.HistoryImpl
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaChapterHistory
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.download.model.DownloadQueue
import eu.kanade.tachiyomi.data.library.LibraryServiceListener
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.recent_updates.DateItem
import eu.kanade.tachiyomi.util.system.executeOnIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Calendar
import java.util.Date
import java.util.TreeMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class RecentsPresenter(
    val controller: RecentsController,
    val preferences: PreferencesHelper = Injekt.get(),
    val downloadManager: DownloadManager = Injekt.get(),
    private val db: DatabaseHelper = Injekt.get()
) : DownloadQueue.DownloadListener, LibraryServiceListener {

    private var scope = CoroutineScope(Job() + Dispatchers.Default)

    var recentItems = listOf<RecentMangaItem>()
        private set
    var query = ""
    private val newAdditionsHeader = RecentMangaHeaderItem(RecentMangaHeaderItem.NEWLY_ADDED)
    private val newChaptersHeader = RecentMangaHeaderItem(RecentMangaHeaderItem.NEW_CHAPTERS)
    val generalHeader = RecentMangaHeaderItem(-1)
    private val continueReadingHeader = RecentMangaHeaderItem(
        RecentMangaHeaderItem
            .CONTINUE_READING
    )
    var viewType: Int = preferences.recentsViewType().getOrDefault()

    fun onCreate() {
        downloadManager.addListener(this)
        LibraryUpdateService.setListener(this)
        if (lastRecents != null) {
            if (recentItems.isEmpty())
                recentItems = lastRecents ?: emptyList()
            lastRecents = null
        }
        getRecents()
    }

    fun getRecents() {
        val oldQuery = query
        scope.launch {
            val isUngrouped = viewType > 0 && query.isEmpty()
            // groupRecents && query.isEmpty()
            val cal = Calendar.getInstance().apply {
                time = Date()
                when {
                    query.isNotEmpty() -> add(Calendar.YEAR, -50)
                    isUngrouped -> add(Calendar.MONTH, -1)
                    else -> add(Calendar.MONTH, -1)
                }
            }

            val calWeek = Calendar.getInstance().apply {
                time = Date()
                when {
                    query.isNotEmpty() -> add(Calendar.YEAR, -50)
                    isUngrouped -> add(Calendar.MONTH, -1)
                    else -> add(Calendar.WEEK_OF_YEAR, -1)
                }
            }

            val calDay = Calendar.getInstance().apply {
                time = Date()
                when {
                    query.isNotEmpty() -> add(Calendar.YEAR, -50)
                    isUngrouped -> add(Calendar.MONTH, -1)
                    else -> add(Calendar.DAY_OF_YEAR, -1)
                }
            }

            val cReading = if (viewType != 3)
                if (query.isEmpty() && viewType != 2)
                    db.getRecentsWithUnread(cal.time, query, isUngrouped).executeOnIO()
                else db.getRecentMangaLimit(
                    cal.time,
                    if (viewType == 2) 200 else 8,
                    query
                ).executeOnIO() else emptyList()
            val rUpdates = when {
                viewType == 3 -> db.getRecentChapters(calWeek.time).executeOnIO().map {
                    MangaChapterHistory(it.manga, it.chapter, HistoryImpl())
                }
                viewType != 2 -> db.getUpdatedManga(calWeek.time, query, isUngrouped).executeOnIO()
                else -> emptyList()
            }
            rUpdates.forEach {
                it.history.last_read = it.chapter.date_fetch
            }
            val nAdditions = if (viewType < 2)
                db.getRecentlyAdded(calDay.time, query, isUngrouped).executeOnIO() else emptyList()
            nAdditions.forEach {
                it.history.last_read = it.manga.date_added
            }
            if (query != oldQuery) return@launch
            val mangaList = (cReading + rUpdates + nAdditions).sortedByDescending {
                it.history.last_read
            }.distinctBy {
                if (query.isEmpty() && viewType != 3) it.manga.id else it.chapter.id
            }
            val pairs = mangaList.mapNotNull {
                val chapter = when {
                    viewType == 3 -> it.chapter
                    it.chapter.read || it.chapter.id == null -> getNextChapter(it.manga)
                    it.history.id == null -> getFirstUpdatedChapter(it.manga, it.chapter)
                    else -> it.chapter
                }
                if (chapter == null) if ((query.isNotEmpty() || viewType > 1) &&
                    it.chapter.id != null
                ) Pair(it, it.chapter)
                else null
                else Pair(it, chapter)
            }
            if (query.isEmpty() && !isUngrouped) {
                val nChaptersItems =
                    pairs.filter { it.first.history.id == null && it.first.chapter.id != null }
                        .sortedWith(
                            Comparator<Pair<MangaChapterHistory, Chapter>> { f1, f2 ->
                                if (abs(f1.second.date_fetch - f2.second.date_fetch) <=
                                    TimeUnit.HOURS.toMillis(12)
                                )
                                    f2.second.date_upload.compareTo(f1.second.date_upload)
                                else
                                    f2.second.date_fetch.compareTo(f1.second.date_fetch)
                            }
                        )
                        .take(4).map {
                            RecentMangaItem(
                                it.first,
                                it.second,
                                newChaptersHeader
                            )
                        } +
                        RecentMangaItem(header = newChaptersHeader)
                val cReadingItems =
                    pairs.filter { it.first.history.id != null }.take(9 - nChaptersItems.size).map {
                        RecentMangaItem(
                            it.first,
                            it.second,
                            continueReadingHeader
                        )
                    } + RecentMangaItem(header = continueReadingHeader)
                val nAdditionsItems = pairs.filter { it.first.chapter.id == null }.take(4)
                    .map { RecentMangaItem(it.first, it.second, newAdditionsHeader) }
                recentItems =
                    listOf(nChaptersItems, cReadingItems, nAdditionsItems).sortedByDescending {
                        it.firstOrNull()?.mch?.history?.last_read ?: 0L
                    }.flatten()
            } else {
                recentItems =
                    if (viewType == 3) {
                        val map = TreeMap<Date, MutableList<Pair<MangaChapterHistory, Chapter>>> {
                            d1, d2 ->
                            d2
                                .compareTo(d1)
                        }
                        val byDay =
                            pairs.groupByTo(map, { getMapKey(it.first.history.last_read) })
                        byDay.flatMap {
                            val dateItem = DateItem(it.key, true)
                            it.value.map { item ->
                                RecentMangaItem(item.first, item.second, dateItem)
                            }
                        }
                    } else pairs.map { RecentMangaItem(it.first, it.second, null) }
                if (isUngrouped && recentItems.isEmpty()) {
                    recentItems = listOf(
                        RecentMangaItem(header = newChaptersHeader),
                        RecentMangaItem(header = continueReadingHeader)
                    )
                }
            }
            setDownloadedChapters(recentItems)
            withContext(Dispatchers.Main) { controller.showLists(recentItems) }
        }
    }

    private fun getNextChapter(manga: Manga): Chapter? {
        val chapters = db.getChapters(manga).executeAsBlocking()
        return chapters.sortedByDescending { it.source_order }.find { !it.read }
    }

    private fun getFirstUpdatedChapter(manga: Manga, chapter: Chapter): Chapter? {
        val chapters = db.getChapters(manga).executeAsBlocking()
        return chapters.sortedByDescending { it.source_order }.find {
            !it.read && abs(it.date_fetch - chapter.date_fetch) <= TimeUnit.HOURS.toMillis(12)
        }
    }

    fun onDestroy() {
        downloadManager.removeListener(this)
        LibraryUpdateService.removeListener(this)
        lastRecents = recentItems
    }

    fun cancelScope() {
        scope.cancel()
    }

    fun toggleGroupRecents(pref: Int) {
        preferences.recentsViewType().set(pref)
        viewType = pref
        getRecents()
    }

    /**
     * Finds and assigns the list of downloaded chapters.
     *
     * @param chapters the list of chapter from the database.
     */
    private fun setDownloadedChapters(chapters: List<RecentMangaItem>) {
        for (item in chapters) {
            if (downloadManager.isChapterDownloaded(item.chapter, item.mch.manga)) {
                item.status = Download.DOWNLOADED
            } else if (downloadManager.hasQueue()) {
                item.status = downloadManager.queue.find { it.chapter.id == item.chapter.id }
                    ?.status ?: 0
            }
        }
    }

    override fun updateDownload(download: Download) {
        recentItems.find { it.chapter.id == download.chapter.id }?.download = download
        scope.launch(Dispatchers.Main) {
            controller.updateChapterDownload(download)
        }
    }

    override fun updateDownloads() {
        scope.launch {
            setDownloadedChapters(recentItems)
            withContext(Dispatchers.Main) {
                controller.showLists(recentItems)
            }
        }
    }

    override fun onUpdateManga(manga: LibraryManga) {
        if (manga.id == null && !LibraryUpdateService.isRunning()) {
            scope.launch(Dispatchers.Main) { controller.setRefreshing(false) }
        } else if (manga.id == null) {
            scope.launch(Dispatchers.Main) { controller.setRefreshing(true) }
        } else {
            getRecents()
        }
    }

    /**
     * Deletes the given list of chapter.
     * @param chapter the chapter to delete.
     */
    fun deleteChapter(chapter: Chapter, manga: Manga, update: Boolean = true) {
        val source = Injekt.get<SourceManager>().getOrStub(manga.source)
        downloadManager.deleteChapters(listOf(chapter), manga, source)

        if (update) {
            val item = recentItems.find { it.chapter.id == chapter.id } ?: return
            item.apply {
                status = Download.NOT_DOWNLOADED
                download = null
            }

            controller.showLists(recentItems)
        }
    }

    /**
     * Get date as time key
     *
     * @param date desired date
     * @return date as time key
     */
    private fun getMapKey(date: Long): Date {
        val cal = Calendar.getInstance()
        cal.time = Date(date)
        cal[Calendar.HOUR_OF_DAY] = 0
        cal[Calendar.MINUTE] = 0
        cal[Calendar.SECOND] = 0
        cal[Calendar.MILLISECOND] = 0
        return cal.time
    }

    /**
     * Downloads the given list of chapters with the manager.
     * @param chapter the chapter to download.
     */
    fun downloadChapter(manga: Manga, chapter: Chapter) {
        downloadManager.downloadChapters(manga, listOf(chapter))
    }

    fun startDownloadChapterNow(chapter: Chapter) {
        downloadManager.startDownloadNow(chapter)
    }

    /**
     * Mark the selected chapter list as read/unread.
     * @param selectedChapters the list of selected chapters.
     * @param read whether to mark chapters as read or unread.
     */
    fun markChapterRead(
        chapter: Chapter,
        read: Boolean,
        lastRead: Int? = null,
        pagesLeft: Int? = null
    ) {
        scope.launch(Dispatchers.IO) {
            chapter.apply {
                this.read = read
                if (!read) {
                    last_page_read = lastRead ?: 0
                    pages_left = pagesLeft ?: 0
                }
            }
            db.updateChaptersProgress(listOf(chapter)).executeAsBlocking()
            getRecents()
        }
    }

    // History
    /**
     * Reset last read of chapter to 0L
     * @param history history belonging to chapter
     */
    fun removeFromHistory(history: History) {
        history.last_read = 0L
        db.updateHistoryLastRead(history).executeAsBlocking()
        getRecents()
    }

    /**
     * Removes all chapters belonging to manga from history.
     * @param mangaId id of manga
     */
    fun removeAllFromHistory(mangaId: Long) {
        val history = db.getHistoryByMangaId(mangaId).executeAsBlocking()
        history.forEach { it.last_read = 0L }
        db.updateHistoryLastRead(history).executeAsBlocking()
        getRecents()
    }

    companion object {
        var lastRecents: List<RecentMangaItem>? = null
    }
}
