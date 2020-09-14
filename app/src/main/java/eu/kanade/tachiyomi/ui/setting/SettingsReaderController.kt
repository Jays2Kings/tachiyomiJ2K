package eu.kanade.tachiyomi.ui.setting

import android.os.Build
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsReaderController : SettingsController() {

    override fun setupPreferenceScreen(screen: PreferenceScreen) = with(screen) {
        titleRes = R.string.reader

        preferenceCategory {
            titleRes = R.string.general
            intListPreference(activity) {
                key = Keys.defaultViewer
                titleRes = R.string.default_viewer
                entriesRes = arrayOf(
                    R.string.left_to_right_viewer,
                    R.string.right_to_left_viewer,
                    R.string.vertical_viewer,
                    R.string.webtoon,
                    R.string.continuous_vertical
                )
                entryRange = 1..5
                defaultValue = 1
            }
            intListPreference(activity) {
                key = Keys.rotation
                titleRes = R.string.rotation
                entriesRes = arrayOf(
                    R.string.free,
                    R.string.lock,
                    R.string.force_portrait,
                    R.string.force_landscape
                )
                entryRange = 1..4
                defaultValue = 1
            }
            intListPreference(activity) {
                key = Keys.readerTheme
                titleRes = R.string.background_color
                entriesRes = arrayOf(
                    R.string.white,
                    R.string.black,
                    R.string.smart_based_on_page,
                    R.string.smart_based_on_page_and_theme
                )
                entryRange = 0..3
                defaultValue = 2
            }
            intListPreference(activity) {
                key = Keys.doubleTapAnimationSpeed
                titleRes = R.string.double_tap_anim_speed
                entries = listOf(
                    context.getString(R.string.no_animation),
                    context.getString(
                        R.string.fast
                    ),
                    context.getString(R.string.normal)
                )
                entryValues = listOf(1, 250, 500) // using a value of 0 breaks the image viewer, so
                // min is 1
                defaultValue = 500
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                switchPreference {
                    key = Keys.trueColor
                    titleRes = R.string.true_32bit_color
                    summaryRes = R.string.reduces_banding_impacts_performance
                    defaultValue = false
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
                key = Keys.alwaysShowChapterTransition
                titleRes = R.string.always_show_chapter_transition
                summaryRes = R.string.if_disabled_transition_will_skip
                defaultValue = true
            }
        }

        preferenceCategory {
            titleRes = R.string.paged

            intListPreference(activity) {
                key = Keys.imageScaleType
                titleRes = R.string.scale_type
                entriesRes = arrayOf(
                    R.string.fit_screen,
                    R.string.stretch,
                    R.string.fit_width,
                    R.string.fit_height,
                    R.string.original_size,
                    R.string.smart_fit
                )
                entryRange = 1..6
                defaultValue = 1
            }
            intListPreference(activity) {
                key = Keys.zoomStart
                titleRes = R.string.zoom_start_position
                entriesRes = arrayOf(
                    R.string.automatic,
                    R.string.left,
                    R.string.right,
                    R.string.center
                )
                entryRange = 1..4
                defaultValue = 1
            }
            switchPreference {
                key = Keys.enableTransitions
                titleRes = R.string.page_transitions
                defaultValue = true
            }
            switchPreference {
                key = Keys.cropBorders
                titleRes = R.string.crop_borders
                defaultValue = false
            }
        }
        preferenceCategory {
            titleRes = R.string.webtoon

            switchPreference {
                key = Keys.cropBordersWebtoon
                titleRes = R.string.crop_borders
                defaultValue = false
            }

            intListPreference(activity) {
                key = Keys.webtoonSidePadding
                titleRes = R.string.pref_webtoon_side_padding
                entriesRes = arrayOf(
                    R.string.webtoon_side_padding_0,
                    R.string.webtoon_side_padding_10,
                    R.string.webtoon_side_padding_15,
                    R.string.webtoon_side_padding_20,
                    R.string.webtoon_side_padding_25
                )
                entryValues = listOf(0, 10, 15, 20, 25)
                defaultValue = "0"
            }

            switchPreference {
                key = Keys.webtoonEnableZoomOut
                titleRes = R.string.enable_zoom_out
                defaultValue = false
            }
        }
        preferenceCategory {
            titleRes = R.string.navigation

            switchPreference {
                key = Keys.readWithTapping
                titleRes = R.string.tapping
                defaultValue = true
            }
            switchPreference {
                key = Keys.readWithLongTap
                titleRes = R.string.long_tap_dialog
                defaultValue = true
            }
            switchPreference {
                key = Keys.readWithVolumeKeys
                titleRes = R.string.volume_keys
                defaultValue = false
            }
            switchPreference {
                key = Keys.readWithVolumeKeysInverted
                titleRes = R.string.invert_volume_keys
                defaultValue = false
            }.apply { dependency = Keys.readWithVolumeKeys }
        }
    }
}
