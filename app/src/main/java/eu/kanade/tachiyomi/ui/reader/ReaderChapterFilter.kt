package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * This class filters chapters for the reader based on the user enabled preferences and filters
 */
class ReaderChapterFilter(
    private val downloadManager: DownloadManager = Injekt.get(),
    private val preferences: PreferencesHelper = Injekt.get()
) {

    fun filterChapter(
        dbChapters: List<Chapter>,
        manga: Manga,
        chapterId: Long,
        selectedChapter: Chapter?
    ): List<Chapter> {

        // if neither preference is enabled dont even filter
        if (!preferences.skipRead() && !preferences.skipFiltered()) {
            return dbChapters
        }

        var filteredChapters = dbChapters
        if (preferences.skipRead()) {
            filteredChapters = filteredChapters.filter { !it.read }
            // add the selected chapter to the list in case it was read and user clicked it
            if (chapterId != -1L) {
                val find = filteredChapters.find { it.id == chapterId }
                if (find == null) {
                    val mutableList = filteredChapters.toMutableList()
                    selectedChapter?.let { mutableList.add(it) }
                    filteredChapters = mutableList.toList()
                }
            }
        }
        if (preferences.skipFiltered()) {
            val readEnabled = manga.readFilter == Manga.SHOW_READ
            val unreadEnabled = manga.readFilter == Manga.SHOW_UNREAD
            val downloadEnabled = manga.downloadedFilter == Manga.SHOW_DOWNLOADED
            val bookmarkEnabled = manga.bookmarkedFilter == Manga.SHOW_BOOKMARKED

            // if none of the filters are enabled skip the filtering of them
            if (readEnabled || unreadEnabled || downloadEnabled || bookmarkEnabled) {

                filteredChapters = filteredChapters.filter {
                    if (readEnabled && it.read.not() ||
                        (unreadEnabled && it.read) ||
                        (bookmarkEnabled && it.bookmark.not()) ||
                        (downloadEnabled && downloadManager.isChapterDownloaded(it, manga).not())
                    ) {
                        return@filter false
                    }
                    return@filter true
                }
            }
        }
        return filteredChapters
    }
}
