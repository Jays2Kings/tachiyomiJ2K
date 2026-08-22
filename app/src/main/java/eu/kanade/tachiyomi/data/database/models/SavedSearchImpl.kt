package eu.kanade.tachiyomi.data.database.models

class SavedSearchImpl : SavedSearch {
    override var id: Long? = null

    override var source: Long = 0

    override lateinit var name: String

    override var query: String? = null

    override var filtersJson: String? = null
}
