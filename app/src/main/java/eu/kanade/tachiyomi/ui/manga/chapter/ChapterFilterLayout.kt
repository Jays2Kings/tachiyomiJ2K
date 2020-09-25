package eu.kanade.tachiyomi.ui.manga.chapter

import android.content.Context
import android.util.AttributeSet
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.RelativeLayout
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import kotlinx.android.synthetic.main.chapter_filter_layout.view.*

class ChapterFilterLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {

    init {
        RelativeLayout.inflate(context, R.layout.chapter_filter_layout, this)
        show_all.setOnCheckedChangeListener(::checkedFilter)
        show_read.setOnCheckedChangeListener(::checkedFilter)
        show_unread.setOnCheckedChangeListener(::checkedFilter)
        show_download.setOnCheckedChangeListener(::checkedFilter)
        show_bookmark.setOnCheckedChangeListener(::checkedFilter)
    }

    private fun checkedFilter(checkBox: CompoundButton, isChecked: Boolean) {
        if (isChecked) {
            if (show_all == checkBox) {
                show_read.isChecked = false
                show_unread.isChecked = false
                show_download.isChecked = false
                show_bookmark.isChecked = false
            } else {
                show_all.isChecked = false
                if (show_read == checkBox) show_unread.isChecked = false
                else if (show_unread == checkBox) show_read.isChecked = false
            }
        } else if (!show_read.isChecked && !show_unread.isChecked && !show_download.isChecked && !show_bookmark.isChecked) {
            show_all.isChecked = true
        }
    }

    fun setCheckboxes(manga: Manga) {
        show_read.isChecked = manga.readFilter == Manga.SHOW_READ
        show_unread.isChecked = manga.readFilter == Manga.SHOW_UNREAD
        show_download.isChecked = manga.downloadedFilter == Manga.SHOW_DOWNLOADED
        show_bookmark.isChecked = manga.bookmarkedFilter == Manga.SHOW_BOOKMARKED

        show_all.isChecked = !(
            show_read.isChecked || show_unread.isChecked ||
                show_download.isChecked || show_bookmark.isChecked
            )
    }
}
