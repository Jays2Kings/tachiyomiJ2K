package eu.kanade.tachiyomi.ui.setting

import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import androidx.core.content.getSystemService
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.data.preference.asImmediateFlow
import eu.kanade.tachiyomi.data.preference.asImmediateFlowIn
import eu.kanade.tachiyomi.ui.reader.settings.OrientationType
import eu.kanade.tachiyomi.ui.reader.settings.PageLayout
import eu.kanade.tachiyomi.ui.reader.settings.ReaderBackgroundColor
import eu.kanade.tachiyomi.ui.reader.settings.ReaderBottomButton
import eu.kanade.tachiyomi.ui.reader.settings.ReadingModeType
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation
import eu.kanade.tachiyomi.util.lang.addBetaTag
import eu.kanade.tachiyomi.util.system.isTablet
import eu.kanade.tachiyomi.util.view.activityBinding
import kotlinx.coroutines.flow.launchIn
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsReaderController : SettingsController() {
    override fun setupPreferenceScreen(screen: PreferenceScreen) =
        screen.apply {
            titleRes = R.string.reader

            preferenceCategory {
                titleRes = R.string.general
                intListPreference(activity) {
                    key = Keys.defaultReadingMode
                    titleRes = R.string.default_reading_mode
                    entriesRes =
                        ReadingModeType.entries
                            .drop(1)
                            .map { value -> value.stringRes }
                            .toTypedArray()
                    entryValues =
                        ReadingModeType.entries
                            .drop(1)
                            .map { value -> value.flagValue }
                    defaultValue = 2
                }
                intListPreference(activity) {
                    key = Keys.doubleTapAnimationSpeed
                    titleRes = R.string.double_tap_anim_speed
                    entries =
                        listOf(
                            context.getString(R.string.no_animation),
                            context.getString(
                                R.string.fast,
                            ),
                            context.getString(R.string.normal),
                        )
                    entryValues = listOf(1, 250, 500) // using a value of 0 breaks the image viewer, so
                    // min is 1
                    defaultValue = 500
                }
                switchPreference {
                    key = Keys.enableTransitions
                    titleRes = R.string.animate_page_transitions
                    defaultValue = true
                }
                multiSelectListPreferenceMat(activity) {
                    key = Keys.readerBottomButtons
                    titleRes = R.string.display_buttons_bottom_reader
                    val enumConstants = ReaderBottomButton.entries
                    entriesRes = ReaderBottomButton.entries.map { it.stringRes }.toTypedArray()
                    entryValues = enumConstants.map { it.value }
                    allSelectionRes = R.string.display_options
                    allIsAlwaysSelected = true
                    showAllLast = true
                    val defaults = ReaderBottomButton.BUTTONS_DEFAULTS.toMutableList()
                    if (context.isTablet()) {
                        defaults.add(ReaderBottomButton.ShiftDoublePage.value)
                    }
                    defaultValue = defaults
                }
                infoPreference(R.string.certain_buttons_can_be_found)
            }

            preferenceCategory {
                titleRes = R.string.display

                intListPreference(activity) {
                    key = Keys.defaultOrientationType
                    titleRes = R.string.default_orientation
                    val enumConstants = OrientationType.entries.drop(1)
                    entriesRes = enumConstants.map { it.stringRes }.toTypedArray()
                    entryValues = enumConstants.map { value -> value.flagValue }
                    defaultValue = OrientationType.FREE.flagValue
                }
                intListPreference(activity) {
                    key = Keys.readerTheme
                    titleRes = R.string.background_color
                    val enumConstants = ReaderBackgroundColor.entries
                    entriesRes = enumConstants.map { it.longStringRes ?: it.stringRes }.toTypedArray()
                    entryValues = enumConstants.map { it.prefValue }
                    defaultValue = ReaderBackgroundColor.SMART_PAGE.prefValue
                }
                switchPreference {
                    key = Keys.fullscreen
                    titleRes = R.string.fullscreen
                    defaultValue = true
                }
                switchPreference {
                    key = Keys.keepScreenOn
                    titleRes = R.string.keep_screen_on
                    defaultValue = true
                }
                switchPreference {
                    key = Keys.showPageNumber
                    titleRes = R.string.show_page_number
                    defaultValue = true
                }
                intListPreference(activity) {
                    bindTo(preferences.landscapeCutoutBehavior())
                    title = "${context.getString(R.string.cutout_area_behavior)} (${context.getString(R.string.landscape)})"
                    entriesRes =
                        arrayOf(
                            R.string.pad_cutout_areas,
                            R.string.ignore_cutout_areas,
                        )
                    entryRange = 0..1
                    defaultValue = 0
                    isVisible =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            activity
                                ?.getSystemService<DisplayManager>()
                                ?.getDisplay(Display.DEFAULT_DISPLAY)
                                ?.cutout != null
                        } else {
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                        }
                }
            }

            preferenceCategory {
                titleRes = R.string.reading

                switchPreference {
                    key = Keys.skipRead
                    titleRes = R.string.skip_read_chapters
                    defaultValue = false
                }
                switchPreference {
                    key = Keys.skipFiltered
                    titleRes = R.string.skip_filtered_chapters
                    defaultValue = true
                }
                switchPreference {
                    bindTo(preferences.skipDupe())
                    titleRes = R.string.skip_dupe_chapters
                }
                switchPreference {
                    key = Keys.alwaysShowChapterTransition
                    titleRes = R.string.always_show_chapter_transition
                    summaryRes = R.string.if_disabled_transition_will_skip
                    defaultValue = true
                }
            }

            preferenceCategory {
                titleRes = R.string.paged

                intListPreference(activity) {
                    key = Keys.navigationModePager
                    titleRes = R.string.tap_zones
                    entries =
                        context.resources
                            .getStringArray(R.array.reader_nav)
                            .also { values ->
                                entryRange = 0..values.size
                            }.toList()
                    defaultValue = "0"
                }
                listPreference(activity) {
                    key = Keys.pagerNavInverted
                    titleRes = R.string.invert_tapping
                    entriesRes =
                        arrayOf(
                            R.string.none,
                            R.string.horizontally,
                            R.string.vertically,
                            R.string.both_axes,
                        )
                    entryValues =
                        listOf(
                            ViewerNavigation.TappingInvertMode.NONE.name,
                            ViewerNavigation.TappingInvertMode.HORIZONTAL.name,
                            ViewerNavigation.TappingInvertMode.VERTICAL.name,
                            ViewerNavigation.TappingInvertMode.BOTH.name,
                        )
                    defaultValue = ViewerNavigation.TappingInvertMode.NONE.name
                }

                intListPreference(activity) {
                    key = Keys.imageScaleType
                    titleRes = R.string.scale_type
                    entriesRes =
                        arrayOf(
                            R.string.fit_screen,
                            R.string.stretch,
                            R.string.fit_width,
                            R.string.fit_height,
                            R.string.original_size,
                            R.string.smart_fit,
                        )
                    entryRange = 1..6
                    defaultValue = 1
                }

                intListPreference(activity) {
                    key = Keys.pagerCutoutBehavior
                    titleRes = R.string.cutout_area_behavior
                    entriesRes =
                        arrayOf(
                            R.string.pad_cutout_areas,
                            R.string.start_past_cutout,
                            R.string.ignore_cutout_areas,
                        )
                    summaryRes = R.string.cutout_behavior_only_applies
                    entryRange = 0..2
                    defaultValue = 0
                    // Calling this once to show only on cutout
                    isVisible =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            activityBinding
                                ?.root
                                ?.rootWindowInsets
                                ?.displayCutout
                                ?.safeInsetTop != null ||
                                activityBinding
                                    ?.root
                                    ?.rootWindowInsets
                                    ?.displayCutout
                                    ?.safeInsetBottom != null
                        } else {
                            false
                        }
                    // Calling this a second time in case activity is recreated while on this page
                    // Keep the first so it shouldn't animate hiding the preference for phones without
                    // cutouts
                    activityBinding?.root?.post {
                        isVisible =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                activityBinding
                                    ?.root
                                    ?.rootWindowInsets
                                    ?.displayCutout
                                    ?.safeInsetTop != null ||
                                    activityBinding
                                        ?.root
                                        ?.rootWindowInsets
                                        ?.displayCutout
                                        ?.safeInsetBottom != null
                            } else {
                                false
                            }
                    }
                }
                switchPreference {
                    bindTo(preferences.landscapeZoom())
                    titleRes = R.string.zoom_double_page_spreads
                    visibleIf(preferences.imageScaleType()) { it == 1 }
                }
                intListPreference(activity) {
                    key = Keys.zoomStart
                    titleRes = R.string.zoom_start_position
                    entriesRes =
                        arrayOf(
                            R.string.automatic,
                            R.string.left,
                            R.string.right,
                            R.string.center,
                        )
                    entryRange = 1..4
                    defaultValue = 1
                }
                switchPreference {
                    key = Keys.cropBorders
                    titleRes = R.string.crop_borders
                    defaultValue = false
                }
                switchPreference {
                    bindTo(preferences.navigateToPan())
                    titleRes = R.string.navigate_pan
                }
                intListPreference(activity) {
                    key = Keys.pageLayout
                    title = context.getString(R.string.page_layout).addBetaTag(context)
                    dialogTitleRes = R.string.page_layout
                    val enumConstants = PageLayout.entries
                    entriesRes = enumConstants.map { it.fullStringRes }.toTypedArray()
                    entryValues = enumConstants.map { it.value }
                    defaultValue = PageLayout.AUTOMATIC.value
                }
                infoPreference(R.string.automatic_can_still_switch).apply {
                    preferences.pageLayout().asImmediateFlowIn(viewScope) { isVisible = it == PageLayout.AUTOMATIC.value }
                }
                switchPreference {
                    key = Keys.automaticSplitsPage
                    titleRes = R.string.split_double_pages_portrait
                    defaultValue = false
                    preferences.pageLayout().asImmediateFlowIn(viewScope) { isVisible = it == PageLayout.AUTOMATIC.value }
                }
                switchPreference {
                    key = Keys.invertDoublePages
                    titleRes = R.string.invert_double_pages
                    defaultValue = false
                    preferences.pageLayout().asImmediateFlowIn(viewScope) { isVisible = it != PageLayout.SINGLE_PAGE.value }
                }
            }
            preferenceCategory {
                titleRes = R.string.webtoon

                intListPreference(activity) {
                    key = Keys.navigationModeWebtoon
                    titleRes = R.string.tap_zones
                    entries =
                        context.resources
                            .getStringArray(R.array.reader_nav)
                            .also { values ->
                                entryRange = 0..values.size
                            }.toList()
                    defaultValue = "0"
                }
                listPreference(activity) {
                    key = Keys.webtoonNavInverted
                    titleRes = R.string.invert_tapping
                    entriesRes =
                        arrayOf(
                            R.string.none,
                            R.string.horizontally,
                            R.string.vertically,
                            R.string.both_axes,
                        )
                    entryValues =
                        listOf(
                            ViewerNavigation.TappingInvertMode.NONE.name,
                            ViewerNavigation.TappingInvertMode.HORIZONTAL.name,
                            ViewerNavigation.TappingInvertMode.VERTICAL.name,
                            ViewerNavigation.TappingInvertMode.BOTH.name,
                        )
                    defaultValue = ViewerNavigation.TappingInvertMode.NONE.name
                }
                listPreference(activity) {
                    bindTo(preferences.webtoonReaderHideThreshold())
                    titleRes = R.string.pref_hide_threshold
                    val enumValues = PreferenceValues.ReaderHideThreshold.entries
                    entriesRes = enumValues.map { it.titleResId }.toTypedArray()
                    entryValues = enumValues.map { it.name }
                }
                switchPreference {
                    key = Keys.cropBordersWebtoon
                    titleRes = R.string.crop_borders
                    defaultValue = false
                }

                sliderPreference {
                    bindTo(preferences.webtoonSidePadding())
                    titleRes = R.string.pref_webtoon_side_padding
                    entryValues = (0..25 step 5).toList()
                    valueFormatter = { if (it == 0) context.getString(R.string.none) else "$it%" }
                }

                intListPreference(activity) {
                    key = Keys.webtoonPageLayout
                    title = context.getString(R.string.page_layout)
                    dialogTitleRes = R.string.page_layout
                    val enumConstants = arrayOf(PageLayout.SINGLE_PAGE, PageLayout.SPLIT_PAGES)
                    entriesRes = enumConstants.map { it.fullStringRes }.toTypedArray()
                    entryValues = enumConstants.map { it.webtoonValue }
                    defaultValue = PageLayout.SINGLE_PAGE.value
                }

                switchPreference {
                    key = Keys.webtoonInvertDoublePages
                    titleRes = R.string.invert_double_pages
                    defaultValue = false
                }

                switchPreference {
                    key = Keys.webtoonEnableZoomOut
                    titleRes = R.string.enable_zoom_out
                    defaultValue = false
                }

                intListPreference(activity) {
                    key = Keys.webtoonDoubleTapZoom
                    titleRes = R.string.double_tap_zoom
                    entriesRes =
                        arrayOf(
                            R.string.double_tap_zoom_in,
                            R.string.double_tap_fit_to_screen,
                        )
                    entryValues = listOf(0, 1)
                    defaultValue = "0"
                }
            }
            preferenceCategory {
                titleRes = R.string.navigation

                switchPreference {
                    key = Keys.readWithVolumeKeys
                    titleRes = R.string.volume_keys
                    defaultValue = false
                }
                switchPreference {
                    key = Keys.readWithVolumeKeysInverted
                    titleRes = R.string.invert_volume_keys
                    defaultValue = false

                    preferences.readWithVolumeKeys().asImmediateFlow { isVisible = it }.launchIn(viewScope)
                }
                multiSelectListPreferenceMat(activity) {
                    key = Keys.readerVerticalSeekbarModes
                    titleRes = R.string.vertical_seekbar
                    val modes = ReadingModeType.entries.filter { it != ReadingModeType.DEFAULT }
                    entriesRes = modes.map { it.stringRes }.toTypedArray()
                    entryValues = modes.map { it.prefValue.toString() }
                    defaultValue = preferences.readerVerticalSeekbarModes().defaultValue.toList()
                    noSelectionRes = R.string.none
                }
                sliderPreference {
                    bindTo(preferences.readerVerticalSeekbarHeightPercent())
                    titleRes = R.string.vertical_seekbar_height
                    entryValues = (60..100 step 5).toList()
                    valueFormatter = { "$it%" }
                    preferences.readerVerticalSeekbarModes().asImmediateFlowIn(viewScope) { isVisible = it.isNotEmpty() }
                }
                twoButtonPreference {
                    bindTo(preferences.readerVerticalSeekbarDockLeft())
                    inverted = true
                    forceDirection = true
                    titleRes = R.string.vertical_seekbar_dock_left
                    startTextRes = R.string.left
                    endTextRes = R.string.right
                    preferences.readerVerticalSeekbarModes().asImmediateFlowIn(viewScope) { isVisible = it.isNotEmpty() }
                }
            }

            preferenceCategory {
                titleRes = R.string.downloading

                sliderPreference {
                    bindTo(preferences.preloadSize())
                    titleRes = R.string.page_preload_amount
                    summaryRes = R.string.amount_of_pages_to_preload
                    entryValues = (4..20 step 2).toList()
                    valueFormatter = { context.resources.getQuantityString(R.plurals.pages_plural, it, it) }
                }
            }

            preferenceCategory {
                titleRes = R.string.actions

                switchPreference {
                    key = Keys.readWithLongTap
                    titleRes = R.string.show_on_long_press
                    defaultValue = true
                }
                switchPreference {
                    bindTo(preferences.folderPerManga())
                    titleRes = R.string.save_pages_separately
                    summaryRes = R.string.create_folders_by_manga_title
                }
            }
        }
}
