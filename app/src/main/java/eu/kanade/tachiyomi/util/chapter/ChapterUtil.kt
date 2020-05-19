package eu.kanade.tachiyomi.util.chapter

import android.content.Context
import android.content.res.ColorStateList
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.util.system.contextCompatColor
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.dpToPxEnd
import eu.kanade.tachiyomi.util.system.timeSpanFromNow

class ChapterUtil {
    companion object {

        fun relativeDate(chapter: Chapter): String? {
            return when (chapter.date_upload > 0) {
                true -> chapter.date_upload.timeSpanFromNow
                false -> null
            }
        }

        fun setTextViewForChapter(
            textView: TextView,
            chapter: Chapter,
            showBookmark: Boolean = true,
            hideStatus: Boolean = false
        ) {
            val context = textView.context
            textView.setTextColor(chapterColor(context, chapter, hideStatus))
            if (!hideStatus && showBookmark) {
                setBookmark(textView, chapter)
            }
        }

        fun setBookmark(textView: TextView, chapter: Chapter) {
            if (chapter.bookmark) {
                val context = textView.context
                val drawable = VectorDrawableCompat.create(
                    textView.resources, R.drawable.ic_bookmark_24dp, context.theme
                )
                drawable?.setBounds(0, 0, textView.textSize.toInt(), textView.textSize.toInt())
                textView.setCompoundDrawablesRelative(
                    drawable, null, null, null
                )
                textView.compoundDrawableTintList = ColorStateList.valueOf(
                    bookmarkedColor(context)
                )
                textView.compoundDrawablePadding = 3.dpToPx
                textView.translationX = (-2f).dpToPxEnd
            } else {
                textView.setCompoundDrawablesRelative(null, null, null, null)
                textView.translationX = 0f
            }
        }

        fun chapterColor(context: Context, chapter: Chapter, hideStatus: Boolean = false): Int {
            return when {
                hideStatus -> unreadColor(context)
                chapter.read -> readColor(context)
                else -> unreadColor(context)
            }
        }

        fun readColor(context: Context, chapter: Chapter): Int {
            return when {
                chapter.read -> readColor(context)
                else -> unreadColor(context)
            }
        }

        fun bookmarkColor(context: Context, chapter: Chapter): Int {
            return when {
                chapter.bookmark -> bookmarkedColor(context)
                else -> readColor(context)
            }
        }

        private fun readColor(context: Context): Int = context.contextCompatColor(R.color.read_chapter)

        private fun unreadColor(context: Context): Int = context.contextCompatColor(R.color.unread_chapter)

        private fun bookmarkedColor(context: Context): Int = context.contextCompatColor(R.color.bookmarked_chapter)

        private fun bookmarkedAndReadColor(context: Context): Int = ColorUtils.setAlphaComponent(context.contextCompatColor(R.color.bookmarked_chapter), 150)
    }
}
