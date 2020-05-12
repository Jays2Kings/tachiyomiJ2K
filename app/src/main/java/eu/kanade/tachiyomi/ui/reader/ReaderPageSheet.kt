package eu.kanade.tachiyomi.ui.reader

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.bottomsheet.BottomSheetDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.util.system.hasSideNavBar
import eu.kanade.tachiyomi.util.system.isInNightMode
import eu.kanade.tachiyomi.util.view.setBottomEdge
import eu.kanade.tachiyomi.util.view.setEdgeToEdge
import kotlinx.android.synthetic.main.reader_page_sheet.*

/**
 * Sheet to show when a page is long clicked.
 */
class ReaderPageSheet(
    private val activity: ReaderActivity,
    private val page: ReaderPage
) : BottomSheetDialog(activity, R.style.BottomSheetDialogTheme) {

    /**
     * View used on this sheet.
     */
    private val view = activity.layoutInflater.inflate(R.layout.reader_page_sheet, null)

    init {
        setContentView(view)
        setEdgeToEdge(activity, view)
        window?.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.isInNightMode() &&
            !activity.window.decorView.rootWindowInsets.hasSideNavBar())
            window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        setBottomEdge(save_layout, activity)

        set_as_cover_layout.setOnClickListener { setAsCover() }
        share_layout.setOnClickListener { share() }
        save_layout.setOnClickListener { save() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val width = context.resources.getDimensionPixelSize(R.dimen.bottom_sheet_width)
        if (width > 0) {
            window?.setLayout(width, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    /**
     * Sets the image of this page as the cover of the manga.
     */
    private fun setAsCover() {
        if (page.status != Page.READY) return

        MaterialDialog(activity)
            .title(R.string.use_image_as_cover)
            .positiveButton(android.R.string.yes) {
                activity.setAsCover(page)
                dismiss()
            }
            .negativeButton(android.R.string.no)
            .show()
    }

    /**
     * Shares the image of this page with external apps.
     */
    private fun share() {
        activity.shareImage(page)
        dismiss()
    }

    /**
     * Saves the image of this page on external storage.
     */
    private fun save() {
        activity.saveImage(page)
        dismiss()
    }
}
