package eu.kanade.tachiyomi.data.library

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import coil.Coil
import coil.request.CachePolicy
import coil.request.LoadRequest
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadService
import eu.kanade.tachiyomi.data.library.LibraryUpdateRanker.rankingScheme
import eu.kanade.tachiyomi.data.library.LibraryUpdateService.Companion.start
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.fetchMangaDetailsAsync
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.executeOnIO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * This class will take care of updating the chapters of the manga from the library. It can be
 * started calling the [start] method. If it's already running, it won't do anything.
 * While the library is updating, a [PowerManager.WakeLock] will be held until the update is
 * completed, preventing the device from going to sleep mode. A notification will display the
 * progress of the update, and if case of an unexpected error, this service will be silently
 * destroyed.
 */
class LibraryUpdateService(
    val db: DatabaseHelper = Injekt.get(),
    val coverCache: CoverCache = Injekt.get(),
    val sourceManager: SourceManager = Injekt.get(),
    val preferences: PreferencesHelper = Injekt.get(),
    val downloadManager: DownloadManager = Injekt.get(),
    val trackManager: TrackManager = Injekt.get()
) : Service() {

    /**
     * Wake lock that will be held until the service is destroyed.
     */
    private lateinit var wakeLock: PowerManager.WakeLock

    private lateinit var notifier: LibraryUpdateNotifier

    private var job: Job? = null

    private val mangaToUpdate = mutableListOf<LibraryManga>()

    private val mangaToUpdateMap = mutableMapOf<Long, List<LibraryManga>>()

    private val categoryIds = mutableSetOf<Int>()

    // List containing new updates
    private val newUpdates = mutableMapOf<LibraryManga, Array<Chapter>>()

    // List containing failed updates
    private val failedUpdates = mutableMapOf<Manga, String?>()

    val count = AtomicInteger(0)
    val jobCount = AtomicInteger(0)

    // List containing categories that get included in downloads.
    private val categoriesToDownload =
        preferences.downloadNewCategories().getOrDefault().map(String::toInt)

    // Boolean to determine if user wants to automatically download new chapters.
    private val downloadNew: Boolean = preferences.downloadNew().getOrDefault()

    // Boolean to determine if DownloadManager has downloads
    private var hasDownloads = false

    private val requestSemaphore = Semaphore(5)

    // For updates delete removed chapters if not preference is set as well
    private val deleteRemoved by lazy {
        preferences.deleteRemovedChapters().get() != 1
    }

    /**
     * Defines what should be updated within a service execution.
     */
    enum class Target {

        CHAPTERS, // Manga chapters
        DETAILS, // Manga metadata
        TRACKING // Tracking metadata
    }

    /**
     * Method called when the service receives an intent.
     *
     * @param intent the start intent from.
     * @param flags the flags of the command.
     * @param startId the start id of this command.
     * @return the start value of the command.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        val target = intent.getSerializableExtra(KEY_TARGET) as? Target ?: return START_NOT_STICKY

        instance = this

        val selectedScheme = preferences.libraryUpdatePrioritization().getOrDefault()
        val savedMangasList = intent.getLongArrayExtra(KEY_MANGAS)?.asList()

        val mangaList = (
            if (savedMangasList != null) {
                val mangas = db.getLibraryMangas().executeAsBlocking().filter {
                    it.id in savedMangasList
                }.distinctBy { it.id }
                val categoryId = intent.getIntExtra(KEY_CATEGORY, -1)
                if (categoryId > -1) categoryIds.add(categoryId)
                mangas
            } else {
                getMangaToUpdate(intent, target)
            }
            ).sortedWith(rankingScheme[selectedScheme])
        // Update favorite manga. Destroy service when completed or in case of an error.
        launchTarget(target, mangaList, startId)
        return START_REDELIVER_INTENT
    }

    /**
     * Method called when the service is created. It injects dagger dependencies and acquire
     * the wake lock.
     */
    override fun onCreate() {
        super.onCreate()
        notifier = LibraryUpdateNotifier(this)
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LibraryUpdateService:WakeLock"
        )
        wakeLock.acquire(TimeUnit.MINUTES.toMillis(30))
        startForeground(Notifications.ID_LIBRARY_PROGRESS, notifier.progressNotificationBuilder.build())
    }

    /**
     * Method called when the service is destroyed. It cancels jobs and releases the wake lock.
     */
    override fun onDestroy() {
        job?.cancel()
        if (instance == this)
            instance = null
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        listener?.onUpdateManga(LibraryManga())
        super.onDestroy()
    }

    private fun getMangaToUpdate(intent: Intent, target: Target): List<LibraryManga> {
        val categoryId = intent.getIntExtra(KEY_CATEGORY, -1)
        return getMangaToUpdate(categoryId, target)
    }

    /**
     * Returns the list of manga to be updated.
     *
     * @param intent the update intent.
     * @param target the target to update.
     * @return a list of manga to update
     */
    private fun getMangaToUpdate(categoryId: Int, target: Target): List<LibraryManga> {
        var listToUpdate = if (categoryId != -1) {
            categoryIds.add(categoryId)
            db.getLibraryMangas().executeAsBlocking().filter { it.category == categoryId }
        } else {
            val categoriesToUpdate =
                preferences.libraryUpdateCategories().getOrDefault().map(String::toInt)
            if (categoriesToUpdate.isNotEmpty()) {
                categoryIds.addAll(categoriesToUpdate)
                db.getLibraryMangas().executeAsBlocking()
                    .filter { it.category in categoriesToUpdate }.distinctBy { it.id }
            } else {
                categoryIds.addAll(db.getCategories().executeAsBlocking().mapNotNull { it.id } + 0)
                db.getLibraryMangas().executeAsBlocking().distinctBy { it.id }
            }
        }
        if (target == Target.CHAPTERS && preferences.updateOnlyNonCompleted()) {
            listToUpdate = listToUpdate.filter { it.status != SManga.COMPLETED }
        }

        return listToUpdate
    }

    private fun launchTarget(target: Target, mangaToAdd: List<LibraryManga>, startId: Int) {
        val handler = CoroutineExceptionHandler { _, exception ->
            Timber.e(exception)
            stopSelf(startId)
        }
        if (target == Target.CHAPTERS) {
            listener?.onUpdateManga(LibraryManga())
        }
        job = GlobalScope.launch(handler) {
            when (target) {
                Target.CHAPTERS -> updateChaptersJob(mangaToAdd)
                Target.DETAILS -> updateDetails(mangaToAdd)
                else -> updateTrackings(mangaToAdd)
            }
        }

        job?.invokeOnCompletion { stopSelf(startId) }
    }

    private fun addManga(mangaToAdd: List<LibraryManga>) {
        val distinctManga = mangaToAdd.filter { it !in mangaToUpdate }
        mangaToUpdate.addAll(distinctManga)
        distinctManga.groupBy { it.source }.forEach {
            // if added queue items is a new source not in the async list or an async list has
            // finished running
            if (mangaToUpdateMap[it.key].isNullOrEmpty()) {
                mangaToUpdateMap[it.key] = it.value
                jobCount.andIncrement
                val handler = CoroutineExceptionHandler { _, exception ->
                    Timber.e(exception)
                }
                GlobalScope.launch(handler) {
                    val hasDLs = try {
                        requestSemaphore.withPermit {
                            updateMangaInSource(
                                it.key,
                                downloadNew,
                                categoriesToDownload
                            )
                        }
                    } catch (e: Exception) {
                        false
                    }
                    hasDownloads = hasDownloads || hasDLs
                    jobCount.andDecrement
                    finishUpdates()
                }
            } else {
                val list = mangaToUpdateMap[it.key] ?: emptyList()
                mangaToUpdateMap[it.key] = (list + it.value)
            }
        }
    }

    private fun addMangaToQueue(categoryId: Int, manga: List<LibraryManga>) {
        val selectedScheme = preferences.libraryUpdatePrioritization().getOrDefault()
        val mangas = manga.sortedWith(rankingScheme[selectedScheme])
        categoryIds.add(categoryId)
        addManga(mangas)
    }

    private fun addCategory(categoryId: Int) {
        val selectedScheme = preferences.libraryUpdatePrioritization().getOrDefault()
        val mangas =
            getMangaToUpdate(categoryId, Target.CHAPTERS).sortedWith(
                rankingScheme[selectedScheme]
            )
        categoryIds.add(categoryId)
        addManga(mangas)
    }

    /**
     * This method needs to be implemented, but it's not used/needed.
     */
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private suspend fun updateChaptersJob(mangaToAdd: List<LibraryManga>) {
        // Initialize the variables holding the progress of the updates.

        mangaToUpdate.addAll(mangaToAdd)
        mangaToUpdateMap.putAll(mangaToAdd.groupBy { it.source })
        coroutineScope {
            jobCount.andIncrement
            val list = mangaToUpdateMap.keys.map { source ->
                async {
                    try {
                        requestSemaphore.withPermit {
                            updateMangaInSource(source, downloadNew, categoriesToDownload)
                        }
                    } catch (e: Exception) {
                        Timber.e(e)
                        false
                    }
                }
            }
            val results = list.awaitAll()
            hasDownloads = hasDownloads || results.any { it }
            jobCount.andDecrement
            finishUpdates()
        }
    }

    private suspend fun finishUpdates() {
        if (jobCount.get() != 0) return
        if (newUpdates.isNotEmpty()) {
            notifier.showResultNotification(newUpdates)

            if (preferences.refreshCoversToo().getOrDefault() && job?.isCancelled == false) {
                updateDetails(newUpdates.keys.toList())
                notifier.cancelProgressNotification()
                if (downloadNew && hasDownloads) {
                    DownloadService.start(this)
                }
            } else if (downloadNew && hasDownloads) {
                DownloadService.start(this)
            }
            newUpdates.clear()
        }
        if (preferences.showLibraryUpdateErrors() && failedUpdates.isNotEmpty()) {
            val errorFile = writeErrorFile(failedUpdates)
            notifier.showUpdateErrorNotification(
                failedUpdates.map { it.key.title },
                errorFile.getUriCompat(this)
            )
        }
        failedUpdates.clear()
        notifier.cancelProgressNotification()
    }

    private suspend fun updateMangaInSource(
        source: Long,
        downloadNew: Boolean,
        categoriesToDownload: List<Int>
    ): Boolean {
        if (mangaToUpdateMap[source] == null) return false
        var count = 0
        var hasDownloads = false
        while (count < mangaToUpdateMap[source]!!.size) {
            val shouldDownload =
                (
                    downloadNew && (
                        categoriesToDownload.isEmpty() ||
                            mangaToUpdateMap[source]!![count].category in categoriesToDownload ||
                            db.getCategoriesForManga(mangaToUpdateMap[source]!![count])
                                .executeOnIO().any { (it.id ?: -1) in categoriesToDownload }
                        )
                    )
            if (updateMangaChapters(mangaToUpdateMap[source]!![count], this.count.andIncrement, shouldDownload)) {
                hasDownloads = true
            }
            count++
        }
        mangaToUpdateMap[source] = emptyList()
        return hasDownloads
    }

    private suspend fun updateMangaChapters(
        manga: LibraryManga,
        progress: Int,
        shouldDownload: Boolean
    ):
        Boolean {
            try {
                var hasDownloads = false
                if (job?.isCancelled == true) {
                    return false
                }
                notifier.showProgressNotification(manga, progress, mangaToUpdate.size)
                val source = sourceManager.get(manga.source) as? HttpSource ?: return false
                val fetchedChapters = withContext(Dispatchers.IO) {
                    source.fetchChapterList(manga).toBlocking().single()
                } ?: emptyList()
                if (fetchedChapters.isNotEmpty()) {
                    val newChapters = syncChaptersWithSource(db, fetchedChapters, manga, source)
                    if (newChapters.first.isNotEmpty()) {
                        if (shouldDownload) {
                            downloadChapters(manga, newChapters.first.sortedBy { it.chapter_number })
                            hasDownloads = true
                        }
                        newUpdates[manga] =
                            newChapters.first.sortedBy { it.chapter_number }.toTypedArray()
                    }
                    if (deleteRemoved && newChapters.second.isNotEmpty()) {
                        val removedChapters = newChapters.second.filter {
                            downloadManager.isChapterDownloaded(it, manga)
                        }
                        if (removedChapters.isNotEmpty()) {
                            downloadManager.deleteChapters(removedChapters, manga, source)
                        }
                    }
                    if (newChapters.first.size + newChapters.second.size > 0) listener?.onUpdateManga(
                        manga
                    )
                }
                return hasDownloads
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    failedUpdates[manga] = e.message
                    Timber.e("Failed updating: ${manga.title}: $e")
                }
                return false
            }
        }

    private fun downloadChapters(manga: Manga, chapters: List<Chapter>) {
        // We don't want to start downloading while the library is updating, because websites
        // may don't like it and they could ban the user.
        downloadManager.downloadChapters(manga, chapters, false)
    }

    /**
     * Method that updates the details of the given list of manga. It's called in a background
     * thread, so it's safe to do heavy operations or network calls here.
     *
     * @param mangaToUpdate the list to update
     */
    suspend fun updateDetails(mangaToUpdate: List<LibraryManga>) = coroutineScope {
        // Initialize the variables holding the progress of the updates.
        val count = AtomicInteger(0)
        val asyncList = mangaToUpdate.groupBy { it.source }.values.map { list ->
            async {
                requestSemaphore.withPermit {
                    list.forEach { manga ->
                        if (job?.isCancelled == true) {
                            return@async
                        }
                        val source = sourceManager.get(manga.source) as? HttpSource ?: return@async
                        notifier.showProgressNotification(
                            manga,
                            count.andIncrement,
                            mangaToUpdate.size
                        )

                        val networkManga = try {
                            source.fetchMangaDetailsAsync(manga)
                        } catch (e: java.lang.Exception) {
                            Timber.e(e)
                            null
                        }
                        if (networkManga != null) {
                            val thumbnailUrl = manga.thumbnail_url
                            manga.copyFrom(networkManga)
                            manga.initialized = true
                            if (thumbnailUrl != manga.thumbnail_url) {
                                coverCache.deleteFromCache(thumbnailUrl)
                                // load new covers in background
                                val request =
                                    LoadRequest.Builder(this@LibraryUpdateService).data(manga)
                                        .memoryCachePolicy(CachePolicy.DISABLED).build()
                                Coil.imageLoader(this@LibraryUpdateService).execute(request)
                            }
                            db.insertManga(manga).executeAsBlocking()
                        }
                    }
                }
            }
        }
        asyncList.awaitAll()
        notifier.cancelProgressNotification()
    }

    /**
     * Method that updates the metadata of the connected tracking services. It's called in a
     * background thread, so it's safe to do heavy operations or network calls here.
     */

    private suspend fun updateTrackings(mangaToUpdate: List<LibraryManga>) {
        // Initialize the variables holding the progress of the updates.
        var count = 0

        val loggedServices = trackManager.services.filter { it.isLogged }

        mangaToUpdate.forEach { manga ->
            notifier.showProgressNotification(manga, count++, mangaToUpdate.size)

            val tracks = db.getTracks(manga).executeAsBlocking()

            tracks.forEach { track ->
                val service = trackManager.getService(track.sync_id)
                if (service != null && service in loggedServices) {
                    try {
                        val newTrack = service.refresh(track)
                        db.insertTrack(newTrack).executeAsBlocking()
                    } catch (e: Exception) {
                        Timber.e(e)
                    }
                }
            }
        }
        notifier.cancelProgressNotification()
    }

    /**
     * Writes basic file of update errors to cache dir.
     */
    private fun writeErrorFile(errors: Map<Manga, String?>): File {
        try {
            if (errors.isNotEmpty()) {
                val destFile = File(externalCacheDir, "tachiyomi_update_errors.txt")

                destFile.bufferedWriter().use { out ->
                    errors.forEach { (manga, error) ->
                        out.write("${manga.title}: $error\n")
                    }
                }
                return destFile
            }
        } catch (e: Exception) {
            // Empty
        }
        return File("")
    }

    companion object {

        /**
         * Key for category to update.
         */
        const val KEY_CATEGORY = "category"

        fun categoryInQueue(id: Int?) = instance?.categoryIds?.contains(id) ?: false
        private var instance: LibraryUpdateService? = null

        /**
         * Key that defines what should be updated.
         */
        const val KEY_TARGET = "target"

        /**
         * Key for list of manga to be updated. (For dynamic categories)
         */
        const val KEY_MANGAS = "mangas"

        /**
         * Returns the status of the service.
         *
         * @return true if the service is running, false otherwise.
         */
        fun isRunning(): Boolean {
            return instance != null
        }

        /**
         * Starts the service. It will be started only if there isn't another instance already
         * running.
         *
         * @param context the application context.
         * @param category a specific category to update, or null for global update.
         * @param target defines what should be updated.
         */
        fun start(
            context: Context,
            category: Category? = null,
            target: Target = Target.CHAPTERS,
            mangaToUse: List<LibraryManga>? = null
        ) {
            if (!isRunning()) {
                val intent = Intent(context, LibraryUpdateService::class.java).apply {
                    putExtra(KEY_TARGET, target)
                    category?.id?.let { id ->
                        putExtra(KEY_CATEGORY, id)
                        if (mangaToUse != null) putExtra(
                            KEY_MANGAS,
                            mangaToUse.mapNotNull { it.id }.toLongArray()
                        )
                    }
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    context.startService(intent)
                } else {
                    context.startForegroundService(intent)
                }
            } else {
                if (target == Target.CHAPTERS) category?.id?.let {
                    if (mangaToUse != null) instance?.addMangaToQueue(it, mangaToUse)
                    else instance?.addCategory(it)
                }
            }
        }

        /**
         * Stops the service.
         *
         * @param context the application context.
         */
        fun stop(context: Context) {
            instance?.job?.cancel()
            GlobalScope.launch {
                instance?.jobCount?.set(0)
                instance?.finishUpdates()
            }
            context.stopService(Intent(context, LibraryUpdateService::class.java))
        }

        private var listener: LibraryServiceListener? = null

        fun setListener(listener: LibraryServiceListener) {
            this.listener = listener
        }

        fun removeListener(listener: LibraryServiceListener) {
            if (this.listener == listener) this.listener = null
        }
    }
}

interface LibraryServiceListener {
    fun onUpdateManga(manga: LibraryManga)
}
