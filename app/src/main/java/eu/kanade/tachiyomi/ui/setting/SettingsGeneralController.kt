package eu.kanade.tachiyomi.ui.setting

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.asImmediateFlow
import eu.kanade.tachiyomi.data.preference.asImmediateFlowIn
import eu.kanade.tachiyomi.data.updater.AutoUpdaterJob
import eu.kanade.tachiyomi.util.system.appDelegateNightMode
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getPrefTheme
import eu.kanade.tachiyomi.util.system.isInNightMode
import kotlinx.coroutines.flow.launchIn
import kotlin.math.max
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
                R.string.browse
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

        switchPreference {
            key = Keys.hideBottomNavOnScroll
            titleRes = R.string.hide_bottom_nav
            summaryRes = R.string.hides_on_scroll
            defaultValue = true
        }

        intListPreference(activity) {
            key = Keys.sideNavIconAlignment
            titleRes = R.string.side_nav_icon_alignment
            entriesRes = arrayOf(
                R.string.top,
                R.string.center,
                R.string.bottom,
            )
            entryRange = 0..2
            defaultValue = 0
            isVisible = max(context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels) >= 720.dpToPx
        }

        preferenceCategory {
            titleRes = R.string.app_theme

            themePreference = themePreference {
                key = "theme_preference"
                titleRes = R.string.app_theme
                lastScrollPostionLight = lastThemeXLight
                lastScrollPostionDark = lastThemeXDark
                summary = context.getString(context.getPrefTheme(preferences).nameRes)
                activity = this@SettingsGeneralController.activity
            }

            switchPreference {
                key = "night_mode_switch"
                isPersistent = false
                titleRes = R.string.follow_system_theme
                isChecked =
                    preferences.nightMode().get() == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM

                onChange {
                    if (it == true) {
                        preferences.nightMode().set(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                        activity?.recreate()
                    } else {
                        preferences.nightMode().set(context.appDelegateNightMode())
                        themePreference?.fastAdapterLight?.notifyDataSetChanged()
                        themePreference?.fastAdapterDark?.notifyDataSetChanged()
                    }
                    true
                }
                preferences.nightMode().asImmediateFlow { mode ->
                    isChecked = mode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }.launchIn(viewScope)
            }

            switchPreference {
                key = Keys.themeDarkAmoled
                titleRes = R.string.pure_black_dark_mode
                defaultValue = false

                preferences.nightMode().asImmediateFlowIn(viewScope) { mode ->
                    isVisible = mode != AppCompatDelegate.MODE_NIGHT_NO
                }

                onChange {
                    if (context.isInNightMode()) {
                        activity?.recreate()
                    } else {
                        themePreference?.fastAdapterDark?.notifyDataSetChanged()
                    }
                    true
                }
            }
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
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isUpdaterEnabled) {
            preferenceCategory {
                titleRes = R.string.auto_updates

                intListPreference(activity) {
                    key = Keys.shouldAutoUpdate
                    titleRes = R.string.auto_update_app
                    entryRange = 0..2
                    entriesRes = arrayOf(R.string.over_any_network, R.string.over_wifi_only, R.string.dont_auto_update)
                    defaultValue = AutoUpdaterJob.ONLY_ON_UNMETERED
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
