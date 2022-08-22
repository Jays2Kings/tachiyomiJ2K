package eu.kanade.tachiyomi.ui.reader

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.annotation.ColorInt
import com.jakewharton.rxrelay.BehaviorRelay
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.ui.manga.chapter.ChapterItem
import eu.kanade.tachiyomi.ui.reader.chapter.ReaderChapterItem
import eu.kanade.tachiyomi.ui.reader.loader.ChapterLoader
import eu.kanade.tachiyomi.ui.reader.loader.DownloadPageLoader
import eu.kanade.tachiyomi.ui.reader.loader.HttpPageLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.settings.OrientationType
import eu.kanade.tachiyomi.ui.reader.settings.ReadingModeType
import eu.kanade.tachiyomi.util.chapter.ChapterFilter
import eu.kanade.tachiyomi.util.chapter.ChapterSort
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import eu.kanade.tachiyomi.util.chapter.updateTrackChapterRead
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.lang.getUrlWithoutDomain
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.system.executeOnIO
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.localeContext
import eu.kanade.tachiyomi.util.system.withUIContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rx.Completable
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Presenter used by the activity to perform background operations.
 */
class ReaderPresenter(
    private val db: DatabaseHelper = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val preferences: PreferencesHelper = Injekt.get(),
    private val chapterFilter: ChapterFilter = Injekt.get(),
) : BasePresenter<ReaderActivity>() {

    /**
     * The manga loaded in the reader. It can be null when instantiated for a short time.
     */
    var manga: Manga? = null
        private set

    val source: Source?
        get() = manga?.source?.let { sourceManager.getOrStub(it) }

    /**
     * The chapter id of the currently loaded chapter. Used to restore from process kill.
     */
    private var chapterId = -1L

    /**
     * The chapter loader for the loaded manga. It'll be null until [manga] is set.
     */
    private var loader: ChapterLoader? = null

    /**
     * The time the chapter was started reading
     */
    private var chapterReadStartTime: Long? = null

    /**
     * Subscription to prevent setting chapters as active from multiple threads.
     */
    private var activeChapterSubscription: Subscription? = null

    /**
     * Relay for currently active viewer chapters.
     */
    private val viewerChaptersRelay = BehaviorRelay.create<ViewerChapters>()

    val viewerChapters: ViewerChapters?
        get() = viewerChaptersRelay.value

    /**
     * Relay used when loading prev/next chapter needed to lock the UI (with a dialog).
     */
    private val isLoadingAdjacentChapterRelay = BehaviorRelay.create<Boolean>()
    private var finished = false
    private var chapterDownload: Download? = null

    /**
     * Chapter list for the active manga. It's retrieved lazily and should be accessed for the first
     * time in a background thread to avoid blocking the UI.
     */
    private val chapterList by lazy {
        val manga = manga!!
        val dbChapters = db.getChapters(manga).executeAsBlocking()

        val selectedChapter = dbChapters.find { it.id == chapterId }
            ?: error("Requested chapter of id $chapterId not found in chapter list")

        val chaptersForReader =
            chapterFilter.filterChaptersForReader(dbChapters, manga, selectedChapter)
        val chapterSort = ChapterSort(manga, chapterFilter, preferences)
        chaptersForReader.sortedWith(chapterSort.sortComparator(true)).map(::ReaderChapter)
    }

    var chapterItems = emptyList<ReaderChapterItem>()

    private var scope = CoroutineScope(Job() + Dispatchers.Default)

    private var hasTrackers: Boolean = false
    private val checkTrackers: (Manga) -> Unit = { manga ->
        val tracks = db.getTracks(manga).executeAsBlocking()

        hasTrackers = tracks.size > 0
    }

    /**
     * Called when the presenter is created. It retrieves the saved active chapter if the process
     * was restored.
     */
    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        if (savedState != null) {
            chapterId = savedState.getLong(::chapterId.name, -1)
        }
    }

    /**
     * Called when the presenter instance is being saved. It saves the currently active chapter
     * id and the last page read.
     */
    override fun onSave(state: Bundle) {
        super.onSave(state)
        val currentChapter = getCurrentChapter()
        if (currentChapter != null) {
            currentChapter.requestedPage = currentChapter.chapter.last_page_read
            state.putLong(::chapterId.name, currentChapter.chapter.id!!)
        }
    }

    /**
     * Called when the user pressed the back button and is going to leave the reader. Used to
     * trigger deletion of the downloaded chapters.
     */
    fun onBackPressed() {
        if (finished) return
        finished = true
        deletePendingChapters()
        val currentChapters = viewerChaptersRelay.value
        if (currentChapters != null) {
            currentChapters.unref()
            saveReadingProgress(currentChapters.currChapter)
            chapterDownload?.let {
                downloadManager.addDownloadsToStartOfQueue(listOf(it))
            }
        }
    }

    /**
     * Called when the activity is saved and not changing configurations. It updates the database
     * to persist the current progress of the active chapter.
     */
    fun onSaveInstanceStateNonConfigurationChange() {
        val currentChapter = getCurrentChapter() ?: return
        saveChapterProgress(currentChapter)
    }

    /**
     * Whether this presenter is initialized yet.
     */
    fun needsInit(): Boolean {
        return manga == null
    }

    /**
     * Initializes this presenter with the given [mangaId] and [initialChapterId]. This method will
     * fetch the manga from the database and initialize the initial chapter.
     */
    fun init(mangaId: Long, initialChapterId: Long) {
        if (!needsInit()) return

        db.getManga(mangaId).asRxObservable()
            .first()
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { init(it, initialChapterId) }
            .subscribeFirst(
                { _, _ ->
                    // Ignore onNext event
                },
                ReaderActivity::setInitialChapterError,
            )
    }

    suspend fun getChapters(): List<ReaderChapterItem> {
        val manga = manga ?: return emptyList()
        chapterItems = withContext(Dispatchers.IO) {
            val chapterSort = ChapterSort(manga, chapterFilter, preferences)
            val dbChapters = db.getChapters(manga).executeAsBlocking()
            chapterSort.getChaptersSorted(
                dbChapters,
                filterForReader = true,
                currentChapter = getCurrentChapter()?.chapter,
            ).map {
                ReaderChapterItem(
                    it,
                    manga,
                    it.id == getCurrentChapter()?.chapter?.id ?: chapterId,
                )
            }
        }

        return chapterItems
    }

    /**
     * Initializes this presenter with the given [manga] and [initialChapterId]. This method will
     * set the chapter loader, view subscriptions and trigger an initial load.
     */
    private fun init(manga: Manga, initialChapterId: Long) {
        if (!needsInit()) return

        this.manga = manga
        if (chapterId == -1L) chapterId = initialChapterId

        checkTrackers(manga)

        NotificationReceiver.dismissNotification(
            preferences.context,
            manga.id!!.hashCode(),
            Notifications.ID_NEW_CHAPTERS,
        )

        val source = sourceManager.getOrStub(manga.source)
        loader = ChapterLoader(downloadManager, manga, source)

        Observable.just(manga).subscribeLatestCache(ReaderActivity::setManga)
        viewerChaptersRelay.subscribeLatestCache(ReaderActivity::setChapters)
        isLoadingAdjacentChapterRelay.subscribeLatestCache(ReaderActivity::setProgressDialog)

        // Read chapterList from an io thread because it's retrieved lazily and would block main.
        activeChapterSubscription?.unsubscribe()
        activeChapterSubscription = Observable
            .fromCallable { chapterList.first { chapterId == it.chapter.id } }
            .flatMap { getLoadObservable(loader!!, it) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeFirst(
                { _, _ ->
                    // Ignore onNext event
                },
                ReaderActivity::setInitialChapterError,
            )
    }

    /**
     * Returns an observable that loads the given [chapter] with this [loader]. This observable
     * handles main thread synchronization and updating the currently active chapters on
     * [viewerChaptersRelay], however callers must ensure there won't be more than one
     * subscription active by unsubscribing any existing [activeChapterSubscription] before.
     * Callers must also handle the onError event.
     */
    private fun getLoadObservable(
        loader: ChapterLoader,
        chapter: ReaderChapter,
    ): Observable<ViewerChapters> {
        return loader.loadChapter(chapter)
            .andThen(
                Observable.fromCallable {
                    val chapterPos = chapterList.indexOf(chapter)

                    ViewerChapters(
                        chapter,
                        chapterList.getOrNull(chapterPos - 1),
                        chapterList.getOrNull(chapterPos + 1),
                    )
                },
            )
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { newChapters ->
                val oldChapters = viewerChaptersRelay.value

                // Add new references first to avoid unnecessary recycling
                newChapters.ref()
                oldChapters?.unref()

                chapterDownload = deleteChapterFromDownloadQueue(newChapters.currChapter)
                viewerChaptersRelay.call(newChapters)
            }
    }

    fun canLoadUrl(uri: Uri): Boolean {
        val host = uri.host ?: return false
        val delegatedSource = sourceManager.getDelegatedSource(host) ?: return false
        return delegatedSource.canOpenUrl(uri)
    }

    fun intentPageNumber(url: Uri): Int? {
        val host = url.host ?: return null
        val delegatedSource = sourceManager.getDelegatedSource(host) ?: error(
            preferences.context.getString(R.string.source_not_installed),
        )
        return delegatedSource.pageNumber(url)?.minus(1)
    }

    @Suppress("DEPRECATION")
    suspend fun loadChapterURL(url: Uri) {
        val host = url.host ?: return
        val context = view ?: preferences.context
        val delegatedSource = sourceManager.getDelegatedSource(host) ?: error(
            context.getString(R.string.source_not_installed),
        )
        val chapterUrl = delegatedSource.chapterUrl(url)
        val sourceId = delegatedSource.delegate?.id ?: error(
            context.getString(R.string.source_not_installed),
        )
        if (chapterUrl != null) {
            val dbChapter = db.getChapters(chapterUrl).executeOnIO().find {
                val source = db.getManga(it.manga_id!!).executeOnIO()?.source ?: return@find false
                if (source == sourceId) {
                    true
                } else {
                    val httpSource = sourceManager.getOrStub(source) as? HttpSource
                    val domainName = delegatedSource.domainName
                    httpSource?.baseUrl?.contains(domainName) == true
                }
            }
            if (dbChapter?.manga_id != null) {
                val dbManga = db.getManga(dbChapter.manga_id!!).executeOnIO()
                if (dbManga != null) {
                    withContext(Dispatchers.Main) {
                        init(dbManga, dbChapter.id!!)
                    }
                    return
                }
            }
        }
        val info = delegatedSource.fetchMangaFromChapterUrl(url)
        if (info != null) {
            val (chapter, manga, chapters) = info
            val id = db.insertManga(manga).executeOnIO().insertedId()
            manga.id = id ?: manga.id
            chapter.manga_id = manga.id
            val matchingChapterId =
                db.getChapters(manga).executeOnIO().find { it.url == chapter.url }?.id
            if (matchingChapterId != null) {
                withContext(Dispatchers.Main) {
                    this@ReaderPresenter.init(manga, matchingChapterId)
                }
            } else {
                val chapterId: Long
                if (chapters.isNotEmpty()) {
                    val newChapters = syncChaptersWithSource(
                        db,
                        chapters,
                        manga,
                        delegatedSource.delegate!!,
                    ).first
                    chapterId = newChapters.find { it.url == chapter.url }?.id
                        ?: error(context.getString(R.string.chapter_not_found))
                } else {
                    chapter.date_fetch = Date().time
                    chapterId = db.insertChapter(chapter).executeOnIO().insertedId() ?: error(
                        context.getString(R.string.unknown_error),
                    )
                }
                withContext(Dispatchers.Main) {
                    init(manga, chapterId)
                }
            }
        } else error(context.getString(R.string.unknown_error))
    }

    /**
     * Called when the user changed to the given [chapter] when changing pages from the viewer.
     * It's used only to set this chapter as active.
     */
    private fun loadNewChapter(chapter: ReaderChapter) {
        val loader = loader ?: return

        Timber.d("Loading ${chapter.chapter.url}")

        activeChapterSubscription?.unsubscribe()
        activeChapterSubscription = getLoadObservable(loader, chapter)
            .toCompletable()
            .onErrorComplete()
            .subscribe()
            .also(::add)
    }

    fun loadChapter(chapter: Chapter) {
        val loader = loader ?: return

        viewerChaptersRelay.value?.currChapter?.let(::saveReadingProgress)

        Timber.d("Loading ${chapter.url}")

        activeChapterSubscription?.unsubscribe()
        val lastPage = if (chapter.pages_left <= 1) 0 else chapter.last_page_read
        activeChapterSubscription = getLoadObservable(loader, ReaderChapter(chapter))
            .doOnSubscribe { isLoadingAdjacentChapterRelay.call(true) }
            .doOnUnsubscribe { isLoadingAdjacentChapterRelay.call(false) }
            .subscribeFirst(
                { view, _ ->
                    scope.launchUI {
                        view.moveToPageIndex(lastPage, false, chapterChange = true)
                    }
                    view.refreshChapters()
                },
                { _, _ ->
                    // Ignore onError event, viewers handle that state
                },
            )
    }

    fun toggleBookmark(chapter: Chapter) {
        chapter.bookmark = !chapter.bookmark
        db.updateChapterProgress(chapter).executeAsBlocking()
    }

    /**
     * Called when the viewers decide it's a good time to preload a [chapter] and improve the UX so
     * that the user doesn't have to wait too long to continue reading.
     */
    private fun preload(chapter: ReaderChapter) {
        if (chapter.pageLoader is HttpPageLoader) {
            val manga = manga ?: return
            val isDownloaded = downloadManager.isChapterDownloaded(chapter.chapter, manga)
            if (isDownloaded) {
                chapter.state = ReaderChapter.State.Wait
            }
        }

        if (chapter.state != ReaderChapter.State.Wait && chapter.state !is ReaderChapter.State.Error) {
            return
        }

        Timber.d("Preloading ${chapter.chapter.url}")

        val loader = loader ?: return

        loader.loadChapter(chapter)
            .observeOn(AndroidSchedulers.mainThread())
            // Update current chapters whenever a chapter is preloaded
            .doOnCompleted { viewerChaptersRelay.value?.let(viewerChaptersRelay::call) }
            .onErrorComplete()
            .subscribe()
            .also(::add)
    }

    /**
     * Called from the activity to load and set the next chapter as active.
     */
    fun loadNextChapter(): Boolean {
        val nextChapter = viewerChaptersRelay.value?.nextChapter ?: return false
        loadChapter(nextChapter.chapter)
        return true
    }

    /**
     * Called from the activity to load and set the previous chapter as active.
     */
    fun loadPreviousChapter(): Boolean {
        val prevChapter = viewerChaptersRelay.value?.prevChapter ?: return false
        loadChapter(prevChapter.chapter)
        return true
    }

    /**
     * Called every time a page changes on the reader. Used to mark the flag of chapters being
     * read, update tracking services, enqueue downloaded chapter deletion, and updating the active chapter if this
     * [page]'s chapter is different from the currently active.
     */
    fun onPageSelected(page: ReaderPage, hasExtraPage: Boolean) {
        val currentChapters = viewerChaptersRelay.value ?: return

        val selectedChapter = page.chapter

        // Save last page read and mark as read if needed
        selectedChapter.chapter.last_page_read = page.index
        selectedChapter.chapter.pages_left =
            (selectedChapter.pages?.size ?: page.index) - page.index
        val shouldTrack = !preferences.incognitoMode().get() || hasTrackers
        if (shouldTrack &&
            // For double pages, check if the second to last page is doubled up
            (
                (selectedChapter.pages?.lastIndex == page.index && page.firstHalf != true) ||
                    (hasExtraPage && selectedChapter.pages?.lastIndex?.minus(1) == page.index)
                )
        ) {
            selectedChapter.chapter.read = true
            updateTrackChapterAfterReading(selectedChapter)
            deleteChapterIfNeeded(selectedChapter)
        }

        if (selectedChapter != currentChapters.currChapter) {
            Timber.d("Setting ${selectedChapter.chapter.url} as active")
            saveReadingProgress(currentChapters.currChapter)
            setReadStartTime()
            loadNewChapter(selectedChapter)
        }
        val pages = page.chapter.pages ?: return
        val inDownloadRange = page.number.toDouble() / pages.size > 0.2
        if (inDownloadRange) {
            downloadNextChapters()
        }
    }

    private fun downloadNextChapters() {
        val manga = manga ?: return
        if (getCurrentChapter()?.pageLoader !is DownloadPageLoader) return
        val nextChapter = viewerChaptersRelay.value?.nextChapter?.chapter ?: return
        val chaptersNumberToDownload = preferences.autoDownloadWhileReading().get()
        if (chaptersNumberToDownload == 0 || !manga.favorite) return
        val isNextChapterDownloaded = downloadManager.isChapterDownloaded(nextChapter, manga)
        if (isNextChapterDownloaded) {
            downloadAutoNextChapters(chaptersNumberToDownload, nextChapter.id)
        }
    }

    private fun downloadAutoNextChapters(choice: Int, nextChapterId: Long?) {
        val chaptersToDownload = getNextUnreadChaptersSorted(nextChapterId).take(choice - 1)
        if (chaptersToDownload.isNotEmpty()) {
            downloadChapters(chaptersToDownload)
        }
    }

    private fun getNextUnreadChaptersSorted(nextChapterId: Long?): List<ChapterItem> {
        val chapterSort = ChapterSort(manga!!, chapterFilter, preferences)
        return chapterList.map { ChapterItem(it.chapter, manga!!) }
            .filter { !it.read || it.id == nextChapterId }
            .sortedWith(chapterSort.sortComparator(true))
            .takeLastWhile { it.id != nextChapterId }
    }

    /**
     * Downloads the given list of chapters with the manager.
     * @param chapters the list of chapters to download.
     */
    private fun downloadChapters(chapters: List<ChapterItem>) {
        downloadManager.downloadChapters(manga!!, chapters.filter { !it.isDownloaded })
    }

    /**
     * Removes [currentChapter] from download queue
     * if setting is enabled and [currentChapter] is queued for download
     */
    private fun deleteChapterFromDownloadQueue(currentChapter: ReaderChapter): Download? {
        return downloadManager.getChapterDownloadOrNull(currentChapter.chapter)?.apply {
            downloadManager.deletePendingDownloads(this)
        }
    }

    /**
     * Determines if deleting option is enabled and nth to last chapter actually exists.
     * If both conditions are satisfied enqueues chapter for delete
     * @param currentChapter current chapter, which is going to be marked as read.
     */
    private fun deleteChapterIfNeeded(currentChapter: ReaderChapter) {
        // Determine which chapter should be deleted and enqueue
        val currentChapterPosition = chapterList.indexOf(currentChapter)
        val removeAfterReadSlots = preferences.removeAfterReadSlots()
        val chapterToDelete = chapterList.getOrNull(currentChapterPosition - removeAfterReadSlots)

        if (removeAfterReadSlots != 0 && chapterDownload != null) {
            downloadManager.addDownloadsToStartOfQueue(listOf(chapterDownload!!))
        } else {
            chapterDownload = null
        }
        // Check if deleting option is enabled and chapter exists
        if (removeAfterReadSlots != -1 && chapterToDelete != null) {
            enqueueDeleteReadChapters(chapterToDelete)
        }
    }

    /**
     * Called when reader chapter is changed in reader or when activity is paused.
     */
    private fun saveReadingProgress(readerChapter: ReaderChapter) {
        saveChapterProgress(readerChapter)
        saveChapterHistory(readerChapter)
    }

    fun saveCurrentChapterReadingProgress() = getCurrentChapter()?.let { saveReadingProgress(it) }

    /**
     * Saves this [readerChapter] progress (last read page and whether it's read).
     * If incognito mode isn't on or has at least 1 tracker
     */
    private fun saveChapterProgress(readerChapter: ReaderChapter) {
        db.getChapter(readerChapter.chapter.id!!).executeAsBlocking()?.let { dbChapter ->
            readerChapter.chapter.bookmark = dbChapter.bookmark
        }
        if (!preferences.incognitoMode().get() || hasTrackers) {
            db.updateChapterProgress(readerChapter.chapter).executeAsBlocking()
        }
    }

    /**
     * Saves this [readerChapter] last read history.
     */
    private fun saveChapterHistory(readerChapter: ReaderChapter) {
        if (!preferences.incognitoMode().get()) {
            val readAt = Date().time
            val sessionReadDuration = chapterReadStartTime?.let { readAt - it } ?: 0
            val oldTimeRead = db.getHistoryByChapterUrl(readerChapter.chapter.url).executeAsBlocking()?.time_read ?: 0
            val history = History.create(readerChapter.chapter).apply {
                last_read = readAt
                time_read = sessionReadDuration + oldTimeRead
            }
            db.upsertHistoryLastRead(history).executeAsBlocking()
            chapterReadStartTime = null
        }
    }

    fun setReadStartTime() {
        chapterReadStartTime = Date().time
    }

    /**
     * Called from the activity to preload the given [chapter].
     */
    fun preloadChapter(chapter: ReaderChapter) {
        preload(chapter)
    }

    /**
     * Returns the currently active chapter.
     */
    fun getCurrentChapter(): ReaderChapter? {
        return viewerChaptersRelay.value?.currChapter
    }

    fun getChapterUrl(): String? {
        val manga = manga ?: return null
        val source = getSource() ?: return null
        val chapter = getCurrentChapter()?.chapter ?: return null

        val chapterUrl = chapter.url.getUrlWithoutDomain()
        val mangaUrl = source.mangaDetailsRequest(manga).url.toString()
        return if (chapterUrl.isBlank()) {
            mangaUrl
        } else {
            source.fullChapterUrl(mangaUrl, chapterUrl, chapter)
        }
    }

    /** Helper method to handle guya-like sources */
    private fun HttpSource.fullChapterUrl(mangaUrl: String, chapterUrl: String, chapter: Chapter): String {
        val lowerUrl = baseUrl.lowercase()
        return when {
            chapter.url.startsWith("http") -> {
                chapter.url
            }
            lowerUrl.contains("guya") || lowerUrl.contains("danke") ||
                lowerUrl.contains("hachirumi") || lowerUrl.contains("mahoushoujobu") ||
                (lowerUrl.contains("cubari") && !mangaUrl.contains("imgur")) -> {
                // cubari links would have double / without the trim end
                mangaUrl.trimEnd('/') + "/" + chapter.chapter_number.fmt().replace(".", "-")
            }
            else -> baseUrl + chapterUrl
        }
    }

    private fun Float.fmt(): String {
        return if (this == toLong().toFloat()) {
            String.format("%d", toLong())
        } else {
            String.format("%s", this)
        }
    }

    fun getSource() = manga?.source?.let { sourceManager.getOrStub(it) } as? HttpSource

    /**
     * Returns the viewer position used by this manga or the default one.
     */
    fun getMangaReadingMode(): Int {
        val default = preferences.defaultReadingMode()
        val manga = manga ?: return default
        val readerType = manga.defaultReaderType()
        if (manga.viewer_flags == -1) {
            val cantSwitchToLTR =
                (
                    readerType == ReadingModeType.LEFT_TO_RIGHT.flagValue &&
                        default != ReadingModeType.RIGHT_TO_LEFT.flagValue
                    )
            if (manga.viewer_flags == -1) {
                manga.viewer_flags = 0
            }
            manga.readingModeType = if (cantSwitchToLTR) 0 else readerType
            db.updateViewerFlags(manga).asRxObservable().subscribe()
        }
        return if (manga.readingModeType == 0) default else manga.readingModeType
    }

    /**
     * Updates the viewer position for the open manga.
     */
    fun setMangaReadingMode(readingModeType: Int) {
        val manga = manga ?: return
        manga.readingModeType = readingModeType
        db.updateViewerFlags(manga).executeAsBlocking()

        Observable.timer(250, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
            .subscribeFirst({ view, _ ->
                val currChapters = viewerChaptersRelay.value
                if (currChapters != null) {
                    // Save current page
                    val currChapter = currChapters.currChapter
                    currChapter.requestedPage = currChapter.chapter.last_page_read

                    // Emit manga and chapters to the new viewer
                    view.setManga(manga)
                    view.setChapters(currChapters)
                }
            },)
    }

    /**
     * Returns the orientation type used by this manga or the default one.
     */
    fun getMangaOrientationType(): Int {
        val default = preferences.defaultOrientationType().get()
        return when (manga?.orientationType) {
            OrientationType.DEFAULT.flagValue -> default
            else -> manga?.orientationType ?: default
        }
    }

    /**
     * Updates the orientation type for the open manga.
     */
    fun setMangaOrientationType(rotationType: Int) {
        val manga = manga ?: return
        manga.orientationType = rotationType
        db.updateViewerFlags(manga).executeAsBlocking()

        Timber.i("Manga orientation is ${manga.orientationType}")

        Observable.timer(250, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
            .subscribeFirst({ view, _ ->
                val currChapters = viewerChaptersRelay.value
                if (currChapters != null) {
                    view.setOrientation(getMangaOrientationType())
                }
            },)
    }

    /**
     * Saves the image of this [page] in the given [directory] and returns the file location.
     */
    private fun saveImage(page: ReaderPage, directory: File, manga: Manga): File {
        val stream = page.stream!!
        val type = ImageUtil.findImageType(stream) ?: throw Exception("Not an image")

        directory.mkdirs()

        val chapter = page.chapter.chapter

        // Build destination file.
        val filename = DiskUtil.buildValidFilename(
            "${manga.title} - ${chapter.name}".take(225),
        ) + " - ${page.number}.${type.extension}"

        val destFile = File(directory, filename)
        stream().use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return destFile
    }

    /**
     * Saves the image of this [page] in the given [directory] and returns the file location.
     */
    private fun saveImages(page1: ReaderPage, page2: ReaderPage, isLTR: Boolean, @ColorInt bg: Int, directory: File, manga: Manga): File {
        val stream1 = page1.stream!!
        ImageUtil.findImageType(stream1) ?: throw Exception("Not an image")
        val stream2 = page2.stream!!
        ImageUtil.findImageType(stream2) ?: throw Exception("Not an image")
        val imageBytes = stream1().readBytes()
        val imageBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        val imageBytes2 = stream2().readBytes()
        val imageBitmap2 = BitmapFactory.decodeByteArray(imageBytes2, 0, imageBytes2.size)

        val stream = ImageUtil.mergeBitmaps(imageBitmap, imageBitmap2, isLTR, bg)
        directory.mkdirs()

        val chapter = page1.chapter.chapter

        // Build destination file.
        val filename = DiskUtil.buildValidFilename(
            "${manga.title} - ${chapter.name}".take(225),
        ) + " - ${page1.number}-${page2.number}.jpg"

        val destFile = File(directory, filename)
        stream.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        stream.close()
        return destFile
    }

    /**
     * Saves the image of this [page] on the pictures directory and notifies the UI of the result.
     * There's also a notification to allow sharing the image somewhere else or deleting it.
     */
    fun saveImage(page: ReaderPage) {
        if (page.status != Page.READY) return
        val manga = manga ?: return
        val context = Injekt.get<Application>()

        val notifier = SaveImageNotifier(context.localeContext)
        notifier.onClear()

        // Pictures directory.
        val baseDir = Environment.getExternalStorageDirectory().absolutePath +
            File.separator + Environment.DIRECTORY_PICTURES +
            File.separator + context.getString(R.string.app_name)
        val destDir = if (preferences.folderPerManga()) {
            File(baseDir + File.separator + DiskUtil.buildValidFilename(manga.title))
        } else {
            File(baseDir)
        }

        // Copy file in background.
        Observable.fromCallable { saveImage(page, destDir, manga) }
            .doOnNext { file ->
                DiskUtil.scanMedia(context, file)
                notifier.onComplete(file)
            }
            .doOnError { notifier.onError(it.message) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeFirst(
                { view, file -> view.onSaveImageResult(SaveImageResult.Success(file)) },
                { view, error -> view.onSaveImageResult(SaveImageResult.Error(error)) },
            )
    }

    fun saveImages(firstPage: ReaderPage, secondPage: ReaderPage, isLTR: Boolean, @ColorInt bg: Int) {
        scope.launch {
            if (firstPage.status != Page.READY) return@launch
            if (secondPage.status != Page.READY) return@launch
            val manga = manga ?: return@launch
            val context = Injekt.get<Application>()

            val notifier = SaveImageNotifier(context.localeContext)
            notifier.onClear()

            // Pictures directory.
            val baseDir = Environment.getExternalStorageDirectory().absolutePath +
                File.separator + Environment.DIRECTORY_PICTURES +
                File.separator + context.getString(R.string.app_name)
            val destDir = if (preferences.folderPerManga()) {
                File(baseDir + File.separator + DiskUtil.buildValidFilename(manga.title))
            } else {
                File(baseDir)
            }

            try {
                val file = saveImages(firstPage, secondPage, isLTR, bg, destDir, manga)
                DiskUtil.scanMedia(context, file)
                notifier.onComplete(file)
                withUIContext { view?.onSaveImageResult(SaveImageResult.Success(file)) }
            } catch (e: Exception) {
                withUIContext { view?.onSaveImageResult(SaveImageResult.Error(e)) }
            }
        }
    }

    /**
     * Shares the image of this [page] and notifies the UI with the path of the file to share.
     * The image must be first copied to the internal partition because there are many possible
     * formats it can come from, like a zipped chapter, in which case it's not possible to directly
     * get a path to the file and it has to be decompresssed somewhere first. Only the last shared
     * image will be kept so it won't be taking lots of internal disk space.
     */
    fun shareImage(page: ReaderPage) {
        if (page.status != Page.READY) return
        val manga = manga ?: return
        val context = Injekt.get<Application>()

        val destDir = File(context.cacheDir, "shared_image")

        Observable.fromCallable { destDir.deleteRecursively() } // Keep only the last shared file
            .map { saveImage(page, destDir, manga) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeFirst(
                { view, file -> view.onShareImageResult(file, page) },
                { _, _ -> },
            )
    }

    fun shareImages(firstPage: ReaderPage, secondPage: ReaderPage, isLTR: Boolean, @ColorInt bg: Int) {
        scope.launch {
            if (firstPage.status != Page.READY) return@launch
            if (secondPage.status != Page.READY) return@launch
            val manga = manga ?: return@launch
            val context = Injekt.get<Application>()

            val destDir = File(context.cacheDir, "shared_image")
            destDir.deleteRecursively()
            try {
                val file = saveImages(firstPage, secondPage, isLTR, bg, destDir, manga)
                withUIContext {
                    view?.onShareImageResult(file, firstPage, secondPage)
                }
            } catch (e: Exception) {
            }
        }
    }

    /**
     * Sets the image of this [page] as cover and notifies the UI of the result.
     */
    fun setAsCover(page: ReaderPage) {
        if (page.status != Page.READY) return
        val manga = manga ?: return
        val stream = page.stream ?: return

        Observable
            .fromCallable {
                if (manga.isLocal()) {
                    val context = Injekt.get<Application>()
                    coverCache.deleteFromCache(manga)
                    LocalSource.updateCover(context, manga, stream())
                    R.string.cover_updated
                    SetAsCoverResult.Success
                } else {
                    if (manga.favorite) {
                        coverCache.setCustomCoverToCache(manga, stream())
                        SetAsCoverResult.Success
                    } else {
                        SetAsCoverResult.AddToLibraryFirst
                    }
                }
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeFirst(
                { view, result -> view.onSetAsCoverResult(result) },
                { view, _ -> view.onSetAsCoverResult(SetAsCoverResult.Error) },
            )
    }

    /**
     * Results of the set as cover feature.
     */
    enum class SetAsCoverResult {
        Success, AddToLibraryFirst, Error
    }

    /**
     * Results of the save image feature.
     */
    sealed class SaveImageResult {
        class Success(val file: File) : SaveImageResult()
        class Error(val error: Throwable) : SaveImageResult()
    }

    /**
     * Starts the service that updates the last chapter read in sync services. This operation
     * will run in a background thread and errors are ignored.
     */
    private fun updateTrackChapterAfterReading(readerChapter: ReaderChapter) {
        if (!preferences.autoUpdateTrack()) return

        launchIO {
            val newChapterRead = readerChapter.chapter.chapter_number
            updateTrackChapterRead(db, preferences, manga?.id, newChapterRead, true)
        }
    }

    /**
     * Enqueues this [chapter] to be deleted when [deletePendingChapters] is called. The download
     * manager handles persisting it across process deaths.
     */
    private fun enqueueDeleteReadChapters(chapter: ReaderChapter) {
        if (!chapter.chapter.read) return
        val manga = manga ?: return

        Completable
            .fromCallable {
                downloadManager.enqueueDeleteChapters(listOf(chapter.chapter), manga)
            }
            .onErrorComplete()
            .subscribeOn(Schedulers.io())
            .subscribe()
    }

    /**
     * Deletes all the pending chapters. This operation will run in a background thread and errors
     * are ignored.
     */
    private fun deletePendingChapters() {
        Completable.fromCallable { downloadManager.deletePendingChapters() }
            .onErrorComplete()
            .subscribeOn(Schedulers.io())
            .subscribe()
    }
}
