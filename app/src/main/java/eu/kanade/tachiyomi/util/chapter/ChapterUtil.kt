package eu.kanade.tachiyomi.util.chapter

import android.content.Context
import android.content.res.ColorStateList
import android.widget.TextView
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.ui.manga.chapter.ChapterItem
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
                    textView.resources,
                    R.drawable.ic_bookmark_24dp,
                    context.theme
                )
                drawable?.setBounds(0, 0, textView.textSize.toInt(), textView.textSize.toInt())
                textView.setCompoundDrawablesRelative(
                    drawable,
                    null,
                    null,
                    null
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

        private val volumeRegex = Regex("""(vol|volume)\.? *([0-9]+)?""", RegexOption.IGNORE_CASE)
        private val seasonRegex = Regex("""(Season |S)([0-9]+)?""")

        fun getGroupNumber(chapter: Chapter): Int? {
            val groups = volumeRegex.find(chapter.name)?.groups
            if (groups != null) return groups[2]?.value?.toIntOrNull()
            val seasonGroups = seasonRegex.find(chapter.name)?.groups
            if (seasonGroups != null) return seasonGroups[2]?.value?.toIntOrNull()
            return null
        }

        private fun getVolumeNumber(chapter: Chapter): Int? {
            val groups = volumeRegex.find(chapter.name)?.groups
            if (groups != null) return groups[2]?.value?.toIntOrNull()
            return null
        }

        private fun getSeasonNumber(chapter: Chapter): Int? {
            val groups = seasonRegex.find(chapter.name)?.groups
            if (groups != null) return groups[2]?.value?.toIntOrNull()
            return null
        }

        fun hasMultipleVolumes(chapters: List<Chapter>): Boolean {
            val volumeSet = mutableSetOf<Int>()
            chapters.forEach {
                val volNum = getVolumeNumber(it)
                if (volNum != null) {
                    volumeSet.add(volNum)
                    if (volumeSet.size >= 2) return true
                }
            }
            return false
        }

        fun hasMultipleSeasons(chapters: List<Chapter>): Boolean {
            val volumeSet = mutableSetOf<Int>()
            chapters.forEach {
                val volNum = getSeasonNumber(it)
                if (volNum != null) {
                    volumeSet.add(volNum)
                    if (volumeSet.size >= 2) return true
                }
            }
            return false
        }

        fun hasTensOfChapters(chapters: List<ChapterItem>): Boolean {
            return chapters.size > 20
        }
    }
}
