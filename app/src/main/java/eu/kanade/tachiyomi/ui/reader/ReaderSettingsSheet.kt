package eu.kanade.tachiyomi.ui.reader

import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.Spinner
import androidx.annotation.ArrayRes
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.tfcporciuncula.flow.Preference
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.hasSideNavBar
import eu.kanade.tachiyomi.util.system.isInNightMode
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.setBottomEdge
import eu.kanade.tachiyomi.util.view.setEdgeToEdge
import eu.kanade.tachiyomi.util.view.visible
import eu.kanade.tachiyomi.util.view.visibleIf
import eu.kanade.tachiyomi.widget.IgnoreFirstSpinnerListener
import kotlinx.android.synthetic.main.reader_settings_sheet.*
import uy.kohesive.injekt.injectLazy
import kotlin.math.max

/**
 * Sheet to show reader and viewer preferences.
 */
class ReaderSettingsSheet(private val activity: ReaderActivity) :
    BottomSheetDialog(activity, R.style.BottomSheetDialogTheme) {

    /**
     * Preferences helper.
     */
    private val preferences by injectLazy<PreferencesHelper>()

    private var sheetBehavior: BottomSheetBehavior<*>

    init {
        // Use activity theme for this layout
        val view = activity.layoutInflater.inflate(R.layout.reader_settings_sheet, null)
        setContentView(view)

        sheetBehavior = BottomSheetBehavior.from(view.parent as ViewGroup)
        setEdgeToEdge(
            activity,
            view,
            if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                0 else -1
        )
        window?.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.isInNightMode() &&
            !activity.window.decorView.rootWindowInsets.hasSideNavBar()
        ) {
            window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }

        val height = activity.window.decorView.rootWindowInsets.systemWindowInsetBottom
        sheetBehavior.peekHeight = 550.dpToPx + height

        sheetBehavior.addBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onSlide(bottomSheet: View, progress: Float) {
                    if (progress.isNaN())
                        pill.alpha = 0f
                    else
                        pill.alpha = (1 - max(0f, progress)) * 0.25f
                }

                override fun onStateChanged(p0: View, state: Int) {
                    if (state == BottomSheetBehavior.STATE_EXPANDED) {
                        sheetBehavior.skipCollapsed = true
                    }
                }
            }
        )
    }

    /**
     * Called when the sheet is created. It initializes the listeners and values of the preferences.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initGeneralPreferences()

        when (val view = activity.viewer) {
            is PagerViewer -> initPagerPreferences()
            is WebtoonViewer -> initWebtoonPreferences(view.hasMargins)
        }

        setBottomEdge(constraint_layout, activity)

        close_button.setOnClickListener {
            dismiss()
        }
        settings_scroll_view.viewTreeObserver.addOnGlobalLayoutListener {
            val isScrollable =
                settings_scroll_view.height < constraint_layout.height +
                    settings_scroll_view.paddingTop + settings_scroll_view.paddingBottom
            close_button.visibleIf(isScrollable)
            pill.visibleIf(!isScrollable)
        }
    }

    /**
     * Init general reader preferences.
     */
    private fun initGeneralPreferences() {
        viewer.onItemSelectedListener = IgnoreFirstSpinnerListener { position ->
            activity.presenter.setMangaViewer(position)

            val mangaViewer = activity.presenter.getMangaViewer()
            if (mangaViewer == ReaderActivity.WEBTOON || mangaViewer == ReaderActivity.VERTICAL_PLUS) {
                initWebtoonPreferences(mangaViewer == ReaderActivity.VERTICAL_PLUS)
            } else {
                initPagerPreferences()
            }
        }
        viewer.setSelection(activity.presenter.manga?.viewer ?: 0, false)

        rotation_mode.bindToPreference(preferences.rotation(), 1)
        background_color.bindToPreference(preferences.readerTheme(), 0)
        show_page_number.bindToPreference(preferences.showPageNumber())
        fullscreen.bindToPreference(preferences.fullscreen())
        keepscreen.bindToPreference(preferences.keepScreenOn())
        always_show_chapter_transition.bindToPreference(preferences.alwaysShowChapterTransition())
    }

    /**
     * Init the preferences for the pager reader.
     */
    private fun initPagerPreferences() {
        pager_prefs_group.visible()
        webtoon_prefs_group.gone()
        scale_type.bindToPreference(preferences.imageScaleType(), 1)
        zoom_start.bindToPreference(preferences.zoomStart(), 1)
        crop_borders.bindToPreference(preferences.cropBorders())
        page_transitions.bindToPreference(preferences.pageTransitions())
    }

    /**
     * Init the preferences for the webtoon reader.
     */
    private fun initWebtoonPreferences(hasMargins: Boolean) {
        webtoon_prefs_group.visible()
        pager_prefs_group.gone()
        crop_borders_webtoon.bindToPreference(if (hasMargins) preferences.cropBorders() else preferences.cropBordersWebtoon())
        webtoon_side_padding.bindToIntPreference(preferences.webtoonSidePadding(), R.array.webtoon_side_padding_values)
        webtoon_enable_zoom_out.bindToPreference(preferences.webtoonEnableZoomOut())
    }

    /**
     * Binds a checkbox or switch view with a boolean preference.
     */
    private fun CompoundButton.bindToPreference(pref: Preference<Boolean>) {
        setOnCheckedChangeListener(null)
        isChecked = pref.get()
        setOnCheckedChangeListener { _, isChecked -> pref.set(isChecked) }
    }

    /**
     * Binds a spinner to an int preference with an optional offset for the value.
     */
    private fun Spinner.bindToPreference(
        pref: Preference<Int>,
        offset: Int = 0
    ) {
        onItemSelectedListener = IgnoreFirstSpinnerListener { position ->
            pref.set(position + offset)
        }
        setSelection(pref.get() - offset, false)
    }

    /**
     * Binds a spinner to an int preference. The position of the spinner item must
     * correlate with the [intValues] resource item (in arrays.xml), which is a <string-array>
     * of int values that will be parsed here and applied to the preference.
     */
    private fun Spinner.bindToIntPreference(pref: Preference<Int>, @ArrayRes intValuesResource: Int) {
        val intValues = resources.getStringArray(intValuesResource).map { it.toIntOrNull() }
        onItemSelectedListener = IgnoreFirstSpinnerListener { position ->
            pref.set(intValues[position] ?: 0)
        }
        setSelection(intValues.indexOf(pref.get()), false)
    }
}
