package eu.kanade.tachiyomi.data.shareddata

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.shared.data.db.SharedDataDatabase

/**
 * Temporary bridge for rollout: keeps legacy StorIO DB as source of truth and mirrors records
 * into SQLDelight shared:data database until all read/write paths are switched.
 */
class SharedDataMigrationAdapter(
    private val legacyDb: DatabaseHelper,
    private val sharedDb: SharedDataDatabase,
) {

    fun migrateLegacySnapshot() {
        val mangas = legacyDb.getMangas().executeAsBlocking()
        mangas.forEach(::upsertManga)

        mangas.forEach { manga ->
            legacyDb.getChapters(manga.id).executeAsBlocking().forEach(::upsertChapter)
        }
    }

    fun getManga(id: Long): Manga? {
        return legacyDb.getManga(id).executeAsBlocking()
    }

    fun getChapters(mangaId: Long): List<Chapter> {
        return legacyDb.getChapters(mangaId).executeAsBlocking()
    }

    fun mirrorMangaWrite(manga: Manga) {
        legacyDb.insertManga(manga).executeAsBlocking()
        upsertManga(manga)
    }

    fun mirrorChapterWrite(chapter: Chapter) {
        legacyDb.insertChapter(chapter).executeAsBlocking()
        upsertChapter(chapter)
    }

    private fun upsertManga(manga: Manga) {
        sharedDb.sharedDataDatabaseQueries.upsertManga(
            _id = manga.id,
            source = manga.source,
            url = manga.url,
            artist = manga.artist,
            author = manga.author,
            description = manga.description,
            genre = manga.genre,
            title = manga.title,
            status = manga.status.toLong(),
            thumbnail_url = manga.thumbnail_url,
            favorite = manga.favorite,
            last_update = manga.last_update,
            initialized = manga.initialized,
            viewer = manga.viewer_flags.toLong(),
            hideTitle = manga.hide_title,
            chapter_flags = manga.chapter_flags.toLong(),
            date_added = manga.date_added,
            filtered_scanlators = manga.filtered_scanlators,
            update_strategy = manga.update_strategy.ordinal.toLong(),
        )
    }

    private fun upsertChapter(chapter: Chapter) {
        sharedDb.sharedDataDatabaseQueries.upsertChapter(
            _id = chapter.id,
            manga_id = chapter.manga_id ?: return,
            url = chapter.url,
            name = chapter.name,
            scanlator = chapter.scanlator,
            read = chapter.read,
            bookmark = chapter.bookmark,
            last_page_read = chapter.last_page_read.toLong(),
            pages_left = chapter.pages_left.toLong(),
            chapter_number = chapter.chapter_number.toDouble(),
            source_order = chapter.source_order.toLong(),
            date_fetch = chapter.date_fetch,
            date_upload = chapter.date_upload,
        )
    }
}
