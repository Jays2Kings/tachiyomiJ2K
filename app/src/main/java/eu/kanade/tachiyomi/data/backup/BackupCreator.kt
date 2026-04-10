package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.backup.BackupConst.BACKUP_APP_PREFS
import eu.kanade.tachiyomi.data.backup.BackupConst.BACKUP_APP_PREFS_MASK
import eu.kanade.tachiyomi.data.backup.BackupConst.BACKUP_CATEGORY
import eu.kanade.tachiyomi.data.backup.BackupConst.BACKUP_CATEGORY_MASK
import eu.kanade.tachiyomi.data.backup.BackupConst.BACKUP_CHAPTER
import eu.kanade.tachiyomi.data.backup.BackupConst.BACKUP_CHAPTER_MASK
import eu.kanade.tachiyomi.data.backup.BackupConst.BACKUP_CUSTOM_INFO
import eu.kanade.tachiyomi.data.backup.BackupConst.BACKUP_CUSTOM_INFO_MASK
import eu.kanade.tachiyomi.data.backup.BackupConst.BACKUP_HISTORY
import eu.kanade.tachiyomi.data.backup.BackupConst.BACKUP_HISTORY_MASK
import eu.kanade.tachiyomi.data.backup.BackupConst.BACKUP_READ_MANGA
import eu.kanade.tachiyomi.data.backup.BackupConst.BACKUP_READ_MANGA_MASK
import eu.kanade.tachiyomi.data.backup.BackupConst.BACKUP_SOURCE_PREFS
import eu.kanade.tachiyomi.data.backup.BackupConst.BACKUP_SOURCE_PREFS_MASK
import eu.kanade.tachiyomi.data.backup.BackupConst.BACKUP_TRACK
import eu.kanade.tachiyomi.data.backup.BackupConst.BACKUP_TRACK_MASK
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.FloatPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.LongPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.library.CustomMangaManager
import eu.kanade.tachiyomi.data.preference.Preference
import eu.kanade.tachiyomi.data.preference.PreferenceStore
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.preferenceKey
import eu.kanade.tachiyomi.source.sourcePreferences
import eu.kanade.tachiyomi.ui.library.LibrarySort
import kotlinx.serialization.protobuf.ProtoBuf
import okio.buffer
import okio.gzip
import okio.sink
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.FileOutputStream

class BackupCreator(
    val context: Context,
) {
    private val preferenceStore: PreferenceStore = Injekt.get()
    val parser = ProtoBuf
    private val db: DatabaseHelper = Injekt.get()
    private val sourceManager: SourceManager = Injekt.get()
    private val preferences: PreferencesHelper = Injekt.get()
    private val customMangaManager: CustomMangaManager = Injekt.get()

    /**
     * Create backup Json file from database
     *
     * @param uri path of Uri
     * @param isAutoBackup backup called from scheduled backup job
     */
    fun createBackup(
        uri: Uri,
        flags: Int,
        isAutoBackup: Boolean,
    ): String {
        // Create root object
        var backup: Backup? = null

        db.inTransaction {
            val databaseManga =
                db.getFavoriteMangas().executeAsBlocking() +
                    if (flags and BACKUP_READ_MANGA_MASK == BACKUP_READ_MANGA) {
                        db.getReadNotInLibraryMangas().executeAsBlocking()
                    } else {
                        emptyList()
                    }

            backup =
                Backup(
                    backupMangas(databaseManga, flags),
                    backupCategories(),
                    emptyList(),
                    backupExtensionInfo(databaseManga),
                    backupAppPreferences(flags),
                    backupSourcePreferences(flags),
                )
        }

        var file: UniFile? = null
        try {
            file = (
                if (isAutoBackup) {
                    // Get dir of file and create
                    var dir = UniFile.fromUri(context, uri)
                    dir = dir.createDirectory("automatic")

                    // Delete older backups
                    val numberOfBackups = preferences.numberOfBackups().get()
                    dir
                        .listFiles { _, filename -> Backup.filenameRegex.matches(filename) }
                        .orEmpty()
                        .sortedByDescending { it.name }
                        .drop(numberOfBackups - 1)
                        .forEach { it.delete() }

                    // Create new file to place backup
                    dir.createFile(Backup.getBackupFilename())
                } else {
                    UniFile.fromUri(context, uri)
                }
            )
                ?: throw Exception("Couldn't create backup file")

            if (!file.isFile) {
                throw IllegalStateException("Failed to get handle on file")
            }

            val byteArray = parser.encodeToByteArray(Backup.serializer(), backup!!)
            if (byteArray.isEmpty()) {
                throw IllegalStateException(context.getString(R.string.empty_backup_error))
            }

            file
                .openOutputStream()
                .also {
                    // Force overwrite old file
                    (it as? FileOutputStream)?.channel?.truncate(0)
                }.sink()
                .gzip()
                .buffer()
                .use { it.write(byteArray) }
            val fileUri = file.uri

            // Make sure it's a valid backup file
            BackupFileValidator().validate(context, fileUri)

            return fileUri.toString()
        } catch (e: Exception) {
            Timber.e(e)
            file?.delete()
            throw e
        }
    }

    private fun backupMangas(
        mangas: List<Manga>,
        flags: Int,
    ): List<BackupManga> =
        mangas.map {
            backupManga(it, flags)
        }

    private fun backupExtensionInfo(mangas: List<Manga>): List<BackupSource> =
        mangas
            .asSequence()
            .map { it.source }
            .distinct()
            .map { sourceManager.getOrStub(it) }
            .map { BackupSource.copyFrom(it) }
            .toList()

    /**
     * Backup the categories of library
     *
     * @return list of [BackupCategory] to be backed up
     */
    private fun backupCategories(): List<BackupCategory> =
        db
            .getCategories()
            .executeAsBlocking()
            .map { BackupCategory.copyFrom(it) }

    /**
     * Convert a manga to Json
     *
     * @param manga manga that gets converted
     * @param options options for the backup
     * @return [BackupManga] containing manga in a serializable form
     */
    private fun backupManga(
        manga: Manga,
        options: Int,
    ): BackupManga {
        // Entry for this manga
        val mangaObject =
            BackupManga.copyFrom(
                manga,
                if (options and BACKUP_CUSTOM_INFO_MASK ==
                    BACKUP_CUSTOM_INFO
                ) {
                    customMangaManager
                } else {
                    null
                },
            )

        // Check if user wants chapter information in backup
        if (options and BACKUP_CHAPTER_MASK == BACKUP_CHAPTER) {
            // Backup all the chapters
            val chapters = db.getChapters(manga).executeAsBlocking()
            if (chapters.isNotEmpty()) {
                mangaObject.chapters = chapters.map { BackupChapter.copyFrom(it) }
            }
        }

        // Check if user wants category information in backup
        if (options and BACKUP_CATEGORY_MASK == BACKUP_CATEGORY) {
            // Backup categories for this manga
            val categoriesForManga = db.getCategoriesForManga(manga).executeAsBlocking()
            if (categoriesForManga.isNotEmpty()) {
                mangaObject.categories = categoriesForManga.mapNotNull { it.order }
            }
        }

        // Check if user wants track information in backup
        if (options and BACKUP_TRACK_MASK == BACKUP_TRACK) {
            val tracks = db.getTracks(manga).executeAsBlocking()
            if (tracks.isNotEmpty()) {
                mangaObject.tracking = tracks.map { BackupTracking.copyFrom(it) }
            }
        }

        // Check if user wants history information in backup
        if (options and BACKUP_HISTORY_MASK == BACKUP_HISTORY) {
            val historyForManga = db.getHistoryByMangaId(manga.id!!).executeAsBlocking()
            if (historyForManga.isNotEmpty()) {
                val history =
                    historyForManga.mapNotNull { history ->
                        val url = db.getChapter(history.chapter_id).executeAsBlocking()?.url
                        url?.let { BackupHistory(url, history.last_read, history.time_read) }
                    }
                if (history.isNotEmpty()) {
                    mangaObject.history = history
                }
            }
        }

        return mangaObject
    }

    private fun backupAppPreferences(flags: Int): List<BackupPreference> {
        if (flags and BACKUP_APP_PREFS_MASK != BACKUP_APP_PREFS) return emptyList()
        return preferenceStore.getAll().toBackupPreferences()
    }

    private fun backupSourcePreferences(flags: Int): List<BackupSourcePreferences> {
        if (flags and BACKUP_SOURCE_PREFS_MASK != BACKUP_SOURCE_PREFS) return emptyList()
        return sourceManager
            .getOnlineSources()
            .filterIsInstance<ConfigurableSource>()
            .map {
                BackupSourcePreferences(
                    it.preferenceKey(),
                    it.sourcePreferences().all.toBackupPreferences(),
                )
            }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, *>.toBackupPreferences(): List<BackupPreference> {
        return this
            .filterKeys { !Preference.isPrivate(it) }
            .mapNotNull { (key, value) ->
                // j2k fork differences
                if (key == "library_sorting_mode" && value is Int) {
                    val stringValue = (LibrarySort.valueOf(value) ?: LibrarySort.Title).serialize()
                    return@mapNotNull BackupPreference(key, StringPreferenceValue(stringValue))
                }
                // end j2k fork differences
                when (value) {
                    is Int -> BackupPreference(key, IntPreferenceValue(value))
                    is Long -> BackupPreference(key, LongPreferenceValue(value))
                    is Float -> BackupPreference(key, FloatPreferenceValue(value))
                    is String -> BackupPreference(key, StringPreferenceValue(value))
                    is Boolean -> BackupPreference(key, BooleanPreferenceValue(value))
                    is Set<*> ->
                        (value as? Set<String>)?.let {
                            BackupPreference(key, StringSetPreferenceValue(it))
                        }
                    else -> null
                }
            }
    }
}
