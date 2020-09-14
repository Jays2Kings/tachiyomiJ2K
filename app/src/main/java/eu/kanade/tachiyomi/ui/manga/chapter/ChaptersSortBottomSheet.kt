package eu.kanade.tachiyomi.ui.manga.chapter

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.view.invisible
import eu.kanade.tachiyomi.util.view.setBottomEdge
import eu.kanade.tachiyomi.util.view.setEdgeToEdge
import eu.kanade.tachiyomi.util.view.visInvisIf
import eu.kanade.tachiyomi.util.view.visibleIf
import kotlinx.android.synthetic.main.chapter_filter_layout.*
import kotlinx.android.synthetic.main.chapter_sort_bottom_sheet.*
import kotlin.math.max

class ChaptersSortBottomSheet(controller: MangaDetailsController) : BottomSheetDialog
(controller.activity!!, R.style.BottomSheetDialogTheme) {

    val activity = controller.activity!!

    private var sheetBehavior: BottomSheetBehavior<*>

    private val presenter = controller.presenter

    init {
        // Use activity theme for this layout
        val view = activity.layoutInflater.inflate(R.layout.chapter_sort_bottom_sheet, null)
        setContentView(view)

        sheetBehavior = BottomSheetBehavior.from(view.parent as ViewGroup)
        setEdgeToEdge(activity, view)
        val height = activity.window.decorView.rootWindowInsets.systemWindowInsetBottom
        sheetBehavior.peekHeight = 415.dpToPx + height

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

    override fun onStart() {
        super.onStart()
        sheetBehavior.skipCollapsed = true
    }

    /**
     * Called when the sheet is created. It initializes the listeners and values of the preferences.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initGeneralPreferences()
        setBottomEdge(hide_titles, activity)
        close_button.setOnClickListener { dismiss() }
        settings_scroll_view.viewTreeObserver.addOnGlobalLayoutListener {
            val isScrollable =
                settings_scroll_view!!.height < sort_layout.height +
                    settings_scroll_view.paddingTop + settings_scroll_view.paddingBottom
            close_button.visibleIf(isScrollable)
            // making the view gone somehow breaks the layout so lets make it invisible
            pill.visInvisIf(!isScrollable)
        }

        setOnDismissListener {
            presenter.setFilters(
                show_read.isChecked,
                show_unread.isChecked,
                show_download.isChecked,
                show_bookmark.isChecked
            )
        }
    }

    private fun initGeneralPreferences() {
        chapter_filter_layout.setCheckboxes(presenter.manga)

        var defPref = presenter.globalSort()
        sort_group.check(
            if (presenter.manga.sortDescending(defPref)) R.id.sort_newest else
                R.id.sort_oldest
        )

        hide_titles.isChecked = presenter.manga.displayMode != Manga.DISPLAY_NAME
        sort_method_group.check(
            if (presenter.manga.sorting == Manga.SORTING_SOURCE) R.id.sort_by_source else
                R.id.sort_by_number
        )

        set_as_default_sort.visInvisIf(
            defPref != presenter.manga.sortDescending() &&
                presenter.manga.usesLocalSort()
        )
        sort_group.setOnCheckedChangeListener { _, checkedId ->
            presenter.setSortOrder(checkedId == R.id.sort_newest)
            set_as_default_sort.visInvisIf(
                defPref != presenter.manga.sortDescending() &&
                    presenter.manga.usesLocalSort()
            )
        }

        set_as_default_sort.setOnClickListener {
            val desc = sort_group.checkedRadioButtonId == R.id.sort_newest
            presenter.setGlobalChapterSort(desc)
            defPref = desc
            set_as_default_sort.invisible()
        }

        sort_method_group.setOnCheckedChangeListener { _, checkedId ->
            presenter.setSortMethod(checkedId == R.id.sort_by_source)
        }

        hide_titles.setOnCheckedChangeListener { _, isChecked ->
            presenter.hideTitle(isChecked)
        }
    }
}
