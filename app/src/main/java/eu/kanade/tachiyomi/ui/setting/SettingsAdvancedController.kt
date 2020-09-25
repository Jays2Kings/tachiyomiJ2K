package eu.kanade.tachiyomi.ui.setting

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.core.net.toUri
import androidx.preference.PreferenceScreen
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItemsMultiChoice
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.data.library.LibraryUpdateService.Target
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class SettingsAdvancedController : SettingsController() {

    private val network: NetworkHelper by injectLazy()

    private val chapterCache: ChapterCache by injectLazy()

    private val db: DatabaseHelper by injectLazy()

    private val coverCache: CoverCache by injectLazy()

    @SuppressLint("BatteryLife")
    override fun setupPreferenceScreen(screen: PreferenceScreen) = with(screen) {
        titleRes = R.string.advanced

        switchPreference {
            key = "acra.enable"
            titleRes = R.string.send_crash_report
            summaryRes = R.string.helps_fix_bugs
            defaultValue = true
        }
        preferenceCategory {
            titleRes = R.string.data_management
            preference {
                key = CLEAR_CACHE_KEY
                titleRes = R.string.clear_chapter_cache
                summary = context.getString(R.string.used_, chapterCache.readableSize)

                onClick { clearChapterCache() }
            }

            preference {
                titleRes = R.string.clean_up_cached_covers
                summary = context.getString(
                    R.string.delete_old_covers_in_library_used_,
                    coverCache.getChapterCacheSize()
                )

                onClick {
                    context.toast(R.string.starting_cleanup)
                    coverCache.deleteOldCovers()
                }
            }
            preference {
                titleRes = R.string.clear_cached_covers_non_library
                summary = context.getString(
                    R.string.delete_all_covers__not_in_library_used_,
                    coverCache.getOnlineCoverCacheSize()
                )

                onClick {
                    context.toast(R.string.starting_cleanup)
                    coverCache.deleteAllCachedCovers()
                }
            }
            preference {
                titleRes = R.string.clean_up_downloaded_chapters

                summaryRes = R.string.delete_unused_chapters

                onClick {
                    val ctrl = CleanupDownloadsDialogController()
                    ctrl.targetController = this@SettingsAdvancedController
                    ctrl.showDialog(router)
                }
            }
            preference {
                titleRes = R.string.clear_database
                summaryRes = R.string.clear_database_summary

                onClick {
                    val ctrl = ClearDatabaseDialogController()
                    ctrl.targetController = this@SettingsAdvancedController
                    ctrl.showDialog(router)
                }
            }
        }

        preferenceCategory {
            titleRes = R.string.network
            preference {
                titleRes = R.string.clear_cookies

                onClick {
                    network.cookieManager.removeAll()
                    activity?.toast(R.string.cookies_cleared)
                }
            }

            switchPreference {
                key = PreferenceKeys.enableDoh
                titleRes = R.string.dns_over_https
                summaryRes = R.string.requires_app_restart
                defaultValue = false
            }
        }

        preferenceCategory {
            titleRes = R.string.library
            preference {
                titleRes = R.string.refresh_library_metadata
                summaryRes = R.string.updates_covers_genres_desc

                onClick { LibraryUpdateService.start(context, target = Target.DETAILS) }
            }
            preference {
                titleRes = R.string.refresh_tracking_metadata
                summaryRes = R.string.updates_tracking_details

                onClick { LibraryUpdateService.start(context, target = Target.TRACKING) }
            }
        }

        preferenceCategory {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager?
                if (pm != null) preference {
                    titleRes = R.string.disable_battery_optimization
                    summaryRes = R.string.disable_if_issues_with_updating

                    onClick {
                        val packageName: String = context.packageName
                        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                            val intent = Intent().apply {
                                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                data = "package:$packageName".toUri()
                            }
                            startActivity(intent)
                        } else {
                            context.toast(R.string.battery_optimization_disabled)
                        }
                    }
                }
            }
        }
    }

    class CleanupDownloadsDialogController() : DialogController() {
        override fun onCreateDialog(savedViewState: Bundle?): Dialog {

            return MaterialDialog(activity!!).show {

                title(R.string.clean_up_downloaded_chapters)
                    .listItemsMultiChoice(R.array.clean_up_downloads, disabledIndices = intArrayOf(0), initialSelection = intArrayOf(0, 1, 2)) { dialog, selections, items ->
                        val deleteRead = selections.contains(1)
                        val deleteNonFavorite = selections.contains(2)
                        (targetController as? SettingsAdvancedController)?.cleanupDownloads(deleteRead, deleteNonFavorite)
                    }
                positiveButton(android.R.string.ok)
                negativeButton(android.R.string.cancel)
            }
        }
    }

    private fun cleanupDownloads(removeRead: Boolean, removeNonFavorite: Boolean) {
        if (job?.isActive == true) return
        activity?.toast(R.string.starting_cleanup)
        job = GlobalScope.launch(Dispatchers.IO, CoroutineStart.DEFAULT) {
            val mangaList = db.getMangas().executeAsBlocking()
            val sourceManager: SourceManager = Injekt.get()
            val downloadManager: DownloadManager = Injekt.get()
            val downloadProvider = DownloadProvider(activity!!)
            var foldersCleared = 0
            val sources = sourceManager.getOnlineSources()

            for (source in sources) {
                val mangaFolders = downloadManager.getMangaFolders(source)
                val sourceManga = mangaList.filter { it.source == source.id }

                for (mangaFolder in mangaFolders) {
                    val manga = sourceManga.find { downloadProvider.getMangaDirName(it) == mangaFolder.name }
                    if (manga == null) {
                        // download is orphaned and not even in the db delete it if remove non favorited is enabled
                        if (removeNonFavorite) {
                            foldersCleared += 1 + (mangaFolder.listFiles()?.size ?: 0)
                            mangaFolder.delete()
                        }
                        continue
                    }
                    val chapterList = db.getChapters(manga).executeAsBlocking()
                    foldersCleared += downloadManager.cleanupChapters(chapterList, manga, source, removeRead, removeNonFavorite)
                }
            }
            launchUI {
                val activity = activity ?: return@launchUI
                val cleanupString =
                    if (foldersCleared == 0) activity.getString(R.string.no_folders_to_cleanup)
                    else resources!!.getQuantityString(
                        R.plurals.cleanup_done,
                        foldersCleared,
                        foldersCleared
                    )
                activity.toast(cleanupString, Toast.LENGTH_LONG)
            }
        }
    }

    private fun clearChapterCache() {
        if (activity == null) return
        val files = chapterCache.cacheDir.listFiles() ?: return

        var deletedFiles = 0

        Observable.defer { Observable.from(files) }
            .doOnNext { file ->
                if (chapterCache.removeFileFromCache(file.name)) {
                    deletedFiles++
                }
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                },
                {
                    activity?.toast(R.string.cache_delete_error)
                },
                {
                    activity?.toast(
                        resources?.getQuantityString(
                            R.plurals.cache_cleared,
                            deletedFiles,
                            deletedFiles
                        )
                    )
                    findPreference(CLEAR_CACHE_KEY)?.summary =
                        resources?.getString(R.string.used_, chapterCache.readableSize)
                }
            )
    }

    class ClearDatabaseDialogController : DialogController() {
        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            return MaterialDialog(activity!!)
                .message(R.string.clear_database_confirmation)
                .positiveButton(android.R.string.ok) {
                    (targetController as? SettingsAdvancedController)?.clearDatabase()
                }
                .negativeButton(android.R.string.cancel)
        }
    }

    private fun clearDatabase() {
        db.deleteMangasNotInLibrary().executeAsBlocking()
        db.deleteHistoryNoLastRead().executeAsBlocking()
        activity?.toast(R.string.clear_database_completed)
    }

    private companion object {
        const val CLEAR_CACHE_KEY = "pref_clear_cache_key"

        private var job: Job? = null
    }
}
