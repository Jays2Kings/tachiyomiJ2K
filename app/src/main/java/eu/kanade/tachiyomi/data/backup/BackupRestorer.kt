package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.FloatPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.LongPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.library.CustomMangaManager
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.preference.AndroidPreferenceStore
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.data.preference.PreferenceStore
import eu.kanade.tachiyomi.extension.ExtensionUpdateJob
import eu.kanade.tachiyomi.extension.util.ExtensionInstaller
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.sourcePreferences
import eu.kanade.tachiyomi.ui.library.LibrarySort
import eu.kanade.tachiyomi.util.BackupUtil
import eu.kanade.tachiyomi.util.chapter.ChapterUtil
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.serialization.protobuf.ProtoBuf
import okio.source
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class BackupRestorer(
    val context: Context,
    val notifier: BackupNotifier,
) {
    private val db: DatabaseHelper by injectLazy()
    private val customMangaManager: CustomMangaManager by injectLazy()
    private val preferenceStore: PreferenceStore = Injekt.get()
    private val parser = ProtoBuf

    private var restoreAmount = 0
    private var restoreProgress = 0

    /**
     * Mapping of source ID to source name from backup data
     */
    private var sourceMapping: Map<Long, String> = emptyMap()

    private val errors = mutableListOf<Pair<Date, String>>()

    suspend fun restoreBackup(uri: Uri): Boolean {
        val startTime = System.currentTimeMillis()
        restoreProgress = 0
        errors.clear()

        if (!performRestore(uri)) {
            return false
        }

        val endTime = System.currentTimeMillis()
        val time = endTime - startTime

        val logFile = writeErrorLog()

        notifier.showRestoreComplete(time, errors.size, logFile.parent, logFile.name)
        return true
    }

    private suspend fun performRestore(uri: Uri): Boolean {
        val backup = BackupUtil.decodeBackup(context, uri)

        restoreAmount = backup.backupManga.size + 3 // +3 for categories, app prefs, source prefs

        // Restore categories
        if (backup.backupCategories.isNotEmpty()) {
            restoreCategories(backup.backupCategories)
        }

        // Store source mapping for error messages
        val backupMaps = backup.backupBrokenSources.map { BackupSource(it.name, it.sourceId) } + backup.backupSources
        sourceMapping = backupMaps.associate { it.sourceId to it.name }

        return coroutineScope {
            restoreAppPreferences(backup.backupPreferences)
            restoreSourcePreferences(backup.backupSourcePreferences)

            // Restore individual manga, batched into transactions to avoid
            // committing to disk on every single database write
            backup.backupManga.chunked(100).forEach { mangaChunk ->
                if (!isActive) {
                    return@coroutineScope false
                }

                db.inTransaction {
                    mangaChunk.forEach { restoreManga(it, backup.backupCategories) }
                }
            }
            true
        }
        // TODO: optionally trigger online library + tracker update
    }

    private fun restoreCategories(backupCategories: List<BackupCategory>) {
        db.inTransaction {
            // Get categories from file and from db
            val dbCategories = db.getCategories().executeAsBlocking()

            // Iterate over them
            backupCategories.map { it.getCategoryImpl() }.forEach { category ->
                // Used to know if the category is already in the db
                var found = false
                for (dbCategory in dbCategories) {
                    // If the category is already in the db, assign the id to the file's category
                    // and do nothing
                    if (category.name == dbCategory.name) {
                        category.id = dbCategory.id
                        found = true
                        break
                    }
                }
                // If the category isn't in the db, remove the id and insert a new category
                // Store the inserted id in the category
                if (!found) {
                    // Let the db assign the id
                    category.id = null
                    val result = db.insertCategory(category).executeAsBlocking()
                    category.id = result.insertedId()?.toInt()
                }
            }
        }

        restoreProgress += 1
        showRestoreProgress(restoreProgress, restoreAmount, context.getString(R.string.categories))
    }

    private fun restoreManga(
        backupManga: BackupManga,
        backupCategories: List<BackupCategory>,
    ) {
        val manga = backupManga.getMangaImpl()
        val chapters = backupManga.getChaptersImpl()
        val categories = backupManga.categories
        val history =
            backupManga.brokenHistory.map { BackupHistory(it.url, it.lastRead, it.readDuration) } + backupManga.history
        val tracks = backupManga.getTrackingImpl()
        val customManga = backupManga.getCustomMangaInfo()
        val filteredScanlators = backupManga.excludedScanlators

        try {
            val dbManga = db.getManga(manga.url, manga.source).executeAsBlocking()
            if (dbManga == null) {
                // Manga not in database
                restoreExistingManga(
                    manga,
                    chapters,
                    categories,
                    history,
                    tracks,
                    backupCategories,
                    filteredScanlators,
                    customManga,
                )
            } else {
                // Manga in database
                // Copy information from manga already in database
                manga.id = dbManga.id
                manga.filtered_scanlators = dbManga.filtered_scanlators
                manga.copyFrom(dbManga)
                db.insertManga(manga).executeAsBlocking()
                // Fetch rest of manga information
                restoreNewManga(
                    manga,
                    chapters,
                    categories,
                    history,
                    tracks,
                    backupCategories,
                    filteredScanlators,
                    customManga,
                )
            }
        } catch (e: Exception) {
            val sourceName = sourceMapping[manga.source] ?: manga.source.toString()
            errors.add(Date() to "${manga.title} [$sourceName]: ${e.message}")
        }

        restoreProgress += 1
        showRestoreProgress(restoreProgress, restoreAmount, manga.title)
        LibraryUpdateJob.updateMutableFlow.tryEmit(manga.id)
    }

    /**
     * Fetches manga information
     *
     * @param manga manga that needs updating
     * @param chapters chapters of manga that needs updating
     * @param categories categories that need updating
     */
    private fun restoreExistingManga(
        manga: Manga,
        chapters: List<Chapter>,
        categories: List<Int>,
        history: List<BackupHistory>,
        tracks: List<Track>,
        backupCategories: List<BackupCategory>,
        filteredScanlators: List<String>,
        customManga: CustomMangaManager.MangaJson?,
    ) {
        val fetchedManga =
            manga.also {
                it.initialized = it.description != null
                it.id = db.insertManga(it).executeAsBlocking().insertedId()
            }
        fetchedManga.id ?: return

        restoreChapters(fetchedManga, chapters)
        restoreExtras(fetchedManga, categories, history, tracks, backupCategories, filteredScanlators, customManga)
    }

    private fun restoreNewManga(
        backupManga: Manga,
        chapters: List<Chapter>,
        categories: List<Int>,
        history: List<BackupHistory>,
        tracks: List<Track>,
        backupCategories: List<BackupCategory>,
        filteredScanlators: List<String>,
        customManga: CustomMangaManager.MangaJson?,
    ) {
        restoreChapters(backupManga, chapters)
        restoreExtras(backupManga, categories, history, tracks, backupCategories, filteredScanlators, customManga)
    }

    private fun restoreChapters(
        manga: Manga,
        chapters: List<Chapter>,
    ) {
        val dbChapters = db.getChapters(manga).executeAsBlocking()

        chapters.forEach { chapter ->
            val dbChapter = dbChapters.find { it.url == chapter.url }
            if (dbChapter != null) {
                chapter.id = dbChapter.id
                chapter.copyFrom(dbChapter as SChapter)
                if (dbChapter.read && !chapter.read) {
                    chapter.read = dbChapter.read
                    chapter.last_page_read = dbChapter.last_page_read
                } else if (chapter.last_page_read == 0 && dbChapter.last_page_read != 0) {
                    chapter.last_page_read = dbChapter.last_page_read
                }
                if (!chapter.bookmark && dbChapter.bookmark) {
                    chapter.bookmark = dbChapter.bookmark
                }
            }

            chapter.manga_id = manga.id
        }

        val newChapters = chapters.groupBy { it.id != null }
        newChapters[true]?.let { db.updateKnownChaptersBackup(it).executeAsBlocking() }
        newChapters[false]?.let { db.insertChapters(it).executeAsBlocking() }
    }

    private fun restoreExtras(
        manga: Manga,
        categories: List<Int>,
        history: List<BackupHistory>,
        tracks: List<Track>,
        backupCategories: List<BackupCategory>,
        filteredScanlators: List<String>,
        customManga: CustomMangaManager.MangaJson?,
    ) {
        restoreCategories(manga, categories, backupCategories)
        restoreHistoryForManga(history)
        restoreTrackForManga(manga, tracks)
        restoreFilteredScanlatorsForManga(manga, filteredScanlators)
        customManga?.id = manga.id!!
        customManga?.let { customMangaManager.saveMangaInfo(it) }
    }

    /**
     * Restores the categories a manga is in.
     *
     * @param manga the manga whose categories have to be restored.
     * @param categories the categories to restore.
     */
    private fun restoreCategories(
        manga: Manga,
        categories: List<Int>,
        backupCategories: List<BackupCategory>,
    ) {
        val dbCategories = db.getCategories().executeAsBlocking()
        val mangaCategoriesToUpdate = ArrayList<MangaCategory>(categories.size)
        categories.forEach { backupCategoryOrder ->
            backupCategories
                .firstOrNull {
                    it.order == backupCategoryOrder
                }?.let { backupCategory ->
                    dbCategories
                        .firstOrNull { dbCategory ->
                            dbCategory.name == backupCategory.name
                        }?.let { dbCategory ->
                            mangaCategoriesToUpdate += MangaCategory.create(manga, dbCategory)
                        }
                }
        }

        // Update database
        if (mangaCategoriesToUpdate.isNotEmpty()) {
            db.deleteOldMangasCategories(listOf(manga)).executeAsBlocking()
            db.insertMangasCategories(mangaCategoriesToUpdate).executeAsBlocking()
        }
    }

    /**
     * Restore history from Json
     *
     * @param history list containing history to be restored
     */
    internal fun restoreHistoryForManga(history: List<BackupHistory>) {
        // List containing history to be updated
        val historyToBeUpdated = ArrayList<History>(history.size)
        for ((url, lastRead, readDuration) in history) {
            val dbHistory = db.getHistoryByChapterUrl(url).executeAsBlocking()
            // Check if history already in database and update
            if (dbHistory != null) {
                dbHistory.apply {
                    last_read = max(lastRead, dbHistory.last_read)
                    time_read = max(readDuration, dbHistory.time_read)
                }
                historyToBeUpdated.add(dbHistory)
            } else {
                // If not in database create
                db.getChapter(url).executeAsBlocking()?.let {
                    val historyToAdd =
                        History.create(it).apply {
                            last_read = lastRead
                            time_read = readDuration
                        }
                    historyToBeUpdated.add(historyToAdd)
                }
            }
        }
        db.upsertHistoryLastRead(historyToBeUpdated).executeAsBlocking()
    }

    /**
     * Restores the sync of a manga.
     *
     * @param manga the manga whose sync have to be restored.
     * @param tracks the track list to restore.
     */
    private fun restoreTrackForManga(
        manga: Manga,
        tracks: List<Track>,
    ) {
        // Fix foreign keys with the current manga id
        tracks.map { it.manga_id = manga.id!! }

        // Get tracks from database
        val dbTracks = db.getTracks(manga).executeAsBlocking()
        val trackToUpdate = mutableListOf<Track>()

        tracks.forEach { track ->
            var isInDatabase = false
            for (dbTrack in dbTracks) {
                if (track.sync_id == dbTrack.sync_id) {
                    // The sync is already in the db, only update its fields
                    if (track.media_id != dbTrack.media_id) {
                        dbTrack.media_id = track.media_id
                    }
                    if (track.library_id != dbTrack.library_id) {
                        dbTrack.library_id = track.library_id
                    }
                    dbTrack.last_chapter_read = max(dbTrack.last_chapter_read, track.last_chapter_read)
                    isInDatabase = true
                    trackToUpdate.add(dbTrack)
                    break
                }
            }
            if (!isInDatabase) {
                // Insert new sync. Let the db assign the id
                track.id = null
                trackToUpdate.add(track)
            }
        }
        // Update database
        if (trackToUpdate.isNotEmpty()) {
            db.insertTracks(trackToUpdate).executeAsBlocking()
        }
    }

    private fun restoreFilteredScanlatorsForManga(
        manga: Manga,
        filteredScanlators: List<String>,
    ) {
        if (filteredScanlators.isEmpty()) return

        val actualList = ChapterUtil.getScanlators(manga.filtered_scanlators) +
            filteredScanlators.flatMap { ChapterUtil.getScanlators(it) }
        manga.filtered_scanlators = ChapterUtil.getScanlatorString(actualList.toSet())
        db.updateMangaFilteredScanlators(manga).executeAsBlocking()
    }

    private fun restoreAppPreferences(preferences: List<BackupPreference>) {
        restorePreferences(preferences, preferenceStore)

        ExtensionUpdateJob.setupTask(context)
        LibraryUpdateJob.setupTask(context)
        BackupCreatorJob.setupTask(context)

        restoreProgress += 1
        showRestoreProgress(restoreProgress, restoreAmount, context.getString(R.string.app_settings))
    }

    private fun restoreSourcePreferences(preferences: List<BackupSourcePreferences>) {
        preferences.forEach {
            val sourcePrefs = AndroidPreferenceStore(context, sourcePreferences(it.sourceKey))
            restorePreferences(it.prefs, sourcePrefs)
        }

        restoreProgress += 1
        showRestoreProgress(restoreProgress, restoreAmount, context.getString(R.string.source_settings))
    }

    private fun restorePreferences(
        toRestore: List<BackupPreference>,
        preferenceStore: PreferenceStore,
    ) {
        val prefs = preferenceStore.getAll()
        toRestore.forEach { (key, value) ->
            // j2k fork differences
            if (key == "library_sorting_mode" &&
                value is StringPreferenceValue &&
                prefs[key] is Int?
            ) {
                val intValue = LibrarySort.deserialize(value.value)
                preferenceStore.getInt(key).set(intValue.mainValue)
                return@forEach
            }
            // Upstream (Mihon) stores these chapter filter/sort keys as Long, but J2K
            // stores them as Int. When a key hasn't been written locally yet, the type
            // check below can't tell the two forks apart (prefs[key] is null for both),
            // so a Mihon backup would otherwise write a raw Long into a slot J2K reads
            // with getInt(), causing a ClassCastException on next read.
            if (key in mihonLongToJ2kIntPreferenceKeys && value is LongPreferenceValue) {
                preferenceStore.getInt(key).set(value.value.toInt())
                return@forEach
            }
            // Upstream stores the installer choice as a String enum name, J2K stores it
            // as an Int (see ExtensionInstaller.PACKAGE_INSTALLER/SHIZUKU/PRIVATE).
            if (key == "extension_installer" && value is StringPreferenceValue) {
                val intValue =
                    when (value.value) {
                        "SHIZUKU" -> ExtensionInstaller.SHIZUKU
                        "PRIVATE" -> ExtensionInstaller.PRIVATE
                        else -> ExtensionInstaller.PACKAGE_INSTALLER // PACKAGEINSTALLER, LEGACY
                    }
                preferenceStore.getInt(key).set(intValue)
                return@forEach
            }
            // end j2k fork differences
            when (value) {
                is IntPreferenceValue -> {
                    if (prefs[key] is Int?) {
                        preferenceStore.getInt(key).set(value.value)
                    }
                }
                is LongPreferenceValue -> {
                    if (prefs[key] is Long?) {
                        preferenceStore.getLong(key).set(value.value)
                    }
                }
                is FloatPreferenceValue -> {
                    if (prefs[key] is Float?) {
                        preferenceStore.getFloat(key).set(value.value)
                    }
                }
                is StringPreferenceValue -> {
                    if (prefs[key] is String?) {
                        preferenceStore.getString(key).set(value.value)
                    }
                }
                is BooleanPreferenceValue -> {
                    if (prefs[key] is Boolean?) {
                        preferenceStore.getBoolean(key).set(value.value)
                    }
                }
                is StringSetPreferenceValue -> {
                    if (prefs[key] is Set<*>?) {
                        preferenceStore.getStringSet(key).set(value.value)
                    }
                }
            }
        }
    }

    /**
     * Called to update dialog in [BackupConst]
     *
     * @param progress restore progress
     * @param amount total restoreAmount of manga
     * @param title title of restored manga
     */
    private fun showRestoreProgress(
        progress: Int,
        amount: Int,
        title: String,
    ) {
        notifier.showRestoreProgress(title, progress, amount)
    }

    internal fun writeErrorLog(): File {
        try {
            if (errors.isNotEmpty()) {
                val file = context.createFileInCacheDir("tachiyomi_restore.txt")
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

                file.bufferedWriter().use { out ->
                    errors.forEach { (date, message) ->
                        out.write("[${sdf.format(date)}] $message\n")
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
        // Keys upstream (Mihon) stores as Long that J2K stores as Int.
        private val mihonLongToJ2kIntPreferenceKeys =
            setOf(
                PreferenceKeys.defaultChapterFilterByRead,
                PreferenceKeys.defaultChapterFilterByDownloaded,
                PreferenceKeys.defaultChapterFilterByBookmarked,
                PreferenceKeys.defaultChapterSortBySourceOrNumber,
                PreferenceKeys.defaultChapterSortByAscendingOrDescending,
            )
    }
}
