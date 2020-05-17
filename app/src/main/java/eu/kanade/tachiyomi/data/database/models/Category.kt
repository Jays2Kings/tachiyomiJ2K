package eu.kanade.tachiyomi.data.database.models

import android.content.Context
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.library.LibrarySort
import java.io.Serializable

interface Category : Serializable {

    var id: Int?

    var name: String

    var order: Int

    var flags: Int

    var mangaOrder: List<Long>

    var mangaSort: Char?

    var isAlone: Boolean

    val nameLower: String
        get() = name.toLowerCase()

    var isHidden: Boolean

    var isDynamic: Boolean

    fun isAscending(): Boolean {
        return ((mangaSort?.minus('a') ?: 0) % 2) != 1
    }

    fun sortingMode(): Int? = when (mangaSort) {
        ALPHA_ASC, ALPHA_DSC -> LibrarySort.ALPHA
        UPDATED_ASC, UPDATED_DSC -> LibrarySort.LATEST_CHAPTER
        UNREAD_ASC, UNREAD_DSC -> LibrarySort.UNREAD
        LAST_READ_ASC, LAST_READ_DSC -> LibrarySort.LAST_READ
        TOTAL_ASC, TOTAL_DSC -> LibrarySort.TOTAL
        DRAG_AND_DROP -> LibrarySort.DRAG_AND_DROP
        DATE_ADDED_ASC, DATE_ADDED_DSC -> LibrarySort.DATE_ADDED
        else -> null
    }

    fun sortRes(): Int = when (mangaSort) {
        ALPHA_ASC, ALPHA_DSC -> R.string.title
        UPDATED_ASC, UPDATED_DSC -> R.string.latest_chapter
        UNREAD_ASC, UNREAD_DSC -> R.string.unread
        LAST_READ_ASC, LAST_READ_DSC -> R.string.last_read
        TOTAL_ASC, TOTAL_DSC -> R.string.total_chapters
        DATE_ADDED_ASC, DATE_ADDED_DSC -> R.string.date_added
        else -> if (isDynamic) R.string.category else R.string.drag_and_drop
    }

    fun catSortingMode(): Int? = when (mangaSort) {
        ALPHA_ASC, ALPHA_DSC -> 0
        UPDATED_ASC, UPDATED_DSC -> 1
        UNREAD_ASC, UNREAD_DSC -> 2
        LAST_READ_ASC, LAST_READ_DSC -> 3
        TOTAL_ASC, TOTAL_DSC -> 4
        DATE_ADDED_ASC, DATE_ADDED_DSC -> 5
        else -> null
    }

    fun changeSortTo(sort: Int) {
        mangaSort = when (sort) {
            LibrarySort.ALPHA -> ALPHA_ASC
            LibrarySort.LATEST_CHAPTER -> UPDATED_ASC
            LibrarySort.UNREAD -> UNREAD_ASC
            LibrarySort.LAST_READ -> LAST_READ_ASC
            LibrarySort.TOTAL -> ALPHA_ASC
            LibrarySort.DATE_ADDED -> DATE_ADDED_ASC
            else -> ALPHA_ASC
        }
    }

    companion object {
        private const val DRAG_AND_DROP = 'D'
        private const val ALPHA_ASC = 'a'
        private const val ALPHA_DSC = 'b'
        private const val UPDATED_ASC = 'c'
        private const val UPDATED_DSC = 'd'
        private const val UNREAD_ASC = 'e'
        private const val UNREAD_DSC = 'f'
        private const val LAST_READ_ASC = 'g'
        private const val LAST_READ_DSC = 'h'
        private const val TOTAL_ASC = 'i'
        private const val TOTAL_DSC = 'j'
        private const val DATE_ADDED_ASC = 'k'
        private const val DATE_ADDED_DSC = 'l'

        fun create(name: String): Category = CategoryImpl().apply {
            this.name = name
        }

        fun createDefault(context: Context): Category =
            create(context.getString(R.string.default_value)).apply {
                id = 0
            }

        fun createCustom(name: String, libSort: Int, ascending: Boolean): Category =
            create(name).apply {
                mangaSort = when (libSort) {
                    LibrarySort.ALPHA -> ALPHA_ASC
                    LibrarySort.LATEST_CHAPTER -> UPDATED_ASC
                    LibrarySort.UNREAD -> UNREAD_ASC
                    LibrarySort.LAST_READ -> LAST_READ_ASC
                    LibrarySort.TOTAL -> TOTAL_ASC
                    LibrarySort.DATE_ADDED -> DATE_ADDED_ASC
                    LibrarySort.DRAG_AND_DROP -> DRAG_AND_DROP
                    else -> DRAG_AND_DROP
                }
                if (mangaSort != DRAG_AND_DROP && !ascending) {
                    mangaSort = mangaSort?.plus(1)
                }
                isDynamic = true
            }

        fun createAll(context: Context, libSort: Int, ascending: Boolean): Category =
            createCustom(context.getString(R.string.all), libSort, ascending).apply {
                id = -1
                order = -1
            }
    }
}
