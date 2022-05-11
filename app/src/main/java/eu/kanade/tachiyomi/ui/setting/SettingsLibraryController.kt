package eu.kanade.tachiyomi.ui.setting

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.preference.DEVICE_BATTERY_NOT_LOW
import eu.kanade.tachiyomi.data.preference.DEVICE_CHARGING
import eu.kanade.tachiyomi.data.preference.DEVICE_ONLY_ON_WIFI
import eu.kanade.tachiyomi.data.preference.DelayedLibrarySuggestionsJob
import eu.kanade.tachiyomi.data.preference.MANGA_HAS_UNREAD
import eu.kanade.tachiyomi.data.preference.MANGA_NON_COMPLETED
import eu.kanade.tachiyomi.data.preference.MANGA_NON_READ
import eu.kanade.tachiyomi.data.preference.asImmediateFlowIn
import eu.kanade.tachiyomi.ui.category.CategoryController
import eu.kanade.tachiyomi.ui.library.LibraryPresenter
import eu.kanade.tachiyomi.ui.library.display.TabbedLibraryDisplaySheet
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsLibraryController : SettingsController() {

    private val db: DatabaseHelper = Injekt.get()

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.library
        preferenceCategory {
            titleRes = R.string.general
            switchPreference {
                key = Keys.removeArticles
                titleRes = R.string.sort_by_ignoring_articles
                summaryRes = R.string.when_sorting_ignore_articles
                defaultValue = false
            }

            switchPreference {
                key = Keys.showLibrarySearchSuggestions
                titleRes = R.string.search_suggestions
                summaryRes = R.string.search_tips_show_periodically

                onChange {
                    it as Boolean
                    if (it) {
                        launchIO {
                            LibraryPresenter.setSearchSuggestion(preferences, db, Injekt.get())
                        }
                    } else {
                        DelayedLibrarySuggestionsJob.setupTask(context, false)
                        preferences.librarySearchSuggestion().set("")
                    }
                    true
                }
            }

            preference {
                key = "library_display_options"
                isPersistent = false
                titleRes = R.string.display_options
                summaryRes = R.string.can_be_found_in_library_filters

                onClick {
                    TabbedLibraryDisplaySheet(this@SettingsLibraryController).show()
                }
            }
        }

        val dbCategories = db.getCategories().executeAsBlocking()

        preferenceCategory {
            titleRes = R.string.categories
            preference {
                key = "edit_categories"
                isPersistent = false
                val catCount = db.getCategories().executeAsBlocking().size
                titleRes = if (catCount > 0) R.string.edit_categories else R.string.add_categories
                if (catCount > 0) summary = context.resources.getQuantityString(R.plurals.category_plural, catCount, catCount)
                onClick { router.pushController(CategoryController().withFadeTransaction()) }
            }
            intListPreference(activity) {
                key = Keys.defaultCategory
                titleRes = R.string.default_category

                val categories = listOf(Category.createDefault(context)) + dbCategories
                entries =
                    listOf(context.getString(R.string.always_ask)) + categories.map { it.name }.toTypedArray()
                entryValues = listOf(-1) + categories.mapNotNull { it.id }.toList()
                defaultValue = "-1"

                val selectedCategory = categories.find { it.id == preferences.defaultCategory() }
                summary =
                    selectedCategory?.name ?: context.getString(R.string.always_ask)
                onChange { newValue ->
                    summary = categories.find {
                        it.id == newValue as Int
                    }?.name ?: context.getString(R.string.always_ask)
                    true
                }
            }
        }

        preferenceCategory {
            titleRes = R.string.global_updates
            intListPreference(activity) {
                key = Keys.libraryUpdateInterval
                titleRes = R.string.library_update_frequency
                entriesRes = arrayOf(
                    R.string.manual,
                    R.string.every_12_hours,
                    R.string.daily,
                    R.string.every_2_days,
                    R.string.every_3_days,
                    R.string.weekly,
                )
                entryValues = listOf(0, 12, 24, 48, 72, 168)
                defaultValue = 24

                onChange { newValue ->
                    // Always cancel the previous task, it seems that sometimes they are not updated.
                    LibraryUpdateJob.setupTask(context, 0)

                    val interval = newValue as Int
                    if (interval > 0) {
                        LibraryUpdateJob.setupTask(context, interval)
                    }
                    true
                }
            }
            multiSelectListPreferenceMat(activity) {
                bindTo(preferences.libraryUpdateDeviceRestriction())
                titleRes = R.string.library_update_restriction
                entriesRes = arrayOf(R.string.wifi, R.string.charging, R.string.battery_not_low)
                entryValues = listOf(DEVICE_ONLY_ON_WIFI, DEVICE_CHARGING, DEVICE_BATTERY_NOT_LOW)
                preSummaryRes = R.string.restrictions_
                noSelectionRes = R.string.none

                preferences.libraryUpdateInterval().asImmediateFlowIn(viewScope) {
                    isVisible = it > 0
                }

                onChange {
                    // Post to event looper to allow the preference to be updated.
                    viewScope.launchUI { LibraryUpdateJob.setupTask(context) }
                    true
                }
            }

            multiSelectListPreferenceMat(activity) {
                bindTo(preferences.libraryUpdateMangaRestriction())
                titleRes = R.string.pref_library_update_manga_restriction
                entriesRes = arrayOf(
                    R.string.pref_update_only_completely_read,
                    R.string.pref_update_only_started,
                    R.string.pref_update_only_non_completed,
                )
                entryValues = listOf(MANGA_HAS_UNREAD, MANGA_NON_READ, MANGA_NON_COMPLETED)
                noSelectionRes = R.string.none
            }

            triStateListPreference(activity) {
                preferences.apply {
                    bindTo(libraryUpdateCategories(), libraryUpdateCategoriesExclude())
                }
                titleRes = R.string.categories

                val categories = listOf(Category.createDefault(context)) + dbCategories
                entries = categories.map { it.name }
                entryValues = categories.map { it.id.toString() }

                allSelectionRes = R.string.all
            }

            switchPreference {
                key = Keys.refreshCoversToo
                titleRes = R.string.auto_refresh_covers
                summaryRes = R.string.auto_refresh_covers_summary
                defaultValue = true
            }
        }
    }
}
