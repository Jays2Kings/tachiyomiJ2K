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
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.CategoryImpl
import eu.kanade.tachiyomi.data.database.tables.CategoryTable.COL_FLAGS
import eu.kanade.tachiyomi.data.database.tables.CategoryTable.COL_ID
import eu.kanade.tachiyomi.data.database.tables.CategoryTable.COL_MANGA_ORDER
import eu.kanade.tachiyomi.data.database.tables.CategoryTable.COL_NAME
import eu.kanade.tachiyomi.data.database.tables.CategoryTable.COL_ORDER
import eu.kanade.tachiyomi.data.database.tables.CategoryTable.TABLE

class CategoryTypeMapping : SQLiteTypeMapping<Category>(
    CategoryPutResolver(),
    CategoryGetResolver(),
    CategoryDeleteResolver()
)

class CategoryPutResolver : DefaultPutResolver<Category>() {

    override fun mapToInsertQuery(obj: Category) = InsertQuery.builder()
        .table(TABLE)
        .build()

    override fun mapToUpdateQuery(obj: Category) = UpdateQuery.builder()
        .table(TABLE)
        .where("$COL_ID = ?")
        .whereArgs(obj.id)
        .build()

    override fun mapToContentValues(obj: Category) = ContentValues(4).apply {
        put(COL_ID, obj.id)
        put(COL_NAME, obj.name)
        put(COL_ORDER, obj.order)
        put(COL_FLAGS, obj.flags)
        if (obj.mangaSort != null)
            put(COL_MANGA_ORDER, obj.mangaSort.toString())
        else {
            val orderString = obj.mangaOrder.joinToString("/")
            put(COL_MANGA_ORDER, orderString)
        }
    }
}

class CategoryGetResolver : DefaultGetResolver<Category>() {

    override fun mapFromCursor(cursor: Cursor): Category = CategoryImpl().apply {
        id = cursor.getInt(cursor.getColumnIndex(COL_ID))
        name = cursor.getString(cursor.getColumnIndex(COL_NAME))
        order = cursor.getInt(cursor.getColumnIndex(COL_ORDER))
        flags = cursor.getInt(cursor.getColumnIndex(COL_FLAGS))

        val orderString = cursor.getString(cursor.getColumnIndex(COL_MANGA_ORDER))
        when {
            orderString.isNullOrBlank() -> {
                mangaSort = 'a'
                mangaOrder = emptyList()
            }
            orderString.firstOrNull()?.isLetter() == true -> {
                mangaSort = orderString.first()
                mangaOrder = emptyList()
            }
            else -> mangaOrder = orderString.split("/")?.mapNotNull { it.toLongOrNull() }
        }
    }
}

class CategoryDeleteResolver : DefaultDeleteResolver<Category>() {

    override fun mapToDeleteQuery(obj: Category) = DeleteQuery.builder()
        .table(TABLE)
        .where("$COL_ID = ?")
        .whereArgs(obj.id)
        .build()
}
