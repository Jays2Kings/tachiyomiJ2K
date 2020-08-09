package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ChapterFilter(val preferences: PreferencesHelper = Injekt.get(), val downloadManager: DownloadManager = Injekt.get()) {

    // filters chapters based on the manga values
    fun filterChapters(chapters: List<Chapter>, manga: Manga): List<Chapter> {
        val readEnabled = manga.readFilter == Manga.SHOW_READ
        val unreadEnabled = manga.readFilter == Manga.SHOW_UNREAD
        val downloadEnabled = manga.downloadedFilter == Manga.SHOW_DOWNLOADED
        val bookmarkEnabled = manga.bookmarkedFilter == Manga.SHOW_BOOKMARKED

        // if none of the filters are enabled skip the filtering of them
        return if (readEnabled || unreadEnabled || downloadEnabled || bookmarkEnabled) {
            chapters.filter {
                if (readEnabled && it.read.not() ||
                    (unreadEnabled && it.read) ||
                    (bookmarkEnabled && it.bookmark.not()) ||
                    (downloadEnabled && downloadManager.isChapterDownloaded(it, manga).not())
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
    fun filterChaptersForReader(chapters: List<Chapter>, manga: Manga, selectedChapter: Chapter? = null): List<Chapter> {
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
