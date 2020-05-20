package eu.kanade.tachiyomi.data.database.resolvers

import android.content.ContentValues
import com.pushtorefresh.storio.sqlite.StorIOSQLite
import com.pushtorefresh.storio.sqlite.operations.put.PutResolver
import com.pushtorefresh.storio.sqlite.operations.put.PutResult
import com.pushtorefresh.storio.sqlite.queries.UpdateQuery
import eu.kanade.tachiyomi.data.database.inTransactionReturn
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.tables.MangaTable

class MangaInfoPutResolver(val reset: Boolean = false) : PutResolver<Manga>() {

    override fun performPut(db: StorIOSQLite, manga: Manga) = db.inTransactionReturn {
        val updateQuery = mapToUpdateQuery(manga)
        val contentValues = if (reset) resetToContentValues(manga) else mapToContentValues(manga)

        val numberOfRowsUpdated = db.lowLevel().update(updateQuery, contentValues)
        PutResult.newUpdateResult(numberOfRowsUpdated, updateQuery.table())
    }

    fun mapToUpdateQuery(manga: Manga) = UpdateQuery.builder()
        .table(MangaTable.TABLE)
        .where("${MangaTable.COL_ID} = ?")
        .whereArgs(manga.id)
        .build()

    fun mapToContentValues(manga: Manga) = ContentValues(1).apply {
        put(MangaTable.COL_TITLE, manga.originalTitle)
        put(MangaTable.COL_GENRE, manga.originalGenre)
        put(MangaTable.COL_AUTHOR, manga.originalAuthor)
        put(MangaTable.COL_ARTIST, manga.originalArtist)
        put(MangaTable.COL_DESCRIPTION, manga.originalDescription)
    }

    fun resetToContentValues(manga: Manga) = ContentValues(1).apply {
        val splitter = "▒ ▒∩▒"
        put(MangaTable.COL_TITLE, manga.title.split(splitter).last())
        put(MangaTable.COL_GENRE, manga.genre?.split(splitter)?.lastOrNull())
        put(MangaTable.COL_AUTHOR, manga.author?.split(splitter)?.lastOrNull())
        put(MangaTable.COL_ARTIST, manga.artist?.split(splitter)?.lastOrNull())
        put(MangaTable.COL_DESCRIPTION, manga.description?.split(splitter)?.lastOrNull())
    }
}
