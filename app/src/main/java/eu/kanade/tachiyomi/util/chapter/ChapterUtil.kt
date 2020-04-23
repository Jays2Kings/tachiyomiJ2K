package eu.kanade.tachiyomi.util.chapter

import android.content.Context
import android.text.format.DateUtils
import androidx.core.graphics.ColorUtils
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.util.system.contextCompatColor
import java.util.Date

class ChapterUtil {
    companion object {

        fun relativeDate(chapter: Chapter): String? {
            return when (chapter.date_upload > 0) {
                true -> DateUtils.getRelativeTimeSpanString(chapter.date_upload, Date().time, DateUtils.HOUR_IN_MILLIS).toString()
                false -> null
            }
        }

        fun readColor(context: Context): Int = context.contextCompatColor(R.color.read_chapter)

        fun unreadColor(context: Context): Int = context.contextCompatColor(R.color.unread_chapter)

        fun bookmarkedColor(context: Context): Int = context.contextCompatColor(R.color.bookmarked_chapter)

        fun bookmarkedAndReadColor(context: Context): Int = ColorUtils.setAlphaComponent(context.contextCompatColor(R.color.bookmarked_chapter), 150)
    }
}
