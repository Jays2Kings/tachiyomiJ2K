package eu.kanade.tachiyomi.ui.setting

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceScreen
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob.Target
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.data.preference.asImmediateFlowIn
import eu.kanade.tachiyomi.extension.ShizukuInstaller
import eu.kanade.tachiyomi.extension.util.ExtensionInstaller
import eu.kanade.tachiyomi.extension.util.TrustExtension
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.PREF_DOH_360
import eu.kanade.tachiyomi.network.PREF_DOH_ADGUARD
import eu.kanade.tachiyomi.network.PREF_DOH_ALIDNS
import eu.kanade.tachiyomi.network.PREF_DOH_CLOUDFLARE
import eu.kanade.tachiyomi.network.PREF_DOH_DNSPOD
import eu.kanade.tachiyomi.network.PREF_DOH_GOOGLE
import eu.kanade.tachiyomi.network.PREF_DOH_QUAD101
import eu.kanade.tachiyomi.network.PREF_DOH_QUAD9
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.setting.database.ClearDatabaseController
import eu.kanade.tachiyomi.ui.setting.debug.DebugController
import eu.kanade.tachiyomi.util.CrashLogUtil
import eu.kanade.tachiyomi.util.system.disableItems
import eu.kanade.tachiyomi.util.system.isPackageInstalled
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.localeContext
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.withUIContext
import eu.kanade.tachiyomi.util.view.openInBrowser
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.Headers
import rikka.sui.Sui
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File

class SettingsAdvancedController : SettingsController() {
    private val network: NetworkHelper by injectLazy()

    private val chapterCache: ChapterCache by injectLazy()

    private val db: DatabaseHelper by injectLazy()

    private val coverCache: CoverCache by injectLazy()

    private val downloadManager: DownloadManager by injectLazy()

    val trustExtension: TrustExtension by injectLazy()

    private val isUpdaterEnabled = BuildConfig.INCLUDE_UPDATER

    @SuppressLint("BatteryLife")
    override fun setupPreferenceScreen(screen: PreferenceScreen) =
        screen.apply {
            titleRes = R.string.advanced

            switchPreference {
                key = "acra.enable"
                titleRes = R.string.send_crash_report
                summaryRes = R.string.helps_fix_bugs
                defaultValue = true
                onChange {
                    try {
                        Firebase.crashlytics.setCrashlyticsCollectionEnabled(it as Boolean)
                    } catch (_: Exception) {
                    }
                    true
                }
            }

            preference {
                key = "dump_crash_logs"
                titleRes = R.string.dump_crash_logs
                summaryRes = R.string.saves_error_logs

                onClick {
                    CrashLogUtil(context.localeContext).dumpLogs()
                }
            }

            preference {
                key = "debug_info"
                titleRes = R.string.pref_debug_info

                onClick {
                    router.pushController(DebugController().withFadeTransaction())
                }
            }

            preferenceCategory {
                titleRes = R.string.label_background_activity
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager?
                if (pm != null) {
                    preference {
                        key = "disable_batt_opt"
                        titleRes = R.string.disable_battery_optimization
                        summaryRes = R.string.disable_if_issues_with_updating

                        onClick {
                            val packageName: String = context.packageName
                            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                                val intent =
                                    Intent().apply {
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

                preference {
                    key = "pref_dont_kill_my_app"
                    title = "Don't kill my app!"
                    summaryRes = R.string.about_dont_kill_my_app

                    onClick {
                        openInBrowser("https://dontkillmyapp.com/")
                    }
                }
            }

            if (isUpdaterEnabled) {
                switchPreference {
                    titleRes = R.string.check_for_beta_releases
                    summaryRes = R.string.try_new_features
                    bindTo(preferences.checkForBetas())

                    onChange {
                        it as Boolean
                        if (it != BuildConfig.BETA) {
                            activity!!
                                .materialAlertDialog()
                                .setTitle(R.string.warning)
                                .setMessage(if (it) R.string.warning_enroll_into_beta else R.string.warning_unenroll_from_beta)
                                .setPositiveButton(android.R.string.ok) { _, _ -> isChecked = it }
                                .setNegativeButton(android.R.string.cancel, null)
                                .show()
                            false
                        } else {
                            true
                        }
                    }
                }
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
                    titleRes = R.string.force_download_cache_refresh
                    summaryRes = R.string.force_download_cache_refresh_summary
                    onClick { downloadManager.refreshCache() }
                }

                preference {
                    key = "clean_cached_covers"
                    titleRes = R.string.clean_up_cached_covers
                    summary =
                        context.getString(
                            R.string.delete_old_covers_in_library_used_,
                            coverCache.getChapterCacheSize(),
                        )

                    onClick {
                        context.toast(R.string.starting_cleanup)
                        (activity as? AppCompatActivity)?.lifecycleScope?.launchIO {
                            coverCache.deleteOldCovers()
                        }
                    }
                }
                preference {
                    key = "clear_cached_not_library"
                    titleRes = R.string.clear_cached_covers_non_library
                    summary =
                        context.getString(
                            R.string.delete_all_covers__not_in_library_used_,
                            coverCache.getOnlineCoverCacheSize(),
                        )

                    onClick {
                        context.toast(R.string.starting_cleanup)
                        (activity as? AppCompatActivity)?.lifecycleScope?.launchIO {
                            coverCache.deleteAllCachedCovers()
                        }
                    }
                }
                preference {
                    key = "clean_downloaded_chapters"
                    titleRes = R.string.clean_up_downloaded_chapters

                    summaryRes = R.string.delete_unused_chapters

                    onClick {
                        activity!!
                            .materialAlertDialog()
                            .setTitle(R.string.clean_up_downloaded_chapters)
                            .setMultiChoiceItems(
                                R.array.clean_up_downloads,
                                booleanArrayOf(true, true, true),
                            ) { dialog, position, _ ->
                                if (position == 0) {
                                    val listView = (dialog as AlertDialog).listView
                                    listView.setItemChecked(position, true)
                                }
                            }.setPositiveButton(android.R.string.ok) { dialog, _ ->
                                val listView = (dialog as AlertDialog).listView
                                val deleteRead = listView.isItemChecked(1)
                                val deleteNonFavorite = listView.isItemChecked(2)
                                cleanupDownloads(deleteRead, deleteNonFavorite)
                            }.setNegativeButton(android.R.string.cancel, null)
                            .show()
                            .apply {
                                disableItems(arrayOf(activity!!.getString(R.string.clean_orphaned_downloads)))
                            }
                    }
                }
                preference {
                    key = "pref_clear_webview_data"
                    titleRes = R.string.pref_clear_webview_data

                    onClick { clearWebViewData() }
                }
                preference {
                    key = "clear_database"
                    titleRes = R.string.clear_database
                    summaryRes = R.string.clear_database_summary
                    onClick { router.pushController(ClearDatabaseController().withFadeTransaction()) }
                }
            }

            preferenceCategory {
                titleRes = R.string.network
                preference {
                    key = "clear_cookies"
                    titleRes = R.string.clear_cookies

                    onClick {
                        network.cookieJar.removeAll()
                        activity?.toast(R.string.cookies_cleared)
                    }
                }
                intListPreference(activity) {
                    key = PreferenceKeys.dohProvider
                    titleRes = R.string.doh
                    entriesRes =
                        arrayOf(
                            R.string.disabled,
                            R.string.cloudflare,
                            R.string.google,
                            R.string.ad_guard,
                            R.string.quad9,
                            R.string.aliDNS,
                            R.string.dnsPod,
                            R.string.dns_360,
                            R.string.quad_101,
                        )
                    entryValues =
                        listOf(
                            -1,
                            PREF_DOH_CLOUDFLARE,
                            PREF_DOH_GOOGLE,
                            PREF_DOH_ADGUARD,
                            PREF_DOH_QUAD9,
                            PREF_DOH_ALIDNS,
                            PREF_DOH_DNSPOD,
                            PREF_DOH_360,
                            PREF_DOH_QUAD101,
                        )

                    defaultValue = -1
                    onChange {
                        activity?.toast(R.string.requires_app_restart)
                        true
                    }
                }
                editTextPreference(activity) {
                    bindTo(preferences.defaultUserAgent())
                    titleRes = R.string.user_agent_string

                    onChange {
                        it as String
                        try {
                            // OkHttp checks for valid values internally
                            Headers.Builder().add("User-Agent", it)
                        } catch (_: IllegalArgumentException) {
                            context.toast(R.string.error_user_agent_string_invalid)
                            return@onChange false
                        }
                        true
                    }
                }
            }

            preferenceCategory {
                titleRes = R.string.extensions

                intListPreference(activity) {
                    bindTo(preferences.extensionInstaller())
                    titleRes = R.string.ext_installer_pref
                    entriesRes =
                        arrayOf(
                            R.string.default_value,
                            R.string.ext_installer_shizuku,
                            R.string.ext_installer_private,
                        )
                    entryValues =
                        listOf(
                            ExtensionInstaller.PACKAGE_INSTALLER,
                            ExtensionInstaller.SHIZUKU,
                            ExtensionInstaller.PRIVATE,
                        )

                    onChange {
                        it as Int
                        if (it == ExtensionInstaller.SHIZUKU) {
                            return@onChange if (!context.isPackageInstalled(ShizukuInstaller.shizukuPkgName) && !Sui.isSui()) {
                                context
                                    .materialAlertDialog()
                                    .setTitle(R.string.ext_installer_shizuku)
                                    .setMessage(R.string.ext_installer_shizuku_unavailable_dialog)
                                    .setPositiveButton(R.string.download) { _, _ ->
                                        openInBrowser(ShizukuInstaller.downloadLink)
                                    }.setNegativeButton(android.R.string.cancel, null)
                                    .show()
                                false
                            } else {
                                true
                            }
                        }
                        true
                    }
                }
                infoPreference(R.string.ext_installer_summary).apply {
                    preferences.extensionInstaller().asImmediateFlowIn(viewScope) {
                        isVisible =
                            it != ExtensionInstaller.PACKAGE_INSTALLER &&
                            Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                    }
                }
                preference {
                    titleRes = R.string.ext_revoke_trust

                    onClick {
                        trustExtension.revokeAll()
                        activity?.toast(R.string.requires_app_restart)
                    }
                }
            }

            preferenceCategory {
                titleRes = R.string.library
                preference {
                    key = "refresh_lib_meta"
                    titleRes = R.string.refresh_library_metadata
                    summaryRes = R.string.updates_covers_genres_desc

                    onClick { LibraryUpdateJob.startNow(context, target = Target.DETAILS) }
                }
                preference {
                    key = "refresh_teacking_meta"
                    titleRes = R.string.refresh_tracking_metadata
                    summaryRes = R.string.updates_tracking_details

                    onClick { LibraryUpdateJob.startNow(context, target = Target.TRACKING) }
                }
            }
        }

    private fun cleanupDownloads(
        removeRead: Boolean,
        removeNonFavorite: Boolean,
    ) {
        if (job?.isActive == true) return
        activity?.toast(R.string.starting_cleanup)
        job =
            GlobalScope.launch(Dispatchers.IO, CoroutineStart.DEFAULT) {
                val mangaList = db.getMangas().executeAsBlocking()
                val sourceManager: SourceManager = Injekt.get()
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
                        if (foldersCleared == 0) {
                            activity.getString(R.string.no_folders_to_cleanup)
                        } else {
                            resources!!.getQuantityString(
                                R.plurals.cleanup_done,
                                foldersCleared,
                                foldersCleared,
                            )
                        }
                    activity.toast(cleanupString, Toast.LENGTH_LONG)
                }
            }
    }

    private fun clearChapterCache() {
        if (activity == null) return
        viewScope.launchIO {
            val files = chapterCache.cacheDir.listFiles() ?: return@launchIO
            var deletedFiles = 0
            try {
                files.forEach { file ->
                    if (chapterCache.removeFileFromCache(file.name)) {
                        deletedFiles++
                    }
                }
                withUIContext {
                    activity?.toast(
                        resources?.getQuantityString(
                            R.plurals.cache_cleared,
                            deletedFiles,
                            deletedFiles,
                        ),
                    )
                    findPreference(CLEAR_CACHE_KEY)?.summary =
                        resources?.getString(R.string.used_, chapterCache.readableSize)
                }
            } catch (_: Exception) {
                withUIContext {
                    activity?.toast(R.string.cache_delete_error)
                }
            }
        }
    }

    private fun clearWebViewData() {
        if (activity == null) return
        try {
            val webview = WebView(activity!!)
            webview.setDefaultSettings()
            webview.clearCache(true)
            webview.clearFormData()
            webview.clearHistory()
            webview.clearSslPreferences()
            WebStorage.getInstance().deleteAllData()
            activity?.applicationInfo?.dataDir?.let { File("$it/app_webview/").deleteRecursively() }
            activity?.toast(R.string.webview_data_deleted)
        } catch (e: Throwable) {
            Timber.e(e)
            activity?.toast(R.string.cache_delete_error)
        }
    }

    private companion object {
        const val CLEAR_CACHE_KEY = "pref_clear_cache_key"

        private var job: Job? = null
    }
}
