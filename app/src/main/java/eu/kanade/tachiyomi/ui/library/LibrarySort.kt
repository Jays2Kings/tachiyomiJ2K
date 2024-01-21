package eu.kanade.tachiyomi.ui.library

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.MaterialMenuSheet

enum class LibrarySort(
    val mainValue: Int,
    @StringRes private val stringRes: Int,
    @DrawableRes private val iconRes: Int,
    private val catValue: Int = mainValue,
    @StringRes private val dynamicStringRes: Int = stringRes,
    @DrawableRes private val dynamicIconRes: Int = iconRes,
) {

    Title(0, R.string.title, R.drawable.ic_sort_by_alpha_24dp),
    LastRead(1, R.string.last_read, R.drawable.ic_recent_read_outline_24dp, 3),
    LatestChapter(2, R.string.latest_chapter, R.drawable.ic_new_releases_24dp, 1),
    Unread(3, R.string.unread, R.drawable.ic_eye_24dp, 2),
    TotalChapters(4, R.string.total_chapters, R.drawable.ic_sort_by_numeric_24dp),
    DateAdded(5, R.string.date_added, R.drawable.ic_heart_outline_24dp),
    DateFetched(6, R.string.date_fetched, R.drawable.ic_calendar_text_outline_24dp),
    DragAndDrop(
        7,
        R.string.drag_and_drop,
        R.drawable.ic_swap_vert_24dp,
        7,
        R.string.category,
        R.drawable.ic_label_outline_24dp,
    ),
    NextUnread(8, R.string.next_unread_chapter, R.drawable.ic_next_unread_24dp),
    ;

    val categoryValue: Char
        get() = if (this == DragAndDrop) 'D' else 'a' + catValue * 2

    val categoryValueDescending: Char
        get() = if (this == DragAndDrop) 'D' else 'b' + catValue * 2

    fun serialize(): String {
        val type = when (this) {
            LastRead -> "LAST_READ"
            Unread -> "UNREAD_COUNT"
            TotalChapters -> "TOTAL_CHAPTERS"
            LatestChapter -> "LATEST_CHAPTER"
            DateFetched -> "CHAPTER_FETCH_DATE"
            DateAdded -> "DATE_ADDED"
            else -> "ALPHABETICAL"
        }
        return "$type,ASCENDING"
    }

    @StringRes
    fun stringRes(isDynamic: Boolean) = if (isDynamic) dynamicStringRes else stringRes

    @DrawableRes
    fun iconRes(isDynamic: Boolean) = if (isDynamic) dynamicIconRes else iconRes

    val hasInvertedSort: Boolean
        get() = this in listOf(LastRead, DateAdded, LatestChapter, DateFetched, NextUnread)

    fun menuSheetItem(isDynamic: Boolean): MaterialMenuSheet.MenuSheetItem {
        return MaterialMenuSheet.MenuSheetItem(
            mainValue,
            iconRes(isDynamic),
            stringRes(isDynamic),
        )
    }

    companion object {
        fun deserialize(serialized: String): LibrarySort {
            if (serialized.isEmpty()) return Title
            return try {
                val values = serialized.split(",")
                when (values[0]) {
                    "ALPHABETICAL" -> Title
                    "LAST_READ" -> LastRead
                    "LAST_MANGA_UPDATE" -> LatestChapter
                    "UNREAD_COUNT" -> Unread
                    "TOTAL_CHAPTERS" -> TotalChapters
                    "LATEST_CHAPTER" -> LatestChapter
                    "CHAPTER_FETCH_DATE" -> DateFetched
                    "DATE_ADDED" -> DateAdded
                    else -> Title
                }
            } catch (e: Exception) {
                Title
            }
        }

        fun valueOf(value: Int) = entries.find { it.mainValue == value }
        fun valueOf(char: Char?) = entries.find { it.categoryValue == char || it.categoryValueDescending == char }
    }
}
