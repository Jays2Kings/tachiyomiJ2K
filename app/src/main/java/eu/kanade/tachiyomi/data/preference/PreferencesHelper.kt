package eu.kanade.tachiyomi.data.preference

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.f2prateek.rx.preferences.Preference
import com.f2prateek.rx.preferences.RxSharedPreferences
import com.tfcporciuncula.flow.FlowSharedPreferences
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.updater.AutoUpdaterJob
import eu.kanade.tachiyomi.extension.model.InstalledExtensionsOrder
import eu.kanade.tachiyomi.ui.library.filter.FilterBottomSheet
import eu.kanade.tachiyomi.ui.reader.settings.OrientationType
import eu.kanade.tachiyomi.ui.reader.settings.PageLayout
import eu.kanade.tachiyomi.ui.reader.settings.ReaderBottomButton
import eu.kanade.tachiyomi.ui.reader.settings.ReadingModeType
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation
import eu.kanade.tachiyomi.ui.recents.RecentMangaAdapter
import eu.kanade.tachiyomi.util.system.Themes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

fun <T> Preference<T>.getOrDefault(): T = get() ?: defaultValue()!!

fun <T> com.tfcporciuncula.flow.Preference<T>.asImmediateFlow(block: (value: T) -> Unit): Flow<T> {
    block(get())
    return asFlow()
        .onEach { block(it) }
}

fun <T> com.tfcporciuncula.flow.Preference<T>.asImmediateFlowIn(scope: CoroutineScope, block: (value: T) -> Unit): Job {
    block(get())
    return asFlow()
        .onEach { block(it) }
        .launchIn(scope)
}

fun com.tfcporciuncula.flow.Preference<Boolean>.toggle() = set(!get())

private class DateFormatConverter : Preference.Adapter<DateFormat> {
    override fun get(key: String, preferences: SharedPreferences): DateFormat {
        val dateFormat = preferences.getString(Keys.dateFormat, "")!!

        if (dateFormat != "") {
            return SimpleDateFormat(dateFormat, Locale.getDefault())
        }

        return DateFormat.getDateInstance(DateFormat.SHORT)
    }

    override fun set(key: String, value: DateFormat, editor: SharedPreferences.Editor) {
        // No-op
    }
}

operator fun <T> com.tfcporciuncula.flow.Preference<Set<T>>.plusAssign(item: T) {
    set(get() + item)
}

operator fun <T> com.tfcporciuncula.flow.Preference<Set<T>>.minusAssign(item: T) {
    set(get() - item)
}

operator fun <T> com.tfcporciuncula.flow.Preference<Set<T>>.plusAssign(item: Collection<T>) {
    set(get() + item)
}

operator fun <T> com.tfcporciuncula.flow.Preference<Set<T>>.minusAssign(item: Collection<T>) {
    set(get() - item)
}

class PreferencesHelper(val context: Context) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val rxPrefs = RxSharedPreferences.create(prefs)
    private val flowPrefs = FlowSharedPreferences(prefs)

    private val defaultDownloadsDir = Uri.fromFile(
        File(
            Environment.getExternalStorageDirectory().absolutePath + File.separator +
                context.getString(R.string.app_name),
            "downloads"
        )
    )

    private val defaultBackupDir = Uri.fromFile(
        File(
            Environment.getExternalStorageDirectory().absolutePath + File.separator +
                context.getString(R.string.app_name),
            "backup"
        )
    )

    fun getInt(key: String, default: Int?) = rxPrefs.getInteger(key, default)
    fun getStringPref(key: String, default: String?) = rxPrefs.getString(key, default)
    fun getStringSet(key: String, default: Set<String>) = rxPrefs.getStringSet(key, default)

    fun startingTab() = flowPrefs.getInt(Keys.startingTab, 0)
    fun backReturnsToStart() = flowPrefs.getBoolean(Keys.backToStart, true)

    fun hasDeniedA11FilePermission() = flowPrefs.getBoolean(Keys.deniedA11FilePermission, false)

    fun clear() = prefs.edit().clear().apply()

    fun oldTheme() = flowPrefs.getInt(Keys.theme, 5)

    fun nightMode() = flowPrefs.getInt(Keys.nightMode, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    fun themeDarkAmoled() = flowPrefs.getBoolean(Keys.themeDarkAmoled, false)

    val isOnA12 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    fun lightTheme() = flowPrefs.getEnum(Keys.lightTheme, if (isOnA12) Themes.MONET else Themes.DEFAULT)
    fun darkTheme() = flowPrefs.getEnum(Keys.darkTheme, if (isOnA12) Themes.MONET else Themes.DEFAULT)

    fun pageTransitions() = flowPrefs.getBoolean(Keys.enableTransitions, true)

    fun pagerCutoutBehavior() = flowPrefs.getInt(Keys.pagerCutoutBehavior, 0)

    fun doubleTapAnimSpeed() = flowPrefs.getInt(Keys.doubleTapAnimationSpeed, 500)

    fun showPageNumber() = flowPrefs.getBoolean(Keys.showPageNumber, true)

    fun trueColor() = flowPrefs.getBoolean(Keys.trueColor, false)

    fun fullscreen() = flowPrefs.getBoolean(Keys.fullscreen, true)

    fun keepScreenOn() = flowPrefs.getBoolean(Keys.keepScreenOn, true)

    fun customBrightness() = flowPrefs.getBoolean(Keys.customBrightness, false)

    fun customBrightnessValue() = flowPrefs.getInt(Keys.customBrightnessValue, 0)

    fun colorFilter() = flowPrefs.getBoolean(Keys.colorFilter, false)

    fun colorFilterValue() = flowPrefs.getInt(Keys.colorFilterValue, 0)

    fun colorFilterMode() = flowPrefs.getInt(Keys.colorFilterMode, 0)

    fun defaultReadingMode() = prefs.getInt(Keys.defaultReadingMode, ReadingModeType.RIGHT_TO_LEFT.flagValue)

    fun defaultOrientationType() = flowPrefs.getInt(Keys.defaultOrientationType, OrientationType.FREE.flagValue)

    fun imageScaleType() = flowPrefs.getInt(Keys.imageScaleType, 1)

    fun zoomStart() = flowPrefs.getInt(Keys.zoomStart, 1)

    fun readerTheme() = flowPrefs.getInt(Keys.readerTheme, 2)

    fun cropBorders() = flowPrefs.getBoolean(Keys.cropBorders, false)

    fun cropBordersWebtoon() = flowPrefs.getBoolean(Keys.cropBordersWebtoon, false)

    fun webtoonSidePadding() = flowPrefs.getInt(Keys.webtoonSidePadding, 0)

    fun webtoonEnableZoomOut() = flowPrefs.getBoolean(Keys.webtoonEnableZoomOut, false)

    fun readWithTapping() = flowPrefs.getBoolean(Keys.readWithTapping, true)

    fun readWithLongTap() = flowPrefs.getBoolean(Keys.readWithLongTap, true)

    fun readWithVolumeKeys() = flowPrefs.getBoolean(Keys.readWithVolumeKeys, false)

    fun readWithVolumeKeysInverted() = flowPrefs.getBoolean(Keys.readWithVolumeKeysInverted, false)

    fun navigationModePager() = flowPrefs.getInt(Keys.navigationModePager, 0)

    fun navigationModeWebtoon() = flowPrefs.getInt(Keys.navigationModeWebtoon, 0)

    fun pagerNavInverted() = flowPrefs.getEnum(Keys.pagerNavInverted, ViewerNavigation.TappingInvertMode.NONE)

    fun webtoonNavInverted() = flowPrefs.getEnum(Keys.webtoonNavInverted, ViewerNavigation.TappingInvertMode.NONE)

    fun pageLayout() = flowPrefs.getInt(Keys.pageLayout, PageLayout.AUTOMATIC.value)

    fun automaticSplitsPage() = flowPrefs.getBoolean(Keys.automaticSplitsPage, false)

    fun invertDoublePages() = flowPrefs.getBoolean(Keys.invertDoublePages, false)

    fun readerBottomButtons() = flowPrefs.getStringSet(
        Keys.readerBottomButtons,
        ReaderBottomButton.BUTTONS_DEFAULTS
    )

    fun showNavigationOverlayNewUser() = flowPrefs.getBoolean(Keys.showNavigationOverlayNewUser, true)

    fun showNavigationOverlayNewUserWebtoon() = flowPrefs.getBoolean(Keys.showNavigationOverlayNewUserWebtoon, true)

    fun preloadSize() = flowPrefs.getInt(Keys.preloadSize, 6)

    fun updateOnlyNonCompleted() = prefs.getBoolean(Keys.updateOnlyNonCompleted, false)

    fun autoUpdateTrack() = prefs.getBoolean(Keys.autoUpdateTrack, true)

    fun autoAddTrack() = prefs.getBoolean(Keys.autoAddTrack, true)

    fun trackingsToAddOnline() = flowPrefs.getStringSet(Keys.trackingsToAddOnline, emptySet())

    fun lastUsedCatalogueSource() = flowPrefs.getLong(Keys.lastUsedCatalogueSource, -1)

    fun lastUsedCategory() = rxPrefs.getInteger(Keys.lastUsedCategory, 0)

    fun lastUsedSources() = flowPrefs.getStringSet("last_used_sources", emptySet())

    fun lastVersionCode() = rxPrefs.getInteger("last_version_code", 0)

    fun browseAsList() = flowPrefs.getBoolean(Keys.catalogueAsList, false)

    fun enabledLanguages() = flowPrefs.getStringSet(Keys.enabledLanguages, setOf("en", Locale.getDefault().language))

    fun sourceSorting() = rxPrefs.getInteger(Keys.sourcesSort, 0)

    fun trackUsername(sync: TrackService) = prefs.getString(Keys.trackUsername(sync.id), "")

    fun trackPassword(sync: TrackService) = prefs.getString(Keys.trackPassword(sync.id), "")

    fun setTrackCredentials(sync: TrackService, username: String, password: String) {
        prefs.edit()
            .putString(Keys.trackUsername(sync.id), username)
            .putString(Keys.trackPassword(sync.id), password)
            .apply()
    }

    fun trackToken(sync: TrackService) = flowPrefs.getString(Keys.trackToken(sync.id), "")

    fun anilistScoreType() = rxPrefs.getString("anilist_score_type", "POINT_10")

    fun backupsDirectory() = flowPrefs.getString(Keys.backupDirectory, defaultBackupDir.toString())

    fun dateFormat(format: String = flowPrefs.getString(Keys.dateFormat, "").get()): DateFormat = when (format) {
        "" -> DateFormat.getDateInstance(DateFormat.SHORT)
        else -> SimpleDateFormat(format, Locale.getDefault())
    }

    fun downloadsDirectory() = rxPrefs.getString(Keys.downloadsDirectory, defaultDownloadsDir.toString())

    fun downloadOnlyOverWifi() = prefs.getBoolean(Keys.downloadOnlyOverWifi, true)

    fun folderPerManga() = prefs.getBoolean(Keys.folderPerManga, false)

    fun librarySearchSuggestion() = flowPrefs.getString(Keys.librarySearchSuggestion, "")

    fun showLibrarySearchSuggestions() = flowPrefs.getBoolean(Keys.showLibrarySearchSuggestions, false)

    fun lastLibrarySuggestion() = flowPrefs.getLong("last_library_suggestion", 0L)

    fun numberOfBackups() = flowPrefs.getInt(Keys.numberOfBackups, 1)

    fun backupInterval() = flowPrefs.getInt(Keys.backupInterval, 0)

    fun removeAfterReadSlots() = prefs.getInt(Keys.removeAfterReadSlots, -1)

    fun removeAfterMarkedAsRead() = prefs.getBoolean(Keys.removeAfterMarkedAsRead, false)

    fun libraryUpdateInterval() = rxPrefs.getInteger(Keys.libraryUpdateInterval, 24)

    fun libraryUpdateRestriction() = prefs.getStringSet(Keys.libraryUpdateRestriction, emptySet())

    fun libraryUpdateCategories() = flowPrefs.getStringSet(Keys.libraryUpdateCategories, emptySet())
    fun libraryUpdateCategoriesExclude() = flowPrefs.getStringSet(Keys.libraryUpdateCategoriesExclude, emptySet())

    fun libraryUpdatePrioritization() = rxPrefs.getInteger(Keys.libraryUpdatePrioritization, 0)

    fun libraryLayout() = flowPrefs.getInt(Keys.libraryLayout, 2)

    fun gridSize() = flowPrefs.getFloat(Keys.gridSize, 1f)

    fun uniformGrid() = flowPrefs.getBoolean(Keys.uniformGrid, true)

    fun downloadBadge() = rxPrefs.getBoolean(Keys.downloadBadge, false)

    fun filterDownloaded() = rxPrefs.getInteger(Keys.filterDownloaded, 0)

    fun filterUnread() = rxPrefs.getInteger(Keys.filterUnread, 0)

    fun filterCompleted() = rxPrefs.getInteger(Keys.filterCompleted, 0)

    fun filterTracked() = rxPrefs.getInteger(Keys.filterTracked, 0)

    fun filterMangaType() = rxPrefs.getInteger(Keys.filterMangaType, 0)

    fun showEmptyCategoriesWhileFiltering() = flowPrefs.getBoolean(Keys.showEmptyCategoriesFiltering, false)

    fun librarySortingMode() = rxPrefs.getInteger(Keys.librarySortingMode, 0)

    fun librarySortingAscending() = rxPrefs.getBoolean("library_sorting_ascending", true)

    fun automaticExtUpdates() = flowPrefs.getBoolean(Keys.automaticExtUpdates, true)

    fun installedExtensionsOrder() = flowPrefs.getInt(Keys.installedExtensionsOrder, InstalledExtensionsOrder.Name.value)

    fun collapsedCategories() = rxPrefs.getStringSet("collapsed_categories", mutableSetOf())

    fun collapsedDynamicCategories() = flowPrefs.getStringSet("collapsed_dynamic_categories", mutableSetOf())

    fun collapsedDynamicAtBottom() = flowPrefs.getBoolean("collapsed_dynamic_at_bottom", false)

    fun hiddenSources() = flowPrefs.getStringSet("hidden_catalogues", mutableSetOf())

    fun pinnedCatalogues() = flowPrefs.getStringSet("pinned_catalogues", mutableSetOf())

    fun downloadNew() = flowPrefs.getBoolean(Keys.downloadNew, false)

    fun downloadNewCategories() = flowPrefs.getStringSet(Keys.downloadNewCategories, emptySet())
    fun downloadNewCategoriesExclude() = flowPrefs.getStringSet(Keys.downloadNewCategoriesExclude, emptySet())

    fun defaultCategory() = prefs.getInt(Keys.defaultCategory, -1)

    fun skipRead() = prefs.getBoolean(Keys.skipRead, false)

    fun skipFiltered() = prefs.getBoolean(Keys.skipFiltered, true)

    fun useBiometrics() = rxPrefs.getBoolean(Keys.useBiometrics, false)

    fun lockAfter() = rxPrefs.getInteger(Keys.lockAfter, 0)

    fun lastUnlock() = rxPrefs.getLong(Keys.lastUnlock, 0)

    fun secureScreen() = rxPrefs.getBoolean(Keys.secureScreen, false)

    fun hideNotificationContent() = prefs.getBoolean(Keys.hideNotificationContent, false)

    fun removeArticles() = rxPrefs.getBoolean(Keys.removeArticles, false)

    fun migrateFlags() = rxPrefs.getInteger("migrate_flags", Int.MAX_VALUE)

    fun trustedSignatures() = rxPrefs.getStringSet("trusted_signatures", emptySet())

    // using string instead of set so it is ordered
    fun migrationSources() = flowPrefs.getString("migrate_sources", "")

    fun useSourceWithMost() = rxPrefs.getBoolean("use_source_with_most", false)

    fun skipPreMigration() = rxPrefs.getBoolean(Keys.skipPreMigration, false)

    fun defaultMangaOrder() = rxPrefs.getString("default_manga_order", "")

    fun refreshCoversToo() = rxPrefs.getBoolean(Keys.refreshCoversToo, true)

    fun updateOnRefresh() = rxPrefs.getInteger(Keys.updateOnRefresh, -1)

    fun extensionUpdatesCount() = rxPrefs.getInteger("ext_updates_count", 0)

    fun recentsViewType() = flowPrefs.getInt("recents_view_type", 0)

    fun showRecentsDownloads() = flowPrefs.getEnum(Keys.showDLsInRecents, RecentMangaAdapter.ShowRecentsDLs.All)

    fun showRecentsRemHistory() = flowPrefs.getBoolean(Keys.showRemHistoryInRecents, true)

    fun showReadInAllRecents() = flowPrefs.getBoolean(Keys.showReadInAllRecents, false)

    fun showUpdatedTime() = flowPrefs.getBoolean(Keys.showUpdatedTime, false)

    fun groupChaptersUpdates() = flowPrefs.getBoolean(Keys.groupChaptersUpdates, false)

    fun groupChaptersHistory() = flowPrefs.getBoolean(Keys.groupChaptersHistory, true)

    fun showTitleFirstInRecents() = flowPrefs.getBoolean(Keys.showTitleFirstInRecents, false)

    fun lastExtCheck() = rxPrefs.getLong("last_ext_check", 0)

    fun lastAppCheck() = flowPrefs.getLong("last_app_check", 0)

    fun unreadBadgeType() = flowPrefs.getInt("unread_badge_type", 2)

    fun categoryNumberOfItems() = flowPrefs.getBoolean(Keys.categoryNumberOfItems, false)

    fun hideStartReadingButton() = flowPrefs.getBoolean("hide_reading_button", false)

    fun alwaysShowChapterTransition() = flowPrefs.getBoolean(Keys.alwaysShowChapterTransition, true)

    fun deleteRemovedChapters() = flowPrefs.getInt(Keys.deleteRemovedChapters, 0)

    fun showAllCategories() = flowPrefs.getBoolean("show_all_categories", true)

    fun hopperGravity() = flowPrefs.getInt("hopper_gravity", 1)

    fun filterOrder() = flowPrefs.getString("filter_order", FilterBottomSheet.Filters.DEFAULT_ORDER)

    fun hopperLongPressAction() = flowPrefs.getInt(Keys.hopperLongPress, 0)

    fun hideHopper() = flowPrefs.getBoolean("hide_hopper", false)

    fun autohideHopper() = flowPrefs.getBoolean(Keys.autoHideHopper, true)

    fun groupLibraryBy() = flowPrefs.getInt("group_library_by", 0)

    fun showCategoryInTitle() = flowPrefs.getBoolean("category_in_title", false)

    fun onlySearchPinned() = flowPrefs.getBoolean(Keys.onlySearchPinned, false)

    fun showLibraryUpdateErrors() = prefs.getBoolean(Keys.showLibraryUpdateErrors, false)

    // Tutorial preferences
    fun shownFilterTutorial() = flowPrefs.getBoolean("shown_filter_tutorial", false)

    fun shownChapterSwipeTutorial() = flowPrefs.getBoolean("shown_swipe_tutorial", false)

    fun shownDownloadQueueTutorial() = flowPrefs.getBoolean("shown_download_queue", false)

    fun shownLongPressCategoryTutorial() = flowPrefs.getBoolean("shown_long_press_category", false)

    fun shownHopperSwipeTutorial() = flowPrefs.getBoolean("shown_hopper_swipe", false)

    fun hideBottomNavOnScroll() = flowPrefs.getBoolean(Keys.hideBottomNavOnScroll, true)

    fun sideNavIconAlignment() = flowPrefs.getInt(Keys.sideNavIconAlignment, 1)

    fun showNsfwSource() = flowPrefs.getBoolean(Keys.showNsfwSource, true)
    fun showNsfwExtension() = flowPrefs.getBoolean(Keys.showNsfwExtension, true)
    fun labelNsfwExtension() = prefs.getBoolean(Keys.labelNsfwExtension, true)

    fun dohProvider() = prefs.getInt(Keys.dohProvider, -1)

    fun showSeriesInShortcuts() = prefs.getBoolean(Keys.showSeriesInShortcuts, true)
    fun showSourcesInShortcuts() = prefs.getBoolean(Keys.showSourcesInShortcuts, true)
    fun openChapterInShortcuts() = prefs.getBoolean(Keys.openChapterInShortcuts, true)

    fun incognitoMode() = flowPrefs.getBoolean(Keys.incognitoMode, false)

    fun hasPromptedBeforeUpdateAll() = flowPrefs.getBoolean("has_prompted_update_all", false)

    fun shouldAutoUpdate() = prefs.getInt(Keys.shouldAutoUpdate, AutoUpdaterJob.ONLY_ON_UNMETERED)

    fun autoUpdateExtensions() = prefs.getInt(Keys.autoUpdateExtensions, AutoUpdaterJob.ONLY_ON_UNMETERED)

    fun filterChapterByRead() = flowPrefs.getInt(Keys.defaultChapterFilterByRead, Manga.SHOW_ALL)

    fun filterChapterByDownloaded() = flowPrefs.getInt(Keys.defaultChapterFilterByDownloaded, Manga.SHOW_ALL)

    fun filterChapterByBookmarked() = flowPrefs.getInt(Keys.defaultChapterFilterByBookmarked, Manga.SHOW_ALL)

    fun sortChapterOrder() = flowPrefs.getInt(Keys.defaultChapterSortBySourceOrNumber, Manga.CHAPTER_SORTING_SOURCE)

    fun hideChapterTitlesByDefault() = flowPrefs.getBoolean(Keys.hideChapterTitles, false)

    fun chaptersDescAsDefault() = flowPrefs.getBoolean(Keys.chaptersDescAsDefault, true)

    fun sortChapterByAscendingOrDescending() = prefs.getInt(Keys.defaultChapterSortByAscendingOrDescending, Manga.CHAPTER_SORT_DESC)
}
