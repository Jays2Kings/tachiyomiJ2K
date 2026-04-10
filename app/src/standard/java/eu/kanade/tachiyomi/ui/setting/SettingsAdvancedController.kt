package eu.kanade.tachiyomi.ui.setting

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.webkit.WebStorage
import android.webkit.WebView
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
import eu.kanade.tachiyomi.ui.setting.database.ClearDatabaseController
import eu.kanade.tachiyomi.ui.setting.debug.DebugController
import eu.kanade.tachiyomi.util.CrashLogUtil
import eu.kanade.tachiyomi.util.system.isPackageInstalled
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class SettingsAdvancedController : SettingsController() {
    private val network: NetworkHelper by injectLazy()

    private val chapterCache: ChapterCache by injectLazy()

    private val coverCache: CoverCache by injectLazy()

    private val db: DatabaseHelper by injectLazy()

    private val downloadManager: DownloadManager by injectLazy()

    private val extensionRepoService: ExtensionRepoService by injectLazy()

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
                summaryRes = R.string.dump_crash_logs_summary

                onClick {
                    CrashLogUtil(context).dumpLogs()
                }
            }

            preferenceCategory {
                titleRes = R.string.library

                preference {
                    key = "refresh_library_metadata"
                    titleRes = R.string.refresh_library_manga_metadata
                    summaryRes = R.string.updates_covers_genres_description

                    onClick { LibraryUpdateJob.startNow(context, target = Target.DETAILS) }
                }
                preference {
                    key = "refresh_teacking_metadata"
                    titleRes = R.string.refresh_tracking_metadata
                    summaryRes = R.string.updates_tracking_details

                    onClick { LibraryUpdateJob.startNow(context, target = Target.TRACKING) }
                }
            }

            preferenceCategory {
                titleRes = R.string.network

                listPreference(activity) {
                    key = PreferenceKeys.dohProvider
                    titleRes = R.string.doh_resolver
                    entriesRes =
                        arrayOf(
                            R.string.disabled,
                            R.string.cloudflare,
                            R.string.google,
                            R.string.adguard,
                            R.string.quad9,
                            R.string.alidns,
                            R.string.dnspod,
                            R.string.quad101,
                            R.string.pref_doh_360,
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
                            PREF_DOH_QUAD101,
                            PREF_DOH_360,
                        )
                    defaultValue = -1

                    onChange { newValue ->
                        activity?.toast(R.string.requires_app_restart)
                        true
                    }
                }

                switchPreference {
                    key = PreferenceKeys.enableVerboseLogging
                    titleRes = R.string.verbose_logging
                    summaryRes = R.string.verbose_logging_summary
                    defaultValue = false

                    onChange { newValue ->
                        activity?.toast(R.string.requires_app_restart)
                        true
                    }
                }
            }

            preferenceCategory {
                titleRes = R.string.data_management

                preference {
                    key = "clear_cache"
                    titleRes = R.string.clear_cache
                    summary = context.getString(R.string.used_cache_size, chapterCache.readableSize)

                    onClick { clearCache() }
                }
                preference {
                    key = "clear_cookies"
                    titleRes = R.string.clear_cookies

                    onClick {
                        network.cookieManager.removeAll()
                        activity?.toast(R.string.cookies_cleared)
                    }
                }
                preference {
                    key = "clear_webview_data"
                    titleRes = R.string.clear_webview_data

                    onClick { clearWebViewData() }
                }
                preference {
                    key = "clear_database"
                    titleRes = R.string.clean_up_database
                    summaryRes = R.string.clean_up_database_summary

                    onClick {
                        val ctrl = ClearDatabaseController()
                        ctrl.targetController = this@SettingsAdvancedController
                        router.pushController(ctrl.withFadeTransaction())
                    }
                }
            }

            preferenceCategory {
                titleRes = R.string.extensions

                multiSelectListPreference(activity) {
                    key = PreferenceKeys.extensionInstaller
                    titleRes = R.string.ext_installer_pref
                    val packageManager = context.packageManager
                    val entries = mutableListOf<Pair<String, String>>()
                    val values = mutableListOf<String>()

                    // Always add the internal installer
                    entries.add(Pair(context.getString(R.string.ext_installer_legacy), ExtensionInstaller.LEGACY.name))
                    values.add(ExtensionInstaller.LEGACY.name)

                    // Add shizuku installer if available
                    if (context.isPackageInstalled(ShizukuInstaller.SHIZUKU_PKG)) {
                        entries.add(Pair(context.getString(R.string.ext_installer_shizuku), ExtensionInstaller.SHIZUKU.name))
                        values.add(ExtensionInstaller.SHIZUKU.name)
                    }

                    if (entries.isEmpty()) {
                        visible = false
                        return@multiSelectListPreference
                    }

                    this.entries = entries.map { it.first }.toTypedArray()
                    entryValues = values.toTypedArray()
                    defValue = setOf(ExtensionInstaller.LEGACY.name)

                    preferences.extensionInstaller().asImmediateFlowIn(viewScope) { isVisible = it.isNotEmpty() }
                }

                switchPreference {
                    key = PreferenceKeys.enableExtensionInstaller
                    titleRes = R.string.enable_extension_installer
                    summaryRes = R.string.enable_extension_installer_summary
                    defaultValue = false

                    preferences.extensionInstaller().asImmediateFlowIn(viewScope) { isVisible = it.isNotEmpty() }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager?
                    if (pm?.isIgnoringBatteryOptimizations(context.packageName) == false) {
                        switchPreference {
                            key = "disable_battery_optimization"
                            titleRes = R.string.disable_battery_optimization
                            summaryRes = R.string.disable_battery_optimization_summary
                            defaultValue = false

                            onChange { newValue ->
                                if (newValue == true) {
                                    requestBatteryOptimizationExemption()
                                }
                                false
                            }
                        }
                    }
                }

                preference {
                    key = "extensions_list"
                    titleRes = R.string.extensions_list_empty_screen
                    summaryRes = R.string.extensions_list_empty_screen_summary

                    onClick {
                        val ctrl = DebugController()
                        router.pushController(ctrl.withFadeTransaction())
                    }
                }

                val extensionRepos = extensionRepoService.getAll()
                if (extensionRepos.isNotEmpty()) {
                    preference {
                        key = "extension_repos"
                        titleRes = R.string.extension_repos
                        summary = extensionRepos.joinToString { "${it.name} (${it.baseUrl})" }

                        onClick {
                            router.pushController(SettingsExtensionReposController().withFadeTransaction())
                        }
                    }
                }
            }

            preferenceCategory {
                titleRes = R.string.security

                switchPreference {
                    key = PreferenceKeys.secureScreen
                    titleRes = R.string.secure_screen
                    summaryRes = R.string.secure_screen_summary
                    defaultValue = false

                    onChange {
                        activity?.recreate()
                        true
                    }
                }

                switchPreference {
                    key = PreferenceKeys.hideNotificationContent
                    titleRes = R.string.hide_notification_content
                    defaultValue = false
                }

                preference {
                    key = "biometric_lock"
                    titleRes = R.string.biometric_lock

                    summary =
                        context.getString(
                            if (preferences.biometricLock().get()) {
                                R.string.enabled
                            } else {
                                R.string.disabled
                            },
                        )

                    onClick {
                        router.pushController(
                            SettingsBiometricController().withFadeTransaction(),
                        )
                    }
                }

                multiSelectListPreference(activity) {
                    key = PreferenceKeys.trustedExtensions
                    titleRes = R.string.label_trusted_extensions
                    summaryRes = R.string.trusted_extensions_summary
                    entries = emptyArray()
                    entryValues = emptyArray()
                    preferences.trustedExtensions().asImmediateFlowIn(viewScope) { pref ->
                        val installedExtensions =
                            TrustExtension
                                .getExtensions(context)
                                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

                        entries = installedExtensions.map { "${it.name} (${it.versionName})" }.toTypedArray()
                        entryValues = installedExtensions.map { it.signatureHash }.toTypedArray()
                        val selectedExtensions =
                            installedExtensions
                                .filter { it.signatureHash in pref }
                                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

                        summary =
                            if (selectedExtensions.isEmpty()) {
                                context.getString(R.string.none)
                            } else {
                                selectedExtensions.joinToString { it.name }
                            }
                    }

                    preferences.trustedExtensions().asImmediateFlowIn(viewScope) { isVisible = it.isNotEmpty() }
                }
            }

            preferenceCategory {
                titleRes = R.string.data_saver

                switchPreference {
                    key = PreferenceKeys.saveChaptersAsCBZ
                    titleRes = R.string.save_chapter_as_cbz
                    summaryRes = R.string.save_chapter_as_cbz_summary
                    defaultValue = false
                }

                switchPreference {
                    key = PreferenceKeys.splitTallImages
                    titleRes = R.string.split_tall_images
                    summaryRes = R.string.split_tall_images_summary
                    defaultValue = false
                }

                switchPreference {
                    key = PreferenceKeys.enableDoh
                    titleRes = R.string.enable_doh
                    summaryRes = R.string.enable_doh_summary
                    defaultValue = false

                    onChange { newValue ->
                        activity?.toast(R.string.requires_app_restart)
                        true
                    }
                }
            }

            if (BuildConfig.DEBUG || BuildConfig.BUILD_TYPE == "beta") {
                preferenceCategory {
                    titleRes = R.string.debug

                    preference {
                        key = "debug_info"
                        titleRes = R.string.debug_info

                        onClick {
                            val ctrl = DebugController()
                            router.pushController(ctrl.withFadeTransaction())
                        }
                    }
                }
            }
        }

    @SuppressLint("BatteryLife")
    private fun requestBatteryOptimizationExemption() {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager?
        if (pm?.isIgnoringBatteryOptimizations(context.packageName) == false) {
            val intent =
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = "package:${context.packageName}".toUri()
                }
            requestPermissionsSafe(arrayOf(intent), 0)
        }
    }

    private fun clearCache() {
        if (activity == null) return
        val activity = activity ?: return
        activity.lifecycleScope.launch {
            val deletedFiles = withContext(Dispatchers.IO) { chapterCache.clear() }
            activity.toast(
                activity.resources.getQuantityString(
                    R.plurals.cache_deleted,
                    deletedFiles,
                    deletedFiles,
                ),
            )
            findPreference("clear_cache")?.summary =
                context.getString(R.string.used_cache_size, chapterCache.readableSize)
        }
    }

    private fun clearWebViewData() {
        if (activity == null) return
        try {
            val webview = WebView(activity)
            webview.setNetworkAvailable(false)
            webview.clearCache(true)
            webview.clearFormData()
            webview.clearHistory()
            webview.clearSslPreferences()
            WebStorage.getInstance().deleteAllData()
            activity?.toast(R.string.webview_data_deleted)
            webview.destroy()
        } catch (e: Throwable) {
            activity?.toast(R.string.cache_delete_error)
        }
    }
}
