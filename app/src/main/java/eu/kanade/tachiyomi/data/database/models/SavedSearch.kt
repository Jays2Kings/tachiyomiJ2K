package eu.kanade.tachiyomi.data.database.models

import java.io.Serializable

interface SavedSearch : Serializable {
    var id: Long?

    var source: Long

    var name: String

    var query: String?

    var filtersJson: String?

    companion object {
        fun create(source: Long): SavedSearch =
            SavedSearchImpl().apply {
                this.source = source
            }
    }
}
