package eu.kanade.tachiyomi.data.database.models

class LibraryManga : MangaImpl() {

    var unread: Int = 0

    var category: Int = 0

    companion object {
        fun createBlank(categoryId: Int): LibraryManga = LibraryManga().apply {
            title = ""
            id = Long.MIN_VALUE
            category = categoryId
        }

        fun createHide(categoryId: Int, title: String): LibraryManga =
            createBlank(categoryId).apply {
                    this.title = title
                    status = -1
                }
    }
}
