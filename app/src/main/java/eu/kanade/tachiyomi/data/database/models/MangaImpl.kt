package eu.kanade.tachiyomi.data.database.models

import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.library.CustomMangaManager
import eu.kanade.tachiyomi.source.model.SManga
import uy.kohesive.injekt.injectLazy

open class MangaImpl : Manga {

    override var id: Long? = null

    override var source: Long = -1

    override lateinit var url: String

    private val customMangaManager: CustomMangaManager by injectLazy()

    override var title: String
        get() = if (favorite) {
            val customTitle = customMangaManager.getManga(this)?.title
            if (customTitle.isNullOrBlank()) ogTitle else customTitle
        } else {
            ogTitle
        }
        set(value) {
            ogTitle = value
        }

    override var author: String?
        get() = if (favorite) customMangaManager.getManga(this)?.author ?: ogAuthor else ogAuthor
        set(value) { ogAuthor = value }

    override var artist: String?
        get() = if (favorite) customMangaManager.getManga(this)?.artist ?: ogArtist else ogArtist
        set(value) { ogArtist = value }

    override var description: String?
        get() = if (favorite) customMangaManager.getManga(this)?.description ?: ogDesc else ogDesc
        set(value) { ogDesc = value }

    override var genre: String?
        get() = if (favorite) customMangaManager.getManga(this)?.genre ?: ogGenre else ogGenre
        set(value) { ogGenre = value }

    override var status: Int = 0

    override var thumbnail_url: String? = null

    override var favorite: Boolean = false

    override var last_update: Long = 0

    override var initialized: Boolean = false

    override var viewer: Int = -1

    override var chapter_flags: Int = 0

    override var hide_title: Boolean = false

    override var date_added: Long = 0

    lateinit var ogTitle: String
        private set
    var ogAuthor: String? = null
        private set
    var ogArtist: String? = null
        private set
    var ogDesc: String? = null
        private set
    var ogGenre: String? = null
        private set

    override fun copyFrom(other: SManga) {
        if (other is MangaImpl && other::ogTitle.isInitialized &&
            !other.title.isBlank() && other.ogTitle != ogTitle
        ) {
            val oldTitle = ogTitle
            title = other.ogTitle
            val db: DownloadManager by injectLazy()
            val provider = DownloadProvider(db.context)
            provider.renameMangaFolder(oldTitle, ogTitle, source)
        }
        super.copyFrom(other)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val manga = other as Manga

        return url == manga.url
    }

    override fun hashCode(): Int {
        return if (::url.isInitialized) url.hashCode()
        else (id ?: 0L).hashCode()
    }
}
