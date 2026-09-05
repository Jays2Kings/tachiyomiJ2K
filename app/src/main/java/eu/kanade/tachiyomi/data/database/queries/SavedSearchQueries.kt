package eu.kanade.tachiyomi.data.database.queries

import com.pushtorefresh.storio.sqlite.queries.Query
import eu.kanade.tachiyomi.data.database.DbProvider
import eu.kanade.tachiyomi.data.database.models.SavedSearch
import eu.kanade.tachiyomi.data.database.tables.SavedSearchTable

interface SavedSearchQueries : DbProvider {
    fun getSavedSearches(sourceId: Long) =
        db
            .get()
            .listOfObjects(SavedSearch::class.java)
            .withQuery(
                Query
                    .builder()
                    .table(SavedSearchTable.TABLE)
                    .where("${SavedSearchTable.COL_SOURCE} = ?")
                    .whereArgs(sourceId)
                    .build(),
            ).prepare()

    fun insertSavedSearch(savedSearch: SavedSearch) = db.put().`object`(savedSearch).prepare()

    fun deleteSavedSearch(savedSearch: SavedSearch) = db.delete().`object`(savedSearch).prepare()
}
