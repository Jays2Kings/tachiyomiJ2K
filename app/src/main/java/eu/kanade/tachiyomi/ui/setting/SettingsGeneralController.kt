package eu.kanade.tachiyomi.ui.setting

import android.content.Intent
import android.content.res.XmlResourceParser
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.updater.AppDownloadInstallJob
import eu.kanade.tachiyomi.util.lang.addBetaTag
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.tachiyomi.util.system.systemLangContext
import java.util.Locale
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsGeneralController : SettingsController() {

    private val isUpdaterEnabled = BuildConfig.INCLUDE_UPDATER

    var lastThemeXLight: Int? = null
    var lastThemeXDark: Int? = null
    var themePreference: ThemePreference? = null

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.general

        intListPreference(activity) {
            key = Keys.startingTab
            titleRes = R.string.starting_screen
            summaryRes = when (preferences.startingTab().get()) {
                -1 -> R.string.library
                -2 -> R.string.recents
                -3 -> R.string.browse
                else -> R.string.last_used_library_recents
            }
            entriesRes = arrayOf(
                R.string.last_used_library_recents,
                R.string.library,
                R.string.recents,
                R.string.browse,
            )
            entryValues = (0 downTo -3).toList()
            defaultValue = 0
            customSelectedValue = when (val value = preferences.startingTab().get()) {
                in -3..-1 -> value
                else -> 0
            }

            onChange { newValue ->
                summaryRes = when (newValue) {
                    0, 1 -> R.string.last_used_library_recents
                    -1 -> R.string.library
                    -2 -> R.string.recents
                    -3 -> R.string.browse
                    else -> R.string.last_used_library_recents
                }
                customSelectedValue = when (newValue) {
                    in -3..-1 -> newValue as Int
                    else -> 0
                }
                true
            }
        }

        switchPreference {
            key = Keys.backToStart
            titleRes = R.string.back_to_start
            summaryRes = R.string.pressing_back_to_start
            defaultValue = true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            preference {
                key = "pref_manage_notifications"
                titleRes = R.string.pref_manage_notifications
                onClick {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    startActivity(intent)
                }
            }
        }
        switchPreference {
            key = Keys.disableNotificationSizeWarning
            titleRes = R.string.disable_notification_size_warning
            defaultValue = false
        }

        preferenceCategory {
            titleRes = R.string.app_shortcuts

            switchPreference {
                key = Keys.showSeriesInShortcuts
                titleRes = R.string.show_recent_series
                summaryRes = R.string.includes_recently_read_updated_added
                defaultValue = true
            }

            switchPreference {
                key = Keys.showSourcesInShortcuts
                titleRes = R.string.show_recent_sources
                defaultValue = true
            }

            switchPreference {
                key = Keys.openChapterInShortcuts
                titleRes = R.string.series_opens_new_chapters
                summaryRes = R.string.no_new_chapters_open_details
                defaultValue = true
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isUpdaterEnabled) {
            preferenceCategory {
                titleRes = R.string.auto_updates

                intListPreference(activity) {
                    key = Keys.shouldAutoUpdate
                    titleRes = R.string.auto_update_app
                    entryRange = 0..2
                    entriesRes = arrayOf(R.string.over_any_network, R.string.over_wifi_only, R.string.dont_auto_update)
                    defaultValue = AppDownloadInstallJob.ONLY_ON_UNMETERED
                }
            }
        }

        preferenceCategory {
            titleRes = R.string.locale
            listPreference(activity) {
                key = Keys.dateFormat
                titleRes = R.string.date_format
                entryValues = listOf("", "MM/dd/yy", "dd/MM/yy", "yyyy-MM-dd")
                entries = entryValues.map { value ->
                    if (value == "") {
                        context.getString(R.string.system_default)
                    } else {
                        value
                    }
                }
                defaultValue = ""
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                listPreference(activity) {
                    key = "language"
                    isPersistent = false
                    title = context.getString(R.string.language).let {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            it.addBetaTag(context)
                        } else {
                            it
                        }
                    }
                    dialogTitleRes = R.string.language
                    val locales = mutableListOf<String>()
                    val availLocales = Locale.getAvailableLocales()
                    resources?.getXml(R.xml.locales_config).use { parser ->
                        parser ?: return@use
                        while (parser.next() != XmlResourceParser.END_DOCUMENT) {
                            if (parser.eventType == XmlResourceParser.START_TAG && parser.name == "locale") {
                                val locale = parser.getAttributeValue(
                                    "http://schemas.android.com/apk/res/android",
                                    "name",
                                ) ?: continue
                                if (availLocales.contains(Locale.forLanguageTag(locale))) {
                                    locales.add(locale)
                                }
                            }
                        }
                    }
                    val localesMap = locales.associateBy { Locale.forLanguageTag(it) }
                        .toSortedMap { locale1, locale2 ->
                            val l1 = locale1.getDisplayName(locale1)
                                .replaceFirstChar { it.uppercase(locale1) }
                            val l2 = locale2.getDisplayName(locale2)
                                .replaceFirstChar { it.uppercase(locale2) }
                            l1.compareToCaseInsensitiveNaturalOrder(l2)
                        }
                    val localArray = localesMap.keys.filterNotNull().toTypedArray()
                    val localeList = LocaleListCompat.create(*localArray)
                    val sysDef = context.systemLangContext.getString(R.string.system_default)
                    entries = listOf(sysDef) + localesMap.keys.map { locale ->
                        locale.getDisplayName(locale).replaceFirstChar { it.uppercase(locale) }
                    }
                    entryValues = listOf("") + localesMap.values
                    defaultValue = ""
                    val locale = AppCompatDelegate.getApplicationLocales()
                        .getFirstMatch(locales.toTypedArray())
                    if (locale != null) {
                        tempValue = localArray.indexOf(
                            if (locales.contains(locale.toLanguageTag())) {
                                locale
                            } else {
                                localeList.getFirstMatch(arrayOf(locale.toLanguageTag()))
                            },
                        ) + 1
                        tempEntry =
                            locale.getDisplayName(locale).replaceFirstChar { it.uppercase(locale) }
                    }

                    onChange {
                        val value = it as String
                        val appLocale: LocaleListCompat = if (value.isBlank()) {
                            preferences.appLanguage().delete()
                            LocaleListCompat.getEmptyLocaleList()
                        } else {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                preferences.appLanguage().set(value)
                            }
                            LocaleListCompat.forLanguageTags(value)
                        }
                        AppCompatDelegate.setApplicationLocales(appLocale)
                        true
                    }
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    infoPreference(R.string.language_requires_app_restart)
                }
            }
        }
    }

    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
        themePreference = null
    }

    override fun onSaveViewState(view: View, outState: Bundle) {
        outState.putInt(::lastThemeXLight.name, themePreference?.lastScrollPostionLight ?: 0)
        outState.putInt(::lastThemeXDark.name, themePreference?.lastScrollPostionDark ?: 0)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreViewState(view: View, savedViewState: Bundle) {
        super.onRestoreViewState(view, savedViewState)
        lastThemeXLight = savedViewState.getInt(::lastThemeXLight.name)
        lastThemeXDark = savedViewState.getInt(::lastThemeXDark.name)
        themePreference?.lastScrollPostionLight = lastThemeXLight
        themePreference?.lastScrollPostionDark = lastThemeXDark
    }
}
