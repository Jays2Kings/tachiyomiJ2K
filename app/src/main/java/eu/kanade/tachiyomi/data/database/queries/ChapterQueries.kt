package eu.kanade.tachiyomi.data.database.queries

import com.pushtorefresh.storio.Queries
import com.pushtorefresh.storio.sqlite.queries.Query
import com.pushtorefresh.storio.sqlite.queries.RawQuery
import eu.kanade.tachiyomi.data.database.DbProvider
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaChapter
import eu.kanade.tachiyomi.data.database.resolvers.ChapterBackupPutResolver
import eu.kanade.tachiyomi.data.database.resolvers.ChapterKnownBackupPutResolver
import eu.kanade.tachiyomi.data.database.resolvers.ChapterProgressPutResolver
import eu.kanade.tachiyomi.data.database.resolvers.ChapterSourceOrderPutResolver
import eu.kanade.tachiyomi.data.database.resolvers.MangaChapterGetResolver
import eu.kanade.tachiyomi.data.database.tables.ChapterTable
import eu.kanade.tachiyomi.util.lang.sqLite

interface ChapterQueries : DbProvider {
    fun getChapters(manga: Manga) = getChapters(manga.id)

    fun getChapters(mangaId: Long?) =
        db
            .get()
            .listOfObjects(Chapter::class.java)
            .withQuery(
                Query
                    .builder()
                    .table(ChapterTable.TABLE)
                    .where("${ChapterTable.COL_MANGA_ID} = ?")
                    .whereArgs(mangaId)
                    .build(),
            ).prepare()

    /** Batched variant of [getChapters] for callers that would otherwise query per-manga in a loop. */
    fun getChapters(mangaIds: List<Long>) =
        db
            .get()
            .listOfObjects(Chapter::class.java)
            .withQuery(
                Query
                    .builder()
                    .table(ChapterTable.TABLE)
                    .where("${ChapterTable.COL_MANGA_ID} IN (${Queries.placeholders(mangaIds.size)})")
                    .whereArgs(*mangaIds.toTypedArray())
                    .build(),
            ).prepare()

    fun getLatestChapterNumbers(mangaIds: List<Long>): Map<Long, Float> {
        if (mangaIds.isEmpty()) return emptyMap()

        val result = HashMap<Long, Float>()
        mangaIds.distinct().chunked(500).forEach { ids ->
            val cursor =
                db
                    .lowLevel()
                    .rawQuery(
                        RawQuery
                            .builder()
                            .query(
                                "SELECT ${ChapterTable.COL_MANGA_ID}, MAX(${ChapterTable.COL_CHAPTER_NUMBER}) " +
                                    "FROM ${ChapterTable.TABLE} " +
                                    "WHERE ${ChapterTable.COL_MANGA_ID} IN (${ids.joinToString(",")}) " +
                                    "GROUP BY ${ChapterTable.COL_MANGA_ID}",
                            ).observesTables(ChapterTable.TABLE)
                            .build(),
                    )
            cursor.use {
                while (it.moveToNext()) {
                    result[it.getLong(0)] = it.getFloat(1)
                }
            }
        }
        return result
    }

    fun getRecentChapters(
        search: String = "",
        offset: Int,
        isResuming: Boolean,
    ) = db
        .get()
        .listOfObjects(MangaChapter::class.java)
        .withQuery(
            RawQuery
                .builder()
                .query(getRecentsQuery(search.sqLite, offset, isResuming))
                .observesTables(ChapterTable.TABLE)
                .build(),
        ).withGetResolver(MangaChapterGetResolver.INSTANCE)
        .prepare()

    fun getChapter(id: Long) =
        db
            .get()
            .`object`(Chapter::class.java)
            .withQuery(
                Query
                    .builder()
                    .table(ChapterTable.TABLE)
                    .where("${ChapterTable.COL_ID} = ?")
                    .whereArgs(id)
                    .build(),
            ).prepare()

    fun getChapter(url: String) =
        db
            .get()
            .`object`(Chapter::class.java)
            .withQuery(
                Query
                    .builder()
                    .table(ChapterTable.TABLE)
                    .where("${ChapterTable.COL_URL} = ?")
                    .whereArgs(url)
                    .build(),
            ).prepare()

    fun getChapters(url: String) =
        db
            .get()
            .listOfObjects(Chapter::class.java)
            .withQuery(
                Query
                    .builder()
                    .table(ChapterTable.TABLE)
                    .where("${ChapterTable.COL_URL} = ?")
                    .whereArgs(url)
                    .build(),
            ).prepare()

    fun getChapter(
        url: String,
        mangaId: Long,
    ) = db
        .get()
        .`object`(Chapter::class.java)
        .withQuery(
            Query
                .builder()
                .table(ChapterTable.TABLE)
                .where("${ChapterTable.COL_URL} = ? AND ${ChapterTable.COL_MANGA_ID} = ?")
                .whereArgs(url, mangaId)
                .build(),
        ).prepare()

    fun insertChapter(chapter: Chapter) = db.put().`object`(chapter).prepare()

    fun insertChapters(chapters: List<Chapter>) = db.put().objects(chapters).prepare()

    fun deleteChapter(chapter: Chapter) = db.delete().`object`(chapter).prepare()

    fun deleteChapters(chapters: List<Chapter>) = db.delete().objects(chapters).prepare()

    fun updateChaptersBackup(chapters: List<Chapter>) =
        db
            .put()
            .objects(chapters)
            .withPutResolver(ChapterBackupPutResolver())
            .prepare()

    fun updateKnownChaptersBackup(chapters: List<Chapter>) =
        db
            .put()
            .objects(chapters)
            .withPutResolver(ChapterKnownBackupPutResolver())
            .prepare()

    fun updateChapterProgress(chapter: Chapter) =
        db
            .put()
            .`object`(chapter)
            .withPutResolver(ChapterProgressPutResolver())
            .prepare()

    fun updateChaptersProgress(chapters: List<Chapter>) =
        db
            .put()
            .objects(chapters)
            .withPutResolver(ChapterProgressPutResolver())
            .prepare()

    fun fixChaptersSourceOrder(chapters: List<Chapter>) =
        db
            .put()
            .objects(chapters)
            .withPutResolver(ChapterSourceOrderPutResolver())
            .prepare()
}
