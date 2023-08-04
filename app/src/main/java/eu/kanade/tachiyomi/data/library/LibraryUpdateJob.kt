package eu.kanade.tachiyomi.data.library

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
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
import eu.kanade.tachiyomi.data.download.DownloadJob
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.DEVICE_BATTERY_NOT_LOW
import eu.kanade.tachiyomi.data.preference.DEVICE_CHARGING
import eu.kanade.tachiyomi.data.preference.DEVICE_ONLY_ON_WIFI
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
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithTrackServiceTwoWay
import eu.kanade.tachiyomi.util.manga.MangaShortcutManager
import eu.kanade.tachiyomi.util.shouldDownloadNewChapters
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import eu.kanade.tachiyomi.util.system.isConnectedToWifi
import eu.kanade.tachiyomi.util.system.localeContext
import eu.kanade.tachiyomi.util.system.tryToSetForeground
import eu.kanade.tachiyomi.util.system.withIOContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.lang.ref.WeakReference
import java.util.Date
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class LibraryUpdateJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val db: DatabaseHelper = Injekt.get()
    private val coverCache: CoverCache = Injekt.get()
    private val sourceManager: SourceManager = Injekt.get()
    private val preferences: PreferencesHelper = Injekt.get()
    private val downloadManager: DownloadManager = Injekt.get()
    private val trackManager: TrackManager = Injekt.get()
    private val mangaShortcutManager: MangaShortcutManager = Injekt.get()

    private var extraDeferredJobs = mutableListOf<Deferred<Any>>()

    private val extraScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val emitScope = MainScope()

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

    // Boolean to determine if user wants to automatically download new chapters.
    private val downloadNew: Boolean = preferences.downloadNewChapters().get()

    // Boolean to determine if DownloadManager has downloads
    private var hasDownloads = false

    private val requestSemaphore = Semaphore(5)

    // For updates delete removed chapters if not preference is set as well
    private val deleteRemoved by lazy { preferences.deleteRemovedChapters().get() != 1 }

    private val notifier = LibraryUpdateNotifier(context.localeContext)

    override suspend fun doWork(): Result {
        if (tags.contains(WORK_NAME_AUTO)) {
            val preferences = Injekt.get<PreferencesHelper>()
            val restrictions = preferences.libraryUpdateDeviceRestriction().get()
            if ((DEVICE_ONLY_ON_WIFI in restrictions) && !context.isConnectedToWifi()) {
                return Result.failure()
            }

            // Find a running manual worker. If exists, try again later
            if (instance != null) {
                return Result.retry()
            }
        }

        tryToSetForeground()

        instance = WeakReference(this)

        val target = inputData.getString(KEY_TARGET)?.let { Target.valueOf(it) } ?: Target.CHAPTERS

        // If this is a chapter update, set the last update time to now
        if (target == Target.CHAPTERS) {
            preferences.libraryUpdateLastTimestamp().set(Date().time)
        }

        val savedMangasList = inputData.getLongArray(KEY_MANGAS)?.asList()

        val mangaList = (
            if (savedMangasList != null) {
                val mangas = db.getLibraryMangas().executeAsBlocking().filter {
                    it.id in savedMangasList
                }.distinctBy { it.id }
                val categoryId = inputData.getInt(KEY_CATEGORY, -1)
                if (categoryId > -1) categoryIds.add(categoryId)
                mangas
            } else {
                getMangaToUpdate()
            }
            ).sortedBy { it.title }

        return withIOContext {
            try {
                launchTarget(target, mangaList)
                Result.success()
            } catch (e: Exception) {
                if (e is CancellationException) {
                    // Assume success although cancelled
                    finishUpdates(true)
                    Result.success()
                } else {
                    Timber.e(e)
                    Result.failure()
                }
            } finally {
                instance = null
                sendUpdate(null)
                notifier.cancelProgressNotification()
            }
        }
    }

    private suspend fun launchTarget(target: Target, mangaToAdd: List<LibraryManga>) {
        if (target == Target.CHAPTERS) {
            sendUpdate(STARTING_UPDATE_SOURCE)
        }
        when (target) {
            Target.CHAPTERS -> updateChaptersJob(filterMangaToUpdate(mangaToAdd))
            Target.DETAILS -> updateDetails(mangaToAdd)
            else -> updateTrackings(mangaToAdd)
        }
    }

    private suspend fun sendUpdate(mangaId: Long?) {
        if (isStopped) {
            updateMutableFlow.tryEmit(mangaId)
        } else {
            emitScope.launch { updateMutableFlow.emit(mangaId) }
        }
    }

    private suspend fun updateChaptersJob(mangaToAdd: List<LibraryManga>) {
        // Initialize the variables holding the progress of the updates.
        mangaToUpdate.addAll(mangaToAdd)
        mangaToUpdateMap.putAll(mangaToAdd.groupBy { it.source })
        checkIfMassiveUpdate()
        coroutineScope {
            val list = mangaToUpdateMap.keys.map { source ->
                async {
                    try {
                        requestSemaphore.withPermit { updateMangaInSource(source) }
                    } catch (e: Exception) {
                        Timber.e(e)
                        false
                    }
                }
            }
            val results = list.awaitAll()
            if (!hasDownloads) {
                hasDownloads = results.any { it }
            }
            finishUpdates()
        }
    }

    /**
     * Method that updates the details of the given list of manga. It's called in a background
     * thread, so it's safe to do heavy operations or network calls here.
     *
     * @param mangaToUpdate the list to update
     */
    private suspend fun updateDetails(mangaToUpdate: List<LibraryManga>) = coroutineScope {
        // Initialize the variables holding the progress of the updates.
        val count = AtomicInteger(0)
        val asyncList = mangaToUpdate.groupBy { it.source }.values.map { list ->
            async {
                requestSemaphore.withPermit {
                    list.forEach { manga ->
                        ensureActive()
                        val source = sourceManager.get(manga.source) as? HttpSource ?: return@async
                        notifier.showProgressNotification(
                            manga,
                            count.andIncrement,
                            mangaToUpdate.size,
                        )
                        ensureActive()
                        val networkManga = try {
                            source.getMangaDetails(manga.copy())
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
                                    ImageRequest.Builder(context).data(manga)
                                        .memoryCachePolicy(CachePolicy.DISABLED).build()
                                Coil.imageLoader(context).execute(request)
                            } else {
                                val request =
                                    ImageRequest.Builder(context).data(manga)
                                        .memoryCachePolicy(CachePolicy.DISABLED)
                                        .diskCachePolicy(CachePolicy.WRITE_ONLY)
                                        .build()
                                Coil.imageLoader(context).execute(request)
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

    private suspend fun finishUpdates(wasStopped: Boolean = false) {
        if (!wasStopped && !isStopped) {
            extraDeferredJobs.awaitAll()
        }
        if (newUpdates.isNotEmpty()) {
            notifier.showResultNotification(newUpdates)
            if (!wasStopped && preferences.refreshCoversToo().get() && !isStopped) {
                updateDetails(newUpdates.keys.toList())
                notifier.cancelProgressNotification()
                if (downloadNew && hasDownloads) {
                    DownloadJob.start(context, runExtensionUpdatesAfter)
                    runExtensionUpdatesAfter = false
                }
            } else if (downloadNew && hasDownloads) {
                DownloadJob.start(applicationContext, runExtensionUpdatesAfter)
                runExtensionUpdatesAfter = false
            }
        }
        newUpdates.clear()
        if (skippedUpdates.isNotEmpty() && Notifications.isNotificationChannelEnabled(context, Notifications.CHANNEL_LIBRARY_SKIPPED)) {
            val skippedFile = writeErrorFile(
                skippedUpdates,
                "skipped",
                context.getString(R.string.learn_why) + " - " + LibraryUpdateNotifier.HELP_SKIPPED_URL,
            ).getUriCompat(context)
            notifier.showUpdateSkippedNotification(skippedUpdates.map { it.key.title }, skippedFile)
        }
        if (failedUpdates.isNotEmpty() && Notifications.isNotificationChannelEnabled(context, Notifications.CHANNEL_LIBRARY_ERROR)) {
            val errorFile = writeErrorFile(failedUpdates).getUriCompat(context)
            notifier.showUpdateErrorNotification(failedUpdates.map { it.key.title }, errorFile)
        }
        mangaShortcutManager.updateShortcuts(context)
        failedUpdates.clear()
        notifier.cancelProgressNotification()
        if (runExtensionUpdatesAfter && !DownloadJob.isRunning(context)) {
            ExtensionUpdateJob.runJobAgain(context, NetworkType.CONNECTED)
            runExtensionUpdatesAfter = false
        }
    }

    private fun checkIfMassiveUpdate() {
        val largestSourceSize = mangaToUpdate
            .groupBy { it.source }
            .filterKeys { sourceManager.get(it) !is UnmeteredSource }
            .maxOfOrNull { it.value.size } ?: 0
        if (largestSourceSize > MANGA_PER_SOURCE_QUEUE_WARNING_THRESHOLD && !preferences.disableNotificationSizeWarning().get()) {
            notifier.showQueueSizeWarningNotification()
        }
    }

    private suspend fun updateMangaInSource(source: Long): Boolean {
        if (mangaToUpdateMap[source] == null) return false
        var count = 0
        var hasDownloads = false
        val httpSource = sourceManager.get(source) as? HttpSource ?: return false
        while (count < mangaToUpdateMap[source]!!.size) {
            val manga = mangaToUpdateMap[source]!![count]
            val shouldDownload = manga.shouldDownloadNewChapters(db, preferences)
            if (updateMangaChapters(manga, this.count.andIncrement, httpSource, shouldDownload)) {
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
        source: HttpSource,
        shouldDownload: Boolean,
    ): Boolean = coroutineScope {
        try {
            var hasDownloads = false
            ensureActive()
            notifier.showProgressNotification(manga, progress, mangaToUpdate.size)
            val fetchedChapters = source.getChapterList(manga)

            if (fetchedChapters.isNotEmpty()) {
                val newChapters = syncChaptersWithSource(db, fetchedChapters, manga, source)
                if (newChapters.first.isNotEmpty()) {
                    if (shouldDownload) {
                        downloadChapters(
                            manga,
                            newChapters.first.sortedBy { it.chapter_number },
                        )
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
                if (newChapters.first.size + newChapters.second.size > 0) {
                    sendUpdate(manga.id)
                }
            }
            return@coroutineScope hasDownloads
        } catch (e: Exception) {
            if (e !is CancellationException) {
                failedUpdates[manga] = e.message
                Timber.e("Failed updating: ${manga.title}: $e")
            }
            return@coroutineScope false
        }
    }

    private fun downloadChapters(manga: Manga, chapters: List<Chapter>) {
        // We don't want to start downloading while the library is updating, because websites
        // may don't like it and they could ban the user.
        downloadManager.downloadChapters(manga, chapters, false)
    }

    private fun filterMangaToUpdate(mangaToAdd: List<LibraryManga>): List<LibraryManga> {
        val restrictions = preferences.libraryUpdateMangaRestriction().get()
        return mangaToAdd.filter { manga ->
            when {
                MANGA_NON_COMPLETED in restrictions && manga.status == SManga.COMPLETED -> {
                    skippedUpdates[manga] = context.getString(R.string.skipped_reason_completed)
                }
                MANGA_HAS_UNREAD in restrictions && manga.unread != 0 -> {
                    skippedUpdates[manga] = context.getString(R.string.skipped_reason_not_caught_up)
                }
                MANGA_NON_READ in restrictions && manga.totalChapters > 0 && !manga.hasRead -> {
                    skippedUpdates[manga] = context.getString(R.string.skipped_reason_not_started)
                }
                manga.update_strategy != UpdateStrategy.ALWAYS_UPDATE -> {
                    skippedUpdates[manga] = context.getString(R.string.skipped_reason_not_always_update)
                }
                else -> {
                    return@filter true
                }
            }
            return@filter false
        }
    }

    private fun getMangaToUpdate(): List<LibraryManga> {
        val categoryId = inputData.getInt(KEY_CATEGORY, -1)
        return getMangaToUpdate(categoryId)
    }

    /**
     * Returns the list of manga to be updated.
     *
     * @param categoryId the category to update
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
        val listToExclude = if (categoriesToExclude.isNotEmpty() && categoryId == -1) {
            libraryManga.filter { it.category in categoriesToExclude }.toSet()
        } else {
            emptySet()
        }

        return listToUpdate.minus(listToExclude)
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = notifier.progressNotificationBuilder.build()
        val id = Notifications.ID_LIBRARY_PROGRESS
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }

    /**
     * Writes basic file of update errors to cache dir.
     */
    private fun writeErrorFile(errors: Map<Manga, String?>, fileName: String = "errors", additionalInfo: String? = null): File {
        try {
            if (errors.isNotEmpty()) {
                val file = context.createFileInCacheDir("tachiyomi_update_$fileName.txt")
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

    private fun addManga(mangaToAdd: List<LibraryManga>) {
        val distinctManga = mangaToAdd.filter { it !in mangaToUpdate }
        mangaToUpdate.addAll(distinctManga)
        checkIfMassiveUpdate()
        distinctManga.groupBy { it.source }.forEach {
            // if added queue items is a new source not in the async list or an async list has
            // finished running
            if (mangaToUpdateMap[it.key].isNullOrEmpty()) {
                mangaToUpdateMap[it.key] = it.value
                extraScope.launch {
                    extraDeferredJobs.add(
                        async(Dispatchers.IO) {
                            val hasDLs = try {
                                requestSemaphore.withPermit { updateMangaInSource(it.key) }
                            } catch (e: Exception) {
                                false
                            }
                            if (!hasDownloads) {
                                hasDownloads = hasDLs
                            }
                        },
                    )
                }
            } else {
                val list = mangaToUpdateMap[it.key] ?: emptyList()
                mangaToUpdateMap[it.key] = (list + it.value)
            }
        }
    }

    enum class Target {

        CHAPTERS, // Manga chapters

        DETAILS, // Manga metadata

        TRACKING, // Tracking metadata
    }

    companion object {
        private const val TAG = "LibraryUpdate"
        private const val WORK_NAME_AUTO = "LibraryUpdate-auto"
        private const val WORK_NAME_MANUAL = "LibraryUpdate-manual"

        private const val ERROR_LOG_HELP_URL = "https://tachiyomi.org/help/guides/troubleshooting"

        private const val MANGA_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 60

        /**
         * Key for category to update.
         */
        private const val KEY_CATEGORY = "category"
        const val STARTING_UPDATE_SOURCE = -5L

        /**
         * Key that defines what should be updated.
         */
        private const val KEY_TARGET = "target"

        private const val KEY_MANGAS = "mangas"

        private var instance: WeakReference<LibraryUpdateJob>? = null

        val updateMutableFlow = MutableSharedFlow<Long?>(
            extraBufferCapacity = 10,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        val updateFlow = updateMutableFlow.asSharedFlow()

        private var runExtensionUpdatesAfter = false

        fun runExtensionUpdatesAfterJob() { runExtensionUpdatesAfter = true }

        fun setupTask(context: Context, prefInterval: Int? = null) {
            val preferences = Injekt.get<PreferencesHelper>()
            val interval = prefInterval ?: preferences.libraryUpdateInterval().get()
            if (interval > 0) {
                val restrictions = preferences.libraryUpdateDeviceRestriction().get()

                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresCharging(DEVICE_CHARGING in restrictions)
                    .setRequiresBatteryNotLow(DEVICE_BATTERY_NOT_LOW in restrictions)
                    .build()

                val request = PeriodicWorkRequestBuilder<LibraryUpdateJob>(
                    interval.toLong(),
                    TimeUnit.HOURS,
                    10,
                    TimeUnit.MINUTES,
                )
                    .addTag(TAG)
                    .addTag(WORK_NAME_AUTO)
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME_AUTO,
                    ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                    request,
                )
            } else {
                WorkManager.getInstance(context).cancelAllWorkByTag(WORK_NAME_AUTO)
            }
        }

        fun cancelAllWorks(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        }

        fun isRunning(context: Context): Boolean {
            val list = WorkManager.getInstance(context).getWorkInfosByTag(TAG).get()
            return list.any { it.state == WorkInfo.State.RUNNING }
        }

        fun categoryInQueue(id: Int?) = instance?.get()?.categoryIds?.contains(id) ?: false

        fun startNow(
            context: Context,
            category: Category? = null,
            target: Target = Target.CHAPTERS,
            mangaToUse: List<LibraryManga>? = null,
        ): Boolean {
            if (isRunning(context)) {
                if (target == Target.CHAPTERS) {
                    category?.id?.let {
                        if (mangaToUse != null) {
                            instance?.get()?.addMangaToQueue(it, mangaToUse)
                        } else {
                            instance?.get()?.addCategory(it)
                        }
                    }
                }
                // Already running either as a scheduled or manual job
                return false
            }

            val builder = Data.Builder()
            builder.putString(KEY_TARGET, target.name)
            category?.id?.let { id ->
                builder.putInt(KEY_CATEGORY, id)
                if (mangaToUse != null) {
                    builder.putLongArray(
                        KEY_MANGAS,
                        mangaToUse.mapNotNull { it.id }.toLongArray(),
                    )
                }
            }
            val inputData = builder.build()
            val request = OneTimeWorkRequestBuilder<LibraryUpdateJob>()
                .addTag(TAG)
                .addTag(WORK_NAME_MANUAL)
                .setInputData(inputData)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME_MANUAL, ExistingWorkPolicy.KEEP, request)

            return true
        }

        fun stop(context: Context) {
            val wm = WorkManager.getInstance(context)
            val workQuery = WorkQuery.Builder.fromTags(listOf(TAG))
                .addStates(listOf(WorkInfo.State.RUNNING))
                .build()
            wm.getWorkInfos(workQuery).get()
                // Should only return one work but just in case
                .forEach {
                    wm.cancelWorkById(it.id)

                    // Re-enqueue cancelled scheduled work
                    if (it.tags.contains(WORK_NAME_AUTO)) {
                        setupTask(context)
                    }
                }
        }
    }
}
