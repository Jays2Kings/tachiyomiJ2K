package eu.kanade.tachiyomi.source.model

import eu.kanade.tachiyomi.data.database.models.MangaImpl
import java.io.Serializable

interface SManga : Serializable {

    var url: String

    var title: String

    var artist: String?

    var author: String?

    var description: String?

    var genre: String?

    var status: Int

    var thumbnail_url: String?

    var initialized: Boolean

    val originalTitle: String
        get() = (this as? MangaImpl)?.ogTitle ?: title
    val originalAuthor: String?
        get() = (this as? MangaImpl)?.ogAuthor ?: author
    val originalArtist: String?
        get() = (this as? MangaImpl)?.ogArtist ?: artist
    val originalDescription: String?
        get() = (this as? MangaImpl)?.ogDesc ?: description
    val originalGenre: String?
        get() = (this as? MangaImpl)?.ogGenre ?: genre

    fun copyFrom(other: SManga) {
        if (other.author != null)
            author = other.originalAuthor

        if (other.artist != null)
            artist = other.originalArtist

        if (other.description != null)
            description = other.originalDescription

        if (other.genre != null)
            genre = other.originalGenre

        if (other.thumbnail_url != null)
            thumbnail_url = other.thumbnail_url

        status = other.status

        if (!initialized)
            initialized = other.initialized
    }

    companion object {
        const val UNKNOWN = 0
        const val ONGOING = 1
        const val COMPLETED = 2
        const val LICENSED = 3

        fun create(): SManga {
            return MangaImpl()
        }
    }
}
