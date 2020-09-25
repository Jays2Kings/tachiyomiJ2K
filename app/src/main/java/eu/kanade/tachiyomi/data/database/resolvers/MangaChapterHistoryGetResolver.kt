package eu.kanade.tachiyomi.data.database.resolvers

import android.database.Cursor
import com.pushtorefresh.storio.sqlite.operations.get.DefaultGetResolver
import eu.kanade.tachiyomi.data.database.mappers.ChapterGetResolver
import eu.kanade.tachiyomi.data.database.mappers.HistoryGetResolver
import eu.kanade.tachiyomi.data.database.mappers.MangaGetResolver
import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import eu.kanade.tachiyomi.data.database.models.HistoryImpl
import eu.kanade.tachiyomi.data.database.models.MangaChapterHistory
import eu.kanade.tachiyomi.data.database.tables.ChapterTable
import eu.kanade.tachiyomi.data.database.tables.HistoryTable

class MangaChapterHistoryGetResolver : DefaultGetResolver<MangaChapterHistory>() {
    companion object {
        val INSTANCE = MangaChapterHistoryGetResolver()
    }

    /**
     * Manga get resolver
     */
    private val mangaGetResolver = MangaGetResolver()

    /**
     * Chapter get resolver
     */
    private val chapterResolver = ChapterGetResolver()

    /**
     * History get resolver
     */
    private val historyGetResolver = HistoryGetResolver()

    /**
     * Map correct objects from cursor result
     */
    override fun mapFromCursor(cursor: Cursor): MangaChapterHistory {
        // Get manga object
        val manga = mangaGetResolver.mapFromCursor(cursor)

        // Get chapter object
        val chapter =
            if (!cursor.isNull(cursor.getColumnIndex(ChapterTable.COL_MANGA_ID))) chapterResolver
                .mapFromCursor(
                    cursor
                ) else ChapterImpl()

        // Get history object
        val history =
            if (!cursor.isNull(cursor.getColumnIndex(HistoryTable.COL_ID))) historyGetResolver.mapFromCursor(
                cursor
            ) else HistoryImpl()

        // Make certain column conflicts are dealt with
        if (chapter.id != null) {
            manga.id = chapter.manga_id
            manga.url = cursor.getString(cursor.getColumnIndex("mangaUrl"))
        }
        if (history.id != null) chapter.id = history.chapter_id

        // Return result
        return MangaChapterHistory(manga, chapter, history)
    }
}
