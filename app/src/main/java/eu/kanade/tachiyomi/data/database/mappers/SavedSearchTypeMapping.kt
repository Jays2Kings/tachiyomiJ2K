package eu.kanade.tachiyomi.data.database.mappers

import android.content.ContentValues
import android.database.Cursor
import com.pushtorefresh.storio.sqlite.SQLiteTypeMapping
import com.pushtorefresh.storio.sqlite.operations.delete.DefaultDeleteResolver
import com.pushtorefresh.storio.sqlite.operations.get.DefaultGetResolver
import com.pushtorefresh.storio.sqlite.operations.put.DefaultPutResolver
import com.pushtorefresh.storio.sqlite.queries.DeleteQuery
import com.pushtorefresh.storio.sqlite.queries.InsertQuery
import com.pushtorefresh.storio.sqlite.queries.UpdateQuery
import eu.kanade.tachiyomi.data.database.models.SavedSearch
import eu.kanade.tachiyomi.data.database.models.SavedSearchImpl
import eu.kanade.tachiyomi.data.database.tables.SavedSearchTable.COL_FILTERS_JSON
import eu.kanade.tachiyomi.data.database.tables.SavedSearchTable.COL_ID
import eu.kanade.tachiyomi.data.database.tables.SavedSearchTable.COL_NAME
import eu.kanade.tachiyomi.data.database.tables.SavedSearchTable.COL_QUERY
import eu.kanade.tachiyomi.data.database.tables.SavedSearchTable.COL_SOURCE
import eu.kanade.tachiyomi.data.database.tables.SavedSearchTable.TABLE

class SavedSearchTypeMapping :
    SQLiteTypeMapping<SavedSearch>(
        SavedSearchPutResolver(),
        SavedSearchGetResolver(),
        SavedSearchDeleteResolver(),
    )

class SavedSearchPutResolver : DefaultPutResolver<SavedSearch>() {
    override fun mapToInsertQuery(obj: SavedSearch) =
        InsertQuery
            .builder()
            .table(TABLE)
            .build()

    override fun mapToUpdateQuery(obj: SavedSearch) =
        UpdateQuery
            .builder()
            .table(TABLE)
            .where("$COL_ID = ?")
            .whereArgs(obj.id)
            .build()

    override fun mapToContentValues(obj: SavedSearch) =
        ContentValues(5).apply {
            put(COL_ID, obj.id)
            put(COL_SOURCE, obj.source)
            put(COL_NAME, obj.name)
            put(COL_QUERY, obj.query)
            put(COL_FILTERS_JSON, obj.filtersJson)
        }
}

class SavedSearchGetResolver : DefaultGetResolver<SavedSearch>() {
    override fun mapFromCursor(cursor: Cursor): SavedSearch =
        SavedSearchImpl().apply {
            id = cursor.getLong(cursor.getColumnIndex(COL_ID))
            source = cursor.getLong(cursor.getColumnIndex(COL_SOURCE))
            name = cursor.getString(cursor.getColumnIndex(COL_NAME))
            query = cursor.getString(cursor.getColumnIndex(COL_QUERY))
            filtersJson = cursor.getString(cursor.getColumnIndex(COL_FILTERS_JSON))
        }
}

class SavedSearchDeleteResolver : DefaultDeleteResolver<SavedSearch>() {
    override fun mapToDeleteQuery(obj: SavedSearch) =
        DeleteQuery
            .builder()
            .table(TABLE)
            .where("$COL_ID = ?")
            .whereArgs(obj.id)
            .build()
}
