package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ChapterSort(val manga: Manga, val chapterFilter: ChapterFilter = Injekt.get(), val preferences: PreferencesHelper = Injekt.get()) {

    fun <T : Chapter> getChaptersSorted(
        rawChapters: List<T>,
        andFiltered: Boolean = true,
        filterForReader: Boolean = false,
        currentChapter: T? = null
    ): List<T> {
        val filteredChapterList = manga.filtered_scanlators?.let { filteredScanlatorString ->
            val filteredScanlators = ChapterUtil.getScanlators(filteredScanlatorString)
            return rawChapters.filter { ChapterUtil.getScanlators(it.scanlator).any { group -> filteredScanlators.contains(group) } }
        } ?: rawChapters
        val chapters = when {
            filterForReader -> chapterFilter.filterChaptersForReader(
                filteredChapterList,
                manga,
                currentChapter
            )
            andFiltered -> chapterFilter.filterChapters(filteredChapterList, manga)
            else -> filteredChapterList
        }

        return chapters.sortedWith(sortComparator())
    }

    fun <T : Chapter> getNextUnreadChapter(rawChapters: List<T>, andFiltered: Boolean = true,): T? {
        val chapters = when {
            andFiltered -> chapterFilter.filterChapters(rawChapters, manga)
            else -> rawChapters
        }
        return chapters.sortedWith(sortComparator(true)).find { !it.read }
    }

    fun <T : Chapter> sortComparator(ignoreAsc: Boolean = false): Comparator<T> {
        val sortDescending = !ignoreAsc &&
            manga.sortDescending(preferences)
        val sortFunction: (T, T) -> Int =
            when (manga.chapterOrder(preferences)) {
                Manga.CHAPTER_SORTING_SOURCE -> when (sortDescending) {
                    true -> { c1, c2 -> c1.source_order.compareTo(c2.source_order) }
                    false -> { c1, c2 -> c2.source_order.compareTo(c1.source_order) }
                }
                Manga.CHAPTER_SORTING_NUMBER -> when (sortDescending) {
                    true -> { c1, c2 -> c2.chapter_number.compareTo(c1.chapter_number) }
                    false -> { c1, c2 -> c1.chapter_number.compareTo(c2.chapter_number) }
                }
                Manga.CHAPTER_SORTING_UPLOAD_DATE -> when (sortDescending) {
                    true -> { c1, c2 -> c2.date_upload.compareTo(c1.date_upload) }
                    false -> { c1, c2 -> c1.date_upload.compareTo(c2.date_upload) }
                }
                else -> { c1, c2 -> c1.source_order.compareTo(c2.source_order) }
            }
        return Comparator(sortFunction)
    }
}
