package eu.kanade.tachiyomi.ui.reader

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.format.DateUtils
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.items.AbstractItem
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.util.chapter.ChapterUI
import eu.kanade.tachiyomi.util.system.contextCompatDrawable
import eu.kanade.tachiyomi.util.system.getResourceColor
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Date

class ReaderChapterItem(val chapter: Chapter, val manga: Manga, val isCurrent: Boolean) :
    AbstractItem<ReaderChapterItem.ViewHolder>
    () {

    val decimalFormat =
        DecimalFormat("#.###", DecimalFormatSymbols().apply { decimalSeparator = '.' })

    /** defines the type defining this item. must be unique. preferably an id */
    override val type: Int  = R.id.reader_chapter_layout

    /** defines the layout which will be used for this item in the list */
    override val layoutRes: Int = R.layout.reader_chapter_item

    override var identifier: Long
        get() = chapter.id!!
        set(value) {}

    override fun getViewHolder(v: View): ViewHolder {
        return ViewHolder(v)
    }

    class ViewHolder(view: View) : FastAdapter.ViewHolder<ReaderChapterItem>(view) {
        var chapterTitle: TextView = view.findViewById(R.id.chapter_title)
        var chapterSubtitle: TextView = view.findViewById(R.id.chapter_scanlator)
        var bookmarkButton: FrameLayout = view.findViewById(R.id.bookmark_layout)
        var bookmarkImage: ImageView = view.findViewById(R.id.bookmark_image)

        private var readColor = ChapterUI.readColor(view.context)
        private var unreadColor = ChapterUI.unreadColor(view.context)
        private var activeColor = ChapterUI.activeColor(view.context)

        private var unbookmark = view.context.contextCompatDrawable(R.drawable.ic_bookmark_border_24dp)
        private var bookmark = view.context.contextCompatDrawable(R.drawable.ic_bookmark_24dp)

        override fun bindView(item: ReaderChapterItem, payloads: List<Any>) {
            val chapter = item.chapter
            val manga = item.manga
            chapterTitle.text = when (manga.displayMode) {
                Manga.DISPLAY_NUMBER -> {
                    val number = item.decimalFormat.format(chapter.chapter_number.toDouble())
                    itemView.context.getString(R.string.chapter_, number)
                }
                else -> chapter.name
            }
            val statuses = mutableListOf<String>()
            if (chapter.date_upload > 0) {
                statuses.add(
                    DateUtils.getRelativeTimeSpanString(
                        chapter.date_upload, Date().time, DateUtils.HOUR_IN_MILLIS
                    ).toString()
                )
            }

            if (!chapter.scanlator.isNullOrBlank()) {
                statuses.add(chapter.scanlator!!)
            }

            chapterTitle.setTextColor(
                when {
                    chapter.bookmark -> activeColor
                    chapter.read && !item.isCurrent -> readColor
                    else -> unreadColor
                }
            )

            if (item.isCurrent) {
                chapterTitle.setTypeface(null, Typeface.BOLD)
                chapterSubtitle.setTypeface(null, Typeface.BOLD)
            } else {
                chapterTitle.setTypeface(null, Typeface.NORMAL)
                chapterSubtitle.setTypeface(null, Typeface.NORMAL)
            }

            chapterSubtitle.setTextColor(
                when {
                    chapter.read -> readColor
                    else -> unreadColor
                }
            )
            bookmarkImage.setImageDrawable(when(chapter.bookmark){
                true -> bookmark
                false -> unbookmark
            })

            bookmarkImage.imageTintList = ColorStateList.valueOf(when(chapter.bookmark){
                true -> activeColor
                false -> readColor
            })

            chapterSubtitle.text = statuses.joinToString(" • ")
        }

        override fun unbindView(item: ReaderChapterItem) {
            chapterTitle.text = null
            chapterSubtitle.text = null
        }
    }
}
