package eu.kanade.tachiyomi.data.database.tables

object SavedSearchTable {
    const val TABLE = "saved_search"

    const val COL_ID = "_id"

    const val COL_SOURCE = "source"

    const val COL_NAME = "name"

    const val COL_QUERY = "query"

    const val COL_FILTERS_JSON = "filters_json"

    val createTableQuery: String
        get() =
            """CREATE TABLE $TABLE(
            $COL_ID INTEGER NOT NULL PRIMARY KEY,
            $COL_SOURCE INTEGER NOT NULL,
            $COL_NAME TEXT NOT NULL,
            $COL_QUERY TEXT,
            $COL_FILTERS_JSON TEXT
            )"""

    val createSourceIndexQuery: String
        get() = "CREATE INDEX ${TABLE}_${COL_SOURCE}_index ON $TABLE($COL_SOURCE)"
}
