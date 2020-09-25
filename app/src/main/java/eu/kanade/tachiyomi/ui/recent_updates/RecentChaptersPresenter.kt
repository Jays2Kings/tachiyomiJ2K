package eu.kanade.tachiyomi.ui.recent_updates

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaChapter
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.download.model.DownloadQueue
import eu.kanade.tachiyomi.data.library.LibraryServiceListener
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.SourceManager
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

class RecentChaptersPresenter(
    private val controller: RecentChaptersController,
    val preferences: PreferencesHelper = Injekt.get(),
    private val db: DatabaseHelper = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get()
) : DownloadQueue.DownloadListener, LibraryServiceListener {

    /**
     * List containing chapter and manga information
     */
    var chapters: List<RecentChapterItem> = emptyList()

    private var scope = CoroutineScope(Job() + Dispatchers.Default)

    fun onCreate() {
        downloadManager.addListener(this)
        LibraryUpdateService.setListener(this)
        getUpdates()
    }

    fun getUpdates() {
        scope.launch {
            val cal = Calendar.getInstance().apply {
                time = Date()
                add(Calendar.MONTH, -1)
            }
            val mangaChapters = db.getRecentChapters(cal.time).executeOnIO()
            val map = TreeMap<Date, MutableList<MangaChapter>> { d1, d2 -> d2.compareTo(d1) }
            val byDay = mangaChapters.groupByTo(map, { getMapKey(it.chapter.date_fetch) })
            val items = byDay.flatMap {
                val dateItem = DateItem(it.key)
                it.value.map { mc ->
                    RecentChapterItem(mc.chapter, mc.manga, dateItem)
                }
            }
            setDownloadedChapters(items)
            chapters = items
            withContext(Dispatchers.Main) { controller.onNextRecentChapters(chapters) }
        }
    }

    fun onDestroy() {
        downloadManager.removeListener(this)
        LibraryUpdateService.removeListener(this)
    }

    fun cancelScope() {
        scope.cancel()
    }

    override fun updateDownload(download: Download) {
        chapters.find { it.chapter.id == download.chapter.id }?.download = download
        scope.launch(Dispatchers.Main) {
            controller.updateChapterDownload(download)
        }
    }

    override fun updateDownloads() {
        scope.launch {
            setDownloadedChapters(chapters)
            withContext(Dispatchers.Main) {
                controller.onNextRecentChapters(chapters)
            }
        }
    }

    override fun onUpdateManga(manga: LibraryManga) {
        getUpdates()
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
     * Finds and assigns the list of downloaded chapters.
     *
     * @param chapters the list of chapter from the database.
     */
    private fun setDownloadedChapters(chapters: List<RecentChapterItem>) {
        for (item in chapters) {
            if (downloadManager.isChapterDownloaded(item.chapter, item.manga)) {
                item.status = Download.DOWNLOADED
            } else if (downloadManager.hasQueue()) {
                item.status = downloadManager.queue.find { it.chapter.id == item.chapter.id }
                    ?.status ?: 0
            }
        }
    }

    /**
     * Mark selected chapter as read
     *
     * @param items list of selected chapters
     * @param read read status
     */
    fun markChapterRead(
        item: RecentChapterItem,
        read: Boolean,
        lastRead: Int? = null,
        pagesLeft: Int? = null
    ) {
        item.chapter.apply {
            this.read = read
            if (!read) {
                last_page_read = lastRead ?: 0
                pages_left = pagesLeft ?: 0
            }
        }
        db.updateChapterProgress(item.chapter).executeAsBlocking()
        controller.onNextRecentChapters(this.chapters)
    }

    fun startDownloadChapterNow(chapter: Chapter) {
        downloadManager.startDownloadNow(chapter)
    }

    /**
     * Deletes the given list of chapter.
     * @param chapter the chapter to delete.
     */
    fun deleteChapter(chapter: Chapter, manga: Manga, update: Boolean = true) {
        val source = Injekt.get<SourceManager>().getOrStub(manga.source)
        downloadManager.deleteChapters(listOf(chapter), manga, source)

        if (update) {
            val item = chapters.find { it.chapter.id == chapter.id } ?: return
            item.apply {
                status = Download.NOT_DOWNLOADED
                download = null
            }

            controller.onNextRecentChapters(chapters)
        }
    }

    /**
     * Download selected chapters
     * @param items list of recent chapters seleted.
     */
    fun downloadChapters(items: List<RecentChapterItem>) {
        items.forEach { downloadManager.downloadChapters(it.manga, listOf(it.chapter)) }
    }
}
