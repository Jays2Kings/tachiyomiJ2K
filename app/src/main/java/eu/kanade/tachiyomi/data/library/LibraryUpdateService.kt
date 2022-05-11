package eu.kanade.tachiyomi.data.library

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.work.NetworkType
import coil.Coil
import coil.request.CachePolicy
import coil.request.ImageRequest
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.toMangaInfo
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadService
import eu.kanade.tachiyomi.data.library.LibraryUpdateService.Companion.start
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.MANGA_HAS_UNREAD
import eu.kanade.tachiyomi.data.preference.MANGA_NON_COMPLETED
import eu.kanade.tachiyomi.data.preference.MANGA_NON_READ
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.extension.ExtensionUpdateJob
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.toSChapter
import eu.kanade.tachiyomi.source.model.toSManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithTrackServiceTwoWay
import eu.kanade.tachiyomi.util.manga.MangaShortcutManager
import eu.kanade.tachiyomi.util.shouldDownloadNewChapters
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.acquireWakeLock
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
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
    val trackManager: TrackManager = Injekt.get(),
    private val mangaShortcutManager: MangaShortcutManager = Injekt.get(),
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

    // List containing skipped updates
    private val skippedUpdates = mutableMapOf<Manga, String?>()

    val count = AtomicInteger(0)
    val jobCount = AtomicInteger(0)

    // Boolean to determine if user wants to automatically download new chapters.
    private val downloadNew: Boolean = preferences.downloadNewChapters().get()

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
                getMangaToUpdate(intent)
            }
            ).sortedBy { it.title }
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
        wakeLock = acquireWakeLock(timeout = TimeUnit.MINUTES.toMillis(30))
        startForeground(Notifications.ID_LIBRARY_PROGRESS, notifier.progressNotificationBuilder.build())
    }

    /**
     * Method called when the service is destroyed. It cancels jobs and releases the wake lock.
     */
    override fun onDestroy() {
        job?.cancel()
        if (instance == this) {
            instance = null
        }
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        listener?.onUpdateManga()
        super.onDestroy()
    }

    private fun getMangaToUpdate(intent: Intent): List<LibraryManga> {
        val categoryId = intent.getIntExtra(KEY_CATEGORY, -1)
        return getMangaToUpdate(categoryId)
    }

    /**
     * Returns the list of manga to be updated.
     *
     * @param intent the update intent.
     * @param target the target to update.
     * @return a list of manga to update
     */
    private fun getMangaToUpdate(categoryId: Int): List<LibraryManga> {
        val libraryManga = db.getLibraryMangas().executeAsBlocking()

        val listToUpdate = if (categoryId != -1) {
            categoryIds.add(categoryId)
            libraryManga.filter { it.category == categoryId }
        } else {
            val categoriesToUpdate =
                preferences.libraryUpdateCategories().get().map(String::toInt)
            if (categoriesToUpdate.isNotEmpty()) {
                categoryIds.addAll(categoriesToUpdate)
                libraryManga.filter { it.category in categoriesToUpdate }.distinctBy { it.id }
            } else {
                categoryIds.addAll(db.getCategories().executeAsBlocking().mapNotNull { it.id } + 0)
                libraryManga.distinctBy { it.id }
            }
        }

        val categoriesToExclude =
            preferences.libraryUpdateCategoriesExclude().get().map(String::toInt)
        val listToExclude = if (categoriesToExclude.isNotEmpty()) {
            libraryManga.filter { it.category in categoriesToExclude }
        } else {
            emptyList()
        }

        return listToUpdate.minus(listToExclude)
    }

    private fun launchTarget(target: Target, mangaToAdd: List<LibraryManga>, startId: Int) {
        val handler = CoroutineExceptionHandler { _, exception ->
            Timber.e(exception)
            stopSelf(startId)
        }
        if (target == Target.CHAPTERS) {
            listener?.onUpdateManga(Manga.create(STARTING_UPDATE_SOURCE))
        }
        job = GlobalScope.launch(handler) {
            when (target) {
                Target.CHAPTERS -> updateChaptersJob(filterMangaToUpdate(mangaToAdd))
                Target.DETAILS -> updateDetails(mangaToAdd)
                else -> updateTrackings(mangaToAdd)
            }
        }

        job?.invokeOnCompletion { stopSelf(startId) }
    }

    private fun addManga(mangaToAdd: List<LibraryManga>) {
        val distinctManga = mangaToAdd.filter { it !in mangaToUpdate }
        mangaToUpdate.addAll(distinctManga)
        checkIfMassiveUpdate()
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
                        requestSemaphore.withPermit { updateMangaInSource(it.key) }
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
        val mangas = filterMangaToUpdate(manga).sortedBy { it.title }
        categoryIds.add(categoryId)
        addManga(mangas)
    }

    private fun addCategory(categoryId: Int) {
        val mangas = filterMangaToUpdate(getMangaToUpdate(categoryId)).sortedBy { it.title }
        categoryIds.add(categoryId)
        addManga(mangas)
    }

    /**
     * This method needs to be implemented, but it's not used/needed.
     */
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun filterMangaToUpdate(mangaToAdd: List<LibraryManga>): List<LibraryManga> {
        val restrictions = preferences.libraryUpdateMangaRestriction().get()
        return mangaToAdd.filter { manga ->
            return@filter if (MANGA_NON_COMPLETED in restrictions && manga.status == SManga.COMPLETED) {
                skippedUpdates[manga] = getString(R.string.skipped_reason_completed)
                false
            } else if (MANGA_HAS_UNREAD in restrictions && manga.unread != 0) {
                skippedUpdates[manga] = getString(R.string.skipped_reason_not_caught_up)
                false
            } else if (MANGA_NON_READ in restrictions && manga.totalChapters > 0 && !manga.hasRead) {
                skippedUpdates[manga] = getString(R.string.skipped_reason_not_started)
                false
            } else true
        }
    }

    private fun checkIfMassiveUpdate() {
        val largestSourceSize = mangaToUpdate
            .groupBy { it.source }
            .filterKeys { sourceManager.get(it) !is UnmeteredSource }
            .maxOfOrNull { it.value.size } ?: 0
        if (largestSourceSize > MANGA_PER_SOURCE_QUEUE_WARNING_THRESHOLD) {
            notifier.showQueueSizeWarningNotification()
        }
    }

    private suspend fun updateChaptersJob(mangaToAdd: List<LibraryManga>) {
        // Initialize the variables holding the progress of the updates.

        mangaToUpdate.addAll(mangaToAdd)
        mangaToUpdateMap.putAll(mangaToAdd.groupBy { it.source })
        checkIfMassiveUpdate()
        coroutineScope {
            jobCount.andIncrement
            val list = mangaToUpdateMap.keys.map { source ->
                async {
                    try {
                        requestSemaphore.withPermit {
                            updateMangaInSource(source)
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

            if (preferences.refreshCoversToo().get() && job?.isCancelled == false) {
                updateDetails(newUpdates.keys.toList())
                notifier.cancelProgressNotification()
                if (downloadNew && hasDownloads) {
                    DownloadService.start(this)
                }
            } else if (downloadNew && hasDownloads) {
                DownloadService.start(this.applicationContext)
            }
            newUpdates.clear()
        }
        if (skippedUpdates.isNotEmpty() && Notifications.isNotificationChannelEnabled(this, Notifications.CHANNEL_LIBRARY_SKIPPED)) {
            val skippedFile = writeErrorFile(
                skippedUpdates,
                "skipped",
                getString(R.string.learn_why) + " - " + LibraryUpdateNotifier.HELP_SKIPPED_URL,
            ).getUriCompat(this)
            notifier.showUpdateSkippedNotification(skippedUpdates.map { it.key.title }, skippedFile)
        }
        if (failedUpdates.isNotEmpty() && Notifications.isNotificationChannelEnabled(this, Notifications.CHANNEL_LIBRARY_ERROR)) {
            val errorFile = writeErrorFile(failedUpdates).getUriCompat(this)
            notifier.showUpdateErrorNotification(failedUpdates.map { it.key.title }, errorFile)
        }
        mangaShortcutManager.updateShortcuts()
        failedUpdates.clear()
        notifier.cancelProgressNotification()
        if (runExtensionUpdatesAfter && !DownloadService.isRunning(this)) {
            ExtensionUpdateJob.runJobAgain(this, NetworkType.CONNECTED)
            runExtensionUpdatesAfter = false
        }
    }

    private suspend fun updateMangaInSource(source: Long): Boolean {
        if (mangaToUpdateMap[source] == null) return false
        var count = 0
        var hasDownloads = false
        while (count < mangaToUpdateMap[source]!!.size) {
            val manga = mangaToUpdateMap[source]!![count]
            val shouldDownload = manga.shouldDownloadNewChapters(db, preferences)
            if (updateMangaChapters(manga, this.count.andIncrement, shouldDownload)) {
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
        shouldDownload: Boolean,
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
                source.getChapterList(manga.toMangaInfo()).map { it.toSChapter() }
            }
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
                        downloadManager.isChapterDownloaded(it, manga) &&
                            newChapters.first.none { newChapter ->
                                newChapter.chapter_number == it.chapter_number && it.scanlator.isNullOrBlank()
                            }
                    }
                    if (removedChapters.isNotEmpty()) {
                        downloadManager.deleteChapters(removedChapters, manga, source)
                    }
                }
                if (newChapters.first.size + newChapters.second.size > 0) listener?.onUpdateManga(
                    manga,
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
                            mangaToUpdate.size,
                        )

                        val networkManga = try {
                            source.getMangaDetails(manga.toMangaInfo()).toSManga()
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
                                    ImageRequest.Builder(this@LibraryUpdateService).data(manga)
                                        .memoryCachePolicy(CachePolicy.DISABLED).build()
                                Coil.imageLoader(this@LibraryUpdateService).execute(request)
                            } else {
                                val request =
                                    ImageRequest.Builder(this@LibraryUpdateService).data(manga)
                                        .memoryCachePolicy(CachePolicy.DISABLED)
                                        .diskCachePolicy(CachePolicy.WRITE_ONLY)
                                        .build()
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

                        if (service is EnhancedTrackService) {
                            syncChaptersWithTrackServiceTwoWay(db, db.getChapters(manga).executeAsBlocking(), track, service)
                        }
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
    private fun writeErrorFile(errors: Map<Manga, String?>, fileName: String = "errors", additionalInfo: String? = null): File {
        try {
            if (errors.isNotEmpty()) {
                val file = createFileInCacheDir("tachiyomi_update_$fileName.txt")
                file.bufferedWriter().use { out ->
                    additionalInfo?.let { out.write("$it\n\n") }
                    // Error file format:
                    // ! Error
                    //   # Source
                    //     - Manga
                    errors.toList().groupBy({ it.second }, { it.first }).forEach { (error, mangas) ->
                        out.write("! ${error}\n")
                        mangas.groupBy { it.source }.forEach { (srcId, mangas) ->
                            val source = sourceManager.getOrStub(srcId)
                            out.write("  # $source\n")
                            mangas.forEach {
                                out.write("    - ${it.title}\n")
                            }
                        }
                    }
                }
                return file
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
        const val STARTING_UPDATE_SOURCE = -5L

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

        var runExtensionUpdatesAfter = false

        /**
         * Returns the status of the service.
         *
         * @return true if the service is running, false otherwise.
         */
        fun isRunning() = instance != null

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
            mangaToUse: List<LibraryManga>? = null,
        ): Boolean {
            return if (!isRunning()) {
                val intent = Intent(context, LibraryUpdateService::class.java).apply {
                    putExtra(KEY_TARGET, target)
                    category?.id?.let { id ->
                        putExtra(KEY_CATEGORY, id)
                        if (mangaToUse != null) putExtra(
                            KEY_MANGAS,
                            mangaToUse.mapNotNull { it.id }.toLongArray(),
                        )
                    }
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    context.startService(intent)
                } else {
                    context.startForegroundService(intent)
                }
                true
            } else {
                if (target == Target.CHAPTERS) category?.id?.let {
                    if (mangaToUse != null) instance?.addMangaToQueue(it, mangaToUse)
                    else instance?.addCategory(it)
                }
                false
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

        fun callListener(manga: Manga) {
            listener?.onUpdateManga(manga)
        }
    }
}

interface LibraryServiceListener {
    fun onUpdateManga(manga: Manga? = null)
}

const val MANGA_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 60
