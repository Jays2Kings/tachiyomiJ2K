package eu.kanade.tachiyomi.util.chapter

import android.content.Context
import androidx.annotation.AttrRes
import eu.kanade.tachiyomi.util.system.contextCompatDrawable
import eu.kanade.tachiyomi.util.system.getResourceColor

class ChapterUI{
    companion object{
        fun readColor(context: Context): Int = context.getResourceColor(android.R.attr.textColorHint)

        fun unreadColor(context: Context): Int = context.getResourceColor(android.R.attr.textColorPrimary)

        fun bookmarkedColor(context: Context): Int = context.getResourceColor(android.R.attr.colorAccent)

        fun activeColor(context: Context): Int = context.getResourceColor(android.R.attr.colorAccent)
    }
}