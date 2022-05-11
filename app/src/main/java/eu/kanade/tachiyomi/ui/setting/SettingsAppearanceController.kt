package eu.kanade.tachiyomi.ui.setting

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.asImmediateFlow
import eu.kanade.tachiyomi.data.preference.asImmediateFlowIn
import eu.kanade.tachiyomi.util.system.SideNavMode
import eu.kanade.tachiyomi.util.system.appDelegateNightMode
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getPrefTheme
import eu.kanade.tachiyomi.util.system.isInNightMode
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.moveRecyclerViewUp
import kotlinx.coroutines.flow.launchIn
import kotlin.math.max
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsAppearanceController : SettingsController() {

    var lastThemeXLight: Int? = null
    var lastThemeXDark: Int? = null
    var themePreference: ThemePreference? = null

    @SuppressLint("NotifyDataSetChanged")
    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.appearance

        preferenceCategory {
            titleRes = R.string.app_theme

            themePreference = themePreference {
                key = "theme_preference"
                titleRes = R.string.app_theme
                lastScrollPostionLight = lastThemeXLight
                lastScrollPostionDark = lastThemeXDark
                summary = context.getString(context.getPrefTheme(preferences).nameRes)
                activity = this@SettingsAppearanceController.activity
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
            switchPreference {
                bindTo(preferences.useLargeToolbar())
                titleRes = R.string.expanded_toolbar
                summaryRes = R.string.show_larger_toolbar

                onChange {
                    val useLarge = it as Boolean
                    activityBinding?.appBar?.setToolbarModeBy(this@SettingsAppearanceController, !useLarge)
                    activityBinding?.appBar?.hideBigView(!useLarge, !useLarge)
                    activityBinding?.toolbar?.alpha = 1f
                    activityBinding?.toolbar?.translationY = 0f
                    activityBinding?.toolbar?.isVisible = true
                    activityBinding?.appBar?.doOnNextLayout {
                        listView.requestApplyInsets()
                        listView.post {
                            if (useLarge) {
                                moveRecyclerViewUp(true)
                            } else {
                                activityBinding?.appBar?.updateAppBarAfterY(listView)
                            }
                        }
                    }
                    true
                }
            }
        }

        preferenceCategory {
            titleRes = R.string.details_page
            switchPreference {
                key = Keys.themeMangaDetails
                titleRes = R.string.theme_buttons_based_on_cover
                defaultValue = true
            }
        }

        preferenceCategory {
            titleRes = R.string.navigation

            switchPreference {
                key = Keys.hideBottomNavOnScroll
                titleRes = R.string.hide_bottom_nav
                summaryRes = R.string.hides_on_scroll
                defaultValue = true
            }

            intListPreference(activity) {
                key = Keys.sideNavIconAlignment
                titleRes = R.string.side_nav_icon_alignment
                entriesRes = arrayOf(R.string.top, R.string.center, R.string.bottom)
                entryRange = 0..2
                defaultValue = 1
                isVisible = max(
                    context.resources.displayMetrics.widthPixels,
                    context.resources.displayMetrics.heightPixels,
                ) >= 720.dpToPx
            }

            intListPreference(activity) {
                key = Keys.sideNavMode
                titleRes = R.string.use_side_navigation
                val values = SideNavMode.values()
                entriesRes = values.map { it.stringRes }.toTypedArray()
                entryValues = values.map { it.prefValue }
                defaultValue = SideNavMode.DEFAULT.prefValue

                onChange {
                    activity?.recreate()
                    true
                }
            }

            infoPreference(R.string.by_default_side_nav_info)
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
