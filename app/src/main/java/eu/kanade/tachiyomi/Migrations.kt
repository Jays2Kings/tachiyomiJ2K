package eu.kanade.tachiyomi

import androidx.core.content.edit
import androidx.preference.PreferenceManager
import eu.kanade.tachiyomi.data.backup.BackupCreatorJob
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.plusAssign
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.updater.AppUpdateJob
import eu.kanade.tachiyomi.data.updater.AppUpdateService
import eu.kanade.tachiyomi.extension.ExtensionUpdateJob
import eu.kanade.tachiyomi.network.PREF_DOH_CLOUDFLARE
import eu.kanade.tachiyomi.ui.library.LibraryPresenter
import eu.kanade.tachiyomi.ui.reader.settings.OrientationType
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

object Migrations {

    /**
     * Performs a migration when the application is updated.
     *
     * @param preferences Preferences of the application.
     * @return true if a migration is performed, false otherwise.
     */
    fun upgrade(preferences: PreferencesHelper, scope: CoroutineScope): Boolean {
        val context = preferences.context
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit {
            remove(AppUpdateService.NOTIFY_ON_INSTALL_KEY)
        }
        val oldVersion = preferences.lastVersionCode().get()
        if (oldVersion < BuildConfig.VERSION_CODE) {
            preferences.lastVersionCode().set(BuildConfig.VERSION_CODE)

            // Always set up background tasks to ensure they're running
            if (BuildConfig.INCLUDE_UPDATER) {
                AppUpdateJob.setupTask(context)
            }
            ExtensionUpdateJob.setupTask(context)
            LibraryUpdateJob.setupTask(context)
            BackupCreatorJob.setupTask(context)

            if (oldVersion == 0) {
                return BuildConfig.DEBUG
            }

            if (oldVersion < 14) {
                // Restore jobs after upgrading to evernote's job scheduler.
                if (BuildConfig.INCLUDE_UPDATER) {
                    AppUpdateJob.setupTask(context)
                }
                LibraryUpdateJob.setupTask(context)
            }
            if (oldVersion < 15) {
                // Delete internal chapter cache dir.
                File(context.cacheDir, "chapter_disk_cache").deleteRecursively()
            }
            if (oldVersion < 19) {
                // Move covers to external files dir.
                val oldDir = File(context.externalCacheDir, "cover_disk_cache")
                if (oldDir.exists()) {
                    val destDir = context.getExternalFilesDir("covers")
                    if (destDir != null) {
                        oldDir.listFiles()?.forEach {
                            it.renameTo(File(destDir, it.name))
                        }
                    }
                }
            }
            if (oldVersion < 26) {
                // Delete external chapter cache dir.
                val extCache = context.externalCacheDir
                if (extCache != null) {
                    val chapterCache = File(extCache, "chapter_disk_cache")
                    if (chapterCache.exists()) {
                        chapterCache.deleteRecursively()
                    }
                }
            }
            if (oldVersion < 54) {
                DownloadProvider(context).renameChapters()
            }
            if (oldVersion < 62) {
                LibraryPresenter.updateDB()
                // Restore jobs after migrating from Evernote's job scheduler to WorkManager.
                if (BuildConfig.INCLUDE_UPDATER) {
                    AppUpdateJob.setupTask(context)
                }
                LibraryUpdateJob.setupTask(context)
                BackupCreatorJob.setupTask(context)
                ExtensionUpdateJob.setupTask(context)
            }
            if (oldVersion < 66) {
                LibraryPresenter.updateCustoms()
            }
            if (oldVersion < 68) {
                // Force MAL log out due to login flow change
                // v67: switched from scraping to WebView
                // v68: switched from WebView to OAuth
                val trackManager = Injekt.get<TrackManager>()
                if (trackManager.myAnimeList.isLogged) {
                    trackManager.myAnimeList.logout()
                    context.toast(R.string.myanimelist_relogin)
                }
            }
            if (oldVersion < 71) {
                // Migrate DNS over HTTPS setting
                val wasDohEnabled = prefs.getBoolean("enable_doh", false)
                if (wasDohEnabled) {
                    prefs.edit {
                        putInt(PreferenceKeys.dohProvider, PREF_DOH_CLOUDFLARE)
                        remove("enable_doh")
                    }
                }
            }
            if (oldVersion < 73) {
                // Reset rotation to Free after replacing Lock
                if (prefs.contains("pref_rotation_type_key")) {
                    prefs.edit {
                        putInt("pref_rotation_type_key", 1)
                    }
                }
            }
            if (oldVersion < 74) {
                // Turn on auto updates for all users
                if (BuildConfig.INCLUDE_UPDATER) {
                    AppUpdateJob.setupTask(context)
                }
            }
            if (oldVersion < 75) {
                val wasShortcutsDisabled = !prefs.getBoolean("show_manga_app_shortcuts", true)
                if (wasShortcutsDisabled) {
                    prefs.edit {
                        putBoolean(PreferenceKeys.showSourcesInShortcuts, false)
                        putBoolean(PreferenceKeys.showSeriesInShortcuts, false)
                        remove("show_manga_app_shortcuts")
                    }
                }
                // Handle removed every 1 or 2 hour library updates
                val updateInterval = preferences.libraryUpdateInterval().get()
                if (updateInterval == 1 || updateInterval == 2) {
                    preferences.libraryUpdateInterval().set(3)
                    LibraryUpdateJob.setupTask(context, 3)
                }
            }
            if (oldVersion < 77) {
                // Migrate Rotation and Viewer values to default values for viewer_flags
                val newOrientation = when (prefs.getInt("pref_rotation_type_key", 1)) {
                    1 -> OrientationType.FREE.flagValue
                    2 -> OrientationType.PORTRAIT.flagValue
                    3 -> OrientationType.LANDSCAPE.flagValue
                    4 -> OrientationType.LOCKED_PORTRAIT.flagValue
                    5 -> OrientationType.LOCKED_LANDSCAPE.flagValue
                    else -> OrientationType.FREE.flagValue
                }

                // Reading mode flag and prefValue is the same value
                val newReadingMode = prefs.getInt("pref_default_viewer_key", 1)

                prefs.edit {
                    putInt("pref_default_orientation_type_key", newOrientation)
                    remove("pref_rotation_type_key")
                    putInt("pref_default_reading_mode_key", newReadingMode)
                    remove("pref_default_viewer_key")
                }
            }
            if (oldVersion < 83) {
                if (preferences.enabledLanguages().isSet()) {
                    preferences.enabledLanguages() += "all"
                }
            }
            if (oldVersion < 86) {
                // Handle removed every 3, 4, 6, and 8 hour library updates
                val updateInterval = preferences.libraryUpdateInterval().get()
                if (updateInterval in listOf(3, 4, 6, 8)) {
                    preferences.libraryUpdateInterval().set(12)
                    LibraryUpdateJob.setupTask(context, 12)
                }
            }
            if (oldVersion < 88) {
                scope.launchIO {
                    LibraryPresenter.updateRatiosAndColors()
                }
                val oldReaderTap = prefs.getBoolean("reader_tap", true)
                if (!oldReaderTap) {
                    preferences.navigationModePager().set(5)
                    preferences.navigationModeWebtoon().set(5)
                }
            }
            if (oldVersion < 90) {
                val oldSecureScreen = prefs.getBoolean("secure_screen", false)
                if (oldSecureScreen) {
                    preferences.secureScreen().set(PreferenceValues.SecureScreenMode.ALWAYS)
                }
            }

            return true
        }
        return false
    }
}
