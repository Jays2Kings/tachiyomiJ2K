package eu.kanade.tachiyomi.data.backup

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.github.salomonbrys.kotson.fromJson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.backup.models.Backup.CATEGORIES
import eu.kanade.tachiyomi.data.backup.models.Backup.CHAPTERS
import eu.kanade.tachiyomi.data.backup.models.Backup.EXTENSIONS
import eu.kanade.tachiyomi.data.backup.models.Backup.HISTORY
import eu.kanade.tachiyomi.data.backup.models.Backup.MANGA
import eu.kanade.tachiyomi.data.backup.models.Backup.MANGAS
import eu.kanade.tachiyomi.data.backup.models.Backup.TRACK
import eu.kanade.tachiyomi.data.backup.models.Backup.VERSION
import eu.kanade.tachiyomi.data.backup.models.DHistory
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.database.models.TrackImpl
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.SourceNotFoundException
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.isServiceRunning
import eu.kanade.tachiyomi.util.system.notificationManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Restores backup from json file
 */
class BackupRestoreService : Service() {

    /**
     * Wake lock that will be held until the service is destroyed.
     */
    private lateinit var wakeLock: PowerManager.WakeLock

    /**
     * Subscription where the update is done.
     */
    private var job: Job? = null

    /**
     * The progress of a backup restore
     */
    private var restoreProgress = 0

    private var totalAmount = 0

    /**
     * List containing errors
     */
    private val errors = mutableListOf<String>()

    /**
     * count of cancelled
     */
    private var cancelled = 0

    /**
     * List containing distinct errors
     */
    private val trackingErrors = mutableListOf<String>()

    /**
     * List containing missing sources
     */
    private val sourcesMissing = mutableListOf<String>()

    var extensionsMap: Map<String, String> = emptyMap()

    /**
     * List containing missing sources
     */
    private var lincensedManga = 0

    /**
     * Backup manager
     */
    private lateinit var backupManager: BackupManager

    /**
     * Database
     */
    private val db: DatabaseHelper by injectLazy()

    /**
     * Tracking manager
     */
    internal val trackManager: TrackManager by injectLazy()

    /**
     * Method called when the service is created. It injects dependencies and acquire the wake lock.
     */
    override fun onCreate() {
        super.onCreate()
        startForeground(Notifications.ID_RESTORE_PROGRESS, progressNotification.build())
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "BackupRestoreService:WakeLock"
        )
        wakeLock.acquire(TimeUnit.HOURS.toMillis(3))
    }

    /**
     * Method called when the service is destroyed. It destroys the running subscription and
     * releases the wake lock.
     */
    override fun onDestroy() {
        job?.cancel()
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        super.onDestroy()
    }

    /**
     * This method needs to be implemented, but it's not used/needed.
     */
    override fun onBind(intent: Intent): IBinder? = null

    /**
     * Method called when the service receives an intent.
     *
     * @param intent the start intent from.
     * @param flags the flags of the command.
     * @param startId the start id of this command.
     * @return the start value of the command.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uri = intent?.getParcelableExtra<Uri>(BackupConst.EXTRA_URI) ?: return START_NOT_STICKY

        // Unsubscribe from any previous subscription if needed.
        job?.cancel()
        val handler = CoroutineExceptionHandler { _, exception ->
            Timber.e(exception)
            showErrorNotification(exception.message!!)
            stopSelf(startId)
        }
        job = GlobalScope.launch(handler) {
            restoreBackup(uri)
        }
        job?.invokeOnCompletion { stopSelf(startId) }

        return START_NOT_STICKY
    }

    override fun stopService(name: Intent?): Boolean {
        job?.cancel()
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        return super.stopService(name)
    }

    /**
     * Restore a backup json file
     */
    private suspend fun restoreBackup(uri: Uri) {
        val reader = JsonReader(contentResolver.openInputStream(uri)!!.bufferedReader())
        val json = JsonParser.parseReader(reader).asJsonObject

        // Get parser version
        val version = json.get(VERSION)?.asInt ?: 1

        // Initialize manager
        backupManager = BackupManager(this, version)

        val mangasJson = json.get(MANGAS).asJsonArray

        // +1 for categories
        totalAmount = mangasJson.size() + 1
        trackingErrors.clear()
        sourcesMissing.clear()
        lincensedManga = 0
        errors.clear()
        cancelled = 0
        // Restore categories
        restoreCategories(json, backupManager)
        extensionsMap = getExtensionsList(json, backupManager)

        mangasJson.forEach {
            restoreManga(it.asJsonObject, backupManager)
        }

        notificationManager.cancel(Notifications.ID_RESTORE_PROGRESS)

        cancelled = errors.count { it.contains("cancelled", true) }
        val tmpErrors = errors.filter { !it.contains("cancelled", true) }
        errors.clear()
        errors.addAll(tmpErrors)

        val logFile = writeErrorLog()
        showResultNotification(logFile.parent, logFile.name)
    }

    /**
     * Restore extension names if they were backed up
     */
    private fun getExtensionsList(json: JsonObject, backupManager: BackupManager): Map<String,
        String> {
        json.get(EXTENSIONS)?.let { element ->
            return backupManager.getExtensionsMap(element.asJsonArray)
        }
        return emptyMap()
    }

    /**
     * Restore categories if they were backed up
     */
    private fun restoreCategories(json: JsonObject, backupManager: BackupManager) {
        val element = json.get(CATEGORIES)
        if (element != null) {
            backupManager.restoreCategories(element.asJsonArray)
            restoreProgress += 1
            showProgressNotification(restoreProgress, totalAmount, "Categories added")
        } else {
            totalAmount -= 1
        }
    }

    /**
     * Restore manga from json  this should be refactored more at some point to prevent the manga object from being mutable
     */
    private suspend fun restoreManga(obj: JsonObject, backupManager: BackupManager) {
        val manga = backupManager.parser.fromJson<MangaImpl>(obj.get(MANGA))
        val chapters = backupManager.parser.fromJson<List<ChapterImpl>>(obj.get(CHAPTERS) ?: JsonArray())
        val categories = backupManager.parser.fromJson<List<String>>(obj.get(CATEGORIES) ?: JsonArray())
        val history = backupManager.parser.fromJson<List<DHistory>>(obj.get(HISTORY) ?: JsonArray())
        val tracks = backupManager.parser.fromJson<List<TrackImpl>>(obj.get(TRACK) ?: JsonArray())
        val source = backupManager.sourceManager.getOrStub(manga.source)

        try {
            if (job?.isCancelled == false) {
                showProgressNotification(restoreProgress, totalAmount, manga.title)
                restoreProgress += 1
            } else {
                throw java.lang.Exception("Job was cancelled")
            }
            val dbManga = backupManager.getMangaFromDatabase(manga)
            val dbMangaExists = dbManga != null

            if (dbMangaExists) {
                // Manga in database copy information from manga already in database
                backupManager.restoreMangaNoFetch(manga, dbManga!!)
            } else {
                // manga gets details from network
                backupManager.restoreMangaFetch(source, manga)
            }

            // Restore categories
            backupManager.restoreCategoriesForManga(manga, categories)

            if (!dbMangaExists || !backupManager.restoreChaptersForManga(manga, chapters)) {
                // manga gets chapters added
                backupManager.restoreChapterFetch(source, manga, chapters)
            }
            // Restore history
            backupManager.restoreHistoryForManga(history)
            // Restore tracking
            backupManager.restoreTrackForManga(manga, tracks)

            trackingFetch(manga, tracks)
        } catch (e: Exception) {
            Timber.e(e)

            if (e is RuntimeException) {
                val cause = e.cause
                if (cause is SourceNotFoundException) {
                    val sourceName = extensionsMap[cause.id.toString()] ?: cause.id.toString()
                    sourcesMissing.add(
                        extensionsMap[cause.id.toString()] ?: cause.id.toString()
                    )
                    val errorMessage = getString(R.string.source_not_installed_, sourceName)
                    errors.add("${manga.title} - $errorMessage")
                } else {
                    if (e.message?.contains("licensed", true) == true) {
                        lincensedManga++
                    }
                    errors.add("${manga.title} - ${cause?.message ?: e.message}")
                }
                return
            }
            errors.add("${manga.title} - ${e.message}")
        }
    }

    /**
     * [refreshes tracking information
     * @param manga manga that needs updating.
     * @param tracks list containing tracks from restore file.
     */
    private suspend fun trackingFetch(manga: Manga, tracks: List<Track>) {
        tracks.forEach { track ->
            val service = trackManager.getService(track.sync_id)
            if (service != null && service.isLogged) {
                try {
                    service.refresh(track)
                    db.insertTrack(track).executeAsBlocking()
                } catch (e: Exception) {
                    errors.add("${manga.title} - ${e.message}")
                }
            } else {
                errors.add("${manga.title} - ${getString(R.string.not_logged_into_, service?.name)}")
                val notLoggedIn = getString(R.string.not_logged_into_, service?.name)
                trackingErrors.add(notLoggedIn)
            }
        }
    }

    /**
     * Write errors to error log
     */
    private fun writeErrorLog(): File {
        try {
            if (errors.isNotEmpty()) {
                val destFile = File(externalCacheDir, "tachiyomi_restore.log")
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

                destFile.bufferedWriter().use { out ->
                    errors.forEach { message ->
                        out.write("$message\n")
                    }
                }
                return destFile
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
        return File("")
    }

    /**
     * keep a partially constructed progress notification for resuse
     */
    private val progressNotification by lazy {
        NotificationCompat.Builder(this, Notifications.CHANNEL_BACKUP_RESTORE)
            .setContentTitle(getString(R.string.app_name))
            .setSmallIcon(R.drawable.ic_tachi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setColor(ContextCompat.getColor(this, R.color.colorAccent))
            .addAction(R.drawable.ic_close_24dp, getString(android.R.string.cancel), cancelIntent)
    }

    /**
     * Pending intent of action that cancels the library update
     */
    private val cancelIntent by lazy {
        NotificationReceiver.cancelRestorePendingBroadcast(this)
    }

    /**
     * Shows the notification containing the currently updating manga and the progress.
     *
     * @param manga the manga that's being updated.
     * @param current the current progress.
     * @param total the total progress.
     */
    private fun showProgressNotification(current: Int, total: Int, title: String) {
        notificationManager.notify(
            Notifications.ID_RESTORE_PROGRESS,
            progressNotification
                .setContentTitle(title.chop(30))
                .setContentText(
                    getString(
                        R.string.restoring_progress,
                        restoreProgress,
                        totalAmount
                    )
                )
                .setProgress(total, current, false)
                .build()
        )
    }

    /**
     * Show the result notification with option to show the error log
     */
    private fun showResultNotification(path: String?, file: String?) {

        val content = mutableListOf(
            getString(
                R.string.restore_completed_content,
                restoreProgress
                    .toString(),
                errors.size.toString()
            )
        )
        val sourceMissingCount = sourcesMissing.distinct().size
        if (sourceMissingCount > 0) {
            val sources = sourcesMissing.distinct().filter { it.toLongOrNull() == null }
            val missingSourcesString = if (sources.size > 5) {
                sources.take(5).joinToString(", ") + "..."
            } else {
                sources.joinToString(", ")
            }
            if (sources.isEmpty()) {
                content.add(
                    resources.getQuantityString(
                        R.plurals.sources_missing,
                        sourceMissingCount,
                        sourceMissingCount
                    )
                )
            } else {
                content.add(
                    resources.getQuantityString(
                        R.plurals.sources_missing,
                        sourceMissingCount,
                        sourceMissingCount
                    ) + ": " + missingSourcesString
                )
            }
        }
        if (lincensedManga > 0)
            content.add(
                resources.getQuantityString(
                    R.plurals.licensed_manga,
                    lincensedManga,
                    lincensedManga
                )
            )
        val trackingErrors = trackingErrors.distinct()
        if (trackingErrors.isNotEmpty()) {
            val trackingErrorsString = trackingErrors.distinct().joinToString("\n")
            content.add(trackingErrorsString)
        }
        if (cancelled > 0)
            content.add(getString(R.string.restore_content_skipped, cancelled))

        val restoreString = content.joinToString("\n")

        val resultNotification = NotificationCompat.Builder(this, Notifications.CHANNEL_BACKUP_RESTORE)
            .setContentTitle(getString(R.string.restore_completed))
            .setContentText(restoreString)
            .setStyle(NotificationCompat.BigTextStyle().bigText(restoreString))
            .setSmallIcon(R.drawable.ic_tachi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(ContextCompat.getColor(this, R.color.colorAccent))
        if (errors.size > 0 && !path.isNullOrEmpty() && !file.isNullOrEmpty()) {
            resultNotification.addAction(
                R.drawable.ic_close_24dp,
                getString(
                    R.string
                        .view_all_errors
                ),
                getErrorLogIntent(path, file)
            )
        }
        notificationManager.notify(Notifications.ID_RESTORE_COMPLETE, resultNotification.build())
    }

    /**Show an error notification if something happens that prevents the restore from starting/working
     *
     */
    private fun showErrorNotification(errorMessage: String) {
        val resultNotification = NotificationCompat.Builder(this, Notifications.CHANNEL_BACKUP_RESTORE)
            .setContentTitle(getString(R.string.restore_error))
            .setContentText(errorMessage)
            .setSmallIcon(R.drawable.ic_error_24dp)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(ContextCompat.getColor(this, R.color.md_red_500))
        notificationManager.notify(Notifications.ID_RESTORE_ERROR, resultNotification.build())
    }

    /**Get the PendingIntent for the error log
     *
     */
    private fun getErrorLogIntent(path: String, file: String): PendingIntent {
        val destFile = File(path, file!!)
        val uri = destFile.getUriCompat(applicationContext)
        return NotificationReceiver.openFileExplorerPendingActivity(this@BackupRestoreService, uri)
    }

    companion object {

        /**
         * Returns the status of the service.
         *
         * @param context the application context.
         * @return true if the service is running, false otherwise.
         */
        fun isRunning(context: Context): Boolean =
            context.isServiceRunning(BackupRestoreService::class.java)

        /**
         * Starts a service to restore a backup from Json
         *
         * @param context context of application
         * @param uri path of Uri
         */
        fun start(context: Context, uri: Uri) {
            if (!isRunning(context)) {
                val intent = Intent(context, BackupRestoreService::class.java).apply {
                    putExtra(BackupConst.EXTRA_URI, uri)
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    context.startService(intent)
                } else {
                    context.startForegroundService(intent)
                }
            }
        }

        /**
         * Stops the service.
         *
         * @param context the application context.
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, BackupRestoreService::class.java))
        }
    }
}
