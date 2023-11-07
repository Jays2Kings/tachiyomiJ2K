package eu.kanade.tachiyomi.ui.setting

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.data.preference.asImmediateFlowIn
import eu.kanade.tachiyomi.data.updater.AppDownloadInstallJob
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.ExtensionUpdateJob
import eu.kanade.tachiyomi.extension.util.ExtensionInstaller
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.migration.MigrationController
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import uy.kohesive.injekt.injectLazy

class SettingsBrowseController : SettingsController() {

    val sourceManager: SourceManager by injectLazy()
    var updatedExtNotifPref: SwitchPreferenceCompat? = null

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.browse

        preferenceCategory {
            switchPreference {
                bindTo(preferences.hideInLibraryItems())
                titleRes = R.string.hide_in_library_items
            }
        }

        preferenceCategory {
            titleRes = R.string.extensions
            switchPreference {
                key = PreferenceKeys.automaticExtUpdates
                titleRes = R.string.check_for_extension_updates
                defaultValue = true

                onChange {
                    it as Boolean
                    ExtensionUpdateJob.setupTask(context, it)
                    true
                }
            }
            if (ExtensionManager.canAutoInstallUpdates()) {
                val intPref = intListPreference(activity) {
                    key = PreferenceKeys.autoUpdateExtensions
                    titleRes = R.string.auto_update_extensions
                    entryRange = 0..2
                    entriesRes = arrayOf(
                        R.string.over_any_network,
                        R.string.over_wifi_only,
                        R.string.dont_auto_update,
                    )
                    defaultValue = AppDownloadInstallJob.ONLY_ON_UNMETERED
                }
                val infoPref = if (preferences.extensionInstaller().get() != ExtensionInstaller.SHIZUKU) {
                    infoPreference(R.string.some_extensions_may_not_update)
                } else {
                    null
                }
                val switchPref = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    switchPreference {
                        key = "notify_ext_updated"
                        isPersistent = false
                        titleRes = R.string.notify_extension_updated
                        isChecked = Notifications.isNotificationChannelEnabled(
                            context,
                            Notifications.CHANNEL_EXT_UPDATED,
                        )
                        updatedExtNotifPref = this
                        onChange {
                            false
                        }
                        onClick {
                            val intent =
                                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, BuildConfig.APPLICATION_ID)
                                    putExtra(
                                        Settings.EXTRA_CHANNEL_ID,
                                        Notifications.CHANNEL_EXT_UPDATED,
                                    )
                                }
                            startActivity(intent)
                        }
                    }
                } else {
                    null
                }
                preferences.automaticExtUpdates().asImmediateFlowIn(viewScope) { value ->
                    arrayOf(intPref, infoPref, switchPref).forEach { it?.isVisible = value }
                }
            }
        }

        preferenceCategory {
            titleRes = R.string.pref_global_search
            switchPreference {
                key = PreferenceKeys.onlySearchPinned
                titleRes = R.string.only_search_pinned_when
            }
        }

        preferenceCategory {
            titleRes = R.string.migration
            // Only show this if someone has mass migrated manga once

            preference {
                titleRes = R.string.source_migration
                onClick { router.pushController(MigrationController().withFadeTransaction()) }
            }
            if (preferences.skipPreMigration().get() || preferences.migrationSources()
                .isSet()
            ) {
                switchPreference {
                    key = PreferenceKeys.skipPreMigration
                    titleRes = R.string.skip_pre_migration
                    summaryRes = R.string.use_last_saved_migration_preferences
                    defaultValue = false
                }
            }
            preference {
                key = "match_pinned_sources"
                titleRes = R.string.match_pinned_sources
                summaryRes = R.string.only_enable_pinned_for_migration
                onClick {
                    val ogSources = preferences.migrationSources().get()
                    val pinnedSources =
                        preferences.pinnedCatalogues().get().joinToString("/")
                    preferences.migrationSources().set(pinnedSources)
                    (activity as? MainActivity)?.setUndoSnackBar(
                        view?.snack(
                            R.string.migration_sources_changed,
                        ) {
                            setAction(R.string.undo) {
                                preferences.migrationSources().set(ogSources)
                            }
                        },
                    )
                }
            }

            preference {
                key = "match_enabled_sources"
                titleRes = R.string.match_enabled_sources
                summaryRes = R.string.only_enable_enabled_for_migration
                onClick {
                    val ogSources = preferences.migrationSources().get()
                    val languages = preferences.enabledLanguages().get()
                    val hiddenCatalogues = preferences.hiddenSources().get()
                    val enabledSources =
                        sourceManager.getCatalogueSources().filter { it.lang in languages }
                            .filterNot { it.id.toString() in hiddenCatalogues }
                            .sortedBy { "(${it.lang}) ${it.name}" }
                            .joinToString("/") { it.id.toString() }
                    preferences.migrationSources().set(enabledSources)
                    (activity as? MainActivity)?.setUndoSnackBar(
                        view?.snack(
                            R.string.migration_sources_changed,
                        ) {
                            setAction(R.string.undo) {
                                preferences.migrationSources().set(ogSources)
                            }
                        },
                    )
                }
            }

            infoPreference(R.string.you_can_migrate_in_library)
        }

        preferenceCategory {
            titleRes = R.string.nsfw_sources

            switchPreference {
                key = PreferenceKeys.showNsfwSource
                titleRes = R.string.show_in_sources_and_extensions
                summaryRes = R.string.requires_app_restart
                defaultValue = true
            }
            infoPreference(R.string.does_not_prevent_unofficial_nsfw)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        updatedExtNotifPref?.isChecked = Notifications.isNotificationChannelEnabled(activity, Notifications.CHANNEL_EXT_UPDATED)
    }
}
