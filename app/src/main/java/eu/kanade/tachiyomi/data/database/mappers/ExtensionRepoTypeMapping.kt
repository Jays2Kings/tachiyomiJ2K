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
import eu.kanade.tachiyomi.data.database.tables.ExtensionRepoTable.COL_BASE_URL
import eu.kanade.tachiyomi.data.database.tables.ExtensionRepoTable.COL_NAME
import eu.kanade.tachiyomi.data.database.tables.ExtensionRepoTable.COL_SHORT_NAME
import eu.kanade.tachiyomi.data.database.tables.ExtensionRepoTable.COL_SIGNING_KEY_FINGERPRINT
import eu.kanade.tachiyomi.data.database.tables.ExtensionRepoTable.COL_WEBSITE
import eu.kanade.tachiyomi.data.database.tables.ExtensionRepoTable.TABLE
import eu.kanade.tachiyomi.extension.model.ExtensionRepo

/**
 * Defines the mapping between [ExtensionRepo] objects and the database table.
 */
class ExtensionRepoTypeMapping :
    SQLiteTypeMapping<ExtensionRepo>(
        ExtensionRepoPutResolver(),
        ExtensionRepoGetResolver(),
        ExtensionRepoDeleteResolver(),
    )

/**
 * Resolver for putting [ExtensionRepo] objects into the database.
 */
class ExtensionRepoPutResolver : DefaultPutResolver<ExtensionRepo>() {
    /**
     * Creates the insert query for an [ExtensionRepo].
     * Note: This resolver is primarily used for updates/upserts via [mapToUpdateQuery]
     * and [mapToContentValues] with appropriate `StorIOSQLite.put()` operations.
     * A direct insert might fail on constraint violations if not handled carefully.
     */
    override fun mapToInsertQuery(obj: ExtensionRepo) =
        InsertQuery
            .builder()
            .table(TABLE)
            .build()

    /**
     * Creates the update query for an [ExtensionRepo].
     * Uses the [COL_BASE_URL] as the key for updates.
     */
    override fun mapToUpdateQuery(obj: ExtensionRepo) =
        UpdateQuery
            .builder()
            .table(TABLE)
            .where("$COL_BASE_URL = ?")
            .whereArgs(obj.baseUrl)
            .build()

    /**
     * Maps an [ExtensionRepo] object to [ContentValues] for database insertion or update.
     */
    override fun mapToContentValues(obj: ExtensionRepo) =
        ContentValues(5).apply {
            put(COL_BASE_URL, obj.baseUrl)
            put(COL_NAME, obj.name)
            put(COL_SHORT_NAME, obj.shortName) // Handles null automatically
            put(COL_WEBSITE, obj.website)
            put(COL_SIGNING_KEY_FINGERPRINT, obj.signingKeyFingerprint)
        }
}

/**
 * Resolver for getting [ExtensionRepo] objects from the database cursor.
 */
class ExtensionRepoGetResolver : DefaultGetResolver<ExtensionRepo>() {
    /**
     * Maps the current row of the [Cursor] to an [ExtensionRepo] object.
     */
    override fun mapFromCursor(cursor: Cursor): ExtensionRepo {
        val baseUrl = cursor.getString(cursor.getColumnIndexOrThrow(COL_BASE_URL))
        val name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME))
        val shortName = cursor.getString(cursor.getColumnIndexOrThrow(COL_SHORT_NAME)) // Returns null if column is null
        val website = cursor.getString(cursor.getColumnIndexOrThrow(COL_WEBSITE))
        val signingKeyFingerprint = cursor.getString(cursor.getColumnIndexOrThrow(COL_SIGNING_KEY_FINGERPRINT))

        return ExtensionRepo(
            baseUrl = baseUrl,
            name = name,
            shortName = shortName,
            website = website,
            signingKeyFingerprint = signingKeyFingerprint,
        )
    }
}

/**
 * Resolver for deleting [ExtensionRepo] objects from the database.
 */
class ExtensionRepoDeleteResolver : DefaultDeleteResolver<ExtensionRepo>() {
    /**
     * Creates the delete query for an [ExtensionRepo].
     * Uses the [COL_BASE_URL] as the key for deletion.
     */
    override fun mapToDeleteQuery(obj: ExtensionRepo) =
        DeleteQuery
            .builder()
            .table(TABLE)
            .where("$COL_BASE_URL = ?")
            .whereArgs(obj.baseUrl)
            .build()
}
