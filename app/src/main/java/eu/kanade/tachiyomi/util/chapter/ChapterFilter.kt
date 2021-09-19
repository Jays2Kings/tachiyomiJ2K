package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ChapterFilter(val preferences: PreferencesHelper = Injekt.get(), val downloadManager: DownloadManager = Injekt.get()) {

    // filters chapters based on the manga values
    fun <T : Chapter> filterChapters(chapters: List<T>, manga: Manga): List<T> {
        val readEnabled = manga.readFilter(preferences) == Manga.CHAPTER_SHOW_READ
        val unreadEnabled = manga.readFilter(preferences) == Manga.CHAPTER_SHOW_UNREAD
        val downloadEnabled = manga.downloadedFilter(preferences) == Manga.CHAPTER_SHOW_DOWNLOADED
        val notDownloadEnabled = manga.downloadedFilter(preferences) == Manga.CHAPTER_SHOW_NOT_DOWNLOADED
        val bookmarkEnabled = manga.bookmarkedFilter(preferences) == Manga.CHAPTER_SHOW_BOOKMARKED
        val notBookmarkEnabled = manga.bookmarkedFilter(preferences) == Manga.CHAPTER_SHOW_NOT_BOOKMARKED

        // if none of the filters are enabled skip the filtering of them
        return if (readEnabled || unreadEnabled || downloadEnabled || notDownloadEnabled || bookmarkEnabled || notBookmarkEnabled) {
            chapters.filter {
                if (readEnabled && it.read.not() ||
                    (unreadEnabled && it.read) ||
                    (bookmarkEnabled && it.bookmark.not()) ||
                    (notBookmarkEnabled && it.bookmark) ||
                    (downloadEnabled && downloadManager.isChapterDownloaded(it, manga).not()) ||
                    (notDownloadEnabled && downloadManager.isChapterDownloaded(it, manga))
                ) {
                    return@filter false
                }
                return@filter true
            }
        } else {
            chapters
        }
    }

    // filter chapters for the reader
    fun <T : Chapter> filterChaptersForReader(chapters: List<T>, manga: Manga, selectedChapter: T? = null): List<T> {
        // if neither preference is enabled don't even filter
        if (!preferences.skipRead() && !preferences.skipFiltered()) {
            return chapters
        }

        var filteredChapters = chapters
        if (preferences.skipRead()) {
            filteredChapters = filteredChapters.filter { !it.read }
        }
        if (preferences.skipFiltered()) {
            filteredChapters = filterChapters(filteredChapters, manga)
        }
        // add the selected chapter to the list in case it was filtered out
        if (selectedChapter?.id != null) {
            val find = filteredChapters.find { it.id == selectedChapter.id }
            if (find == null) {
                val mutableList = filteredChapters.toMutableList()
                mutableList.add(selectedChapter)
                filteredChapters = mutableList.toList()
            }
        }

        return filteredChapters
    }
}
